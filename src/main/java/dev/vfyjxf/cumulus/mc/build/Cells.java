package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The cells of a set in canonical order. */
final class Cells {

    static List<LatticePoint> of(CuboidSet set) {
        List<LatticePoint> cells = new ArrayList<>();
        for (Cuboid box : set.decomposition()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        cells.add(LatticePoint.of(x, y, z));
                    }
                }
            }
        }
        return cells;
    }

    /** The bounding box of these cells, or null when there are none. */
    static @Nullable Cuboid bounds(Collection<LatticePoint> cells) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (LatticePoint cell : cells) {
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
        }
        if (maxX < minX) {
            return null;
        }
        return Cuboid.of(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private Cells() {}
}
