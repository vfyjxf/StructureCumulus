package dev.vfyjxf.cumulus.kernel.space;

/** One packed long: x:26, z:26, y:12. */
public record LatticeKey(long packed) implements Comparable<LatticeKey> {

    public static LatticeKey of(LatticePoint point) {
        int x = point.x();
        int y = point.y();
        int z = point.z();
        long packed = ((long) (x & 0x3ff_ffff) << 38) | ((long) (z & 0x3ff_ffff) << 12) | (y & 0xfffL);
        return new LatticeKey(packed);
    }

    public LatticePoint point() {
        int x = (int) (packed >> 38);
        int z = (int) (packed << 26 >> 38);
        int y = (int) (packed << 52 >> 52);
        return LatticePoint.of(x, y, z);
    }

    public int x() {
        return (int) (packed >> 38);
    }

    public int y() {
        return (int) (packed << 52 >> 52);
    }

    public int z() {
        return (int) (packed << 26 >> 38);
    }

    /** Layer, then row, then column, matching LatticePoint's order without decoding. */
    @Override
    public int compareTo(LatticeKey other) {
        int result = Integer.compare(y(), other.y());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(z(), other.z());
        return result != 0 ? result : Integer.compare(x(), other.x());
    }
}
