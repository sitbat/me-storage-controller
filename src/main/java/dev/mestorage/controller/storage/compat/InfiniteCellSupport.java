package dev.mestorage.controller.storage.compat;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import dev.mestorage.controller.storage.StorageScanner.Capacity;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Explicit integrations for creative suppliers, never inferred from their reported amount. */
public final class InfiniteCellSupport {
    private static final String EAE_ITEM = "com.glodblock.github.extendedae.common.items.InfinityCell";
    private static final String EAE_INVENTORY = "com.glodblock.github.extendedae.common.inventory.InfinityCellInventory";
    private static final String AE_CREATIVE_INVENTORY = "appeng.me.cells.CreativeCellInventory";
    private static final int MAX_RECORD_BYTES = 16 * 1024;

    private static final ClassValue<Boolean> EAE_ITEMS = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            for (var current = type; current != null; current = current.getSuperclass()) {
                if (current.getName().equals(EAE_ITEM)) return true;
            }
            return false;
        }
    };

    private InfiniteCellSupport() {}

    /**
     * EAE 1.4.18/1.4.21 uses record for both the name and tooltip image; its
     * absence means water. Carry only that bounded display record, never cell
     * contents, save metadata, or arbitrary addon tags.
     */
    public static ItemStack displayStack(ItemStack real) {
        if (real.isEmpty()) return ItemStack.EMPTY;
        var display = new ItemStack(real.getItem());
        if (EAE_ITEMS.get(real.getItem().getClass()) && real.hasTag()) {
            var record = real.getTag().get("record");
            if (record != null && record.getId() == Tag.TAG_COMPOUND) {
                int bytes = record.sizeInBytes();
                if (bytes >= 0 && bytes <= MAX_RECORD_BYTES) {
                    display.getOrCreateTag().put("record", record.copy());
                } else {
                    // An oversized key cannot enter lightweight directory packets.
                    // Retain its actual display name instead of the default water name.
                    display.setHoverName(real.getHoverName().copy());
                }
            }
        }
        if (real.hasCustomHoverName()) display.setHoverName(real.getHoverName().copy());
        return display;
    }

    /** Exact known implementations: subclasses may change the supply semantics. */
    public static boolean isInfinite(@Nullable MEStorage inventory) {
        if (inventory == null) return false;
        String type = inventory.getClass().getName();
        return type.equals(EAE_INVENTORY) || type.equals(AE_CREATIVE_INVENTORY);
    }

    /** Zero physical bytes retained; unbounded supply is not a finite stored-byte tally. */
    public static Capacity capacity(MEStorage inventory) {
        if (!isInfinite(inventory)) return Capacity.UNKNOWN;
        if (inventory.getClass().getName().equals(EAE_INVENTORY)) {
            return new Capacity(0, Capacity.UNLIMITED, 1, 1, false, 0);
        }
        var contents = new KeyCounter();
        inventory.getAvailableStacks(contents);
        long types = 0;
        for (var entry : contents) if (entry.getLongValue() > 0) types++;
        // AE2 15.4.10 CellConfig.create(ItemStack) exposes 63 configuration slots.
        return new Capacity(0, Capacity.UNLIMITED, types, 63, false, 0);
    }
}
