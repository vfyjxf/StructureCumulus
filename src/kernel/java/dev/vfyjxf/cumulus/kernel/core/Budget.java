package dev.vfyjxf.cumulus.kernel.core;

/** A ceiling for one attempt: cells and sections. */
public record Budget(int cells, int sections) {

    public static final Budget ZERO = new Budget(0, 0);
    public static final Budget UNBOUNDED = new Budget(Integer.MAX_VALUE, Integer.MAX_VALUE);

    public Budget {
        if (cells < 0 || sections < 0) {
            throw new IllegalArgumentException("non-negative budget");
        }
    }

    public static Budget of(int cells, int sections) {
        return new Budget(cells, sections);
    }

    public static Budget unbounded() {
        return UNBOUNDED;
    }

    public boolean admits(Spend spend) {
        return spend.cells() <= cells && spend.sections() <= sections;
    }
}
