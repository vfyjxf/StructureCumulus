package dev.vfyjxf.cumulus.kernel.space;

/** A yaw quarter turn; a generator, not a name for one of the 48 orientations. */
public enum Turn {
    none, clockwise, half, counterclockwise;

    public Turn compose(Turn other) {
        int sum = (ordinal() + other.ordinal()) % 4;
        return values()[sum];
    }

    public Turn inverse() {
        return values()[(4 - ordinal()) % 4];
    }

    int[][] matrix() {
        return switch (this) {
            case none -> new int[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            case clockwise -> new int[][]{{0, 0, -1}, {0, 1, 0}, {1, 0, 0}};
            case half -> new int[][]{{-1, 0, 0}, {0, 1, 0}, {0, 0, -1}};
            case counterclockwise -> new int[][]{{0, 0, 1}, {0, 1, 0}, {-1, 0, 0}};
        };
    }
}
