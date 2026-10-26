package dev.vfyjxf.cumulus.kernel.space;

/** A reflection: none, or one of the two improper generators. */
public enum Flip {
    none, leftRight, frontBack;

    public boolean isMirror() {
        return this != none;
    }

    int[][] matrix() {
        return switch (this) {
            case none -> new int[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            case leftRight -> new int[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, -1}};
            case frontBack -> new int[][]{{-1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        };
    }
}
