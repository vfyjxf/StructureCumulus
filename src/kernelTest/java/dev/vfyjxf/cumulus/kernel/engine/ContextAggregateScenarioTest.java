package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.AggregateComputation;
import dev.vfyjxf.cumulus.kernel.structure.AggregateContext;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Context aggregates: custom aggregate values, missing results, dead whenAbsent and derivation keys. */
class ContextAggregateScenarioTest {

    private static final Siting siting = MagicFixtures.identity(LatticePoint.origin);
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
    private static final CuboidSet region = CuboidSet.of(bounds);

    @Test
    void aCustomAggregatePublishesAValueToTheCheck() {
        ContextKey<Long> key = ContextKeys
                .of(MagicFixtures.id("context/aggregate_key"), Long.class, Retention.instance);
        Blueprint blueprint = aggregateBlueprint("context/aggregate", key, Outcome.found(42L));
        TestWorld.View view = MagicFixtures.view().air(LatticePoint.origin);
        MatchResult result = MagicFixtures.check(blueprint, siting, view, new ClaimView(List.of(), 0, true));
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, result);
        assertEquals(42L, admitted.outcome().value(key).fold(value -> value, () -> -1L));
    }

    @Test
    void anAggregateMissingResultIsReportedAndTheCheckIsUndecided() {
        ContextKey<Long> key = ContextKeys.of(MagicFixtures.id("context/missing_key"), Long.class, Retention.instance);
        Blueprint blueprint = aggregateBlueprint("context/missing", key, Outcome.missing());
        TestWorld.View view = MagicFixtures.view().air(LatticePoint.origin);
        MatchResult result = MagicFixtures.check(blueprint, siting, view, new ClaimView(List.of(), 0, true));
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertTrue(MagicFixtures.hasCode(undecided, Codes.computationFailed), "the missing aggregate is SC-CTX-0001");
    }

    @Test
    @DisplayName("known gap: whenAbsent is unreachable through a check with no section computation")
    void whenAbsentIsADeadPathForACheckOnlyKey() {
        ContextKey<Long> key = ContextKeys
                .of(MagicFixtures.id("context/when_absent"), Long.class, Retention.instance, null, 7L);
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("context/absent_bay"),
            region,
            Coverage.anySubject,
            Footprint.box(),
            Sharing.shared
        );
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id("context/absent")).bounds(bounds)
                .check(Check.of(MagicFixtures.id("context/absent_check"), bay, Focuses.context(key), expectation(key)));
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () -> builder.build());
        String message = Objects.requireNonNull(rejected.getMessage(), "message");
        assertTrue(message.contains("SC-CHK-0002"), "no computation produces the whenAbsent key: " + message);
    }

    @Test
    void aDerivationOutputIsNotACheckComputation() {
        ContextKey<Long> derived = ContextKeys
                .of(MagicFixtures.id("context/derived_key"), Long.class, Retention.instance);
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("context/derived_bay"),
            region,
            Coverage.anySubject,
            Footprint.box(),
            Sharing.shared
        );
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id("context/derived")).bounds(bounds).check(
            Check.of(MagicFixtures.id("context/derived_check"), bay, Focuses.context(derived), expectation(derived))
        ).derive(new DerivationSpec(MagicFixtures.id("context/deriver"), Value.object(), derived.id(), 0));
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () -> builder.build());
        String message = Objects.requireNonNull(rejected.getMessage(), "message");
        assertTrue(message.contains("SC-CHK-0002"), "a derivation output is not a check computation: " + message);
    }

    private static Blueprint aggregateBlueprint(String path, ContextKey<Long> key, Outcome<Long> result) {
        Bay bay = MagicFixtures
                .bay(MagicFixtures.id(path + "_bay"), region, Coverage.anySubject, Footprint.box(), Sharing.shared);
        AggregateComputation<Long> aggregate = new AggregateComputation<>() {
            @Override
            public NamespacedId id() {
                return MagicFixtures.id(path + "_aggregate");
            }

            @Override
            public ContextKey<Long> output() {
                return key;
            }

            @Override
            public Set<ContextKey<?>> inputs() {
                return Set.of();
            }

            @Override
            public CuboidSet region() {
                return region;
            }

            @Override
            public Outcome<Long> compute(AggregateContext ctx) {
                return result;
            }
        };
        return Blueprint.builder(MagicFixtures.id(path)).bounds(bounds).computation(aggregate)
                .check(Check.of(MagicFixtures.id(path + "_check"), bay, Focuses.context(key), expectation(key)))
                .build();
    }

    private static Expectation<Long> expectation(ContextKey<Long> key) {
        return Expectations.custom(
            Long.class,
            new ExpectationMeta(
                MagicFixtures.id("context/expectation"),
                10,
                Set.of(key),
                FindingCode.of(FindingCode.Family.exp, 1)
            ),
            context -> {
                @Nullable
                Long subject = context.subject();
                return subject != null && key.type().isInstance(subject) ? Reply.match() : Reply.abstain();
            }
        );
    }
}
