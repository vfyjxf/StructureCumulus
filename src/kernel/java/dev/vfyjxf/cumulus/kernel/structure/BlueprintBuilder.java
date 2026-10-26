package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
/** Fluent construction; every rule is checked once in {@link #build()}. */
public final class BlueprintBuilder {

    private final NamespacedId id;
    private @Nullable Cuboid bounds;
    private final List<Check<?>> checks = new ArrayList<>();
    private final List<Computation<?>> computations = new ArrayList<>();
    private final List<Seam> seams = new ArrayList<>();
    private final List<AnchorSpec> anchors = new ArrayList<>();
    private final List<DerivationSpec> derivations = new ArrayList<>();
    private @Nullable OrientationPolicy orientation;
    private Value arguments = Value.object();
    private @Nullable SourceSpan source;

    BlueprintBuilder(NamespacedId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public BlueprintBuilder bounds(Cuboid bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        return this;
    }

    public BlueprintBuilder check(Check<?> check) {
        checks.add(Objects.requireNonNull(check, "check"));
        return this;
    }

    public BlueprintBuilder check(List<? extends Check<?>> checks) {
        for (Check<?> check : checks) {
            check(check);
        }
        return this;
    }

    public BlueprintBuilder checks(Collection<? extends Check<?>> checks) {
        for (Check<?> check : checks) {
            check(check);
        }
        return this;
    }

    public BlueprintBuilder computation(Computation<?> computation) {
        computations.add(Objects.requireNonNull(computation, "computation"));
        return this;
    }

    public BlueprintBuilder computations(Collection<? extends Computation<?>> computations) {
        for (Computation<?> computation : computations) {
            computation(computation);
        }
        return this;
    }

    public BlueprintBuilder seam(Seam seam) {
        seams.add(Objects.requireNonNull(seam, "seam"));
        return this;
    }

    public BlueprintBuilder seams(Collection<Seam> seams) {
        for (Seam seam : seams) {
            seam(seam);
        }
        return this;
    }

    public BlueprintBuilder anchor(AnchorSpec anchor) {
        anchors.add(Objects.requireNonNull(anchor, "anchor"));
        return this;
    }

    public BlueprintBuilder anchors(Collection<AnchorSpec> anchors) {
        for (AnchorSpec anchor : anchors) {
            anchor(anchor);
        }
        return this;
    }

    public BlueprintBuilder orientation(OrientationPolicy orientation) {
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        return this;
    }

    public BlueprintBuilder derive(DerivationSpec derivation) {
        derivations.add(Objects.requireNonNull(derivation, "derivation"));
        return this;
    }

    public BlueprintBuilder derivations(Collection<DerivationSpec> derivations) {
        for (DerivationSpec derivation : derivations) {
            derive(derivation);
        }
        return this;
    }

    public BlueprintBuilder arguments(Value arguments) {
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        return this;
    }

    public BlueprintBuilder source(SourceSpan source) {
        this.source = Objects.requireNonNull(source, "source");
        return this;
    }

    public Blueprint build() {
        if (bounds == null) {
            fail("SC-BPT-0001", "a blueprint needs bounds");
        }
        if (bounds.isEmpty()) {
            fail("SC-BPT-0001", "bounds must not be empty");
        }
        if (checks.isEmpty()) {
            fail("SC-BPT-0001", "a blueprint needs at least one check");
        }

        Map<NamespacedId, Bay> bays = new LinkedHashMap<>();
        for (Check<?> check : checks) {
            Bay bay = check.bay();
            Bay previous = bays.putIfAbsent(bay.id(), bay);
            if (previous != null && !previous.equals(bay)) {
                fail("SC-BPT-0004", "two different bays share id " + bay.id());
            }
        }
        Set<NamespacedId> checkIds = new HashSet<>();
        for (Check<?> check : checks) {
            if (!checkIds.add(check.id())) {
                fail("SC-BPT-0004", "duplicate check id " + check.id());
            }
        }

        CuboidSet boundsSet = bounds.asSet();
        for (Bay bay : bays.values()) {
            if (!boundsSet.containsAll(bay.cells())) {
                fail("SC-BPT-0003", "bay " + bay.id() + " leaves the blueprint bounds");
            }
            if (bay.footprint() instanceof Footprint.Declared declared
                    && !boundsSet.containsAll(declared.cells())) {
                fail("SC-BPT-0010", "declared footprint of " + bay.id() + " leaves bounds");
            }
        }

        Map<NamespacedId, List<Check<?>>> checksByBay = new HashMap<>();
        for (Check<?> check : checks) {
            checksByBay.computeIfAbsent(check.bay().id(), ignored -> new ArrayList<>()).add(check);
        }
        for (Bay bay : bays.values()) {
            List<Check<?>> bayChecks = checksByBay.getOrDefault(bay.id(), List.of());
            CuboidSet decided = CuboidSet.empty();
            for (Check<?> check : bayChecks) {
                CuboidSet staticCells = check.focus().staticCells(bay.cells());
                if (!bay.cells().containsAll(staticCells)) {
                    fail("SC-CHK-0003", "focus of " + check.id() + " decides cells outside its bay");
                }
                decided = decided.union(staticCells);
            }
            switch (bay.coverage()) {
                case EVERY_CELL -> {
                    if (!decided.containsAll(bay.cells())) {
                        fail("SC-BPT-0005", "EVERY_CELL bay " + bay.id() + " has undecided cells");
                    }
                }
                case ONE_SUBJECT -> {
                    boolean binds = false;
                    for (Check<?> check : bayChecks) {
                        binds |= check.focus().bindsSubjects();
                    }
                    if (!binds) {
                        fail("SC-BPT-0005", "ONE_SUBJECT bay " + bay.id() + " binds no subject");
                    }
                }
                case ANY_SUBJECT -> {
                    if (!decided.isEmpty()) {
                        fail("SC-BPT-0005", "ANY_SUBJECT bay " + bay.id() + " has per-cell checks");
                    }
                }
            }
            if (bay.sharing() == Sharing.EXCLUSIVE && bay.footprint() instanceof Footprint.None) {
                fail("SC-BPT-0005", "EXCLUSIVE bay " + bay.id() + " owns nothing");
            }
        }

        List<Bay> bayList = List.copyOf(bays.values());
        for (int i = 0; i < bayList.size(); i++) {
            for (int j = i + 1; j < bayList.size(); j++) {
                Bay left = bayList.get(i);
                Bay right = bayList.get(j);
                if (left.cells().intersects(right.cells())
                        && (left.sharing() == Sharing.EXCLUSIVE
                        || right.sharing() == Sharing.EXCLUSIVE)) {
                    fail("SC-BPT-0002", "bays " + left.id() + " and " + right.id()
                            + " overlap with an exclusive side");
                }
            }
        }

        CuboidSet bayCells = Blueprint.unionOfCells(bayList);
        Set<SeamId> seamIds = new HashSet<>();
        for (Seam seam : seams) {
            if (!seamIds.add(seam.id())) {
                fail("SC-BPT-0004", "duplicate seam id " + seam.id());
            }
            if (seam.cells().isEmpty()) {
                fail("SC-BPT-0009", "seam " + seam.id() + " is empty");
            }
            if (!bayCells.containsAll(seam.cells().asSet())) {
                fail("SC-BPT-0009", "seam " + seam.id() + " leaves every bay");
            }
        }

        Set<NamespacedId> roles = new HashSet<>();
        for (AnchorSpec anchor : anchors) {
            if (!roles.add(anchor.role())) {
                fail("SC-BPT-0006", "duplicate anchor role " + anchor.role());
            }
        }
        if (anchors.isEmpty()) {
            if (orientation != null) {
                fail("SC-BPT-0006", "an orientation policy needs an anchor");
            }
        } else {
            if (orientation == null) {
                fail("SC-BPT-0006", "anchors need an orientation policy");
            }
            if (anchor(orientation.primaryRole()) == null) {
                fail("SC-BPT-0006", "the policy's primary role has no anchor");
            }
        }

        Set<NamespacedId> computationIds = new HashSet<>();
        Set<NamespacedId> outputs = new HashSet<>();
        Map<NamespacedId, Computation<?>> byOutput = new HashMap<>();
        for (Computation<?> computation : computations) {
            if (!computationIds.add(computation.id())) {
                fail("SC-BPT-0007", "duplicate computation id " + computation.id());
            }
            if (!outputs.add(computation.output().id())) {
                fail("SC-BPT-0007", "two computations produce " + computation.output().id());
            }
            byOutput.put(computation.output().id(), computation);
            if (computation instanceof SectionComputation<?> && !computation.inputs().isEmpty()) {
                fail("SC-BPT-0007", "section computation " + computation.id() + " has inputs");
            }
        }
        for (Computation<?> computation : computations) {
            for (ContextKey<?> input : computation.inputs()) {
                if (!byOutput.containsKey(input.id())) {
                    fail("SC-BPT-0007", "computation " + computation.id()
                            + " reads unknown key " + input.id());
                }
            }
            if (computation instanceof SectionComputation<?>
                    && computation.output().merge() == null) {
                fail("SC-BPT-0007", "section computation " + computation.id()
                        + " needs a contributed output key");
            }
        }
        Set<ContextKey<?>> produced = new HashSet<>();
        for (Computation<?> computation : computations) {
            produced.add(computation.output());
        }
        for (Check<?> check : checks) {
            for (ContextKey<?> required : check.expectation().meta().requires()) {
                if (!produced.contains(required)) {
                    fail("SC-CHK-0002", "check " + check.id() + " requires "
                            + required.id() + ", which no computation produces");
                }
            }
        }

        Set<Integer> orders = new HashSet<>();
        Set<NamespacedId> derivationOutputs = new HashSet<>();
        for (DerivationSpec derivation : derivations) {
            if (!orders.add(derivation.order())) {
                fail("SC-BPT-0008", "duplicate derivation order " + derivation.order());
            }
            if (!derivationOutputs.add(derivation.output())) {
                fail("SC-BPT-0008", "two derivations produce " + derivation.output());
            }
            if (outputs.contains(derivation.output())) {
                fail("SC-BPT-0008", "a derivation and a computation produce "
                        + derivation.output());
            }
        }

        List<SectionComputation<?>> sections = new ArrayList<>();
        List<AggregateComputation<?>> aggregateList = new ArrayList<>();
        for (Computation<?> computation : computations) {
            if (computation instanceof SectionComputation<?> section) {
                sections.add(section);
            } else if (computation instanceof AggregateComputation<?> aggregate) {
                aggregateList.add(aggregate);
            } else {
                fail("SC-BPT-0007", "unknown computation kind " + computation.id());
            }
        }
        List<AggregateComputation<?>> orderedAggregates = orderAggregates(aggregateList, byOutput);
        List<Check<?>> checkList = List.copyOf(checks);
        return new Blueprint(id, bounds, bayList, checkList, List.copyOf(computations),
                List.copyOf(seams), List.copyOf(anchors), orientation, List.copyOf(derivations),
                arguments, source, new Plan(sections, orderedAggregates, checkList), bayCells);
    }

    private List<AggregateComputation<?>> orderAggregates(
            List<AggregateComputation<?>> aggregates, Map<NamespacedId, Computation<?>> byOutput) {
        Map<NamespacedId, Integer> indegree = new LinkedHashMap<>();
        Map<NamespacedId, List<AggregateComputation<?>>> dependents = new HashMap<>();
        for (AggregateComputation<?> aggregate : aggregates) {
            int dependencies = 0;
            for (ContextKey<?> input : aggregate.inputs()) {
                Computation<?> producer = byOutput.get(input.id());
                if (producer instanceof AggregateComputation<?> upstream) {
                    dependencies++;
                    dependents.computeIfAbsent(upstream.id(), ignored -> new ArrayList<>())
                            .add(aggregate);
                }
            }
            indegree.put(aggregate.id(), dependencies);
        }
        List<AggregateComputation<?>> ordered = new ArrayList<>(aggregates.size());
        while (ordered.size() < aggregates.size()) {
            AggregateComputation<?> next = null;
            for (AggregateComputation<?> aggregate : aggregates) {
                if (!ordered.contains(aggregate) && indegree.get(aggregate.id()) == 0) {
                    next = aggregate;
                    break;
                }
            }
            if (next == null) {
                fail("SC-BPT-0007", "the computation graph has a cycle");
            }
            ordered.add(next);
            for (AggregateComputation<?> dependent : dependents.getOrDefault(next.id(), List.of())) {
                indegree.computeIfPresent(dependent.id(), (ignored, value) -> value - 1);
            }
        }
        return ordered;
    }

    private @Nullable AnchorSpec anchor(NamespacedId role) {
        for (AnchorSpec anchor : anchors) {
            if (anchor.role().equals(role)) {
                return anchor;
            }
        }
        return null;
    }

    private static void fail(String code, String message) {
        throw new IllegalArgumentException(code + ": " + message);
    }
}
