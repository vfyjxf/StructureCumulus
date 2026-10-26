package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.Objects;

/** What a resolver may see. */
public record ResolveContext(
    SnapshotId snapshot,
    int frame,
    LatticePoint anchor,
    int remainingDepth,
    SnapshotPolicy policy
) {

    public ResolveContext {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(policy, "policy");
        if (frame < 0 || remainingDepth < 0) {
            throw new IllegalArgumentException("non-negative frame/depth");
        }
    }
}
