package dev.vfyjxf.cumulus.kernel.finding;

/** Declaration order is strength. */
public enum Severity {
    ERROR, WARNING, INFO;

    public int rank() {
        return ordinal();
    }

    public boolean atLeast(Severity floor) {
        return rank() <= floor.rank();
    }
}
