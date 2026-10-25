package dev.vfyjxf.cumulus.kernel.engine;

/** The slot's ceiling for one instance: cells read and sections copied, nothing else. */
public record Budget(int cells, int sections) {

    public static final Budget ZERO = new Budget(0, 0);

    public Budget {
        if (cells < 0 || sections < 0) {
            throw new IllegalArgumentException("a budget leg must not be negative: " + cells + " / " + sections);
        }
    }

    public static Budget of(int cells, int sections) {
        return new Budget(cells, sections);
    }

    public static Budget unbounded() {
        return new Budget(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public boolean admits(Spend spend) {
        return spend.cells() <= cells && spend.sections() <= sections;
    }
}
