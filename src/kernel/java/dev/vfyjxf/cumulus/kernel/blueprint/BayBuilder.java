package dev.vfyjxf.cumulus.kernel.blueprint;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Carries the bay declaration while its checks are built with {@link #bayId()}, then seals the
 * immutable {@link Bay} once the requirement exists.
 */
public final class BayBuilder {

    private final NamespacedId id;
    private final Cuboid cells;
    private final Coverage coverage;
    private final ClaimMode mode;
    private final ClaimScope scope;
    private final @Nullable String description;

    private BayBuilder(NamespacedId id, Cuboid cells, Coverage coverage, ClaimMode mode,
                       ClaimScope scope, @Nullable String description) {
        this.id = Objects.requireNonNull(id, "id");
        this.cells = Objects.requireNonNull(cells, "cells");
        this.coverage = Objects.requireNonNull(coverage, "coverage");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.description = description;
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("B-1: bay " + id + " has no cells");
        }
        if (!coverage.accepts(scope)) {
            throw new IllegalArgumentException("SC-CHK-0106: " + coverage + " bay " + id
                    + " cannot claim " + scope.getClass().getSimpleName());
        }
    }

    public static BayBuilder start(NamespacedId id, Cuboid cells, Coverage coverage, ClaimMode mode,
                                   ClaimScope scope) {
        return start(id, cells, coverage, mode, scope, null);
    }

    public static BayBuilder start(NamespacedId id, Cuboid cells, Coverage coverage, ClaimMode mode,
                                   ClaimScope scope, @Nullable String description) {
        return new BayBuilder(id, cells, coverage, mode, scope, description);
    }

    /** The id every check of this bay names as its {@code bay}. */
    public NamespacedId bayId() {
        return id;
    }

    public Bay attach(Requirement requirement) {
        return new Bay(id, cells, coverage, mode, scope,
                Objects.requireNonNull(requirement, "requirement"), description);
    }
}
