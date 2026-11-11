package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.ArrayList;
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

    private Cells() {}
}
