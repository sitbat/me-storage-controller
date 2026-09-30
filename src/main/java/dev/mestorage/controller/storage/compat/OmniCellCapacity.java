package dev.mestorage.controller.storage.compat;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Optional;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import dev.mestorage.controller.storage.StorageScanner.Capacity;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Optional AE2OmniCells 1.20.1-forge 1.1.6 adapter. Verified against the shipped
 * jar and upstream 1.20.1 commit 75927568887a1f8d80822a722375751cbadabbe1.
 * Only the already mounted inventory and public item limit getters are used.
 */
public final class OmniCellCapacity {
    private static final String INVENTORY = "com.wintercogs.ae2omnicells.common.me.AEUniversalCellInventory";
    private static final String BIG_INVENTORY =
            "com.wintercogs.ae2omnicells.common.me.biginteger.AEBigIntegerCellInventory";
    private static final String ITEM_API = "com.wintercogs.ae2omnicells.common.me.IAEUniversalCell";
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private record Limits(Method bytes, Method types) {}

    private static final ClassValue<Optional<Limits>> LIMITS = new ClassValue<>() {
        @Override protected Optional<Limits> computeValue(Class<?> type) {
            var api = findInterface(type, ITEM_API);
            if (api == null) return Optional.empty();
            try {
                return Optional.of(new Limits(api.getMethod("getTotalBytes"), api.getMethod("getTotalTypes")));
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        }
    };

    private OmniCellCapacity() {}

    public static Capacity read(@Nullable MEStorage inventory, Item item) {
        if (inventory == null) return Capacity.UNKNOWN;
        String name = inventory.getClass().getName();
        boolean big = name.equals(BIG_INVENTORY);
        if (!big && !name.equals(INVENTORY)) return Capacity.UNKNOWN;

        long totalBytes = Capacity.UNLIMITED, totalTypes = Capacity.UNLIMITED;
        if (!big) {
            var limits = LIMITS.get(item.getClass()).orElse(null);
            if (limits == null) return Capacity.UNKNOWN;
            try {
                totalBytes = limit(((Number) limits.bytes().invoke(item)).longValue());
                totalTypes = limit(((Number) limits.types().invoke(item)).longValue());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read AE2OmniCells public capacity limits", e);
            }
        }

        // The item's tooltip-NBT usage is only refreshed by persist(), so it can
        // lag live transfers. Never instantiate another cell or force a save.
        var contents = new KeyCounter();
        inventory.getAvailableStacks(contents);
        var buckets = new HashMap<Integer, BigInteger>();
        long types = 0;
        boolean truncated = false;
        for (var entry : contents) {
            long amount = entry.getLongValue();
            if (amount <= 0) continue;
            types++;
            // BigInteger storage exposes amounts through AE2's clamped long API.
            // At the boundary the exact usage is unknowable without private state.
            truncated |= big && amount == Long.MAX_VALUE;
            int perByte = Math.max(1, entry.getKey().getType().getAmountPerByte());
            buckets.merge(perByte, BigInteger.valueOf(amount), BigInteger::add);
        }
        BigInteger used = BigInteger.ZERO;
        for (var bucket : buckets.entrySet()) {
            var divisor = BigInteger.valueOf(bucket.getKey());
            used = used.add(bucket.getValue().add(divisor).subtract(BigInteger.ONE).divide(divisor));
            // The ordinary implementation maintains long bucket sums. If those
            // overflow, reconstructing a guessed "exact" cache would be misleading.
            truncated |= !big && bucket.getValue().compareTo(LONG_MAX) > 0;
        }
        truncated |= used.compareTo(LONG_MAX) > 0;
        return new Capacity(truncated ? -1 : used.longValue(), totalBytes, types, totalTypes,
                truncated, truncated ? 1 : 0);
    }

    private static long limit(long value) { return value <= 0 ? Capacity.UNLIMITED : value; }

    private static @Nullable Class<?> findInterface(Class<?> type, String name) {
        if (type.getName().equals(name)) return type;
        for (var candidate : type.getInterfaces()) {
            var found = findInterface(candidate, name);
            if (found != null) return found;
        }
        var parent = type.getSuperclass();
        return parent == null ? null : findInterface(parent, name);
    }
}
