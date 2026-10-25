package dev.vfyjxf.cumulus.kernel.space;

import java.util.ArrayList;
import java.util.List;

public record ChunkGrid(int cellSize, LatticePoint origin) {

    public static final int ADAPTER_CELL_SIZE = 16;

    public ChunkGrid {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive: " + cellSize);
        }
    }

    public ChunkKey chunkOf(LatticePoint point) {
        return new ChunkKey(
                Math.floorDiv(point.x() - origin.x(), cellSize),
                Math.floorDiv(point.y() - origin.y(), cellSize),
                Math.floorDiv(point.z() - origin.z(), cellSize));
    }

    public Cuboid boundsOf(ChunkKey key) {
        long lowX = (long) origin.x() + (long) key.cx() * cellSize;
        long lowY = (long) origin.y() + (long) key.cy() * cellSize;
        long lowZ = (long) origin.z() + (long) key.cz() * cellSize;
        if (lowX < LatticePoint.MIN_XZ || lowX + cellSize > (long) LatticePoint.MAX_XZ + 1
                || lowZ < LatticePoint.MIN_XZ || lowZ + cellSize > (long) LatticePoint.MAX_XZ + 1
                || lowY < LatticePoint.MIN_Y || lowY + cellSize > (long) LatticePoint.MAX_Y + 1) {
            throw new IllegalArgumentException("chunk bounds leave the lattice: " + key);
        }
        return Cuboid.of((int) lowX, (int) lowY, (int) lowZ,
                (int) (lowX + cellSize), (int) (lowY + cellSize), (int) (lowZ + cellSize));
    }

    public int chunkCount(Cuboid box) {
        if (box.isEmpty()) {
            return 0;
        }
        ChunkKey low = chunkOf(box.min());
        ChunkKey high = chunkOf(lastCell(box));
        long count = (long) (high.cx() - low.cx() + 1)
                * (high.cy() - low.cy() + 1)
                * (high.cz() - low.cz() + 1);
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    public List<ChunkKey> chunksOf(Cuboid box) {
        if (box.isEmpty()) {
            return List.of();
        }
        ChunkKey low = chunkOf(box.min());
        ChunkKey high = chunkOf(lastCell(box));
        List<ChunkKey> keys = new ArrayList<>();
        for (int cy = low.cy(); cy <= high.cy(); cy++) {
            for (int cz = low.cz(); cz <= high.cz(); cz++) {
                for (int cx = low.cx(); cx <= high.cx(); cx++) {
                    keys.add(new ChunkKey(cx, cy, cz));
                }
            }
        }
        return List.copyOf(keys);
    }

    private static LatticePoint lastCell(Cuboid box) {
        return LatticePoint.of(box.max().x() - 1, box.max().y() - 1, box.max().z() - 1);
    }
}
