package dev.vfyjxf.cumulus.kernel.space;

/** One of six proper rotations with distinct {@link #up()} images. */
public enum Tilt {
    LEVEL, PITCH_UP, PITCH_DOWN, ROLL_LEFT, ROLL_RIGHT, INVERTED;

    public Heading up() {
        return switch (this) {
            case LEVEL -> Heading.UP;
            case PITCH_UP -> Heading.SOUTH;
            case PITCH_DOWN -> Heading.NORTH;
            case ROLL_LEFT -> Heading.WEST;
            case ROLL_RIGHT -> Heading.EAST;
            case INVERTED -> Heading.DOWN;
        };
    }

    int[][] matrix() {
        return switch (this) {
            case LEVEL -> new int[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            case PITCH_UP -> new int[][] {{1, 0, 0}, {0, 0, -1}, {0, 1, 0}};
            case PITCH_DOWN -> new int[][] {{1, 0, 0}, {0, 0, 1}, {0, -1, 0}};
            case ROLL_LEFT -> new int[][] {{0, -1, 0}, {1, 0, 0}, {0, 0, 1}};
            case ROLL_RIGHT -> new int[][] {{0, 1, 0}, {-1, 0, 0}, {0, 0, 1}};
            case INVERTED -> new int[][] {{1, 0, 0}, {0, -1, 0}, {0, 0, -1}};
        };
    }
}
