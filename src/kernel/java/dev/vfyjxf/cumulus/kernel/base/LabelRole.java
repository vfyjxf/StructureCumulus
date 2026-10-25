package dev.vfyjxf.cumulus.kernel.base;

public enum LabelRole {
    PRIMARY,
    SECONDARY,
    CONTEXT;

    public int rank() {
        return ordinal();
    }
}
