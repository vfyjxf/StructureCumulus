package dev.vfyjxf.cumulus.kernel.engine;

/** The whole ledger of one instance: cells read and sections copied. */
public record Spend(int cells, int sections) {

    public static final Spend ZERO = new Spend(0, 0);

    public Spend {
        if (cells < 0 || sections < 0) {
            throw new IllegalArgumentException("a spend leg must not be negative: " + cells + " / " + sections);
        }
    }

    public static Spend of(int cells, int sections) {
        return new Spend(cells, sections);
    }

    public Spend plus(Spend other) {
        return new Spend(add(cells, other.cells()), add(sections, other.sections()));
    }

    private static int add(int left, int right) {
        long sum = (long) left + (long) right;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}
