package dev.vfyjxf.cumulus.kernel.space;

public enum Turn {
    NONE,
    CLOCKWISE,
    HALF,
    COUNTERCLOCKWISE;

    private static final Turn[] VALUES = values();

    public Turn compose(Turn other) {
        return VALUES[(ordinal() + other.ordinal()) & 3];
    }

    public Turn inverse() {
        return VALUES[(4 - ordinal()) & 3];
    }
}
