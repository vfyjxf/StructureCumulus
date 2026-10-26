package dev.vfyjxf.cumulus.kernel.space;

/** A reflection: none, or one of the two improper generators. */
public enum Flip {
    NONE, LEFT_RIGHT, FRONT_BACK;

    public boolean isMirror() {
        return this != NONE;
    }

    int[][] matrix() {
        return switch (this) {
            case NONE -> new int[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            case LEFT_RIGHT -> new int[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, -1}};
            case FRONT_BACK -> new int[][] {{-1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        };
    }
}
