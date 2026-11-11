package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Build findings: one builder per report site so every entry keeps its catalogued severity. */
final class BuildFindings {

    static Finding at(CodeEntry entry, Phase phase, String message, @Nullable Cuboid cells, String... notes) {
        Finding.Builder builder = Finding.builder(entry).phase(phase).message(message);
        if (cells != null) {
            builder.label(Locus.world(CuboidSet.of(cells)), message);
        }
        for (String note : notes) {
            builder.note(note);
        }
        return builder.build();
    }

    /** The bounding box of every step's placements, for a finding that is about the whole plan. */
    static @Nullable Cuboid bounds(List<Map<LatticePoint, BlockPlacement>> steps) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Map<LatticePoint, BlockPlacement> placements : steps) {
            for (LatticePoint cell : placements.keySet()) {
                minX = Math.min(minX, cell.x());
                minY = Math.min(minY, cell.y());
                minZ = Math.min(minZ, cell.z());
                maxX = Math.max(maxX, cell.x());
                maxY = Math.max(maxY, cell.y());
                maxZ = Math.max(maxZ, cell.z());
            }
        }
        if (maxX < minX) {
            return null;
        }
        return Cuboid.of(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    private BuildFindings() {}
}
