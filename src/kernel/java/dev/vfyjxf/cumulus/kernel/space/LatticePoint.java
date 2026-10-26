package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

/** An integer point inside Minecraft's packing range. */
public record LatticePoint(int x, int y, int z) implements Comparable<LatticePoint> {

    public static final int minXz = -33_554_432;
    public static final int maxXz = 33_554_431;
    public static final int minY = -2_048;
    public static final int maxY = 2_047;
    public static final LatticePoint origin = new LatticePoint(0, 0, 0);

    public LatticePoint {
        if (!inLattice(x, y, z)) {
            throw new IllegalArgumentException("point outside the lattice: " + x + "," + y + "," + z);
        }
    }

    public static LatticePoint of(int x, int y, int z) {
        return new LatticePoint(x, y, z);
    }

    public static boolean inLattice(int x, int y, int z) {
        return x >= minXz && x <= maxXz && y >= minY && y <= maxY && z >= minXz && z <= maxXz;
    }

    public @Nullable LatticePoint offset(int dx, int dy, int dz) {
        long nx = (long) x + dx;
        long ny = (long) y + dy;
        long nz = (long) z + dz;
        if (nx < minXz || nx > maxXz || ny < minY || ny > maxY || nz < minXz || nz > maxXz) {
            return null;
        }
        return new LatticePoint((int) nx, (int) ny, (int) nz);
    }

    public LatticeKey key() {
        return LatticeKey.of(this);
    }

    /** Layer, then row, then column. */
    @Override
    public int compareTo(LatticePoint other) {
        int byY = Integer.compare(y, other.y);
        if (byY != 0) {
            return byY;
        }
        int byZ = Integer.compare(z, other.z);
        return byZ != 0 ? byZ : Integer.compare(x, other.x);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ")";
    }
}
