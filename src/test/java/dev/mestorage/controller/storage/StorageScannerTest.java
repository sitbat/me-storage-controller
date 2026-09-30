package dev.mestorage.controller.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class StorageScannerTest {
    @Test
    void unlimitedCapacitySurvivesFiniteAndUnknownMembers() {
        assertEquals(StorageScanner.Capacity.UNLIMITED, StorageScanner.sumLimits(1024, StorageScanner.Capacity.UNLIMITED));
        assertEquals(StorageScanner.Capacity.UNLIMITED, StorageScanner.sumLimits(StorageScanner.Capacity.UNLIMITED, -1));
        assertEquals(StorageScanner.Capacity.UNLIMITED, StorageScanner.sumLimits(-1, StorageScanner.Capacity.UNLIMITED));
        assertEquals(-1, StorageScanner.sumLimits(1024, -1));
        assertEquals(5120, StorageScanner.sumLimits(1024, 4096));
    }

    @Test
    void unknownUsageDoesNotCorruptUnlimitedTotalsOrOverflowNegative() {
        assertEquals(-1, StorageScanner.sumUsed(128, -1));
        assertEquals(-1, StorageScanner.sumUsed(-1, 128));
        assertEquals(Long.MAX_VALUE, StorageScanner.sumUsed(Long.MAX_VALUE - 2, 4));
        assertTrue(new StorageScanner.Capacity(1024, StorageScanner.Capacity.UNLIMITED,
                3, StorageScanner.Capacity.UNLIMITED, false, 0).known());
        assertFalse(new StorageScanner.Capacity(-1, StorageScanner.Capacity.UNLIMITED,
                3, StorageScanner.Capacity.UNLIMITED, true, 1).known());
    }

    @Test
    void nodesSharingProviderAndRepeatedMountsDoNotDuplicateContents() {
        var reads = new AtomicInteger();
        var mounts = new AtomicInteger();
        MEStorage inventory = new MEStorage() {
            @Override public Component getDescription() { return Component.literal("inventory"); }
            @Override public void getAvailableStacks(KeyCounter out) { reads.incrementAndGet(); }
        };
        IStorageProvider provider = out -> {
            mounts.incrementAndGet();
            out.mount(inventory);
            out.mount(inventory);
        };
        IGridNode first = node(provider, new Object());
        IGridNode second = node(provider, new Object());
        IGrid grid = proxy(IGrid.class, (method, args) -> {
            if (method.equals("getNodes")) return List.of(first, second);
            throw new AssertionError(method);
        });
        var devices = StorageScanner.discover(grid);
        assertEquals(1, devices.size());
        assertEquals(1, mounts.get());
        StorageScanner.contents(devices.get(0).storage());
        assertEquals(1, reads.get());
    }

    @Test
    void overviewCopiesAeCacheInsteadOfSummingDevicesOrRefreshingNetwork() {
        var cached = new KeyCounter();
        IStorageService service = proxy(IStorageService.class, (method, args) -> {
            if (method.equals("getCachedInventory")) return cached;
            throw new AssertionError("Overview must not request live network traversal: " + method);
        });
        IGrid grid = proxy(IGrid.class, (method, args) -> {
            if (method.equals("getStorageService")) return service;
            throw new AssertionError(method);
        });
        var result = StorageScanner.gridContents(grid);
        assertNotSame(cached, result);
        assertTrue(result.isEmpty());
    }

    @Test
    void destroyedNodeCannotBeUsedForRemoteSlotActions() {
        IGridNode node = proxy(IGridNode.class, (method, args) -> {
            if (method.equals("getGrid")) throw new IllegalStateException("destroyed");
            throw new AssertionError(method);
        });
        var device = new StorageScanner.Device("test", Component.literal("test"), null, null,
                StorageScanner.Kind.OTHER, null, null, true, node, new Object());
        assertFalse(StorageScanner.isCurrent(device, null));
    }

    @Test
    void brokenAddonMountDoesNotHideHealthyDeviceAndIsMarkedUnknown() {
        IStorageProvider broken = out -> { throw new IllegalStateException("broken addon mount"); };
        IStorageProvider healthy = out -> {};
        IGrid grid = proxy(IGrid.class, (method, args) -> {
            if (method.equals("getNodes")) return List.of(node(broken, new Object()), node(healthy, new Object()));
            throw new AssertionError(method);
        });
        var devices = StorageScanner.discover(grid);
        assertEquals(2, devices.size());
        assertEquals(1, devices.stream().filter(StorageScanner::isDegraded).count());
        var degraded = devices.stream().filter(StorageScanner::isDegraded).findFirst().orElseThrow();
        assertFalse(StorageScanner.capacity(degraded).known());
        assertEquals(1, StorageScanner.capacity(degraded).unknownCells());
    }

    @Test
    void brokenAddonContentReadIsIsolatedAndExposesErrorFlag() {
        MEStorage broken = new MEStorage() {
            @Override public Component getDescription() { return Component.literal("broken"); }
            @Override public void getAvailableStacks(KeyCounter out) { throw new IllegalArgumentException("broken addon read"); }
        };
        IStorageProvider provider = out -> out.mount(broken);
        IGrid grid = proxy(IGrid.class, (method, args) -> {
            if (method.equals("getNodes")) return List.of(node(provider, new Object()));
            throw new AssertionError(method);
        });
        var device = StorageScanner.discover(grid).get(0);
        assertFalse(StorageScanner.isDegraded(device));
        assertTrue(StorageScanner.contents(device.storage()).isEmpty());
        assertTrue(StorageScanner.isDegraded(device));
    }

    private static IGridNode node(IStorageProvider provider, Object owner) {
        return proxy(IGridNode.class, (method, args) -> switch (method) {
            case "getService" -> provider;
            case "getOwner" -> owner;
            case "getVisualRepresentation" -> null;
            case "isActive" -> true;
            default -> throw new AssertionError(method);
        });
    }

    @FunctionalInterface
    private interface Invocation { Object call(String method, Object[] args); }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.call(method.getName(), args)));
    }
}
