package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.LocusFrame;
import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A finding's cell loci mapped between the blueprint-local and world frames of the solve siting. */
final class FindingCells {

    /** One finding cell in both frames; local talks to the blueprint, world to the capture. */
    record CellRef(LatticePoint local, LatticePoint world) {}

    private FindingCells() {}

    /** The finding's cells as (local, world) pairs in canonical lattice order; unmapped cells drop out. */
    static List<CellRef> pairs(Finding finding, Siting siting) {
        Map<LatticeKey, CellRef> byKey = new TreeMap<>();
        for (Label label : finding.labels()) {
            Locus locus = label.locus();
            if (locus.frame() == LocusFrame.local) {
                for (LatticePoint local : cells(locus.cells())) {
                    LatticePoint world = siting.toWorld(local);
                    if (world != null) {
                        byKey.putIfAbsent(LatticeKey.of(local), new CellRef(local, world));
                    }
                }
            } else if (locus.frame() == LocusFrame.world) {
                for (LatticePoint world : cells(locus.cells())) {
                    LatticePoint local = siting.toLocal(world);
                    if (local != null) {
                        byKey.putIfAbsent(LatticeKey.of(local), new CellRef(local, world));
                    }
                }
            }
        }
        return List.copyOf(byKey.values());
    }

    /** The finding's cells in the world frame only, canonical order; local loci map through the siting. */
    static List<LatticePoint> world(Finding finding, Siting siting) {
        Map<LatticeKey, LatticePoint> byKey = new TreeMap<>();
        for (Label label : finding.labels()) {
            Locus locus = label.locus();
            if (locus.frame() == LocusFrame.world) {
                for (LatticePoint cell : cells(locus.cells())) {
                    byKey.putIfAbsent(LatticeKey.of(cell), cell);
                }
            } else if (locus.frame() == LocusFrame.local) {
                CuboidSet world = siting.toWorld(locus.cells());
                if (world != null) {
                    for (LatticePoint cell : cells(world)) {
                        byKey.putIfAbsent(LatticeKey.of(cell), cell);
                    }
                }
            }
        }
        return List.copyOf(byKey.values());
    }

    static List<LatticePoint> cells(CuboidSet set) {
        List<LatticePoint> out = new ArrayList<>();
        for (Cuboid box : set.decomposition()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        out.add(LatticePoint.of(x, y, z));
                    }
                }
            }
        }
        return out;
    }
}
