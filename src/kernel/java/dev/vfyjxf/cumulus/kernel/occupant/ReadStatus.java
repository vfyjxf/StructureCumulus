package dev.vfyjxf.cumulus.kernel.occupant;

/**
 * One answer per cell; only {@link #READABLE} may be materialised as air.
 */
public enum ReadStatus {
    READABLE,
    OUT_OF_BOUNDS,
    NOT_CAPTURED,
    DENIED,
    STALE;

    public boolean readable() {
        return this == READABLE;
    }
}
