package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.CachedContext;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The determinism tuple of one attempt. */
public record MatchRequest(
    Blueprint blueprint,
    Siting siting,
    SnapshotLease lease,
    InstanceId instance,
    OwnerId owner,
    Budget budget,
    @Nullable ClaimView claims,
    CancelToken cancel,
    @Nullable CachedContext cachedContext
) {

    public MatchRequest {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(cancel, "cancel");
    }

    public MatchRequest(
        Blueprint blueprint,
        Siting siting,
        SnapshotLease lease,
        InstanceId instance,
        OwnerId owner,
        Budget budget,
        @Nullable ClaimView claims,
        CancelToken cancel
    ) {
        this(blueprint, siting, lease, instance, owner, budget, claims, cancel, null);
    }

    public static MatchRequest of(
        Blueprint blueprint,
        Siting siting,
        SnapshotLease lease,
        InstanceId instance,
        OwnerId owner,
        Budget budget,
        @Nullable ClaimView claims
    ) {
        return new MatchRequest(blueprint, siting, lease, instance, owner, budget, claims, CancelToken.never(), null);
    }

    public static MatchRequest of(
        Blueprint blueprint,
        Siting siting,
        SnapshotLease lease,
        InstanceId instance,
        OwnerId owner,
        Budget budget,
        @Nullable ClaimView claims,
        @Nullable CachedContext cachedContext
    ) {
        return new MatchRequest(
            blueprint,
            siting,
            lease,
            instance,
            owner,
            budget,
            claims,
            CancelToken.never(),
            cachedContext
        );
    }

    public static MatchRequest of(
        Blueprint blueprint,
        Siting siting,
        SnapshotLease lease,
        InstanceId instance,
        OwnerId owner,
        Budget budget,
        @Nullable ClaimView claims,
        CancelToken cancel,
        @Nullable CachedContext cachedContext
    ) {
        return new MatchRequest(blueprint, siting, lease, instance, owner, budget, claims, cancel, cachedContext);
    }
}
