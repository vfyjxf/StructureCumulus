package dev.vfyjxf.cumulus.mc.visual;

import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/** One persisted claim row as the read-only visual snapshot presents it. */
@NullMarked
public record VisualClaim(LatticeKey cell, Sharing sharing, OwnerId owner, Siting siting, NamespacedId bay) {

    public VisualClaim {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(sharing, "sharing");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(bay, "bay");
    }
}
