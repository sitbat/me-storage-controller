package dev.mestorage.controller.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.CellState;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.me.cells.BasicCellInventory;
import dev.mestorage.controller.storage.compat.OmniCellCapacity;
import appeng.parts.AEBasePart;
import dev.mestorage.controller.storage.compat.StorageCompatibility;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * Server-thread-only views of existing AE inventories. Never creates a second cell
 * inventory from its ItemStack, and never inserts/extracts network contents.
 * Callers must check controller access and {@link #isCurrent} before slot actions.
 */
public final class StorageScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageScanner.class);
    private static final Set<Class<?>> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();
    private StorageScanner() {}

    public enum Kind { DRIVE, CHEST, EXTERNAL, OTHER }

    public record Device(String id, Component name, @Nullable GlobalPos location,
                         @Nullable Direction side, Kind kind, MEStorage storage,
                         @Nullable IItemHandler cells, boolean active,
                         IGridNode node, Object owner) {}

    /** -1 is unknown; UNLIMITED is an explicitly unbounded upper limit. */
    public record Capacity(long usedBytes, long totalBytes, long usedTypes, long totalTypes,
                           boolean partial, int unknownCells) {
        public static final long UNLIMITED = -2;
        public static final Capacity UNKNOWN = new Capacity(-1, -1, -1, -1, true, 1);
        public boolean known() { return usedBytes >= 0 && (totalBytes >= 0 || totalBytes == UNLIMITED); }
    }

    public record CellInfo(int slot, ItemStack stack, @Nullable MEStorage storage,
                           Capacity capacity, CellState state, boolean readable) {}

    /** Physical target capacity, independently of storage-bus filters. */
    public record ExternalCapacity(long occupiedSlots, long totalSlots, long fluidAmount, long fluidCapacity) {
        public static final ExternalCapacity UNKNOWN = new ExternalCapacity(-1, -1, -1, -1);
    }

    /** Bus attachment face is separate from the target's accessed face. */
    public record TargetInfo(Component name, GlobalPos location, Direction face, String adapter) {}

    public static @Nullable TargetInfo targetInfo(Device device) {
        if (!StorageCompatibility.isExternalStorageBus(device.owner())) return null;
        var bus = (AEBasePart) device.owner();
        var level = bus.getLevel();
        if (level == null) return null;
        var pos = bus.getBlockEntity().getBlockPos().relative(bus.getSide());
        var location = GlobalPos.of(level.dimension(), pos);
        var face = bus.getSide().getOpposite();
        if (!level.hasChunkAt(pos)) return new TargetInfo(Component.literal("?"), location, face, "unloaded");
        try {
            var entity = level.getBlockEntity(pos);
            var name = entity instanceof Nameable named ? named.getName() : level.getBlockState(pos).getBlock().getName();
            String adapter = "unknown";
            if (entity != null) {
                if (entity.getCapability(Capabilities.STORAGE, face).orElse(null) != null) {
                    adapter = "me";
                } else {
                    boolean item = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null) != null;
                    boolean fluid = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElse(null) != null;
                    adapter = item && fluid ? "items+fluids" : item ? "items" : fluid ? "fluids" : "unknown";
                }
            }
            return new TargetInfo(name, location, face, adapter);
        } catch (RuntimeException exception) {
            reportFailure(device.owner(), exception);
            markDegraded(device);
            return new TargetInfo(Component.literal("?"), location, face, "unknown");
        }
    }

    public static List<Device> discover(IGrid grid) {
        var result = new ArrayList<Device>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var node : grid.getNodes()) {
            var provider = node.getService(IStorageProvider.class);
            var owner = node.getOwner();
            if (provider == null || !visited.add(provider)) continue;
            var location = locationOf(owner);
            var side = owner instanceof AEBasePart part ? part.getSide() : null;
            var name = nameOf(owner, node);
            Kind kind = owner instanceof DriveBlockEntity ? Kind.DRIVE
                    : owner instanceof ChestBlockEntity ? Kind.CHEST
                    : StorageCompatibility.isExternalStorageBus(owner) ? Kind.EXTERNAL : Kind.OTHER;
            IItemHandler cells = null;
            boolean failed = false;
            try {
                if (owner instanceof DriveBlockEntity drive) {
                    cells = drive.getInternalInventory().toItemHandler();
                } else if (owner instanceof ChestBlockEntity chest) {
                    // ME chest slot 0 is an input buffer, not its storage cell.
                    cells = chest.getInternalInventory().getSubInventory(1, 2).toItemHandler();
                }
            } catch (RuntimeException exception) {
                reportFailure(owner, exception);
                failed = true;
            }

            var mounted = new ArrayList<MEStorage>();
            Set<MEStorage> seenInventories = Collections.newSetFromMap(new IdentityHashMap<>());
            try {
                provider.mountInventories((storage, priority) -> {
                    if (storage != null && seenInventories.add(storage)) mounted.add(storage);
                });
            } catch (RuntimeException exception) {
                reportFailure(provider, exception);
                // A provider may have failed halfway through. No partial list is
                // presented as a complete device inventory.
                mounted.clear();
                failed = true;
            }
            var view = readOnly(name, mounted);
            view.failed = failed;
            String id = location == null
                    ? "virtual:" + owner.getClass().getName() + ":" + Integer.toUnsignedString(System.identityHashCode(owner))
                    : location.dimension().location() + ":" + location.pos().asLong() + ":"
                    + (side == null ? "block" : side.getName());
            result.add(new Device(id, name, location, side, kind, view, cells,
                    node.isActive(), node, owner));
        }
        result.sort(Comparator.comparing(Device::id));
        return List.copyOf(result);
    }

    /** Re-check after every network/selection change; never load a missing chunk. */
    public static boolean isCurrent(Device device, IGrid grid) {
        try {
            if (device.node().getGrid() != grid || device.node().getOwner() != device.owner()) return false;
            var location = device.location();
            if (location == null) return true;
            var level = device.node().getLevel();
            if (!level.dimension().equals(location.dimension()) || !level.hasChunkAt(location.pos())) return false;
            var entity = level.getBlockEntity(location.pos());
            if (device.owner() instanceof BlockEntity) return entity == device.owner() && !entity.isRemoved();
            if (device.owner() instanceof AEBasePart part) {
                return entity == part.getBlockEntity() && !entity.isRemoved()
                        && part.getHost().getPart(part.getSide()) == part;
            }
            return true;
        } catch (RuntimeException staleNode) {
            return false;
        }
    }

    public static List<CellInfo> readCells(Device device) {
        return readCells(device,Integer.MAX_VALUE);
    }

    /** Read up to the requested physical slot count; full directories request every slot. */
    public static List<CellInfo> readCells(Device device,int limit) {
        if (!(device.owner() instanceof IChestOrDrive host)) return List.of();
        var cells = new ArrayList<CellInfo>();
        int count = cellCount(device);
        if (count < 0) return List.of(new CellInfo(0, ItemStack.EMPTY, null, Capacity.UNKNOWN, CellState.ABSENT, false));
        for (int slot = 0; slot < Math.min(count,Math.max(0,limit)); slot++) {
            ItemStack stack = ItemStack.EMPTY;
            try {
                if (device.cells() != null && slot < device.cells().getSlots()) {
                    var real = device.cells().getStackInSlot(slot);
                    // Storage-cell NBT may contain thousands of keys. Only carry its
                    // display item and optional custom name in a scanner snapshot.
                    if (!real.isEmpty()) {
                        stack = new ItemStack(real.getItem());
                        if (real.hasCustomHoverName()) stack.setHoverName(real.getHoverName().copy());
                    }
                }
                if (stack.isEmpty() && device.cells() == null && host.getCellItem(slot) != null) {
                    // Third-party IChestOrDrive hosts can still expose read-only cell
                    // detail without granting access to a guessed physical inventory.
                    stack = new ItemStack(host.getCellItem(slot));
                }
                var original = host.getOriginalCellInventory(slot);
                var inventory = host.getCellInventory(slot);
                Capacity capacity = stack.isEmpty() ? new Capacity(0, 0, 0, 0, false, 0)
                        : original instanceof BasicCellInventory basic
                        ? new Capacity(basic.getUsedBytes(), basic.getTotalBytes(), basic.getStoredItemTypes(),
                        basic.getTotalItemTypes(), false, 0) : OmniCellCapacity.read(original, stack.getItem());
                cells.add(new CellInfo(slot, stack,
                        inventory == null ? null : readOnly(stack.getHoverName(), List.of(inventory)),
                        capacity, host.getCellStatus(slot), inventory != null));
            } catch (RuntimeException exception) {
                reportFailure(host, exception);
                markDegraded(device);
                cells.add(new CellInfo(slot, stack, null, Capacity.UNKNOWN, CellState.ABSENT, false));
            }
        }
        return List.copyOf(cells);
    }

    /** Full count, independent of the number of remote slots shown by a menu. */
    public static int cellCount(Device device) {
        if (!(device.owner() instanceof IChestOrDrive host)) return 0;
        try {
            return host.getCellCount();
        } catch (RuntimeException exception) {
            reportFailure(host, exception);
            markDegraded(device);
            return -1;
        }
    }

    public static boolean isDegraded(Device device) { return hasReadError(device.storage()); }

    public static boolean hasReadError(@Nullable MEStorage storage) {
        return storage instanceof ReadOnlyStorage view && view.failed;
    }

    /** Known cell capacity only: external capacities must not be added as bytes. */
    public static Capacity capacity(Device device) {
        if (!(device.owner() instanceof IChestOrDrive) || isDegraded(device)) return Capacity.UNKNOWN;
        return aggregateCapacity(readCells(device));
    }

    public static Capacity aggregateCapacity(List<CellInfo> cells) {
        long used = 0, total = 0, types = 0, maxTypes = 0;
        boolean known = false, partial = false;
        int unknown = 0;
        for (var cell : cells) {
            if (cell.stack().isEmpty() && cell.capacity().unknownCells() == 0) continue;
            var capacity = cell.capacity();
            // Entirely unknown addons stay outside the known sum. A partially known
            // capacity still contributes its limits, including explicit infinity.
            if (capacity.totalBytes() == -1 && capacity.totalTypes() == -1
                    && capacity.usedBytes() == -1 && capacity.usedTypes() == -1) {
                partial = true;
                unknown += Math.max(1, capacity.unknownCells());
                continue;
            }
            known = true;
            used = sumUsed(used, capacity.usedBytes());
            total = sumLimits(total, capacity.totalBytes());
            types = sumUsed(types, capacity.usedTypes());
            maxTypes = sumLimits(maxTypes, capacity.totalTypes());
            partial |= capacity.partial();
            unknown += capacity.unknownCells();
        }
        return known || unknown == 0 ? new Capacity(used, total, types, maxTypes, partial, unknown)
                : new Capacity(-1, -1, -1, -1, true, unknown);
    }

    /** Unknown usage propagates; it must not turn into a small negative subtraction. */
    public static long sumUsed(long left, long right) {
        return left < 0 || right < 0 ? -1 : saturatedAdd(left, right);
    }

    /** An unlimited member guarantees an unlimited sum, even with unknown members. */
    public static long sumLimits(long left, long right) {
        if (left == Capacity.UNLIMITED || right == Capacity.UNLIMITED) return Capacity.UNLIMITED;
        return sumUsed(left, right);
    }

    public static KeyCounter contents(@Nullable MEStorage storage) {
        var result = new KeyCounter();
        if (storage != null) storage.getAvailableStacks(result);
        result.removeZeros();
        return result;
    }

    /** Do not sum device rows: shared external inventories may overlap. */
    public static KeyCounter gridContents(IGrid grid) {
        var result = new KeyCounter();
        result.addAll(grid.getStorageService().getCachedInventory());
        result.removeZeros();
        return result;
    }

    public static ExternalCapacity externalCapacity(Device device) {
        try {
            return readExternalCapacity(device);
        } catch (RuntimeException exception) {
            reportFailure(device.owner(), exception);
            markDegraded(device);
            return ExternalCapacity.UNKNOWN;
        }
    }

    private static ExternalCapacity readExternalCapacity(Device device) {
        if (!StorageCompatibility.isExternalStorageBus(device.owner())) return ExternalCapacity.UNKNOWN;
        var bus = (AEBasePart) device.owner();
        var level = bus.getLevel();
        var target = bus.getBlockEntity().getBlockPos().relative(bus.getSide());
        if (level == null || !level.hasChunkAt(target)) return ExternalCapacity.UNKNOWN;
        var blockEntity = level.getBlockEntity(target);
        if (blockEntity == null) return ExternalCapacity.UNKNOWN;
        var face = bus.getSide().getOpposite();
        // AE2 prioritizes the ME storage capability over normal item/fluid
        // handlers. An interface's nine-slot input buffer is not its network's
        // capacity and must never be reported as such.
        if (blockEntity.getCapability(Capabilities.STORAGE, face).orElse(null) != null) return ExternalCapacity.UNKNOWN;
        var items = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
        var fluids = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, face).orElse(null);
        long occupied = -1, slots = -1, amount = -1, capacity = -1;
        if (items != null) {
            slots = items.getSlots();
            occupied = 0;
            for (int i = 0; i < slots; i++) if (!items.getStackInSlot(i).isEmpty()) occupied++;
        }
        if (fluids != null) {
            amount = 0;
            capacity = 0;
            for (int i = 0; i < fluids.getTanks(); i++) {
                amount = saturatedAdd(amount, fluids.getFluidInTank(i).getAmount());
                int tankCapacity = fluids.getTankCapacity(i);
                // Forge has no infinity flag. MAX_VALUE is commonly a creative
                // or unbounded sentinel; do not claim it is a finite limit.
                capacity = tankCapacity == Integer.MAX_VALUE ? -1 : saturatedAdd(capacity, tankCapacity);
            }
        }
        return new ExternalCapacity(occupied, slots, amount, capacity);
    }

    private static @Nullable GlobalPos locationOf(Object owner) {
        var entity = owner instanceof BlockEntity be ? be
                : owner instanceof AEBasePart part ? part.getBlockEntity() : null;
        return entity == null || entity.getLevel() == null ? null
                : GlobalPos.of(entity.getLevel().dimension(), entity.getBlockPos());
    }

    private static Component nameOf(Object owner, IGridNode node) {
        if (owner instanceof Nameable nameable) return nameable.getName();
        var icon = node.getVisualRepresentation();
        if (icon != null) return icon.getDisplayName();
        if (owner instanceof BlockEntity entity) return entity.getBlockState().getBlock().getName();
        return Component.literal(owner.getClass().getSimpleName());
    }

    private static ReadOnlyStorage readOnly(Component name, List<MEStorage> storages) {
        return new ReadOnlyStorage(name, storages);
    }

    private static void markDegraded(Device device) {
        if (device.storage() instanceof ReadOnlyStorage view) view.failed = true;
    }

    private static void reportFailure(Object implementation, RuntimeException exception) {
        if (REPORTED_FAILURES.add(implementation.getClass())) {
            LOGGER.warn("Storage controller could not read addon storage {}; showing unknown information", implementation.getClass().getName(), exception);
        }
    }

    private static final class ReadOnlyStorage implements MEStorage {
        private final Component name;
        private final List<MEStorage> storages;
        private boolean failed;

        private ReadOnlyStorage(Component name, List<MEStorage> storages) {
            this.name = name;
            this.storages = List.copyOf(storages);
        }

        @Override public Component getDescription() { return name; }
        @Override public void getAvailableStacks(KeyCounter out) {
            for (var storage : storages) {
                try {
                    // Do not leak a provider's half-written tally if it throws.
                    var local = new KeyCounter();
                    storage.getAvailableStacks(local);
                    out.addAll(local);
                } catch (RuntimeException exception) {
                    failed = true;
                    reportFailure(storage, exception);
                }
            }
        }
    }

    public static long saturatedAdd(long left, long right) {
        if (left < 0 || right < 0) return -1;
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
