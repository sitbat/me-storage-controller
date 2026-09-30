package dev.mestorage.controller.mixin;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.me.Grid;
import appeng.me.service.EnergyService;
import dev.mestorage.controller.config.ControllerConfig;
import dev.mestorage.controller.energy.DirectEnergySupply;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** AE's existing batteries are drained normally; only unmet demand reaches external FE. */
@Mixin(value = EnergyService.class, remap = false)
public abstract class EnergyServiceMixin {
    @Shadow @Final private Grid grid;
    @Shadow private double tickDrainPerTick;
    @Shadow private double tickInjectionPerTick;
    @Shadow public abstract double injectProviderPower(double amount, Actionable mode);

    @Inject(method = "extractAEPower", at = @At("HEAD"))
    private void meStorage$begin(double amount, Actionable mode, PowerMultiplier multiplier,
                                 CallbackInfoReturnable<Double> result) {
        DirectEnergySupply.beginRequest(ControllerConfig.bypassAeEnergyLimit());
    }

    @Inject(method = "extractAEPower", at = @At("RETURN"))
    private void meStorage$end(double amount, Actionable mode, PowerMultiplier multiplier,
                               CallbackInfoReturnable<Double> result) {
        DirectEnergySupply.endRequest();
    }

    @Inject(method = "extractProviderPower", at = @At("RETURN"), cancellable = true)
    private void meStorage$supply(double amount, Actionable mode, CallbackInfoReturnable<Double> result) {
        if (!ControllerConfig.bypassAeEnergyLimit()) return;
        double cached = result.getReturnValueD();
        double supplied = DirectEnergySupply.extract(grid, amount - cached, mode, this::injectProviderPower);
        if (supplied <= 0) return;
        if (mode == Actionable.MODULATE) {
            // Direct conversion is both an input and immediate consumption, not stored power.
            tickInjectionPerTick += supplied;
            tickDrainPerTick += supplied;
        }
        result.setReturnValue(cached + supplied);
    }
}
