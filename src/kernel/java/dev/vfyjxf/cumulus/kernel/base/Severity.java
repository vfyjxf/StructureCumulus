package dev.vfyjxf.cumulus.kernel.base;

public enum Severity {
    ERROR,
    WARNING,
    INFO;

    public int rank() {
        return ordinal();
    }

    public boolean atLeast(Severity floor) {
        return rank() <= floor.rank();
    }
}
