package dev.vfyjxf.cumulus.kernel.finding;

public enum LabelRole {
    primary, secondary, context;

    public int rank() {
        return ordinal();
    }

    public String wireName() {
        return switch (this) {
            case primary -> "PRIMARY";
            case secondary -> "SECONDARY";
            case context -> "CONTEXT";
        };
    }
}
