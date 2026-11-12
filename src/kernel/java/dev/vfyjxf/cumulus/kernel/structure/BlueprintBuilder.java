package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
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

    private static final NamespacedId envelopeId = NamespacedId.of("structurecumulus", "envelope");
    private static final String envelopeDescription = "the derived envelope: every bounds cell that no bay or declared footprint covers";

    private final NamespacedId id;
    private @Nullable Cuboid bounds;
    private final List<Check<?>> checks = new ArrayList<>();
    private final List<Computation<?>> computations = new ArrayList<>();
    private final List<Seam> seams = new ArrayList<>();
    private final List<AnchorSpec> anchors = new ArrayList<>();
    private final List<DerivationSpec> derivations = new ArrayList<>();
    private @Nullable OrientationPolicy orientation;
    private Value arguments = Value.object();
    private @Nullable String description;
    private @Nullable VariantGroup variants;
    private @Nullable SourceSpan source;
    private boolean envelope;
    private boolean envelopeEntities;

    BlueprintBuilder(NamespacedId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public BlueprintBuilder bounds(Cuboid bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        return this;
    }

    /** Requires every bounds cell no bay or declared footprint covers to hold strict air. */
    public BlueprintBuilder envelope() {
        this.envelope = true;
        return this;
    }

    /** Makes the derived envelope count entities as foreign and adds the entity kind to its reads. */
    public BlueprintBuilder envelopeEntities() {
        this.envelopeEntities = true;
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

    public BlueprintBuilder description(@Nullable String description) {
        this.description = description;
        return this;
    }

    public BlueprintBuilder variants(@Nullable VariantGroup variants) {
        this.variants = variants;
        return this;
    }

    public BlueprintBuilder source(SourceSpan source) {
        this.source = Objects.requireNonNull(source, "source");
        return this;
    }

    public Blueprint build() {
        Cuboid declaredBounds = bounds;
        if (declaredBounds == null) {
            fail("SC-BPT-0001", "a blueprint needs bounds");
            throw new AssertionError("unreachable");
        }
        if (declaredBounds.isEmpty()) {
            fail("SC-BPT-0001", "bounds must not be empty");
        }
        List<Check<?>> declaredChecks = new ArrayList<>(checks);
        if (envelope) {
            CuboidSet declaredCells = CuboidSet.empty();
            for (Check<?> check : declaredChecks) {
                Bay bay = check.bay();
                declaredCells = declaredCells.union(bay.cells());
                if (bay.footprint() instanceof Footprint.Declared footprint) {
                    declaredCells = declaredCells.union(footprint.cells());
                }
            }
            CuboidSet envelopeCells = declaredBounds.asSet().subtract(declaredCells);
            if (!envelopeCells.isEmpty()) {
                for (Check<?> check : declaredChecks) {
                    if (check.id().equals(envelopeId) || check.bay().id().equals(envelopeId)) {
                        fail("SC-BPT-0004", "the reserved id " + envelopeId + " is already used");
                    }
                }
                Bay bay = Bay.of(
                    envelopeId,
                    envelopeCells,
                    Coverage.everyCell,
                    Footprint.none(),
                    Sharing.shared,
                    envelopeDescription
                );
                declaredChecks.add(
                    Check.of(
                        envelopeId,
                        bay,
                        Focuses.bay(),
                        envelopeEntities ? Expectations.envelopeEntities() : Expectations.envelopeCells(),
                        envelopeDescription
                    )
                );
            }
        }
        if (declaredChecks.isEmpty()) {
            fail("SC-BPT-0001", "a blueprint needs at least one check");
        }

        Map<NamespacedId, Bay> bays = new LinkedHashMap<>();
        for (Check<?> check : declaredChecks) {
            Bay bay = check.bay();
            Bay previous = bays.putIfAbsent(bay.id(), bay);
            if (previous != null && !previous.equals(bay)) {
                fail("SC-BPT-0004", "two different bays share id " + bay.id());
            }
        }
        Set<NamespacedId> checkIds = new HashSet<>();
        for (Check<?> check : declaredChecks) {
            if (!checkIds.add(check.id())) {
                fail("SC-BPT-0004", "duplicate check id " + check.id());
            }
        }

        CuboidSet boundsSet = declaredBounds.asSet();
        for (Bay bay : bays.values()) {
            if (!boundsSet.containsAll(bay.cells())) {
                fail("SC-BPT-0003", "bay " + bay.id() + " leaves the blueprint bounds");
            }
            if (bay.footprint() instanceof Footprint.Declared declared && !boundsSet.containsAll(declared.cells())) {
                fail("SC-BPT-0010", "declared footprint of " + bay.id() + " leaves bounds");
            }
        }

        Map<NamespacedId, List<Check<?>>> checksByBay = new HashMap<>();
        for (Check<?> check : declaredChecks) {
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
            if (!(bay.footprint() instanceof Footprint.None)) {
                for (Check<?> check : bayChecks) {
                    if (check.focus().bindsSubjects() && !check.focus().bindsClaimableSubject()) {
                        fail(
                            "SC-BPT-0005",
                            "focus of " + check.id() + " binds a non-claimable subject for claiming bay " + bay.id()
                        );
                    }
                }
            }
            switch (bay.coverage()) {
                case everyCell -> {
                    if (!decided.containsAll(bay.cells())) {
                        fail("SC-BPT-0005", "EVERY_CELL bay " + bay.id() + " has undecided cells");
                    }
                }
                case oneSubject -> {
                    boolean binds = false;
                    for (Check<?> check : bayChecks) {
                        binds |= check.focus().bindsSubjects();
                    }
                    if (!binds) {
                        fail("SC-BPT-0005", "ONE_SUBJECT bay " + bay.id() + " binds no subject");
                    }
                }
                case anySubject -> {
                    if (!decided.isEmpty()) {
                        fail("SC-BPT-0005", "ANY_SUBJECT bay " + bay.id() + " has per-cell checks");
                    }
                }
            }
            if (bay.sharing() == Sharing.exclusive) {
                CuboidSet owned = ownedBound(bay);
                if (owned != null && owned.isEmpty()) {
                    fail("SC-BPT-0005", "EXCLUSIVE bay " + bay.id() + " owns nothing");
                }
            }
        }

        List<Bay> bayList = List.copyOf(bays.values());
        for (int i = 0; i < bayList.size(); i++) {
            for (int j = i + 1; j < bayList.size(); j++) {
                Bay left = bayList.get(i);
                Bay right = bayList.get(j);
                CuboidSet leftOwned = ownedBound(left);
                CuboidSet rightOwned = ownedBound(right);
                if (leftOwned == null || rightOwned == null) {
                    continue;
                }
                if (leftOwned.intersects(rightOwned)
                        && (left.sharing() == Sharing.exclusive || right.sharing() == Sharing.exclusive)) {
                    fail("SC-BPT-0002", "bays " + left.id() + " and " + right.id() + " overlap with an exclusive side");
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
            OrientationPolicy policy = orientation;
            if (policy == null) {
                fail("SC-BPT-0006", "anchors need an orientation policy");
                throw new AssertionError("unreachable");
            }
            if (anchor(policy.primaryRole()) == null) {
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
            if (byOutput.putIfAbsent(computation.output().id(), computation) != null) {
                fail("SC-BPT-0007", "duplicate context key " + computation.output().id());
            }
            if (computation instanceof SectionComputation<?> && !computation.inputs().isEmpty()) {
                fail("SC-BPT-0007", "section computation " + computation.id() + " has inputs");
            }
        }
        for (Computation<?> computation : computations) {
            for (ContextKey<?> input : computation.inputs().stream().sorted(ContextKeys.order()).toList()) {
                Computation<?> producer = byOutput.get(input.id());
                if (producer == null) {
                    fail("SC-BPT-0007", "computation " + computation.id() + " reads unknown key " + input.id());
                    throw new AssertionError("unreachable");
                }
                if (producer.output().type() != input.type()) {
                    fail(
                        "SC-BPT-0007",
                        "computation " + computation.id() + " reads " + input.id() + " as " + input.type().getName()
                                + ", but its producer writes " + producer.output().type().getName()
                    );
                }
            }
            if (computation.region().isEmpty()) {
                fail("SC-BPT-0007", "computation " + computation.id() + " has an empty region");
            }
            if (computation instanceof SectionComputation<?>) {
                if (computation.output().merge() == null) {
                    fail("SC-BPT-0007", "section computation " + computation.id() + " needs a contributed output key");
                }
                if (!computation.inputs().isEmpty()) {
                    fail(
                        "SC-BPT-0007",
                        "section computation " + computation.id()
                                + " reads context, but a section sees only its snapshot"
                    );
                }
            }
        }
        for (Check<?> check : declaredChecks) {
            for (ContextKey<?> required : check.expectation().meta().requires()) {
                Computation<?> producer = byOutput.get(required.id());
                boolean produced = producer != null && producer.output().type() == required.type();
                if (!produced && required.retention() != Retention.instance) {
                    fail(
                        "SC-CHK-0002",
                        "check " + check.id() + " requires " + required.id() + ", which no computation produces"
                    );
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
                fail("SC-BPT-0008", "a derivation and a computation produce " + derivation.output());
            }
        }

        List<SectionComputation<?>> sections = new ArrayList<>();
        List<AggregateComputation<?>> aggregateList = new ArrayList<>();
        for (Computation<?> computation : computations) {
            if (computation instanceof SectionComputation<?> && computation instanceof AggregateComputation<?>) {
                fail(
                    "SC-BPT-0007",
                    "computation " + computation.id() + " is both a section and an aggregate computation"
                );
            }
            if (computation instanceof SectionComputation<?> section) {
                sections.add(section);
            } else if (computation instanceof AggregateComputation<?> aggregate) {
                aggregateList.add(aggregate);
            } else {
                fail("SC-BPT-0007", "unknown computation kind " + computation.id());
            }
        }
        List<AggregateComputation<?>> orderedAggregates = orderAggregates(aggregateList, byOutput);
        List<Check<?>> checkList = List.copyOf(declaredChecks);
        return new Blueprint(
            id,
            declaredBounds,
            bayList,
            checkList,
            List.copyOf(computations),
            List.copyOf(seams),
            List.copyOf(anchors),
            orientation,
            List.copyOf(derivations),
            arguments,
            description,
            variants,
            source,
            new Plan(sections, orderedAggregates, checkList),
            bayCells
        );
    }

    private List<AggregateComputation<?>> orderAggregates(
        List<AggregateComputation<?>> aggregates,
        Map<NamespacedId, Computation<?>> byOutput
    ) {
        Map<NamespacedId, Integer> indegree = new LinkedHashMap<>();
        Map<NamespacedId, List<AggregateComputation<?>>> dependents = new HashMap<>();
        for (AggregateComputation<?> aggregate : aggregates) {
            int dependencies = 0;
            for (ContextKey<?> input : aggregate.inputs()) {
                Computation<?> producer = byOutput.get(input.id());
                boolean sameType = producer != null && producer.output().type() == input.type();
                if (sameType && producer instanceof AggregateComputation<?> upstream) {
                    dependencies++;
                    dependents.computeIfAbsent(upstream.id(), ignored -> new ArrayList<>()).add(aggregate);
                }
            }
            indegree.put(aggregate.id(), dependencies);
        }
        List<AggregateComputation<?>> ordered = new ArrayList<>(aggregates.size());
        while (ordered.size() < aggregates.size()) {
            AggregateComputation<?> next = null;
            for (AggregateComputation<?> aggregate : aggregates) {
                Integer degree = indegree.get(aggregate.id());
                if (!ordered.contains(aggregate) && degree != null && degree == 0) {
                    next = aggregate;
                    break;
                }
            }
            if (next == null) {
                fail("SC-BPT-0007", "the computation graph has a cycle");
                throw new AssertionError("unreachable");
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

    private static @Nullable CuboidSet ownedBound(Bay bay) {
        return switch (bay.footprint()) {
            case Footprint.Box ignored -> bay.cells();
            case Footprint.Declared declared -> declared.cells();
            case Footprint.Bound ignored -> null; // the owned set exists only at settle
            case Footprint.None ignored -> CuboidSet.empty();
        };
    }

    private static void fail(String code, String message) {
        FindingCode findingCode = FindingCode.of(code);
        CodeEntry entry = Codes.all.stream().filter(candidate -> candidate.code().equals(findingCode)).findFirst()
                .orElse(Codes.blueprintInvalid);
        throw new BlueprintRejected(entry, message);
    }
}
