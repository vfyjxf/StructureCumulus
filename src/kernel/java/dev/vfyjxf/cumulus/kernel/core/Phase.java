package dev.vfyjxf.cumulus.kernel.core;

/** When a fact became knowable. */
public enum Phase {
    COMPILE, CHECK, SETTLE;

    public int rank() {
        return ordinal();
    }
}
