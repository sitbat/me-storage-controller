package dev.mestorage.controller.storage;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import org.jetbrains.annotations.Nullable;

/** Live mutation targets, retaining the exact wrappers mounted by each provider. */
public final class ContentAccess {
    private ContentAccess() {}

    public static @Nullable MEStorage resolve(IGrid grid, StorageScanner.Device device, int cell) {
        if (!StorageScanner.isCurrent(device, grid) || !device.node().isActive()) return null;
        var provider = device.node().getService(IStorageProvider.class);
        if (provider == null) return null;
        var scoped = new NetworkStorage();
        Set<MEStorage> mounted = Collections.newSetFromMap(new IdentityHashMap<>());
        // Ask the current provider again: cached discovery wrappers may outlive a changed bus target or cell.
        provider.mountInventories((storage, priority) -> {
            if (storage != null && mounted.add(storage)) scoped.mount(priority, storage);
        });
        if (cell < 0) return mounted.isEmpty() ? null : scoped;
        if (!(device.owner() instanceof IChestOrDrive host) || cell >= StorageScanner.cellCount(device)) return null;
        var inventory = host.getCellInventory(cell);
        // Never unwrap an original storage cell or bypass a provider's decision not to mount it.
        return inventory != null && mounted.contains(inventory) ? inventory : null;
    }
}
