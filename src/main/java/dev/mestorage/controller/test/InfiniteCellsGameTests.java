package dev.mestorage.controller.test;

import java.util.List;
import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.items.contents.CellConfig;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.folder.FolderBook;
import dev.mestorage.controller.folder.FolderService;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Network;
import dev.mestorage.controller.network.DirectoryTransfer;
import dev.mestorage.controller.network.Snapshot;
import dev.mestorage.controller.storage.StorageScanner;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Uses real optional ExtendedAE cells and mounted AE inventories, without an addon compile dependency. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class InfiniteCellsGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final AEFluidKey LAVA = AEFluidKey.of(Fluids.LAVA);
    private static final AEItemKey MATTER = AEItemKey.of(AEItems.MATTER_BALL.stack());
    private record Fixture(ControllerBlockEntity controller, DriveBlockEntity infinite, DriveBlockEntity finite) {}

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void nativeCreativeSupplyAndFiniteLongMaximumRemainDistinctOnWire(GameTestHelper h) {
        h.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block()); h.setBlock(POS, MEStorageController.CONTROLLER.get());
        var drive = drive(h, POS.east(), Direction.EAST);
        var stack = AEItems.ITEM_CELL_CREATIVE.stack();
        CellConfig.create(stack).setStack(0, new GenericStack(MATTER, 0));
        drive.getInternalInventory().setItemDirect(0, stack);
        var f = new Fixture((ControllerBlockEntity) h.getBlockEntity(POS), drive, drive);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            var cell = StorageScanner.readCells(device(f, drive)).get(0);
            h.assertTrue(cell.capacity().usedBytes() == 0 && cell.capacity().totalBytes() == -2
                            && cell.capacity().usedTypes() == 1 && cell.capacity().totalTypes() == 63,
                    "Native AE creative configured supplier must retain its real 63-type limit");
            var menu = select(h, f, device(f, drive).id(), 0);
            var s = menu.getSnapshot(); assertEntry(h, s, MATTER, true, -1);
            var finiteKey = AEItemKey.of(Items.GOLD_INGOT);
            // The public two-argument constructor must not infer infinity from a sentinel-sized amount.
            var contents = List.of(s.contents().get(0), new Snapshot.Content(finiteKey, Long.MAX_VALUE));
            var withFinite = new Snapshot(s.online(), s.error(), s.selectedDevice(), s.selectedCell(), s.devices(), s.devicePage(),
                    s.devicePages(), s.deviceCount(), s.title(), s.capacity(), contents, 0, 1, 2, s.cellSlots(), s.editableSlots(),
                    s.cells(), s.selectedInfo(), s.revision(), s.directory(), s.directoryTotalDevices());
            var wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                withFinite.write(wire); var decoded = Snapshot.read(wire);
                assertEntry(h, decoded, MATTER, true, -1);
                assertEntry(h, decoded, finiteKey, false, Long.MAX_VALUE);
            } finally { wire.release(); }
            action(menu, MATTER, 0);
            h.assertTrue(menu.getCarried().is(MATTER.getItem()) && menu.getCarried().getCount() == 64,
                    "Native creative supplier must still extract through the actual menu");
            action(menu, null, 0);
            h.assertTrue(menu.getCarried().isEmpty(), "Native creative supplier must retain normal insertion semantics");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void extendedInfinityPreviewPreservesRecordWithoutCopyingStorageNbt(GameTestHelper h) {
        if (skip(h)) return;
        var f = fixture(h);
        for (int slot = 0; slot < 3; slot++)
            f.infinite.getInternalInventory().getStackInSlot(slot).getOrCreateTag().putByteArray("unrelated_large_payload", new byte[65536]);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            var cells = StorageScanner.readCells(device(f, f.infinite));
            for (int slot = 0; slot < 3; slot++) {
                var real = f.infinite.getInternalInventory().getStackInSlot(slot);
                var preview = cells.get(slot);
                h.assertTrue(preview.readable() && preview.stack().getHoverName().equals(real.getHoverName()),
                        "Infinity preview changed its record-dependent name at slot " + slot);
                h.assertTrue(preview.stack().hasTag() && preview.stack().getTag().getCompound("record").equals(real.getTag().getCompound("record")),
                        "Infinity preview lost or replaced lava/matter record at slot " + slot);
                h.assertTrue(!preview.stack().getTag().contains("unrelated_large_payload") && real.getTag().getByteArray("unrelated_large_payload").length == 65536,
                        "Sanitizing preview must omit unrelated NBT without mutating the actual cell");
                var cap = preview.capacity();
                h.assertTrue(cap.known() && !cap.partial() && cap.usedBytes() == 0 && cap.totalBytes() == -2
                                && cap.usedTypes() == 1 && cap.totalTypes() == 1,
                        "Real infinite supplier must show 0/unlimited bytes and one configured type: " + cap);
            }
            var menu = select(h, f, device(f, f.infinite).id(), -1);
            // Tree entries take a second sanitization path, separate from ordinary Snapshot cells.
            var cursor = new DirectoryTransfer.Cursor(1, menu.getSnapshot().directory());
            var assembler = new DirectoryTransfer.Assembler();
            List<Snapshot.DirectoryEntry> received = null;
            while (!cursor.finished()) {
                var frameWire = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    cursor.next().write(frameWire);
                    var complete = assembler.accept(DirectoryTransfer.Frame.read(frameWire));
                    if (complete != null) received = complete;
                } finally { frameWire.release(); }
            }
            h.assertTrue(received != null, "Actual directory wire generation did not finish");
            var treeCells = received.stream().filter(entry -> entry.device().id().equals(device(f, f.infinite).id()))
                    .findFirst().orElseThrow().cells();
            for (int slot = 0; slot < 3; slot++) {
                var icon = treeCells.get(slot).icon(); var expected = cells.get(slot).stack();
                h.assertTrue(icon.getHoverName().equals(expected.getHoverName()) && icon.hasTag()
                                && icon.getTag().getCompound("record").equals(expected.getTag().getCompound("record"))
                                && !icon.getTag().contains("unrelated_large_payload"),
                        "Directory Cursor/Assembler lost actual infinity-cell record/name or copied unrelated NBT at slot " + slot);
            }
            var wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                menu.getSnapshot().write(wire);
                var decoded = Snapshot.read(wire);
                h.assertTrue(decoded.cells().size() >= 3 && decoded.capacity().totalBytes() == -2,
                        "Wire snapshot lost infinite drive capacity or previews");
                for (int slot = 0; slot < 3; slot++) {
                    var icon = decoded.cells().get(slot).icon();
                    h.assertTrue(icon.getHoverName().equals(cells.get(slot).stack().getHoverName())
                                    && icon.getTag().getCompound("record").equals(cells.get(slot).stack().getTag().getCompound("record"))
                                    && !icon.getTag().contains("unrelated_large_payload"),
                            "Serialized preview changed its configured key or included unrelated NBT");
                }
                assertEntry(h, decoded, LAVA, true, -1);
                assertEntry(h, decoded, MATTER, true, -1);
            } finally { wire.release(); }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void infiniteFlagsFollowNetworkDeviceCellAndFolderScopeWithoutContamination(GameTestHelper h) {
        if (skip(h)) return;
        var f = fixture(h);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            seedFinite(h, f);
            var infiniteId = device(f, f.infinite).id(); var finiteId = device(f, f.finite).id();
            assertEntry(h, select(h, f, "", -1).getSnapshot(), LAVA, true, -1);
            var drive = select(h, f, infiniteId, -1).getSnapshot();
            assertEntry(h, drive, LAVA, true, -1); assertEntry(h, drive, MATTER, true, -1);
            h.assertTrue(drive.contents().size() == 2, "Two lava infinity cells must produce one visible nonzero lava entry, without losing the matter entry");
            assertEntry(h, select(h, f, infiniteId, 0).getSnapshot(), LAVA, true, -1);
            assertEntry(h, select(h, f, infiniteId, 1).getSnapshot(), MATTER, true, -1);
            assertEntry(h, select(h, f, finiteId, 0).getSnapshot(), LAVA, false, 4000);
            assertEntry(h, select(h, f, finiteId, 1).getSnapshot(), MATTER, false, 123);
            var finiteFolder = folder(h, f, "Finite only", List.of(new FolderBook.MemberRef(finiteId, -1)));
            var infiniteFolder = folder(h, f, "Two infinite lava slots", List.of(
                    new FolderBook.MemberRef(infiniteId, 0), new FolderBook.MemberRef(infiniteId, 2)));
            var finite = select(h, f, "folder:" + finiteFolder, -1).getSnapshot();
            assertEntry(h, finite, LAVA, false, 4000); assertEntry(h, finite, MATTER, false, 123);
            h.assertTrue(finite.capacity().totalBytes() == 2048, "Finite folder inherited outside infinite capacity");
            var infinite = select(h, f, "folder:" + infiniteFolder, -1).getSnapshot();
            assertEntry(h, infinite, LAVA, true, -1);
            h.assertTrue(infinite.contents().size() == 1 && infinite.capacity().totalBytes() == -2,
                    "Duplicate-key infinity folder lost its entry or borrowed the outside matter cell");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void actualInfiniteBucketAndMatterTransfersRepeatWithoutTouchingFiniteMembers(GameTestHelper h) {
        if (skip(h)) return;
        var f = fixture(h);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            seedFinite(h, f);
            var before = physical(f);
            var lava = select(h, f, device(f, f.infinite).id(), 0);
            lava.setCarried(new ItemStack(Items.BUCKET));
            for (int repeat = 0; repeat < 12; repeat++) {
                action(lava, LAVA, 0);
                h.assertTrue(lava.getCarried().is(Items.LAVA_BUCKET) && lava.getCarried().getCount() == 1,
                        "Real infinite lava cell failed repeated native bucket extraction " + repeat);
                action(lava, null, 1);
                h.assertTrue(lava.getCarried().is(Items.BUCKET) && lava.getCarried().getCount() == 1,
                        "Real infinite lava cell failed native bucket return " + repeat);
            }
            var matter = select(h, f, device(f, f.infinite).id(), 1);
            for (int repeat = 0; repeat < 12; repeat++) {
                action(matter, MATTER, 0);
                h.assertTrue(matter.getCarried().is(MATTER.getItem()) && matter.getCarried().getCount() == 64,
                        "Real infinite matter cell failed repeated stack extraction " + repeat);
                action(matter, null, 0);
                h.assertTrue(matter.getCarried().isEmpty(), "Infinite matter return must consume the actual carried stack");
            }
            h.assertTrue(physical(f).equals(before), "Infinite resource operations mutated a cell record or neighbouring finite storage NBT");
            var finiteFolder = folder(h, f, "Finite transfer", List.of(new FolderBook.MemberRef(device(f, f.finite).id(), -1)));
            var finite = select(h, f, "folder:" + finiteFolder, -1);
            action(finite, MATTER, 0);
            h.assertTrue(finite.getCarried().getCount() == 64 && amount(f.finite, 1, MATTER) == 59,
                    "Finite folder silently extracted from an infinite cell outside the folder");
            action(finite, null, 0);
            finite.setCarried(new ItemStack(Items.BUCKET)); action(finite, LAVA, 0);
            h.assertTrue(finite.getCarried().is(Items.LAVA_BUCKET) && amount(f.finite, 0, LAVA) == 3000,
                    "Finite folder bucket extraction used an outside infinite source");
            action(finite, null, 1);
            h.assertTrue(finite.getCarried().is(Items.BUCKET) && physical(f).equals(before),
                    "Scoped finite item and bucket roundtrips must restore complete cell NBT");
        }).thenSucceed();
    }

    private static boolean skip(GameTestHelper h) {
        if (ModList.get().isLoaded("expatternprovider")) return false;
        org.slf4j.LoggerFactory.getLogger(InfiniteCellsGameTests.class).info("ME_STORAGE_INFINITY_TEST SKIP: optional ExtendedAE is not loaded");
        h.succeed(); return true;
    }
    private static ItemStack infinity(GameTestHelper h, AEKey key) {
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation("expatternprovider", "infinity_cell"));
        h.assertTrue(item != Items.AIR, "Actual ExtendedAE infinity_cell must be registered");
        var stack = new ItemStack(item); stack.getOrCreateTag().put("record", key.toTagGeneric()); return stack;
    }
    private static Fixture fixture(GameTestHelper h) {
        h.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block()); h.setBlock(POS, MEStorageController.CONTROLLER.get());
        var infinite = drive(h, POS.east(), Direction.EAST); var finite = drive(h, POS.west(), Direction.WEST);
        infinite.getInternalInventory().setItemDirect(0, infinity(h, LAVA));
        infinite.getInternalInventory().setItemDirect(1, infinity(h, MATTER));
        infinite.getInternalInventory().setItemDirect(2, infinity(h, LAVA));
        finite.getInternalInventory().setItemDirect(0, AEItems.FLUID_CELL_1K.stack());
        finite.getInternalInventory().setItemDirect(1, AEItems.ITEM_CELL_1K.stack());
        return new Fixture((ControllerBlockEntity) h.getBlockEntity(POS), infinite, finite);
    }
    private static DriveBlockEntity drive(GameTestHelper h, BlockPos pos, Direction facing) {
        var state = AEBlocks.DRIVE.block().defaultBlockState();
        h.setBlock(pos, IOrientationStrategy.get(state).setFacing(state, facing));
        return (DriveBlockEntity) h.getBlockEntity(pos);
    }
    private static void ready(GameTestHelper h, Fixture f) {
        h.assertTrue(f.controller.getMainNode().isActive() && f.infinite.getMainNode().isActive() && f.finite.getMainNode().isActive(),
                "Actual ExtendedAE test drives must be powered and connected");
    }
    private static void seedFinite(GameTestHelper h, Fixture f) {
        h.assertTrue(f.finite.getCellInventory(0).insert(LAVA, 4000, Actionable.MODULATE, IActionSource.empty()) == 4000
                        && f.finite.getCellInventory(1).insert(MATTER, 123, Actionable.MODULATE, IActionSource.empty()) == 123,
                "Finite comparison cells must accept exact seed quantities");
    }
    private static StorageScanner.Device device(Fixture f, DriveBlockEntity drive) {
        return StorageScanner.discover(f.controller.getMainNode().getGrid()).stream().filter(d -> d.owner() == drive).findFirst().orElseThrow();
    }
    private static ControllerMenu select(GameTestHelper h, Fixture f, String id, int cell) {
        // A fresh real menu per scope avoids same-tick request throttling while exercising every normal scope path.
        var player = h.makeMockSurvivalPlayer(); var pos = h.absolutePos(POS);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        var menu = new ControllerMenu(74, player.getInventory(), f.controller); player.containerMenu = menu;
        menu.handleRequest(new Network.Request(menu.containerId, id, cell, 0, 0, "", "", true, List.of(), List.of(), 1));
        menu.broadcastChanges();
        h.assertTrue(menu.getSnapshot().online() && menu.getSnapshot().error().isEmpty()
                        && menu.getSnapshot().selectedDevice().equals(id) && menu.getSnapshot().selectedCell() == cell
                        && menu.getSnapshot().revision() == 1,
                "Actual infinite-cell test scope did not acknowledge: " + id + "/" + cell + ": " + menu.getSnapshot().error());
        return menu;
    }
    private static UUID folder(GameTestHelper h, Fixture f, String name, List<FolderBook.MemberRef> members) {
        var service = f.controller.getMainNode().getGrid().getService(FolderService.class);
        var result = service.apply(service.view().revision(), new FolderBook.Edit(FolderBook.Op.CREATE, null, null, name, members, List.of()));
        h.assertTrue(result.success() && result.createdId() != null, "Infinity test folder creation failed: " + result.error());
        return result.createdId();
    }
    private static void assertEntry(GameTestHelper h, Snapshot snapshot, AEKey key, boolean infinite, long exact) {
        var entries = snapshot.contents().stream().filter(c -> c.key().equals(key)).toList();
        h.assertTrue(entries.size() == 1 && entries.get(0).amount() > 0 && entries.get(0).infinite() == infinite
                        && (exact < 0 || entries.get(0).amount() == exact),
                "Wrong finite/infinite visible resource in scope " + snapshot.selectedDevice() + "/" + snapshot.selectedCell() + ": " + key + " entries=" + entries);
    }
    private static void action(ControllerMenu menu, AEKey key, int button) {
        var s = menu.getSnapshot();
        menu.handleContentAction(new Network.ContentAction(menu.containerId, s.revision(), s.selectedDevice(), s.selectedCell(), key, button, false,
                menu.getFolderView().revision()));
    }
    private static long amount(DriveBlockEntity drive, int slot, AEKey key) { return StorageScanner.contents(drive.getCellInventory(slot)).get(key); }
    private static List<CompoundTag> physical(Fixture f) {
        var result = new java.util.ArrayList<CompoundTag>();
        for (var drive : List.of(f.infinite, f.finite)) for (int slot = 0; slot < 10; slot++)
            result.add(drive.getInternalInventory().getStackInSlot(slot).save(new CompoundTag()));
        return result;
    }
}
