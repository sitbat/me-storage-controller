package dev.mestorage.controller.energy;

/** Access to paid Flux buffer state on the class that actually declares it. */
public interface FluxBufferAccess {
    long meStorage$rawBuffer();
    long meStorage$rawLimit();
    long meStorage$removeBuffer(long amount);
    void meStorage$recordDirectChange(long amount);
}
