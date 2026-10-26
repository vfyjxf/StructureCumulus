package dev.vfyjxf.cumulus.kernel.space;

public enum LocusFrame {
    definition, local, world, key;
    public int rank() {
        return ordinal();
    }

    public String wireName() {
        return switch (this) {
            case definition -> "DEFINITION";
            case local -> "LOCAL";
            case world -> "WORLD";
            case key -> "KEY";
        };
    }
}
