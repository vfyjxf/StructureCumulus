package dev.vfyjxf.cumulus.kernel.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.ReadScope;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimScope;
import dev.vfyjxf.cumulus.kernel.context.Computation;
import dev.vfyjxf.cumulus.kernel.context.ComputationContext;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.ContextKeys;
import dev.vfyjxf.cumulus.kernel.context.Retention;
import dev.vfyjxf.cumulus.kernel.context.StatePhase;
import dev.vfyjxf.cumulus.kernel.predicate.Check;
import dev.vfyjxf.cumulus.kernel.predicate.DecisionKind;
import dev.vfyjxf.cumulus.kernel.predicate.Expectation;
import dev.vfyjxf.cumulus.kernel.predicate.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.predicate.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.predicate.Focus;
import dev.vfyjxf.cumulus.kernel.predicate.Focuses;
import dev.vfyjxf.cumulus.kernel.predicate.FocusContext;
import dev.vfyjxf.cumulus.kernel.predicate.Reply;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlueprintTest {

    private static final NamespacedId BLUEPRINT = NamespacedId.of("test", "machine");
    private static final NamespacedId BAY = NamespacedId.of("test", "body");
    private static final NamespacedId OTHER_BAY = NamespacedId.of("test", "other");
    private static final Cuboid CELLS = Cuboid.of(0, 0, 0, 2, 2, 2);

    private static ContextKey<String> key(String path) {
        return ContextKeys.of(NamespacedId.of("test", path), String.class, Retention.CONTEXT, null, null);
    }

    private record Compute(NamespacedId id, Stage stage, StatePhase phase, ContextKey<String> output,
                           Set<ContextKey<?>> inputs) implements Computation<String> {

        @Override
        public CuboidSet region() {
            return CuboidSet.of(CELLS);
        }

        @Override
        public Outcome<String> compute(ComputationContext ctx) {
            throw new UnsupportedOperationException();
        }
    }

    private static Focus<String> focus(DecisionKind kind, CuboidSet decides) {
        return new Focus<>() {

            @Override
            public Outcome<String> take(FocusContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Class<String> subject() {
                return String.class;
            }

            @Override
            public DecisionKind decisionKind() {
                return kind;
            }

            @Override
            public CuboidSet staticDecisionCells(Cuboid bayCells) {
                return decides;
            }

            @Override
            public ReadScope reads(Cuboid bayCells) {
                return ReadScope.none();
            }

            @Override
            public FindingBundle describeMissing() {
                return FindingBundle.empty();
            }
        };
    }

    private static Focus<String> cubic() {
        return focus(DecisionKind.CUBIC, CuboidSet.of(CELLS));
    }

    private static Expectation<String> expectation(int specificity) {
        return new Expectation<>() {

            @Override
            public Reply test(ExpectationContext<String> ctx) {
                return Reply.match();
            }

            @Override
            public Class<String> subject() {
                return String.class;
            }

            @Override
            public ExpectationMeta meta() {
                return ExpectationMeta.of(NamespacedId.of("test", "e" + specificity), specificity,
                        dev.vfyjxf.cumulus.kernel.base.FindingCode.of(
                                dev.vfyjxf.cumulus.kernel.base.FindingCode.Family.EXP, 1));
            }
        };
    }

    private static Check<String> check(NamespacedId bay, String path, Stage stage, int specificity) {
        return new Check<>(NamespacedId.of("test", path), stage, bay, cubic(), expectation(specificity),
                null);
    }

    private static BayBuilder shell() {
        return BayBuilder.start(BAY, CELLS, Coverage.CUBIC, ClaimMode.EXCLUSIVE, new ClaimScope.Owned());
    }

    private static BayBuilder shell(NamespacedId id, Cuboid cells, Coverage coverage, ClaimMode mode,
                                    ClaimScope scope) {
        return BayBuilder.start(id, cells, coverage, mode, scope);
    }

    private static Blueprint machine(Bay bay) {
        return Blueprint.builder(BLUEPRINT).bounds(CELLS).bay(bay).build();
    }

    private static Requirement units(String path) {
        return new Requirement(List.of(), List.of(new Compute(NamespacedId.of("test", path), Stage.FORM,
                StatePhase.SECTION, key(path), Set.of())));
    }

    /** A CUBIC bay whose only check is at PROBE, so a FORM plan carries computations alone. */
    private static Blueprint machineWithProbeCheck(Requirement requirement, DerivationSpec... derivations) {
        BayBuilder builder = shell();
        Check<String> probe = check(builder.bayId(), "coverage", Stage.PROBE, 1);
        Requirement withCheck = new Requirement(List.of(probe), requirement.computations());
        BlueprintBuilder blueprint = Blueprint.builder(BLUEPRINT).bounds(CELLS).bay(builder.attach(withCheck));
        for (DerivationSpec derivation : derivations) {
            blueprint.derive(derivation);
        }
        return blueprint.build();
    }

    @Test
    void sectionComputationsComeFirstAndAggregatesFollowTheirInputs() {
        ContextKey<String> alpha = key("alpha");
        ContextKey<String> beta = key("beta");
        Compute section = new Compute(NamespacedId.of("test", "section"), Stage.FORM, StatePhase.SECTION,
                key("sectioned"), Set.of());
        Compute second = new Compute(NamespacedId.of("test", "second"), Stage.FORM, StatePhase.AGGREGATE,
                beta, Set.of(alpha));
        Compute first = new Compute(NamespacedId.of("test", "first"), Stage.FORM, StatePhase.AGGREGATE,
                alpha, Set.of());
        Requirement requirement = new Requirement(List.of(), List.of(second, section, first));

        CheckPlan plan = machineWithProbeCheck(requirement).expand(Stage.FORM);

        assertEquals(List.of(section, first, second), plan.computations());
        assertEquals(List.of(), plan.checks());
        assertEquals(List.of(), plan.derivations());
        assertEquals(Stage.FORM, plan.stage());
    }

    @Test
    void aCheckCannotReadAKeyProducedAtALaterStage() {
        ContextKey<String> late = key("late");
        Compute producer = new Compute(NamespacedId.of("test", "late"), Stage.FORM,
                StatePhase.AGGREGATE, late, Set.of());
        BayBuilder builder = BayBuilder.start(BAY, CELLS, Coverage.PRESENCE,
                ClaimMode.SHARED, new ClaimScope.Audit());
        Check<String> early = new Check<>(NamespacedId.of("test", "early"), Stage.PROBE,
                builder.bayId(), Focuses.context(late), expectation(1), null);
        Blueprint blueprint = Blueprint.builder(BLUEPRINT).bounds(CELLS)
                .bay(builder.attach(new Requirement(List.of(early), List.of(producer)))).build();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> blueprint.expand(Stage.PROBE));

        String message = Objects.requireNonNull(thrown.getMessage());
        assertTrue(message.contains("SC-CHK-0109"), message);
    }

    @Test
    void inputFromAnEarlierStageIsNotACycle() {
        ContextKey<String> probeOnly = key("probe_only");
        Compute aggregate = new Compute(NamespacedId.of("test", "aggregate"), Stage.FORM,
                StatePhase.AGGREGATE, key("later"), Set.of(probeOnly));

        CheckPlan plan = machineWithProbeCheck(new Requirement(List.of(), List.of(aggregate)))
                .expand(Stage.FORM);

        assertEquals(List.of(aggregate), plan.computations());
    }

    @Test
    void aggregateCycleThrows() {
        ContextKey<String> alpha = key("alpha");
        ContextKey<String> beta = key("beta");
        Compute left = new Compute(NamespacedId.of("test", "left"), Stage.FORM, StatePhase.AGGREGATE,
                alpha, Set.of(beta));
        Compute right = new Compute(NamespacedId.of("test", "right"), Stage.FORM, StatePhase.AGGREGATE,
                beta, Set.of(alpha));
        Blueprint blueprint = machineWithProbeCheck(new Requirement(List.of(), List.of(left, right)));

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> blueprint.expand(Stage.FORM));

        String message = Objects.requireNonNull(thrown.getMessage());
        assertTrue(message.contains("SC-CHK-0102"), message);
    }

    @Test
    void derivationsAppearInThePlanEvenWhenNoComputationReadsThem() {
        ContextKey<String> unread = key("unread");
        DerivationSpec spec = new DerivationSpec(NamespacedId.of("test", "deriver"), null, unread.id(),
                Stage.FORM, 0);
        Blueprint blueprint = machineWithProbeCheck(units("body"), spec);

        assertEquals(List.of(spec), blueprint.expand(Stage.FORM).derivations());
        assertEquals(List.of(), blueprint.expand(Stage.PROBE).derivations());
    }

    @Test
    void subjectBayCannotOwnItsCells() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> shell(BAY, CELLS, Coverage.SUBJECT, ClaimMode.EXCLUSIVE, new ClaimScope.Owned()));

        String message = Objects.requireNonNull(thrown.getMessage());
        assertTrue(message.contains("SC-CHK-0106"), message);
    }

    @Test
    void aCubicBayMustDecideEveryCell() {
        BayBuilder builder = shell();
        Check<String> half = new Check<>(NamespacedId.of("test", "half"), Stage.FORM, builder.bayId(),
                focus(DecisionKind.CUBIC, CuboidSet.of(Cuboid.of(0, 0, 0, 1, 2, 2))), expectation(1), null);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> builder.attach(new Requirement(List.of(half), List.of())));

        String message = Objects.requireNonNull(thrown.getMessage());
        assertTrue(message.contains("SC-BAY-0107"), message);
    }

    @Test
    void aDynamicFocusCannotSatisfyCubic() {
        BayBuilder builder = shell();
        Check<String> dynamic = new Check<>(NamespacedId.of("test", "dynamic"), Stage.FORM, builder.bayId(),
                focus(DecisionKind.SUBJECT, CuboidSet.empty()), expectation(1), null);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> builder.attach(new Requirement(List.of(dynamic), List.of())));

        String message = Objects.requireNonNull(thrown.getMessage());
        assertTrue(message.contains("SC-BAY-0107"), message);
    }

    @Test
    void aSubjectBayRefusesACubicCheck() {
        BayBuilder builder = shell(BAY, CELLS, Coverage.SUBJECT, ClaimMode.EXCLUSIVE, new ClaimScope.Occupied());
        Check<String> cubic = new Check<>(NamespacedId.of("test", "cubic"), Stage.FORM, builder.bayId(),
                cubic(), expectation(1), null);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> builder.attach(new Requirement(List.of(cubic), List.of())));

        String message = Objects.requireNonNull(thrown.getMessage());
        assertTrue(message.contains("SC-CHK-0106"), message);
    }

    @Test
    void aPresenceBayCannotBeExclusiveWithoutADeclaredScope() {
        BayBuilder builder = BayBuilder.start(BAY, CELLS, Coverage.PRESENCE,
                ClaimMode.EXCLUSIVE, new ClaimScope.Audit());
        Check<String> presence = new Check<>(NamespacedId.of("test", "presence"), Stage.FORM,
                builder.bayId(), focus(DecisionKind.PRESENCE, CuboidSet.empty()), expectation(1), null);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> builder.attach(new Requirement(List.of(presence), List.of())));

        String message = Objects.requireNonNull(thrown.getMessage());
        assertTrue(message.contains("SC-CHK-0106"), message);
    }

    @Test
    void aPresenceBayRefusesACubicCheck() {
        BayBuilder builder = shell(BAY, CELLS, Coverage.PRESENCE, ClaimMode.SHARED,
                new ClaimScope.Audit());
        Check<String> cubic = new Check<>(NamespacedId.of("test", "cubic"), Stage.FORM, builder.bayId(),
                focus(DecisionKind.CUBIC, CuboidSet.of(CELLS)), expectation(1), null);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> builder.attach(new Requirement(List.of(cubic), List.of())));

        String message = Objects.requireNonNull(thrown.getMessage());
        assertTrue(message.contains("SC-CHK-0106"), message);
    }

    @Test
    void checksKeepDeclarationOrder() {
        BayBuilder builder = shell();
        Check<String> cheap = check(builder.bayId(), "cheap", Stage.FORM, 1);
        Check<String> broad = check(builder.bayId(), "broad", Stage.FORM, 1);
        Check<String> narrow = check(builder.bayId(), "narrow", Stage.FORM, 5);
        Bay bay = builder.attach(new Requirement(List.of(broad, cheap, narrow), List.of()));

        CheckPlan plan = machine(bay).expand(Stage.FORM);

        assertEquals(List.of(broad, cheap, narrow), plan.checks(),
                "22 §7.1: independent units keep declaration order; cost is not a priority");
    }

    @Test
    void checkUnderAnotherBayIsRefused() {
        BayBuilder elsewhere = shell(OTHER_BAY, CELLS, Coverage.CUBIC, ClaimMode.EXCLUSIVE,
                new ClaimScope.Owned());
        BayBuilder builder = shell();
        Check<String> foreign = check(elsewhere.bayId(), "foreign", Stage.FORM, 1);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> builder.attach(new Requirement(List.of(foreign), List.of())));

        String message = Objects.requireNonNull(thrown.getMessage());
        assertTrue(message.contains("SC-CHK-0107"), message);
    }

    @Test
    void bayWithoutUnitsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Requirement(List.of(), List.of()));
    }

    @Test
    void intersectingExclusiveBaysAreRefused() {
        Cuboid other = Cuboid.of(1, 0, 0, 3, 2, 2);
        BlueprintBuilder builder = Blueprint.builder(BLUEPRINT)
                .bounds(Cuboid.of(0, 0, 0, 4, 2, 2))
                .bay(covered(BAY, CELLS, ClaimMode.EXCLUSIVE, new ClaimScope.Owned()))
                .bay(covered(OTHER_BAY, other, ClaimMode.EXCLUSIVE, new ClaimScope.Owned()));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, builder::build);

        String message = Objects.requireNonNull(thrown.getMessage());
        assertTrue(message.contains("B-7"), message);
    }

    @Test
    void sharedOverlapNeedsADeclaration() {
        Cuboid other = Cuboid.of(1, 0, 0, 3, 2, 2);
        BlueprintBuilder undeclared = Blueprint.builder(BLUEPRINT)
                .bounds(Cuboid.of(0, 0, 0, 4, 2, 2))
                .bay(covered(BAY, CELLS, ClaimMode.SHARED, new ClaimScope.Occupied()))
                .bay(covered(OTHER_BAY, other, ClaimMode.SHARED, new ClaimScope.Occupied()));

        assertThrows(IllegalArgumentException.class, undeclared::build);
        Blueprint builder = undeclared.overlap(BAY, OTHER_BAY).build();
        assertEquals(2, builder.bays().size());
    }

    @Test
    void bayOutsideBoundsIsRefused() {
        BlueprintBuilder builder = Blueprint.builder(BLUEPRINT)
                .bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .bay(covered(BAY, CELLS, ClaimMode.EXCLUSIVE, new ClaimScope.Owned()));

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    /** A CUBIC bay whose one check decides all of its own cells. */
    private static Bay covered(NamespacedId id, Cuboid cells, ClaimMode mode, ClaimScope scope) {
        BayBuilder builder = shell(id, cells, Coverage.CUBIC, mode, scope);
        Check<String> check = new Check<>(NamespacedId.of("test", id.path() + "_cells"), Stage.PROBE,
                builder.bayId(), focus(DecisionKind.CUBIC, CuboidSet.of(cells)), expectation(1), null);
        return builder.attach(new Requirement(List.of(check), List.of()));
    }
}
