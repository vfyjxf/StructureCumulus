package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.DeriveContext;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeriveReplacementTest {

    private static final Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
    private static final OwnerId owner = OwnerId.of(id("owner"));

    private static NamespacedId id(String path) {
        return NamespacedId.of("derive_replacement", path);
    }

    @Test
    void deriveDropsAnOutputThePassDidNotProduce() {
        ContextKey<Long> heat = ContextKeys.of(id("heat"), Long.class, Retention.instance);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        ContextStore store = ContextStore.inMemory();
        store.publish(instance, Map.of(heat, 1L), FindingBundle.empty(), Spend.zero);

        AtomicBoolean produce = new AtomicBoolean(true);
        Deriver<Long> burner = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return heat;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of();
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                return produce.get() ? Outcome.found(2L) : Outcome.missing();
            }
        };
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers")).put(id("burner"), burner).build();
        List<DerivationSpec> specs = List.of(new DerivationSpec(id("burner"), Value.object(), heat.id(), 0));

        StructureContext first = MatchEngine.derive(formed(instance, 1), specs, derivers, store);
        assertEquals(2L, first.get(heat));

        produce.set(false);
        StructureContext second = MatchEngine.derive(formed(instance, 2), specs, derivers, store);
        assertNull(second.get(heat), "a Missing output is absent, not stale");
        assertFalse(second.keys().contains(heat));
        assertEquals(3, second.revision());
    }

    @Test
    void deriveKeepsValuesItDidNotDeclare() {
        ContextKey<Long> volume = ContextKeys.of(id("volume"), Long.class, Retention.instance);
        ContextKey<Long> capacity = ContextKeys.of(id("capacity"), Long.class, Retention.instance);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        ContextStore store = ContextStore.inMemory();
        store.publish(instance, Map.of(volume, 5L), FindingBundle.empty(), Spend.zero);

        Deriver<Long> capacityDeriver = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return capacity;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of(volume);
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                return ctx.require(volume).fold(value -> Outcome.found(value * 10L), Outcome::missing);
            }
        };
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers"))
                .put(id("capacity_deriver"), capacityDeriver).build();
        List<DerivationSpec> specs = List.of(new DerivationSpec(id("capacity_deriver"), Value.object(), capacity.id(), 0));

        StructureContext context = MatchEngine.derive(formed(instance, 1), specs, derivers, store);
        assertEquals(5L, context.get(volume), "a settle value survives a derive that does not declare it");
        assertEquals(50L, context.get(capacity));
    }

    @Test
    void deriveCannotReadAStaleDeclaredOutput() {
        ContextKey<Long> shared = ContextKeys.of(id("shared"), Long.class, Retention.instance);
        InstanceId instance = new InstanceId(TestWorld.world, 1);
        ContextStore store = ContextStore.inMemory();
        store.publish(instance, Map.of(shared, 1L), FindingBundle.empty(), Spend.zero);

        Deriver<Long> silent = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return shared;
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
        AtomicBoolean ran = new AtomicBoolean(false);
        Deriver<Long> reader = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return shared;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of(shared);
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                ran.set(true);
                return Outcome.found(9L);
            }
        };
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers"))
                .put(id("silent"), silent)
                .put(id("reader"), reader)
                .build();
        List<DerivationSpec> specs = List.of(
            new DerivationSpec(id("silent"), Value.object(), shared.id(), 0),
            new DerivationSpec(id("reader"), Value.object(), shared.id(), 1)
        );

        StructureContext context = MatchEngine.derive(formed(instance, 1), specs, derivers, store);
        assertFalse(ran.get(), "a declared output the earlier deriver dropped is absent, never the stale value");
        assertNull(context.get(shared));
        assertTrue(
            context.findings().findings().stream().anyMatch(finding -> finding.code().equals(Codes.keyAbsent.code())),
            "the skipped deriver reports SC-CTX-0007"
        );
    }

    @Test
    void settleThenDeriveReplacesTheInstanceContext() {
        ContextKey<Long> heat = ContextKeys.of(id("heat"), Long.class, Retention.instance);
        ContextKey<Long> volume = ContextKeys.of(id("volume"), Long.class, Retention.instance);
        StructureManager manager = StructureManager.create();
        StructureAttempt attempt = manager.begin(TestWorld.world, new StructureSpec(owner, siting, id("furnace")));
        StructureContext settled = attempt
                .publish(attempt.id(), Map.of(heat, 1L, volume, 5L), FindingBundle.empty(), new Spend(2, 1));
        attempt.commit(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)));

        Deriver<Long> burner = new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return heat;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of(volume);
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                return Outcome.found(2L);
            }
        };
        Catalog<Deriver<?>> derivers = Catalog.<Deriver<?>>builder(id("derivers")).put(id("burner"), burner).build();
        List<DerivationSpec> specs = List.of(new DerivationSpec(id("burner"), Value.object(), heat.id(), 0));

        StructureContext derived = MatchEngine.derive(formed(attempt.id(), 1), specs, derivers, manager);
        assertEquals(2, derived.revision());
        assertEquals(2L, derived.get(heat));
        assertEquals(5L, derived.get(volume));
        assertEquals(new Spend(2, 1), derived.spend(), "a derive adds no spend");

        StructureInstance updated = Objects.requireNonNull(manager.find(attempt.id()));
        assertEquals(derived, updated.context(), "the instance holds the replacement table");
        assertNotSame(settled, updated.context(), "the instance context is a new object");
        assertEquals(1L, settled.get(heat), "the settle snapshot keeps its table");
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
}
