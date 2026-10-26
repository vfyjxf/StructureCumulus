package dev.vfyjxf.cumulus.kernel.core;

/** What one attempt consumed. */
public record Spend(int cells, int sections) {

    public static final Spend ZERO = new Spend(0, 0);

    public Spend {
        if (cells < 0 || sections < 0) {
            throw new IllegalArgumentException("non-negative spend");
        }
    }

    public Spend plus(Spend other) {
        long nextCells = (long) cells + other.cells;
        long nextSections = (long) sections + other.sections;
        return new Spend(
            nextCells > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) nextCells,
            nextSections > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) nextSections
        );
    }
}
