package dev.vfyjxf.cumulus.kernel.space;

public record LatticeKey(long packed) implements Comparable<LatticeKey> {

    private static final long FIELD_MASK = 0x3ff_ffffL;
    private static final long Y_MASK = 0xfffL;

    public static LatticeKey of(LatticePoint point) {
        long packed = ((point.x() & FIELD_MASK) << 38)
                | ((point.z() & FIELD_MASK) << 12)
                | (point.y() & Y_MASK);
        return new LatticeKey(packed);
    }

    public int x() {
        return (int) (packed >> 38);
    }

    public int z() {
        return (int) (packed << 26 >> 38);
    }

    public int y() {
        return (int) (packed << 52 >> 52);
    }

    public LatticePoint toPoint() {
        return LatticePoint.of(x(), y(), z());
    }

    // Same order as LatticePoint.compareTo, so a key sorts where its point sorts.
    @Override
    public int compareTo(LatticeKey other) {
        int byY = Integer.compare(y(), other.y());
        if (byY != 0) {
            return byY;
        }
        int byZ = Integer.compare(z(), other.z());
        return byZ != 0 ? byZ : Integer.compare(x(), other.x());
    }

    @Override
    public String toString() {
        return toPoint().toString();
    }
}
