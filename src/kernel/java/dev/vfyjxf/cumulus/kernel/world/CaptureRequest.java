package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;

import java.util.Objects;
/** What to capture. */
public record CaptureRequest(Cuboid bounds, SnapshotPolicy policy) {

    public CaptureRequest {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(policy, "policy");
    }
}
