package dev.vfyjxf.cumulus.kernel.space;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

public record SectionKey(NamespacedId dimension, int x, int y, int z) implements Comparable<SectionKey> {

    public static SectionKey of(NamespacedId dimension, LatticePoint cell, int cellSize) {
        int size = checkedCellSize(cellSize);
        return new SectionKey(
                dimension,
                Math.floorDiv(cell.x(), size),
                Math.floorDiv(cell.y(), size),
                Math.floorDiv(cell.z(), size));
    }

    public SectionKey sectionOf(LatticePoint cell, int cellSize) {
        return of(dimension, cell, cellSize);
    }

    private static int checkedCellSize(int cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive: " + cellSize);
        }
        return cellSize;
    }

    @Override
    public int compareTo(SectionKey other) {
        int byDimension = dimension.compareTo(other.dimension);
        if (byDimension != 0) {
            return byDimension;
        }
        int byX = Integer.compare(x, other.x);
        if (byX != 0) {
            return byX;
        }
        int byZ = Integer.compare(z, other.z);
        return byZ != 0 ? byZ : Integer.compare(y, other.y);
    }
}
