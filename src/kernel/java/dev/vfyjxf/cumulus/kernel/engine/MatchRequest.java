package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.blueprint.Blueprint;
import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One question over one capture: the lease is the only way to the view, and it is also the capture the
 * attempt is judged against.
 *
 * <p>{@code ticket} is the host slot's coalescing token. It is queue state, so no capture comparison
 * reads it; a direct facade call passes {@code null}.
 */
public record MatchRequest(Blueprint blueprint, Siting siting, Stage stage,
                           SnapshotLease lease, MatchPolicy policy, InstanceBudget budget,
                           PinBudget pins, InstanceRecord record, OwnerId owner,
                           @Nullable Ticket ticket) {

    public MatchRequest {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(pins, "pins");
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(owner, "owner");
    }

    /** The world this request reads; the lease's capture names it. */
    public NamespacedId world() {
        return lease.id().world();
    }

    public MatchRequest withStage(Stage next) {
        return new MatchRequest(blueprint, siting, next, lease, policy, budget, pins, record, owner,
                ticket);
    }

    /** The token a later attempt needs, or {@code null} when no slot issued one. */
    public @Nullable RetryToken retryToken() {
        return ticket == null ? null : new RetryToken(ticket.next(), stage);
    }
}
