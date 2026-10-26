package dev.vfyjxf.cumulus.kernel.world;

public enum ReadStatus {
    READABLE, OUT_OF_BOUNDS, NOT_CAPTURED, DENIED;

    public boolean readable() {
        return this == READABLE;
    }
}
