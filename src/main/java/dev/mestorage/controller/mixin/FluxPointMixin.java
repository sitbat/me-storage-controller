package dev.mestorage.controller.mixin;

import dev.mestorage.controller.energy.FluxPointAccess;
import dev.mestorage.controller.energy.FluxBufferAccess;
import dev.mestorage.controller.config.ControllerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "sonar.fluxnetworks.common.device.FluxPointHandler", remap = false)
public abstract class FluxPointMixin implements FluxPointAccess {
    @Shadow private long mDesired;
    @Unique private long meStorage$directOutput;
    @Unique private final long[] meStorage$recentDemand = new long[4];
    @Unique private int meStorage$demandIndex;
    @Unique private long meStorage$currentDemand;
    @Unique private boolean meStorage$allocating;

    @Unique private FluxBufferAccess meStorage$base() {
        return (FluxBufferAccess) (Object) this;
    }

    @Override public long meStorage$remainingLimit() {
        return Math.max(0, meStorage$base().meStorage$rawLimit() - meStorage$directOutput);
    }

    @Override public long meStorage$buffer() { return Math.max(0, meStorage$base().meStorage$rawBuffer()); }

    @Override public void meStorage$request(long amount) {
        if (!ControllerConfig.bypassAeEnergyLimit() || amount <= 0) return;
        long sum = Long.MAX_VALUE - meStorage$currentDemand < amount ? Long.MAX_VALUE : meStorage$currentDemand + amount;
        meStorage$currentDemand = Math.min(Math.max(0, meStorage$base().meStorage$rawLimit()), sum);
    }

    @Inject(method = "getRequest", at = @At("RETURN"), cancellable = true)
    private void meStorage$advertiseRealDemand(CallbackInfoReturnable<Long> result) {
        if (!ControllerConfig.bypassAeEnergyLimit()) return;
        long wanted = meStorage$currentDemand;
        for (long previous : meStorage$recentDemand) wanted = Math.max(wanted, previous);
        if (wanted <= 0) return; // Ordinary Points retain native behavior without recent AE demand.
        // Native consumers refill AE's ordinary cache in addition to the direct path.
        // These are separate withdrawals from this Point, not alternative demands.
        long nativeDemand = Math.max(0, mDesired);
        wanted = Long.MAX_VALUE - wanted < nativeDemand ? Long.MAX_VALUE : wanted + nativeDemand;
        // Incoming replenishment prepares the next cycle; this cycle's already-spent
        // output budget must not prevent the native Flux scheduler from refilling it.
        wanted = Math.min(wanted, Math.max(0, meStorage$base().meStorage$rawLimit()));
        long request = Math.max(0, wanted - meStorage$buffer());
        if (!meStorage$allocating) {
            // Flux samples getRequest again after onCycleEnd to bound what Plugs may
            // receive next tick. FluxPlugHandler.receive subtracts its existing buffer
            // twice from this limiter, so reserve twice the finite cycle demand: one
            // outstanding cycle in the Plug plus room to replenish it before allocation.
            long reserve = wanted > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : wanted * 2;
            request = Long.MAX_VALUE - request < reserve ? Long.MAX_VALUE : request + reserve;
        }
        result.setReturnValue(Math.max(result.getReturnValueJ(), request));
    }

    @Inject(method = "onCycleStart", at = @At("HEAD"))
    private void meStorage$beginAllocation(CallbackInfo callback) {
        meStorage$allocating = ControllerConfig.bypassAeEnergyLimit();
    }

    @Override public long meStorage$takeBuffer(long amount) {
        long taken = meStorage$base().meStorage$removeBuffer(Math.min(Math.max(0, amount), meStorage$remainingLimit()));
        meStorage$directOutput += taken;
        return taken;
    }

    // Both native demand simulation and the real push retain only the remaining budget.
    @ModifyVariable(method = "sendToConsumers", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private long meStorage$limitNativeOutput(long amount) {
        return Math.min(amount, meStorage$remainingLimit());
    }

    @Inject(method = "onCycleEnd", at = @At("RETURN"))
    private void meStorage$finishCycle(CallbackInfo callback) {
        meStorage$allocating = false;
        meStorage$base().meStorage$recordDirectChange(meStorage$directOutput);
        meStorage$directOutput = 0;
        if (ControllerConfig.bypassAeEnergyLimit()) {
            meStorage$recentDemand[meStorage$demandIndex] = meStorage$currentDemand;
            meStorage$demandIndex = (meStorage$demandIndex + 1) % meStorage$recentDemand.length;
        } else {
            java.util.Arrays.fill(meStorage$recentDemand, 0);
        }
        meStorage$currentDemand = 0;
    }
}
