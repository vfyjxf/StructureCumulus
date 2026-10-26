package dev.vfyjxf.cumulus.kernel.space;

public enum LocusFrame {
    DEFINITION, LOCAL, WORLD, KEY;
    public int rank() {
        return ordinal();
    }
}
