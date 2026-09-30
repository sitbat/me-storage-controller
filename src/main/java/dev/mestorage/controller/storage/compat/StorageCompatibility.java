package dev.mestorage.controller.storage.compat;

import appeng.api.storage.IStorageProvider;
import appeng.parts.AEBasePart;
import appeng.parts.storagebus.StorageBusPart;

/** Optional integrations without linking any third-party classes at startup. */
public final class StorageCompatibility {
    private StorageCompatibility() {}

    // Verified against ExtendedAE 1.20.1-forge 1.4.21: tag, mod and precise buses
    // inherit this class and expose the same adjacent-face storage semantics.
    private static final String EAE_STORAGE_BUS =
            "com.glodblock.github.extendedae.common.parts.base.PartSpecialStorageBus";

    private static final ClassValue<Boolean> EAE_BUS_CLASSES = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                if (current.getName().equals(EAE_STORAGE_BUS)) return true;
            }
            return false;
        }
    };

    public static boolean isExternalStorageBus(Object owner) {
        return owner instanceof StorageBusPart || owner instanceof AEBasePart
                && owner instanceof IStorageProvider && EAE_BUS_CLASSES.get(owner.getClass());
    }
}
