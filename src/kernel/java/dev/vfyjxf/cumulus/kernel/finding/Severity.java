package dev.vfyjxf.cumulus.kernel.finding;

/** Declaration order is strength. */
public enum Severity {
    error, warning, info;

    public int rank() {
        return ordinal();
    }

    public boolean atLeast(Severity floor) {
        return rank() <= floor.rank();
    }

    public String wireName() {
        return switch (this) {
            case error -> "ERROR";
            case warning -> "WARNING";
            case info -> "INFO";
        };
    }
}
