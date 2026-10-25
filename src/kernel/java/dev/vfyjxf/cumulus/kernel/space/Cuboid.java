package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

public record Cuboid(LatticePoint min, LatticePoint max) implements Comparable<Cuboid> {

    public Cuboid {
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("min exceeds max: " + min + " .. " + max);
        }
    }

    public static Cuboid of(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new Cuboid(LatticePoint.of(minX, minY, minZ), LatticePoint.of(maxX, maxY, maxZ));
    }

    public static Cuboid of(LatticePoint min, LatticePoint max) {
        return new Cuboid(min, max);
    }

    public static Cuboid of(LatticePoint cell) {
        LatticePoint max = cell.offset(1, 1, 1);
        if (max == null) {
            throw new IllegalArgumentException("cell has no successor inside the lattice: " + cell);
        }
        return new Cuboid(cell, max);
    }

    public boolean isEmpty() {
        return min.x() == max.x() || min.y() == max.y() || min.z() == max.z();
    }

    public boolean contains(LatticePoint point) {
        return !isEmpty()
                && min.x() <= point.x() && point.x() < max.x()
                && min.y() <= point.y() && point.y() < max.y()
                && min.z() <= point.z() && point.z() < max.z();
    }

    public boolean contains(Cuboid other) {
        if (other.isEmpty()) {
            return true;
        }
        return min.x() <= other.min.x() && other.max.x() <= max.x()
                && min.y() <= other.min.y() && other.max.y() <= max.y()
                && min.z() <= other.min.z() && other.max.z() <= max.z();
    }

    public boolean intersects(Cuboid other) {
        if (isEmpty() || other.isEmpty()) {
            return false;
        }
        return min.x() < other.max.x() && other.min.x() < max.x()
                && min.y() < other.max.y() && other.min.y() < max.y()
                && min.z() < other.max.z() && other.min.z() < max.z();
    }

    public @Nullable Cuboid intersect(Cuboid other) {
        if (!intersects(other)) {
            return null;
        }
        int lowX = Math.max(min.x(), other.min.x());
        int lowY = Math.max(min.y(), other.min.y());
        int lowZ = Math.max(min.z(), other.min.z());
        int highX = Math.min(max.x(), other.max.x());
        int highY = Math.min(max.y(), other.max.y());
        int highZ = Math.min(max.z(), other.max.z());
        if (lowX >= highX || lowY >= highY || lowZ >= highZ) {
            return null;
        }
        return of(lowX, lowY, lowZ, highX, highY, highZ);
    }

    public Cuboid bounding(Cuboid other) {
        return of(
                Math.min(min.x(), other.min.x()),
                Math.min(min.y(), other.min.y()),
                Math.min(min.z(), other.min.z()),
                Math.max(max.x(), other.max.x()),
                Math.max(max.y(), other.max.y()),
                Math.max(max.z(), other.max.z()));
    }

    public @Nullable Cuboid translate(int dx, int dy, int dz) {
        LatticePoint low = min.offset(dx, dy, dz);
        LatticePoint high = max.offset(dx, dy, dz);
        return low == null || high == null ? null : new Cuboid(low, high);
    }

    public long volume() {
        if (isEmpty()) {
            return 0L;
        }
        long x = (long) max.x() - min.x();
        long y = (long) max.y() - min.y();
        long z = (long) max.z() - min.z();
        return saturatedMultiply(saturatedMultiply(x, y), z);
    }

    public CuboidSet asSet() {
        return CuboidSet.of(this);
    }

    private static long saturatedMultiply(long left, long right) {
        long high = Math.multiplyHigh(left, right);
        return high != 0 ? Long.MAX_VALUE : left * right;
    }

    @Override
    public int compareTo(Cuboid other) {
        int result = min.compareTo(other.min);
        return result != 0 ? result : max.compareTo(other.max);
    }

    @Override
    public String toString() {
        return "[" + min + " .. " + max + ")";
    }
}
