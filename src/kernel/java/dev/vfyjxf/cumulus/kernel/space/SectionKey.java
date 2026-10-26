package dev.vfyjxf.cumulus.kernel.space;

import dev.vfyjxf.cumulus.kernel.core.World;

import java.util.Objects;
/** The section index of one view frame. */
public record SectionKey(World world, int frame, int x, int y, int z)
        implements Comparable<SectionKey> {

    public SectionKey {
        Objects.requireNonNull(world, "world");
        if (frame < 0) {
            throw new IllegalArgumentException("frame >= 0");
        }
    }

    public static SectionKey of(World world, int frame, LatticePoint cell, int cellSize) {
        if (cellSize < 1) {
            throw new IllegalArgumentException("cellSize >= 1");
        }
        return new SectionKey(world, frame,
                Math.floorDiv(cell.x(), cellSize),
                Math.floorDiv(cell.y(), cellSize),
                Math.floorDiv(cell.z(), cellSize));
    }

    public SectionKey sectionOf(LatticePoint cell, int cellSize) {
        return of(world, frame, cell, cellSize);
    }

    @Override
    public int compareTo(SectionKey other) {
        int byWorld = world.compareTo(other.world);
        if (byWorld != 0) {
            return byWorld;
        }
        int byFrame = Integer.compare(frame, other.frame);
        if (byFrame != 0) {
            return byFrame;
        }
        int byX = Integer.compare(x, other.x);
        if (byX != 0) {
            return byX;
        }
        int byZ = Integer.compare(z, other.z);
        return byZ != 0 ? byZ : Integer.compare(y, other.y);
    }
}
