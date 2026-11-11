package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.SitingResult;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.mc.LevelAccess;
import dev.vfyjxf.cumulus.mc.LevelCapture;
import dev.vfyjxf.cumulus.mc.McAnchors;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.2/7.2: an anchored blueprint takes its pose from the formed anchor block at the origin, not from
 * the free turn cycle. The anchor cell alone is captured; a cell that is not a formed anchor yields
 * no sitting, so the caller reports it instead of inventing a pose.
 */
final class PreviewAnchors {

    private PreviewAnchors() {}

    static List<Siting> resolve(ServerLevel level, Blueprint blueprint, List<LatticePoint> origins) {
        if (blueprint.anchors().isEmpty()) {
            return List.of();
        }
        LevelCapture capture = LevelAccess.capture(level);
        if (capture == null) {
            return List.of();
        }
        List<Siting> sittings = new ArrayList<>(origins.size());
        for (LatticePoint origin : origins) {
            CaptureResult result = capture
                    .capture(new CaptureRequest(Cuboid.of(origin), SnapshotPolicy.minimal()), null);
            if (!(result instanceof CaptureResult.Captured captured)) {
                continue;
            }
            try (SnapshotLease lease = captured.lease()) {
                SitingResult resolved = McAnchors.resolve(blueprint, origin, lease.view());
                if (resolved.outcome() instanceof Outcome.Found<Siting> found) {
                    sittings.add(found.value());
                }
            }
        }
        return List.copyOf(sittings);
    }
}
