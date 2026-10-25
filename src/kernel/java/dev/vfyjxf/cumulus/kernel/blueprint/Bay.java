package dev.vfyjxf.cumulus.kernel.blueprint;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.predicate.DecisionKind;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A named region tested atomically; what a MATCH asserts (coverage) and owns (mode + scope) are
 * independent. A check names its bay by id, so the bay and its requirement are an ordinary acyclic
 * record graph.
 */
public record Bay(NamespacedId id, Cuboid cells, Coverage coverage, ClaimMode mode,
                  ClaimScope scope, Requirement requirement, @Nullable String description) {

    public Bay {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(requirement, "requirement");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("B-1: bay " + id + " has no cells");
        }
        if (!coverage.accepts(scope)) {
            throw new IllegalArgumentException(
                    "SC-CHK-0106: " + coverage + " bay " + id + " cannot claim " + scope.getClass().getSimpleName());
        }
        if (coverage == Coverage.PRESENCE && mode == ClaimMode.EXCLUSIVE
                && !(scope instanceof ClaimScope.Declared)) {
            throw new IllegalArgumentException("SC-CHK-0106: PRESENCE bay " + id
                    + " cannot be EXCLUSIVE without a Declared scope");
        }
        validate(id, cells, coverage, requirement);
    }

    private static void validate(NamespacedId id, Cuboid cells, Coverage coverage, Requirement requirement) {
        CuboidSet decided = CuboidSet.empty();
        for (Check<?> check : requirement.checks()) {
            if (!check.bay().equals(id)) {
                throw new IllegalArgumentException(
                        "SC-CHK-0107: check " + check.id() + " names bay " + check.bay()
                                + " but sits in bay " + id);
            }
            DecisionKind kind = check.focus().decisionKind();
            switch (coverage) {
                case CUBIC -> {
                    if (kind == DecisionKind.CUBIC) {
                        CuboidSet checked = check.focus().staticDecisionCells(cells);
                        if (!CuboidSet.of(cells).containsAll(checked)) {
                            throw new IllegalArgumentException("SC-BAY-0107: " + check.id()
                                    + " decides cells outside bay " + id);
                        }
                        decided = decided.union(checked);
                    }
                }
                case SUBJECT -> requireKind(id, coverage, check, kind, DecisionKind.SUBJECT);
                case PRESENCE -> {
                    if (kind == DecisionKind.CUBIC) {
                        throw new IllegalArgumentException("SC-CHK-0106: PRESENCE bay " + id + " holds "
                                + check.id() + " whose focus kind is CUBIC");
                    }
                }
            }
        }
        if (coverage == Coverage.CUBIC && !decided.containsAll(CuboidSet.of(cells))) {
            throw new IllegalArgumentException("SC-BAY-0107: CUBIC bay " + id + " leaves "
                    + CuboidSet.of(cells).subtract(decided) + " undecided");
        }
    }

    private static void requireKind(NamespacedId id, Coverage coverage, Check<?> check,
                                    DecisionKind kind, DecisionKind expected) {
        if (kind != expected) {
            throw new IllegalArgumentException("SC-CHK-0106: " + coverage + " bay " + id + " holds "
                    + check.id() + " whose focus kind is " + kind + ", not " + expected);
        }
    }
}
