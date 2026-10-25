package dev.vfyjxf.cumulus.kernel.blueprint;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fluent, mutable, not thread-safe; every invariant it can prove is proved once in {@link #build()},
 * which copies without consuming the builder.
 *
 * <p>Overlap declarations are checked at {@code build()} but not retained: the mandated {@code Blueprint}
 * shape carries no overlap list, so the licence pair cannot be re-read from the value.
 */
public final class BlueprintBuilder {

    private final NamespacedId id;
    private final List<Bay> bays = new ArrayList<>();
    private final List<AnchorSpec> anchors = new ArrayList<>();
    private final List<DerivationSpec> derivations = new ArrayList<>();
    private final List<OverlapDecl> overlaps = new ArrayList<>();
    private @Nullable Cuboid bounds;

    public BlueprintBuilder(NamespacedId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public BlueprintBuilder bounds(Cuboid bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        return this;
    }

    public BlueprintBuilder bay(Bay bay) {
        bays.add(Objects.requireNonNull(bay, "bay"));
        return this;
    }

    public BlueprintBuilder anchor(AnchorSpec spec) {
        anchors.add(Objects.requireNonNull(spec, "spec"));
        return this;
    }

    public BlueprintBuilder anchor(NamespacedId role, NamespacedId property, String value) {
        return anchor(new AnchorSpec(role, property, value));
    }

    public BlueprintBuilder derive(DerivationSpec spec) {
        derivations.add(Objects.requireNonNull(spec, "spec"));
        return this;
    }

    /** Declares the licence for two intersecting {@code SHARED} bays; a disjoint pair is a typo. */
    public BlueprintBuilder overlap(NamespacedId low, NamespacedId high) {
        overlaps.add(new OverlapDecl(Objects.requireNonNull(low, "low"), Objects.requireNonNull(high, "high")));
        return this;
    }

    public Blueprint build() {
        if (bounds == null) {
            throw new IllegalArgumentException("B-1: blueprint " + id + " declares no bounds");
        }
        if (bounds.isEmpty()) {
            throw new IllegalArgumentException("B-1: blueprint " + id + " has empty bounds " + bounds);
        }
        List<Bay> builtBays = List.copyOf(bays);
        Map<NamespacedId, Bay> baysById = new LinkedHashMap<>();
        Set<NamespacedId> checkIds = new HashSet<>();
        for (Bay bay : builtBays) {
            if (!bounds.contains(bay.cells())) {
                throw new IllegalArgumentException("B-1: bay " + bay.id() + " " + bay.cells()
                        + " leaves the blueprint bounds " + bounds);
            }
            if (baysById.putIfAbsent(bay.id(), bay) != null) {
                throw new IllegalArgumentException("B-2: bay " + bay.id() + " is declared twice");
            }
            for (Check<?> check : bay.requirement().checks()) {
                if (!checkIds.add(check.id())) {
                    throw new IllegalArgumentException("B-2: check " + check.id() + " is declared twice");
                }
            }
        }
        Set<NamespacedId> roles = new HashSet<>();
        for (AnchorSpec anchor : anchors) {
            if (!roles.add(anchor.role())) {
                throw new IllegalArgumentException("B-4: anchor role " + anchor.role() + " is declared twice");
            }
        }
        Set<NamespacedId> outputs = new HashSet<>();
        Set<StageSlot> slots = new HashSet<>();
        for (DerivationSpec derivation : derivations) {
            if (!outputs.add(derivation.output())) {
                throw new IllegalArgumentException(
                        "B-6: derivation output " + derivation.output() + " has two producers");
            }
            if (!slots.add(new StageSlot(derivation.stage(), derivation.order()))) {
                throw new IllegalArgumentException("B-6: derivation order " + derivation.order()
                        + " is used twice in " + derivation.stage());
            }
        }
        checkOverlaps(builtBays, baysById);
        return new Blueprint(id, bounds, builtBays, anchors, derivations);
    }

    private void checkOverlaps(List<Bay> builtBays, Map<NamespacedId, Bay> baysById) {
        Set<BayPair> declared = new HashSet<>();
        for (OverlapDecl overlap : overlaps) {
            Bay low = baysById.get(overlap.low());
            Bay high = baysById.get(overlap.high());
            if (low == null || high == null) {
                throw new IllegalArgumentException("B-7: overlap names an undeclared bay "
                        + (low == null ? overlap.low() : overlap.high()));
            }
            if (!declared.add(BayPair.of(low.id(), high.id()))) {
                throw new IllegalArgumentException("B-7: overlap of " + low.id() + " and " + high.id()
                        + " is declared twice");
            }
            if (!low.cells().intersects(high.cells())) {
                throw new IllegalArgumentException("B-7: bays " + low.id() + " and " + high.id()
                        + " are declared as overlapping but are disjoint");
            }
            if (low.mode() != ClaimMode.SHARED || high.mode() != ClaimMode.SHARED) {
                throw new IllegalArgumentException("B-7: bays " + low.id() + " and " + high.id()
                        + " overlap but both are not SHARED");
            }
        }
        for (int first = 0; first < builtBays.size(); first++) {
            for (int second = first + 1; second < builtBays.size(); second++) {
                Bay low = builtBays.get(first);
                Bay high = builtBays.get(second);
                if (!low.cells().intersects(high.cells())) {
                    continue;
                }
                if (low.mode() != ClaimMode.SHARED || high.mode() != ClaimMode.SHARED) {
                    throw new IllegalArgumentException("B-7: bays " + low.id() + " and " + high.id()
                            + " intersect at " + low.cells().intersect(high.cells())
                            + " and both are not SHARED");
                }
                if (!declared.contains(BayPair.of(low.id(), high.id()))) {
                    throw new IllegalArgumentException("B-7: bays " + low.id() + " and " + high.id()
                            + " intersect at " + low.cells().intersect(high.cells())
                            + " without an overlap declaration");
                }
            }
        }
    }

    private record OverlapDecl(NamespacedId low, NamespacedId high) {}

    private record StageSlot(Stage stage, int order) {}

    private record BayPair(NamespacedId low, NamespacedId high) {

        static BayPair of(NamespacedId left, NamespacedId right) {
            return left.compareTo(right) <= 0
                    ? new BayPair(left, right)
                    : new BayPair(right, left);
        }
    }
}
