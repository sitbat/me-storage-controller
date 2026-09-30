package dev.mestorage.controller.energy;

/** Shares the point's normal cycle output limit with demand-driven AE transfers. */
public interface FluxPointAccess {
    long meStorage$remainingLimit();
    void meStorage$request(long amount);
    long meStorage$buffer();
    long meStorage$takeBuffer(long amount);
}
