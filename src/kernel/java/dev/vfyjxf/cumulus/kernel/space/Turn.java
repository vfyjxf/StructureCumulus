package dev.vfyjxf.cumulus.kernel.space;

/** A yaw quarter turn; a generator, not a name for one of the 48 orientations. */
public enum Turn {
    NONE, CLOCKWISE, HALF, COUNTERCLOCKWISE;

    public Turn compose(Turn other) {
        int sum = (ordinal() + other.ordinal()) % 4;
        return values()[sum];
    }

    public Turn inverse() {
        return values()[(4 - ordinal()) % 4];
    }

    int[][] matrix() {
        return switch (this) {
            case NONE -> new int[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            case CLOCKWISE -> new int[][] {{0, 0, -1}, {0, 1, 0}, {1, 0, 0}};
            case HALF -> new int[][] {{-1, 0, 0}, {0, 1, 0}, {0, 0, -1}};
            case COUNTERCLOCKWISE -> new int[][] {{0, 0, 1}, {0, 1, 0}, {-1, 0, 0}};
        };
    }
}
