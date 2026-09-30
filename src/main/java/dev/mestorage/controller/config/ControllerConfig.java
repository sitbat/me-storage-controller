package dev.mestorage.controller.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ControllerConfig {
    public static final String FILE_NAME = "me-storage-controller-server.toml";
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BYPASS_AE_ENERGY_LIMIT;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("energy");
        BYPASS_AE_ENERGY_LIMIT = builder
                .comment("绕过AE能量转换限制。默认关闭；开启后按实际供电补足AE用电，能源元件仍保留原有缓存功能。",
                        "Bypass the AE network storage-capacity bottleneck using real external energy.",
                        "Energy cells retain their normal capacity. Source availability and output limits still apply.")
                .translation("config.me_storage_controller.bypass_ae_energy_limit")
                .define("bypassAeEnergyLimit", false);
        builder.pop();
        SPEC = builder.build();
    }

    private ControllerConfig() {}

    public static boolean bypassAeEnergyLimit() {
        return SPEC.isLoaded() && BYPASS_AE_ENERGY_LIMIT.get();
    }

    public static void setBypassAeEnergyLimit(boolean enabled) {
        if (!SPEC.isLoaded()) throw new IllegalStateException("Server configuration is not loaded");
        boolean previous = BYPASS_AE_ENERGY_LIMIT.get();
        BYPASS_AE_ENERGY_LIMIT.set(enabled);
        try {
            SPEC.save();
        } catch (RuntimeException failure) {
            BYPASS_AE_ENERGY_LIMIT.set(previous);
            throw failure;
        }
    }
}
