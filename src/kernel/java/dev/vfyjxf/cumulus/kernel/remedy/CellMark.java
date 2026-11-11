package dev.vfyjxf.cumulus.kernel.remedy;

public enum CellMark {
    expected, present, missing, extra, wrong, blocked, unknown;

    /** The loudest mark wins: blocked > wrong > missing > extra > unknown > present/expected. */
    public static CellMark louder(CellMark left, CellMark right) {
        return loudness(right) > loudness(left) ? right : left;
    }

    private static int loudness(CellMark mark) {
        return switch (mark) {
            case blocked -> 5;
            case wrong -> 4;
            case missing -> 3;
            case extra -> 2;
            case unknown -> 1;
            case present, expected -> 0;
        };
    }
}
