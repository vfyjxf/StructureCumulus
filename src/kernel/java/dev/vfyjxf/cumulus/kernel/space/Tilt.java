package dev.vfyjxf.cumulus.kernel.space;

public enum Tilt {
    LEVEL(Heading.UP),
    PITCH_UP(Heading.SOUTH),
    PITCH_DOWN(Heading.NORTH),
    ROLL_LEFT(Heading.WEST),
    ROLL_RIGHT(Heading.EAST),
    INVERTED(Heading.DOWN);

    private final Heading up;

    Tilt(Heading up) {
        this.up = up;
    }

    public Heading up() {
        return up;
    }
}
