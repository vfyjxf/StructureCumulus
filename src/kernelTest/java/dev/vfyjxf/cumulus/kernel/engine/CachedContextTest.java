package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.CachedContext;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.AggregateComputation;
import dev.vfyjxf.cumulus.kernel.structure.AggregateContext;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Computation;
import dev.vfyjxf.cumulus.kernel.structure.Computations;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.structure.SectionComputation;
import dev.vfyjxf.cumulus.kernel.structure.SectionContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Context expectations, the host cached-context port and the attempt composite's lookup order. */
class CachedContextTest {

    private static final Siting atOrigin = MagicFixtures.identity(LatticePoint.origin);
    private static final Cuboid checkCell = Cuboid.of(0, 0, 0, 1, 1, 1);

    @Test
    void theContextExpectationDeclaresTheKeyOfFocusesContext() {
        ContextKey<Long> key = instanceKey("cached/meta");
        Expectation<Long> expectation = Expectations.context(key, value -> value > 2L);
        assertEquals(Long.class, expectation.subject());
        assertEquals(Set.of(key), expectation.meta().requires());
        assertEquals(FindingCode.of(FindingCode.Family.exp, 1), expectation.meta().mismatchCode());
    }

    @Test
    void contextExpectationMatchesAndMismatches() {
        ContextKey<Long> key = mergedKey("cached/match");
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        Blueprint matching = volumeBlueprint("cached/match", region, key, value -> value == 2L);
        MatchResult.Admitted admitted = assertInstanceOf(
            MatchResult.Admitted.class,
            MatchEngine.check(request(matching, MagicFixtures.view(), null))
        );
        assertEquals(2L, admitted.outcome().value(key).fold(value -> value, () -> -1L));

        Blueprint mismatching = volumeBlueprint("cached/mismatch", region, key, value -> value == 3L);
        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            MatchEngine.check(request(mismatching, MagicFixtures.view(), null))
        );
        assertTrue(MagicFixtures.hasCode(failed, Codes.expected), "a context mismatch is SC-EXP-0001");
    }

    @Test
    void aHostDecodedKeyAnswersByTypeAndIdentity() {
        ContextKey<Long> blueprintKey = ContextKeys
                .of(id("cached/identity"), Long.class, Retention.instance, Merges.sum(), 11L);
        ContextKey<Long> decodedKey = plainKey("cached/identity");
        assertNotEquals(blueprintKey, decodedKey, "a decoded key loses merge and whenAbsent");

        Blueprint blueprint = requiringBlueprint("cached/identity", blueprintKey);
        CachedContext cached = hostDecoded(decodedContext(Map.of(decodedKey, 5L)));
        MatchRequest request = request(blueprint, MagicFixtures.view(), cached);
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        assertTrue(
            admitted.outcome().value(blueprintKey).fold(value -> false, () -> true),
            "a cached value is not a match value"
        );

        TestAttempt attempt = TestAttempt.of(request);
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(request, admitted, null, attempt)
        );
        assertEquals(1, formed.contextRevision());
        assertNull(attempt.store().context(MagicFixtures.instance).get(blueprintKey), "settle never re-publishes the cache");
        Catalog<Deriver<?>> derivers = Catalog.empty(id("derivers"));
        StructureContext derived = MatchEngine.derive(formed, List.of(), derivers, attempt.store());
        assertNull(derived.get(blueprintKey), "derive never reads the cache into the store");

        MatchResult.Undecided withoutCache = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(blueprint, MagicFixtures.view(), null))
        );
        assertTrue(MagicFixtures.hasCode(withoutCache, Codes.keyAbsent), "no cache leaves the key absent");
    }

    @Test
    void anAggregateInputOnlyKeyIsCacheEligible() {
        ContextKey<Long> inputKey = mergedKey("cached/input_only");
        ContextKey<Long> outputKey = instanceKey("cached/input_only_output");
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Blueprint blueprint = blueprint(
            "cached/input_only",
            region,
            List.of(missingSections(inputKey, region), aggregateFromContext(outputKey, inputKey, region)),
            List.of(contextCheck("cached/input_only_check", outputKey, value -> value == 5L))
        );
        assertTrue(
            blueprint.checks().stream().noneMatch(check -> check.expectation().meta().requires().contains(inputKey)),
            "no check declares the aggregate's input key"
        );

        CachedContext cached = hostDecoded(decodedContext(Map.of(plainKey("cached/input_only"), 5L)));
        MatchResult.Admitted admitted = assertInstanceOf(
            MatchResult.Admitted.class,
            MatchEngine.check(request(blueprint, MagicFixtures.view(), cached))
        );
        assertEquals(5L, admitted.outcome().value(outputKey).fold(value -> value, () -> -1L));
        assertTrue(
            admitted.outcome().value(inputKey).fold(value -> false, () -> true),
            "the cached input stays a cache entry, not a match value"
        );

        MatchResult.Undecided withoutCache = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(blueprint, MagicFixtures.view(), null))
        );
        assertTrue(MagicFixtures.hasCode(withoutCache, Codes.computationFailed), "the aggregate reads no input");
        assertTrue(MagicFixtures.hasCode(withoutCache, Codes.keyAbsent), "the aggregate output stays absent");
    }

    @Test
    void theCurrentAttemptValueShadowsTheCachedValue() {
        ContextKey<Long> declaredKey = instanceKey("cached/shadow");
        ContextKey<Long> producedKey = ContextKeys
                .of(id("cached/shadow"), Long.class, Retention.instance, Merges.sum(), 11L);
        ContextKey<Long> aggregateKey = instanceKey("cached/shadow_aggregate");
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        List<Computation<?>> computations = List
                .of(Computations.volume(producedKey, region), aggregateFromContext(aggregateKey, declaredKey, region));
        List<Check<?>> checks = List.of(
            contextCheck("cached/shadow_value", declaredKey, value -> value == 2L),
            contextCheck("cached/shadow_aggregate", aggregateKey, value -> value == 2L)
        );
        Blueprint blueprint = blueprint("cached/shadow", region, computations, checks);
        CachedContext cached = hostDecoded(decodedContext(Map.of(plainKey("cached/shadow"), 99L)));
        MatchResult.Admitted admitted = assertInstanceOf(
            MatchResult.Admitted.class,
            MatchEngine.check(request(blueprint, MagicFixtures.view(), cached))
        );
        assertEquals(
            2L,
            admitted.outcome().value(aggregateKey).fold(value -> value, () -> -1L),
            "the current value shadows the cached one even under a different key object"
        );
    }

    @Test
    void anUndeclaredCachedKeyDoesNotParticipate() {
        ContextKey<Long> extra = instanceKey("cached/extra");
        CachedContext cached = hostDecoded(decodedContext(Map.of(plainKey("cached/extra"), 9L)));
        Blueprint hidden = undeclaredBlueprint("cached/undeclared", extra);
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(hidden, MagicFixtures.view(), cached))
        );
        assertTrue(
            MagicFixtures.hasCode(undecided, Codes.keyAbsent),
            "a cached key no check or computation declares is not composed into the focus context"
        );

        Blueprint declared = requiringBlueprint("cached/declared", extra);
        MatchResult.Admitted admitted = assertInstanceOf(
            MatchResult.Admitted.class,
            MatchEngine.check(request(declared, MagicFixtures.view(), cached))
        );
        assertTrue(admitted.outcome().value(extra).fold(value -> false, () -> true));
    }

    @Test
    void aMatchKeyIsNeverTakenFromTheCache() {
        ContextKey<Long> key = ContextKeys.of(id("cached/match_retention"), Long.class, Retention.match, Merges.sum());
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Blueprint blueprint = blueprint(
            "cached/match_retention",
            region,
            List.of(missingSections(key, region)),
            List.of(contextCheck("cached/match_retention_check", key, value -> true))
        );
        CachedContext cached = hostDecoded(decodedContext(Map.of(plainKey("cached/match_retention"), 5L)));
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(blueprint, MagicFixtures.view(), cached))
        );
        assertTrue(MagicFixtures.hasCode(undecided, Codes.keyAbsent), "a match key is never cached-eligible");
        MatchOutcome outcome = Objects.requireNonNull(undecided.partial());
        assertTrue(outcome.value(key).fold(value -> false, () -> true));
    }

    @Test
    void aWrongTypeCachedValueIsTreatedAsAbsent() {
        ContextKey<Long> key = instanceKey("cached/wrong_type");
        Blueprint blueprint = requiringBlueprint("cached/wrong_type", key);
        CachedContext wrongType = new CachedContext() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> @Nullable T get(NamespacedId id, Class<T> type) {
                return (T) "five";
            }
        };
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(blueprint, MagicFixtures.view(), wrongType))
        );
        assertTrue(MagicFixtures.hasCode(undecided, Codes.keyAbsent), "a wrong-typed cached value is absent");
    }

    @Test
    void aHostExceptionIsTreatedAsAbsent() {
        ContextKey<Long> key = instanceKey("cached/exception");
        Blueprint blueprint = requiringBlueprint("cached/exception", key);
        CachedContext failing = new CachedContext() {
            @Override
            public <T> @Nullable T get(NamespacedId id, Class<T> type) {
                throw new IllegalStateException("host cache broke");
            }
        };
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(blueprint, MagicFixtures.view(), failing))
        );
        assertTrue(MagicFixtures.hasCode(undecided, Codes.keyAbsent), "a host exception is absent");
        assertFalse(
            MagicFixtures.hasCode(undecided, Codes.extensionFailed),
            "a host cache exception is not an extension failure"
        );
    }

    @Test
    void aMissingHostDecodedValueIsScCtx0007() {
        ContextKey<Long> key = instanceKey("cached/absent");
        Blueprint blueprint = requiringBlueprint("cached/absent", key);
        CachedContext cached = hostDecoded(decodedContext(Map.of(plainKey("cached/something_else"), 5L)));
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(blueprint, MagicFixtures.view(), cached))
        );
        assertEquals(UndecidedReason.unreadable, undecided.reason());
        assertTrue(MagicFixtures.hasCode(undecided, Codes.keyAbsent), "the missing cached key is SC-CTX-0007");
    }

    @Test
    void theSameRequestAndCacheAreDeterministic() {
        ContextKey<Long> inputKey = mergedKey("cached/deterministic");
        ContextKey<Long> outputKey = instanceKey("cached/deterministic_output");
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Blueprint blueprint = blueprint(
            "cached/deterministic",
            region,
            List.of(missingSections(inputKey, region), aggregateFromContext(outputKey, inputKey, region)),
            List.of(contextCheck("cached/deterministic_check", outputKey, value -> value == 5L))
        );
        CachedContext cached = hostDecoded(decodedContext(Map.of(plainKey("cached/deterministic"), 5L)));
        MatchResult.Admitted first = assertInstanceOf(
            MatchResult.Admitted.class,
            MatchEngine.check(request(blueprint, MagicFixtures.view(), cached))
        );
        MatchResult.Admitted second = assertInstanceOf(
            MatchResult.Admitted.class,
            MatchEngine.check(request(blueprint, MagicFixtures.view(), cached))
        );
        assertEquals(first.outcome().values(), second.outcome().values());
        assertEquals(first.outcome().spend(), second.outcome().spend());
        assertEquals(first.findings().findings(), second.findings().findings());

        ClaimLedger ledger = MagicFixtures.ledger();
        ContextStore contexts = ContextStore.inMemory();
        ContextKey<Long> failingKey = mergedKey("cached/failing");
        Blueprint failing = volumeBlueprint("cached/failing", region, failingKey, value -> value == 99L);
        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            MatchEngine.check(request(failing, MagicFixtures.view(), null))
        );
        assertTrue(MagicFixtures.hasCode(failed, Codes.expected));
        assertEquals(0L, ledger.revision());
        assertTrue(TestWorld.rows(ledger).isEmpty());
        assertEquals(0L, contexts.context(MagicFixtures.instance).revision());
        assertTrue(contexts.context(MagicFixtures.instance).values().isEmpty());
    }

    private static NamespacedId id(String path) {
        return MagicFixtures.id(path);
    }

    private static ContextKey<Long> instanceKey(String path) {
        return ContextKeys.of(id(path), Long.class, Retention.instance);
    }

    private static ContextKey<Long> mergedKey(String path) {
        return ContextKeys.of(id(path), Long.class, Retention.instance, Merges.sum());
    }

    private static ContextKey<Long> plainKey(String path) {
        return ContextKeys.of(id(path), Long.class, Retention.instance);
    }

    private static StructureContext decodedContext(Map<ContextKey<?>, Object> values) {
        return new StructureContext(MagicFixtures.instance, 3, values, FindingBundle.empty(), Spend.zero);
    }

    /** Models a host that decoded a stored StructureContext and answers by {@code (id, type)}. */
    private static CachedContext hostDecoded(StructureContext context) {
        return new CachedContext() {
            @Override
            public <T> @Nullable T get(NamespacedId id, Class<T> type) {
                for (ContextKey<?> key : context.keys()) {
                    if (key.id().equals(id) && key.type().equals(type)) {
                        Object value = context.values().get(key);
                        return value == null ? null : type.cast(value);
                    }
                }
                return null;
            }
        };
    }

    private static Bay bay(String path) {
        return MagicFixtures
                .bay(id(path + "_bay"), CuboidSet.of(checkCell), Coverage.anySubject, Footprint.none(), Sharing.shared);
    }

    private static Blueprint blueprint(
        String path,
        CuboidSet region,
        List<Computation<?>> computations,
        List<Check<?>> checks
    ) {
        Cuboid bounds = Objects.requireNonNull(region.union(checkCell).bounds(), "bounds");
        var builder = Blueprint.builder(id(path)).bounds(bounds);
        for (Computation<?> computation : computations) {
            builder.computation(computation);
        }
        for (Check<?> check : checks) {
            builder.check(check);
        }
        return builder.build();
    }

    private static Blueprint volumeBlueprint(
        String path,
        CuboidSet region,
        ContextKey<Long> key,
        Predicate<Long> test
    ) {
        return blueprint(
            path,
            region,
            List.of(Computations.volume(key, region)),
            List.of(contextCheck(path + "_value", key, test))
        );
    }

    private static Blueprint requiringBlueprint(String path, ContextKey<Long> key) {
        return blueprint(
            path,
            CuboidSet.of(checkCell),
            List.of(),
            List.of(contextCheck(path + "_check", key, value -> true))
        );
    }

    private static Blueprint undeclaredBlueprint(String path, ContextKey<Long> key) {
        Bay bay = bay(path);
        Check<Long> check = Check.of(
            id(path + "_check"),
            bay,
            Focuses.context(key),
            Expectations.custom(Long.class, ExpectationMeta.anonymous(), context -> Reply.match())
        );
        Cuboid bounds = Objects.requireNonNull(CuboidSet.of(checkCell).bounds(), "bounds");
        return Blueprint.builder(id(path)).bounds(bounds).check(check).build();
    }

    private static Check<Long> contextCheck(String path, ContextKey<Long> key, Predicate<Long> test) {
        return Check.of(id(path), bay(path), Focuses.context(key), Expectations.context(key, test));
    }

    private static SectionComputation<Long> missingSections(ContextKey<Long> output, CuboidSet region) {
        return new SectionComputation<>() {
            @Override
            public NamespacedId id() {
                return output.id();
            }

            @Override
            public ContextKey<Long> output() {
                return output;
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
            public Outcome<Long> compute(SectionContext context) {
                return Outcome.missing();
            }
        };
    }

    private static AggregateComputation<Long> aggregateFromContext(
        ContextKey<Long> output,
        ContextKey<Long> input,
        CuboidSet region
    ) {
        return new AggregateComputation<>() {
            @Override
            public NamespacedId id() {
                return output.id();
            }

            @Override
            public ContextKey<Long> output() {
                return output;
            }

            @Override
            public Set<ContextKey<?>> inputs() {
                return Set.of(input);
            }

            @Override
            public CuboidSet region() {
                return region;
            }

            @Override
            public Outcome<Long> compute(AggregateContext context) {
                Long value = context.context().get(input);
                return value == null ? Outcome.missing() : Outcome.found(value);
            }
        };
    }

    private static MatchRequest request(Blueprint blueprint, TestWorld.View view, @Nullable CachedContext cached) {
        return MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true),
            cached
        );
    }
}
