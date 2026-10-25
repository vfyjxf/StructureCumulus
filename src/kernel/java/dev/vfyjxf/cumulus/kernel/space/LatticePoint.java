package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

public record LatticePoint(int x, int y, int z) implements Comparable<LatticePoint> {

    public static final int MIN_XZ = -33_554_432;
    public static final int MAX_XZ = 33_554_431;
    public static final int MIN_Y = -2_048;
    public static final int MAX_Y = 2_047;

    public static final LatticePoint ORIGIN = new LatticePoint(0, 0, 0);

    public LatticePoint {
        if (!inLattice(x, y, z)) {
            throw new IllegalArgumentException("point leaves the lattice: " + x + ", " + y + ", " + z);
        }
    }

    public static LatticePoint of(int x, int y, int z) {
        return new LatticePoint(x, y, z);
    }

    public static boolean inLattice(int x, int y, int z) {
        return x >= MIN_XZ && x <= MAX_XZ
                && z >= MIN_XZ && z <= MAX_XZ
                && y >= MIN_Y && y <= MAX_Y;
    }

    public @Nullable LatticePoint offset(int dx, int dy, int dz) {
        long nx = (long) x + dx;
        long ny = (long) y + dy;
        long nz = (long) z + dz;
        if (nx < MIN_XZ || nx > MAX_XZ || nz < MIN_XZ || nz > MAX_XZ || ny < MIN_Y || ny > MAX_Y) {
            return null;
        }
        return new LatticePoint((int) nx, (int) ny, (int) nz);
    }

    public LatticeKey key() {
        return LatticeKey.of(this);
    }

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
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
