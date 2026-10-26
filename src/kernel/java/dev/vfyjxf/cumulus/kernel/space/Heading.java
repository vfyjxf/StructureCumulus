package dev.vfyjxf.cumulus.kernel.space;

public enum Heading {
    north(0, 0, -1), south(0, 0, 1), west(-1, 0, 0), east(1, 0, 0), up(0, 1, 0), down(0, -1, 0);

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
            case north -> south;
            case south -> north;
            case west -> east;
            case east -> west;
            case up -> down;
            case down -> up;
        };
    }

    public Axis axis() {
        return switch (this) {
            case north, south -> Axis.z;
            case west, east -> Axis.x;
            case up, down -> Axis.y;
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
