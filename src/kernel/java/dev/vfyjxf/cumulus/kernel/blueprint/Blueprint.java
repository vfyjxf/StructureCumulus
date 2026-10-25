package dev.vfyjxf.cumulus.kernel.blueprint;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.context.Computation;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.StatePhase;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Compiled template; {@link BlueprintBuilder#build()} is the only construction path, so every value
 * satisfies the invariants its builder proves.
 */
public final class Blueprint {

    private final NamespacedId id;
    private final Cuboid bounds;
    private final List<Bay> bays;
    private final Map<NamespacedId, Bay> baysById;
    private final List<AnchorSpec> anchors;
    private final List<DerivationSpec> derivations;

    Blueprint(NamespacedId id, Cuboid bounds, List<Bay> bays, List<AnchorSpec> anchors,
              List<DerivationSpec> derivations) {
        this.id = Objects.requireNonNull(id, "id");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.bays = List.copyOf(bays);
        this.anchors = List.copyOf(anchors);
        this.derivations = List.copyOf(derivations);
        Map<NamespacedId, Bay> index = new LinkedHashMap<>();
        for (Bay bay : this.bays) {
            index.put(bay.id(), bay);
        }
        this.baysById = Map.copyOf(index);
    }

    public static BlueprintBuilder builder(NamespacedId id) {
        return new BlueprintBuilder(id);
    }

    public NamespacedId id() {
        return id;
    }

    public Cuboid bounds() {
        return bounds;
    }

    /** Declaration order: bays, then each bay's {@code Requirement} order. */
    public List<Bay> bays() {
        return bays;
    }

    public @Nullable Bay bay(NamespacedId bayId) {
        return baysById.get(bayId);
    }

    public List<AnchorSpec> anchors() {
        return anchors;
    }

    public List<DerivationSpec> derivations() {
        return derivations;
    }

    /**
     * The stage's plan: section computations, the implicit barrier, aggregate computations and
     * non-{@code DESIGN} derivations in Kahn order over their inputs, then checks.
     */
    public CheckPlan expand(Stage stage) {
        Objects.requireNonNull(stage, "stage");
        List<Computation<?>> section = new ArrayList<>();
        List<Node> graph = new ArrayList<>();
        List<Check<?>> stageChecks = new ArrayList<>();
        List<DerivationSpec> stageDerivations = new ArrayList<>();
        if (stage != Stage.DESIGN) {
            for (DerivationSpec derivation : derivations) {
                if (derivation.stage() == stage) {
                    stageDerivations.add(derivation);
                }
            }
        }
        stageDerivations.sort(Comparator.comparingInt(DerivationSpec::order)
                .thenComparing(DerivationSpec::deriver));
        int declaration = 0;
        for (Bay bay : bays) {
            for (Computation<?> computation : bay.requirement().computations()) {
                if (computation.stage() != stage) {
                    continue;
                }
                if (computation.phase() == StatePhase.SECTION) {
                    section.add(computation);
                } else {
                    graph.add(Node.ofComputation(computation, declaration++));
                }
            }
            for (Check<?> check : bay.requirement().checks()) {
                if (check.stage() == stage) {
                    stageChecks.add(check);
                }
            }
        }
        for (DerivationSpec derivation : stageDerivations) {
            graph.add(Node.ofDerivation(derivation, declaration++));
        }

        Map<ContextKey<?>, Stage> producedAt = producerStages();
        for (Check<?> check : stageChecks) {
            Bay owner = bay(check.bay());
            if (owner == null) {
                throw new IllegalArgumentException("SC-CHK-0107: no bay " + check.bay() + " for " + check.id());
            }
            for (ContextKey<?> key : check.reads(owner.cells()).keys()) {
                Stage producer = producedAt.get(key);
                if (producer != null && rank(producer) > rank(stage)) {
                    throw new IllegalArgumentException("SC-CHK-0109: check " + check.id()
                            + " runs at " + stage + " but reads " + key.id()
                            + ", produced at " + producer);
                }
            }
        }

        List<Integer> resolved = resolve(graph, stage);
        List<Computation<?>> computations = new ArrayList<>(section);
        List<DerivationSpec> orderedDerivations = new ArrayList<>();
        for (int index : resolved) {
            Node node = graph.get(index);
            if (node.computation() != null) {
                computations.add(node.computation());
            } else {
                orderedDerivations.add(Objects.requireNonNull(node.derivation(), "derivation"));
            }
        }
        return new CheckPlan(stage, computations, orderedDerivations, orderChecks(stageChecks));
    }

    private Map<ContextKey<?>, Stage> producerStages() {
        Map<ContextKey<?>, Stage> produced = new HashMap<>();
        for (Bay bay : bays) {
            for (Computation<?> computation : bay.requirement().computations()) {
                Stage previous = produced.putIfAbsent(computation.output(), computation.stage());
                if (previous != null && previous != computation.stage()) {
                    throw new IllegalArgumentException("SC-CHK-0109: " + computation.output().id()
                            + " is produced at both " + previous + " and " + computation.stage());
                }
            }
        }
        return produced;
    }

    private static int rank(Stage stage) {
        return switch (stage) {
            case DESIGN -> 0;
            case PROBE -> 1;
            case FORM -> 2;
            case SETTLE -> 3;
            case STALE -> Integer.MAX_VALUE;
        };
    }

    private static List<Integer> resolve(List<Node> graph, Stage stage) {
        Map<NamespacedId, Integer> producers = new HashMap<>();
        for (int index = 0; index < graph.size(); index++) {
            Node node = graph.get(index);
            Integer previous = producers.putIfAbsent(node.outputId(), index);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "SC-CTX-0102: " + node.outputId() + " has two producers in " + stage);
            }
        }
        int count = graph.size();
        List<List<Integer>> dependents = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            dependents.add(new ArrayList<>());
        }
        int[] inDegree = new int[count];
        for (int index = 0; index < count; index++) {
            for (ContextKey<?> input : graph.get(index).inputs()) {
                Integer producer = producers.get(input.id());
                if (producer == null) {
                    continue;
                }
                dependents.get(producer).add(index);
                inDegree[index]++;
            }
        }
        PriorityQueue<Integer> ready = new PriorityQueue<>((left, right) ->
                Integer.compare(graph.get(left).declaration(), graph.get(right).declaration()));
        for (int index = 0; index < count; index++) {
            if (inDegree[index] == 0) {
                ready.add(index);
            }
        }
        List<Integer> resolved = new ArrayList<>(count);
        while (!ready.isEmpty()) {
            int index = ready.poll();
            resolved.add(index);
            for (int dependent : dependents.get(index)) {
                if (--inDegree[dependent] == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (resolved.size() != count) {
            List<NamespacedId> stuck = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                if (inDegree[index] > 0) {
                    stuck.add(graph.get(index).outputId());
                }
            }
            throw new IllegalArgumentException("SC-CHK-0102: cycle in stage " + stage + " among " + stuck);
        }
        return resolved;
    }

    private static List<Check<?>> orderChecks(List<Check<?>> checks) {
        return List.copyOf(checks);
    }

    private record Node(@Nullable Computation<?> computation, @Nullable DerivationSpec derivation,
                        NamespacedId outputId, Set<ContextKey<?>> inputs, int declaration) {

        static Node ofComputation(Computation<?> computation, int declaration) {
            return new Node(computation, null, computation.output().id(), Set.copyOf(computation.inputs()),
                    declaration);
        }

        static Node ofDerivation(DerivationSpec derivation, int declaration) {
            return new Node(null, derivation, derivation.output(), Set.of(), declaration);
        }
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Blueprint blueprint
                && id.equals(blueprint.id)
                && bounds.equals(blueprint.bounds)
                && bays.equals(blueprint.bays)
                && anchors.equals(blueprint.anchors)
                && derivations.equals(blueprint.derivations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, bounds, bays, anchors, derivations);
    }

    @Override
    public String toString() {
        return "Blueprint[" + id + ", bounds=" + bounds + ", bays=" + bays.size()
                + ", anchors=" + anchors.size() + ", derivations=" + derivations.size() + "]";
    }
}
