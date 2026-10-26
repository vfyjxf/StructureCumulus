package dev.vfyjxf.cumulus.kernel.space;

public enum Heading {
    NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0);

    private final int stepX;
    private final int stepY;
    private final int stepZ;

    Heading(int stepX, int stepY, int stepZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }

    public int stepX() {
        return stepX;
    }

    public int stepY() {
        return stepY;
    }

    public int stepZ() {
        return stepZ;
    }

    public Heading opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }

    public Axis axis() {
        return switch (this) {
            case NORTH, SOUTH -> Axis.Z;
            case WEST, EAST -> Axis.X;
            case UP, DOWN -> Axis.Y;
        };
    }

    public static Heading ofStep(int x, int y, int z) {
        for (Heading heading : values()) {
            if (heading.stepX == x && heading.stepY == y && heading.stepZ == z) {
                return heading;
            }
        }
        throw new IllegalArgumentException("not a cardinal step: " + x + "," + y + "," + z);
    }
}
