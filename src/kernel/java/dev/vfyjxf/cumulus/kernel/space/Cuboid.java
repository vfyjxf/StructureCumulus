package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** A half-open axis-aligned box: {@code [min, max)}. */
public record Cuboid(LatticePoint min, LatticePoint max) implements Comparable<Cuboid> {

    public Cuboid {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("min must not exceed max on any axis");
        }
    }

    public static Cuboid of(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new Cuboid(LatticePoint.of(minX, minY, minZ), LatticePoint.of(maxX, maxY, maxZ));
    }

    public static Cuboid ofSize(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        long maxX = (long) x + sizeX;
        long maxY = (long) y + sizeY;
        long maxZ = (long) z + sizeZ;
        if (maxX > LatticePoint.MAX_XZ || maxY > LatticePoint.MAX_Y || maxZ > LatticePoint.MAX_XZ) {
            throw new IllegalArgumentException("box leaves the lattice");
        }
        return new Cuboid(LatticePoint.of(x, y, z), LatticePoint.of((int) maxX, (int) maxY, (int) maxZ));
    }

    public static Cuboid of(LatticePoint min, LatticePoint max) {
        return new Cuboid(min, max);
    }

    public static Cuboid of(LatticePoint cell) {
        LatticePoint max = cell.offset(1, 1, 1);
        if (max == null) {
            throw new IllegalArgumentException("cell has no in-lattice successor: " + cell);
        }
        return new Cuboid(cell, max);
    }

    public boolean isEmpty() {
        return min.x() == max.x() || min.y() == max.y() || min.z() == max.z();
    }

    public boolean contains(LatticePoint cell) {
        return !isEmpty()
                && cell.x() >= min.x()
                && cell.x() < max.x()
                && cell.y() >= min.y()
                && cell.y() < max.y()
                && cell.z() >= min.z()
                && cell.z() < max.z();
    }

    public boolean contains(Cuboid other) {
        if (other.isEmpty()) {
            return true;
        }
        return other.min.x() >= min.x()
                && other.max.x() <= max.x()
                && other.min.y() >= min.y()
                && other.max.y() <= max.y()
                && other.min.z() >= min.z()
                && other.max.z() <= max.z();
    }

    public boolean intersects(Cuboid other) {
        if (isEmpty() || other.isEmpty()) {
            return false;
        }
        return min.x() < other.max.x()
                && other.min.x() < max.x()
                && min.y() < other.max.y()
                && other.min.y() < max.y()
                && min.z() < other.max.z()
                && other.min.z() < max.z();
    }

    public @Nullable Cuboid intersect(Cuboid other) {
        int x0 = Math.max(min.x(), other.min.x());
        int y0 = Math.max(min.y(), other.min.y());
        int z0 = Math.max(min.z(), other.min.z());
        int x1 = Math.min(max.x(), other.max.x());
        int y1 = Math.min(max.y(), other.max.y());
        int z1 = Math.min(max.z(), other.max.z());
        if (x0 >= x1 || y0 >= y1 || z0 >= z1) {
            return null;
        }
        return of(x0, y0, z0, x1, y1, z1);
    }

    public Cuboid bounding(Cuboid other) {
        return of(
            Math.min(min.x(), other.min.x()),
            Math.min(min.y(), other.min.y()),
            Math.min(min.z(), other.min.z()),
            Math.max(max.x(), other.max.x()),
            Math.max(max.y(), other.max.y()),
            Math.max(max.z(), other.max.z())
        );
    }

    public @Nullable Cuboid translate(int dx, int dy, int dz) {
        LatticePoint newMin = min.offset(dx, dy, dz);
        LatticePoint newMax = max.offset(dx, dy, dz);
        if (newMin == null || newMax == null) {
            return null;
        }
        return new Cuboid(newMin, newMax);
    }

    public long volume() {
        long sx = (long) max.x() - min.x();
        long sy = (long) max.y() - min.y();
        long sz = (long) max.z() - min.z();
        long result = 1;
        for (long extent : new long[]{sx, sy, sz}) {
            if (extent == 0) {
                return 0;
            }
            if (result > Long.MAX_VALUE / extent) {
                return Long.MAX_VALUE;
            }
            result *= extent;
        }
        return result;
    }

    public CuboidSet asSet() {
        return CuboidSet.of(this);
    }

    public int sizeX() {
        return max.x() - min.x();
    }

    public int sizeY() {
        return max.y() - min.y();
    }

    public int sizeZ() {
        return max.z() - min.z();
    }

    @Override
    public int compareTo(Cuboid other) {
        int byMin = min.compareTo(other.min);
        return byMin != 0 ? byMin : max.compareTo(other.max);
    }

    @Override
    public String toString() {
        return "[" + min + ".." + max + ")";
    }
}
