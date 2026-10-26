package dev.vfyjxf.cumulus.kernel.finding;

public enum LabelRole {
    PRIMARY, SECONDARY, CONTEXT;

    public int rank() {
        return ordinal();
    }
}
