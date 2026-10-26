package dev.vfyjxf.cumulus.kernel.core;

/** When a fact became knowable. */
public enum Phase {
    compile, check, settle;

    public int rank() {
        return ordinal();
    }

    public String wireName() {
        return switch (this) {
            case compile -> "COMPILE";
            case check -> "CHECK";
            case settle -> "SETTLE";
        };
    }
}
