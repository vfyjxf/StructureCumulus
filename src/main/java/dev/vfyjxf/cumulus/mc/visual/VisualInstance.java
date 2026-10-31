package dev.vfyjxf.cumulus.mc.visual;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/** One formed structure as the read-only visual snapshot presents it. */
@NullMarked
public record VisualInstance(
    InstanceId id,
    OwnerId owner,
    Siting siting,
    CuboidSet bounds,
    long contextRevision,
    Spend spend,
    List<String> findings,
    List<String> contextKeys,
    int contextValueCount
) {

    public VisualInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(spend, "spend");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(contextKeys, "contextKeys");
        if (contextRevision < 0) {
            throw new IllegalArgumentException("contextRevision >= 0");
        }
        if (contextValueCount < 0) {
            throw new IllegalArgumentException("contextValueCount >= 0");
        }
        findings = List.copyOf(findings);
        contextKeys = List.copyOf(contextKeys);
    }
}
