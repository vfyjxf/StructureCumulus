package dev.vfyjxf.cumulus.kernel.space;

/** One of six proper rotations with distinct {@link #up()} images. */
public enum Tilt {
    level, pitchUp, pitchDown, rollLeft, rollRight, inverted;

    public Heading up() {
        return switch (this) {
            case level -> Heading.up;
            case pitchUp -> Heading.south;
            case pitchDown -> Heading.north;
            case rollLeft -> Heading.west;
            case rollRight -> Heading.east;
            case inverted -> Heading.down;
        };
    }

    int[][] matrix() {
        return switch (this) {
            case level -> new int[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            case pitchUp -> new int[][]{{1, 0, 0}, {0, 0, -1}, {0, 1, 0}};
            case pitchDown -> new int[][]{{1, 0, 0}, {0, 0, 1}, {0, -1, 0}};
            case rollLeft -> new int[][]{{0, -1, 0}, {1, 0, 0}, {0, 0, 1}};
            case rollRight -> new int[][]{{0, 1, 0}, {-1, 0, 0}, {0, 0, 1}};
            case inverted -> new int[][]{{1, 0, 0}, {0, -1, 0}, {0, 0, -1}};
        };
    }
}
