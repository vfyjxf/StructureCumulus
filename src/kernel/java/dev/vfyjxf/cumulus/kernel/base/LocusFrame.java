package dev.vfyjxf.cumulus.kernel.base;

public enum LocusFrame {
    DEFINITION,
    LOCAL,
    WORLD,
    KEY;

    public int rank() {
        return ordinal();
    }
}
