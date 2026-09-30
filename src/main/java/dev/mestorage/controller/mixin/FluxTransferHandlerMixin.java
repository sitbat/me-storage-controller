package dev.mestorage.controller.mixin;

import dev.mestorage.controller.energy.FluxBufferAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "sonar.fluxnetworks.common.connection.TransferHandler", remap = false)
public abstract class FluxTransferHandlerMixin implements FluxBufferAccess {
    @Shadow protected long mBuffer;
    @Shadow protected long mChange;
    @Shadow public abstract long getLimit();

    @Override public long meStorage$rawBuffer() { return mBuffer; }
    @Override public long meStorage$rawLimit() { return getLimit(); }

    @Override public long meStorage$removeBuffer(long amount) {
        long taken = Math.min(Math.max(0, amount), Math.max(0, mBuffer));
        mBuffer -= taken;
        return taken;
    }

    @Override public void meStorage$recordDirectChange(long amount) {
        mChange -= amount;
    }
}
