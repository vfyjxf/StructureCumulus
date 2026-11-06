package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.DeriveContext;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** context.md §7 derive obligations: order, requires, missing deriver, stale base, one batch, no partial publish. */
class DeriveScenarioTest {

    private static final Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);

    private static NamespacedId id(String path) {
        return NamespacedId.of("derive_scenario", path);
    }

    @Test
    void deriversRunInOrderThenIdAndSeeEarlierOutputs() {
        ContextKey<Long> first = ContextKeys.of(id("first"), Long.class, Retention.instance);
        ContextKey<Long> second = ContextKeys.of(id("second"), Long.class, Retention.instance);
        ContextKey<Long> third = ContextKeys.of(id("third"), Long.class, Retention.instance);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        ContextStore store = ContextStore.inMemory();

        List<String> ran = new ArrayList<>();
        Deriver<Long> firstDeriver = constant(first, 3L, ran, "first");
        Deriver<Long> alpha = echo(second, first, 100L, ran, "alpha");
        Deriver<Long> zeta = constant(third, 7L, ran, "zeta");
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers"))
                .put(id("first"), firstDeriver)
                .put(id("alpha"), alpha)
                .put(id("zeta"), zeta)
                .build();
        List<DerivationSpec> specs = List.of(
            new DerivationSpec(id("zeta"), Value.object(), third.id(), 1),
            new DerivationSpec(id("alpha"), Value.object(), second.id(), 1),
            new DerivationSpec(id("first"), Value.object(), first.id(), 0)
        );

        StructureContext context = MatchEngine.derive(formed(instance), specs, derivers, store);
        assertEquals(List.of("first", "alpha", "zeta"), ran);
        assertEquals(3L, context.get(first));
        assertEquals(103L, context.get(second), "a later deriver reads the value produced earlier in the pass");
        assertEquals(7L, context.get(third));
    }

    @Test
    void aPresentRequirementRunsAndAnAbsentOneSkipsWithCtx0007() {
        ContextKey<Long> heat = ContextKeys.of(id("heat"), Long.class, Retention.instance);
        ContextKey<Long> derived = ContextKeys.of(id("derived"), Long.class, Retention.instance);
        ContextKey<Long> absent = ContextKeys.of(id("absent"), Long.class, Retention.instance);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        ContextStore store = ContextStore.inMemory();
        store.publish(instance, Map.of(heat, 40L), FindingBundle.empty(), Spend.zero);

        List<String> ran = new ArrayList<>();
        Deriver<Long> reader = echo(derived, heat, 0L, ran, "reader");
        Deriver<Long> skipped = echo(absent, absent, 0L, ran, "skipped");
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers"))
                .put(id("reader"), reader)
                .put(id("skipped"), skipped)
                .build();
        List<DerivationSpec> specs = List.of(
            new DerivationSpec(id("reader"), Value.object(), derived.id(), 0),
            new DerivationSpec(id("skipped"), Value.object(), absent.id(), 0)
        );

        StructureContext context = MatchEngine.derive(formed(instance), specs, derivers, store);
        assertEquals(List.of("reader"), ran);
        assertEquals(40L, context.get(derived));
        assertNull(context.get(absent));
        Finding finding = finding(context, Codes.keyAbsent.code());
        assertTrue(finding.message().contains("misses a required key"), () -> finding.message());
        assertTrue(
            finding.labels().stream().anyMatch(label -> label.locus().equals(Locus.key(instance, absent.id()))),
            "the skip labels the deriver's output key"
        );
    }

    @Test
    void aMissingDeriverIsCtx0004AndTheNextStillRuns() {
        ContextKey<Long> ghostOut = ContextKeys.of(id("ghost_out"), Long.class, Retention.instance);
        ContextKey<Long> realOut = ContextKeys.of(id("real_out"), Long.class, Retention.instance);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        ContextStore store = ContextStore.inMemory();

        List<String> ran = new ArrayList<>();
        Deriver<Long> real = constant(realOut, 5L, ran, "real");
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers")).put(id("real"), real).build();
        List<DerivationSpec> specs = List.of(
            new DerivationSpec(id("ghost"), Value.object(), ghostOut.id(), 0),
            new DerivationSpec(id("real"), Value.object(), realOut.id(), 1)
        );

        StructureContext context = MatchEngine.derive(formed(instance), specs, derivers, store);
        assertEquals(List.of("real"), ran);
        assertNull(context.get(ghostOut), "a missing deriver leaves its output absent");
        assertEquals(5L, context.get(realOut));
        Finding finding = finding(context, Codes.deriverMissing.code());
        assertTrue(finding.message().contains("no deriver is registered as " + id("ghost").text()), () -> finding.message());
    }

    @Test
    void deriveReadsTheStoreNotTheFormedOutcome() {
        ContextKey<Long> heat = ContextKeys.of(id("heat"), Long.class, Retention.instance);
        ContextKey<Long> derived = ContextKeys.of(id("derived"), Long.class, Retention.instance);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        ContextStore store = ContextStore.inMemory();
        store.publish(instance, Map.of(heat, 7L), FindingBundle.empty(), Spend.zero);
        MatchResult.Formed formed = formed(instance, 1);

        store.publish(instance, Map.of(heat, 9L), FindingBundle.empty(), Spend.zero);
        Deriver<Long> reader = echo(derived, heat, 0L, new ArrayList<>(), "reader");
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers")).put(id("reader"), reader).build();
        List<DerivationSpec> specs = List.of(new DerivationSpec(id("reader"), Value.object(), derived.id(), 0));

        StructureContext context = MatchEngine.derive(formed, specs, derivers, store);
        assertEquals(9L, context.get(derived), "the pass reads the store's latest value, not the formed revision");
        assertEquals(3, context.revision());
    }

    @Test
    void onePassPublishesOneBatchWithEveryFoundValue() {
        ContextKey<Long> settle = ContextKeys.of(id("settle"), Long.class, Retention.instance);
        ContextKey<Long> first = ContextKeys.of(id("first"), Long.class, Retention.instance);
        ContextKey<Long> second = ContextKeys.of(id("second"), Long.class, Retention.instance);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        CountingStore store = new CountingStore();
        store.publish(instance, Map.of(settle, 1L), FindingBundle.empty(), Spend.zero);
        store.startPass();

        Deriver<Long> firstDeriver = constant(first, 10L, new ArrayList<>(), "first");
        Deriver<Long> secondDeriver = constant(second, 20L, new ArrayList<>(), "second");
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers"))
                .put(id("first"), firstDeriver)
                .put(id("second"), secondDeriver)
                .build();
        List<DerivationSpec> specs = List.of(
            new DerivationSpec(id("first"), Value.object(), first.id(), 0),
            new DerivationSpec(id("second"), Value.object(), second.id(), 1)
        );

        StructureContext context = MatchEngine.derive(formed(instance), specs, derivers, store);
        assertEquals(1, store.publishes, "one derive pass publishes exactly once");
        assertEquals(2, context.revision());
        Map<ContextKey<?>, Object> batch = store.tables.get(0);
        assertEquals(1L, batch.get(settle));
        assertEquals(10L, batch.get(first));
        assertEquals(20L, batch.get(second));
    }

    @Test
    void aFailingDeriverPublishesNoPartialBatch() {
        ContextKey<Long> settle = ContextKeys.of(id("settle"), Long.class, Retention.instance);
        ContextKey<Long> failing = ContextKeys.of(id("failing"), Long.class, Retention.instance);
        ContextKey<Long> later = ContextKeys.of(id("later"), Long.class, Retention.instance);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        CountingStore store = new CountingStore();
        store.publish(instance, Map.of(settle, 1L), FindingBundle.empty(), Spend.zero);
        store.startPass();

        Deriver<Long> silent = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return failing;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of();
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                return Outcome.missing();
            }
        };
        Deriver<Long> last = constant(later, 30L, new ArrayList<>(), "last");
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers"))
                .put(id("silent"), silent)
                .put(id("last"), last)
                .build();
        List<DerivationSpec> specs = List.of(
            new DerivationSpec(id("silent"), Value.object(), failing.id(), 0),
            new DerivationSpec(id("last"), Value.object(), later.id(), 1)
        );

        StructureContext context = MatchEngine.derive(formed(instance), specs, derivers, store);
        assertEquals(1, store.publishes, "a failing deriver still yields exactly one publish");
        assertEquals(2, context.revision());
        Map<ContextKey<?>, Object> batch = store.tables.get(0);
        assertFalse(batch.containsKey(failing));
        assertEquals(30L, batch.get(later), "the batch carries the whole pass, not a partial prefix");
        assertEquals(1L, batch.get(settle));
    }

    @Test
    void anEmptyDerivationListPublishesOneRevision() {
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        CountingStore store = new CountingStore();
        store.publish(instance, Map.of(), FindingBundle.empty(), Spend.zero);
        store.startPass();

        StructureContext context = MatchEngine.derive(
            formed(instance),
            List.of(),
            Catalog.empty(id("derivers")),
            store
        );
        assertEquals(1, store.publishes);
        assertEquals(2, context.revision());
    }

    @Test
    void aSilentMissingReturnIsCtx0005() {
        ContextKey<Long> out = ContextKeys.of(id("out"), Long.class, Retention.instance);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        ContextStore store = ContextStore.inMemory();

        Deriver<Long> silent = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return out;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of();
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                return Outcome.missing();
            }
        };
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers")).put(id("silent"), silent).build();
        List<DerivationSpec> specs = List.of(new DerivationSpec(id("silent"), Value.object(), out.id(), 0));

        StructureContext context = MatchEngine.derive(formed(instance), specs, derivers, store);
        assertNull(context.get(out));
        Finding finding = finding(context, Codes.deriverFailed.code());
        assertTrue(finding.message().contains("produced no value"), () -> finding.message());
    }

    @Test
    void aDeriversOwnFindingsReplaceCtx0005() {
        ContextKey<Long> out = ContextKeys.of(id("out"), Long.class, Retention.instance);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        ContextStore store = ContextStore.inMemory();

        Deriver<Long> reporting = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return out;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of();
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                ctx.report(
                    Finding.builder(Codes.keyAbsent).phase(Phase.settle).message("the deriver's own complaint").build()
                );
                return Outcome.missing();
            }
        };
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers")).put(id("reporting"), reporting).build();
        List<DerivationSpec> specs = List.of(new DerivationSpec(id("reporting"), Value.object(), out.id(), 0));

        StructureContext context = MatchEngine.derive(formed(instance), specs, derivers, store);
        assertNull(context.get(out));
        assertTrue(context.findings().findings().stream().noneMatch(f -> f.code().equals(Codes.deriverFailed.code())));
        assertTrue(
            context.findings().findings().stream().anyMatch(f -> f.message().contains("the deriver's own complaint"))
        );
    }

    @Test
    void aMatchRetentionOutputIsRejectedAsCtx0005() {
        ContextKey<Long> matchOut = ContextKeys.of(id("match_out"), Long.class, Retention.match);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        ContextStore store = ContextStore.inMemory();

        Deriver<Long> matchDeriver = constant(matchOut, 1L, new ArrayList<>(), "match");
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers")).put(id("match"), matchDeriver).build();
        List<DerivationSpec> specs = List.of(new DerivationSpec(id("match"), Value.object(), matchOut.id(), 0));

        StructureContext context = MatchEngine.derive(formed(instance), specs, derivers, store);
        assertFalse(context.keys().contains(matchOut), "a match key is never published");
        Finding finding = finding(context, Codes.deriverFailed.code());
        assertTrue(finding.message().contains("must produce an INSTANCE key"), () -> finding.message());
    }

    private static Deriver<Long> echo(
        ContextKey<Long> output,
        ContextKey<Long> requires,
        long offset,
        List<String> ran,
        String name
    ) {
        return new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return output;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of(requires);
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                ran.add(name);
                return ctx.require(requires).fold(value -> Outcome.found(value + offset), Outcome::missing);
            }
        };
    }

    private static Deriver<Long> constant(ContextKey<Long> output, long value, List<String> ran, String name) {
        return new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return output;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of();
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                ran.add(name);
                return Outcome.found(value);
            }
        };
    }

    private static Finding finding(StructureContext context, dev.vfyjxf.cumulus.kernel.core.FindingCode code) {
        return context.findings().findings().stream()
                .filter(finding -> finding.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no finding with code " + code));
    }

    private static MatchResult.Formed formed(InstanceId instance) {
        return formed(instance, 1);
    }

    private static MatchResult.Formed formed(InstanceId instance, long contextRevision) {
        return new MatchResult.Formed(
            Phase.settle,
            instance,
            siting,
            new MatchOutcome(new TestWorld.View(), Map.of(), List.of(), ReadScope.none(), Spend.zero),
            new ClaimSet(List.of(), 0),
            contextRevision,
            0,
            FindingBundle.empty()
        );
    }

    private static final class CountingStore implements ContextStore {

        private final ContextStore delegate = ContextStore.inMemory();
        private final List<Map<ContextKey<?>, Object>> tables = new ArrayList<>();
        private int publishes;

        void startPass() {
            publishes = 0;
            tables.clear();
        }

        @Override
        public StructureContext context(InstanceId instance) {
            return delegate.context(instance);
        }

        @Override
        public @Nullable StructureContext remove(InstanceId instance) {
            return delegate.remove(instance);
        }

        @Override
        public StructureContext publish(
            InstanceId instance,
            Map<ContextKey<?>, Object> values,
            FindingBundle findings,
            Spend spend
        ) {
            publishes++;
            tables.add(new HashMap<>(values));
            return delegate.publish(instance, values, findings, spend);
        }
    }
}
