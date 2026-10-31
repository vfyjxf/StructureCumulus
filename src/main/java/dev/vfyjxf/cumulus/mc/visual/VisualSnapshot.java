package dev.vfyjxf.cumulus.mc.visual;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/**
 * One immutable read of a level's formed instances and persisted claim rows, taken on the server
 * thread. The client visual layer and the test probe read this shape; nothing here mutates the
 * manager or the ledger.
 */
@NullMarked
public record VisualSnapshot(
    NamespacedId level,
    long capturedTick,
    long claimRevision,
    List<VisualInstance> instances,
    List<VisualClaim> claims
) {

    public VisualSnapshot {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(instances, "instances");
        Objects.requireNonNull(claims, "claims");
        if (capturedTick < 0) {
            throw new IllegalArgumentException("capturedTick >= 0");
        }
        if (claimRevision < 0) {
            throw new IllegalArgumentException("claimRevision >= 0");
        }
        instances = List.copyOf(instances);
        claims = List.copyOf(claims);
    }

    public int instanceCount() {
        return instances.size();
    }

    public int claimCount() {
        return claims.size();
    }
}
