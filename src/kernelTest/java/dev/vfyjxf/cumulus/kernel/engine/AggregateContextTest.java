package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.AggregateComputation;
import dev.vfyjxf.cumulus.kernel.structure.AggregateContext;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aggregate total read: region, total local read, refused cells, section charge and budget gate. */
class AggregateContextTest {

    private static final Siting atOrigin = MagicFixtures.identity(LatticePoint.origin);
    private static final Cuboid checkCell = Cuboid.of(0, 0, 0, 1, 1, 1);

    @Test
    void theContextExposesTheComputationRegionAndATotalMappedRead() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        ContextKey<Long> output = key("aggregate/read");
        AggregateContext[] seen = new AggregateContext[1];
        AggregateComputation<Long> aggregate = aggregate(output, region, context -> {
            seen[0] = context;
            return Outcome.found(1L);
        });
        Blueprint blueprint = blueprint("aggregate/read", region, aggregate);
        Siting siting = MagicFixtures.identity(LatticePoint.of(3, 0, 4));
        TestWorld.View view = MagicFixtures.view().put(TestWorld.block(TestWorld.stone, LatticePoint.of(3, 0, 4)))
                .air(LatticePoint.of(4, 0, 4));
        TestWorld.Lease lease = new TestWorld.Lease(view);
        MatchRequest request = MatchRequest.of(
            blueprint,
            siting,
            lease,
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));

        AggregateContext context = Objects.requireNonNull(seen[0], "the aggregate ran");
        assertEquals(region, context.cells());
        assertEquals(new Spend(2, 1), admitted.outcome().spend());
        CellRead first = context.read(LatticePoint.of(0, 0, 0));
        CellRead second = context.read(LatticePoint.of(1, 0, 0));
        assertInstanceOf(CellRead.Found.class, first);
        assertEquals(TestWorld.stone, ((CellRead.Found) first).occupant().descriptor().type());
        assertInstanceOf(CellRead.Found.class, second);
        assertTrue(((CellRead.Found) second).occupant().isAir());
        assertEquals(
            TestWorld.stone,
            Objects.requireNonNull(context.occupantAt(LatticePoint.of(0, 0, 0))).descriptor().type()
        );
        assertTrue(context.unreadable().isEmpty());
        assertNull(context.refusalAt(LatticePoint.of(0, 0, 0)));
    }

    @Test
    void occupantsByBoxStillReturnTheWholeFootprintOnly() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 8, 1, 1));
        ContextKey<Long> output = key("aggregate/occupants");
        AggregateContext[] seen = new AggregateContext[1];
        Blueprint blueprint = blueprint("aggregate/occupants", region, aggregate(output, region, context -> {
            seen[0] = context;
            return Outcome.found(1L);
        }));
        NamespacedId type = MagicFixtures.mc("wide_body");
        OccupantSnapshot stone = TestWorld.block(TestWorld.stone, LatticePoint.of(1, 0, 0));
        OccupantSnapshot second = TestWorld.block(TestWorld.stone, LatticePoint.of(2, 0, 0));
        // The body starts inside the query but its footprint ends outside it: not a clip, not a match.
        OccupantSnapshot body = new OccupantSnapshot(
            new OccupantRef(
                TestWorld.snapshot,
                0,
                type,
                OccupantKind.entity,
                LatticePoint.of(3, 0, 0),
                MagicFixtures.id("wide_body")
            ),
            new OccupantDescriptor(type, OccupantKind.entity, Map.of(), List.of()),
            Cuboid.of(3, 0, 0, 5, 1, 1),
            List.of()
        );
        TestWorld.View view = MagicFixtures.view().put(stone).put(second).put(body);
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        AggregateContext context = Objects.requireNonNull(seen[0], "the aggregate ran");
        List<OccupantSnapshot> found = context.occupants(Cuboid.of(0, 0, 0, 4, 1, 1));
        assertEquals(2, found.size(), "only bodies wholly inside the box are returned");
        assertTrue(found.stream().allMatch(occupant -> occupant.descriptor().type().equals(TestWorld.stone)));
    }

    @Test
    void aRefusedAndAnOutOfBoundsCellAreUnreadableNeverAbsent() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        ContextKey<Long> output = key("aggregate/refused");
        AggregateContext[] seen = new AggregateContext[1];
        Blueprint blueprint = blueprint("aggregate/refused", region, aggregate(output, region, context -> {
            seen[0] = context;
            return Outcome.found(3L);
        }));
        TestWorld.View view = MagicFixtures.view().bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).refuse(LatticePoint.origin);
        TestWorld.Lease lease = new TestWorld.Lease(view);
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            lease,
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));

        AggregateContext context = Objects.requireNonNull(seen[0], "the aggregate ran");
        Map<LatticePoint, ReadRefusal> unreadable = context.unreadable();
        assertEquals(2, unreadable.size());
        assertEquals(ReadStatus.notCaptured, Objects.requireNonNull(unreadable.get(LatticePoint.origin)).status());
        assertEquals(ReadStatus.outOfBounds, Objects.requireNonNull(unreadable.get(LatticePoint.of(1, 0, 0))).status());
        CellRead refused = context.read(LatticePoint.origin);
        CellRead outside = context.read(LatticePoint.of(1, 0, 0));
        assertInstanceOf(CellRead.Unreadable.class, refused);
        assertInstanceOf(CellRead.Unreadable.class, outside);
        assertNull(context.occupantAt(LatticePoint.origin));
        assertEquals(
            ReadStatus.outOfBounds,
            Objects.requireNonNull(context.refusalAt(LatticePoint.of(1, 0, 0))).status()
        );
    }

    @Test
    void aMultiSectionRegionIsChargedOnceAndReadPerSection() {
        CuboidSet region = CuboidSet.of(Cuboid.of(15, 0, 0, 17, 1, 1));
        ContextKey<Long> output = key("aggregate/sections");
        AggregateContext[] seen = new AggregateContext[1];
        AtomicInteger runs = new AtomicInteger();
        Blueprint blueprint = blueprint("aggregate/sections", region, aggregate(output, region, context -> {
            seen[0] = context;
            runs.incrementAndGet();
            return Outcome.found(1L);
        }));
        TestWorld.View view = MagicFixtures.view().sectionSize(16)
                .put(TestWorld.block(TestWorld.stone, LatticePoint.of(15, 0, 0))).refuse(LatticePoint.of(16, 0, 0));
        TestWorld.Lease lease = new TestWorld.Lease(view);
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            lease,
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        assertEquals(1, runs.get(), "an aggregate runs once, not once per section");
        assertEquals(new Spend(2, 2), admitted.outcome().spend(), "region volume plus two sections, charged once");

        AggregateContext context = Objects.requireNonNull(seen[0], "the aggregate ran");
        assertEquals(1, context.unreadable().size());
        assertInstanceOf(CellRead.Found.class, context.read(LatticePoint.of(15, 0, 0)));
        assertInstanceOf(CellRead.Unreadable.class, context.read(LatticePoint.of(16, 0, 0)));
    }

    @Test
    void anExhaustedBudgetSkipsTheAggregateAndWritesNothing() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 2, 1));
        ContextKey<Long> output = key("aggregate/budget");
        AtomicBoolean called = new AtomicBoolean();
        Blueprint blueprint = blueprint("aggregate/budget", region, aggregate(output, region, context -> {
            called.set(true);
            return Outcome.found(1L);
        }));
        TestWorld.Ledger ledger = MagicFixtures.ledger();
        ContextStore contexts = ContextStore.inMemory();
        TestWorld.Lease lease = new TestWorld.Lease(MagicFixtures.view());
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            lease,
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.of(3, 10),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.budgetExhausted, undecided.reason());
        assertTrue(MagicFixtures.hasCode(undecided, Codes.budgetExhausted), "the aggregate charge is SC-ENG-0001");
        assertFalse(called.get(), "the aggregate must not run when its charge is refused");
        assertEquals(Spend.zero, Objects.requireNonNull(undecided.partial()).spend());
        assertEquals(0L, ledger.revision());
        assertTrue(ledger.all().isEmpty());
        assertEquals(0L, contexts.context(MagicFixtures.instance).revision());
        assertTrue(contexts.context(MagicFixtures.instance).values().isEmpty());
    }

    @Test
    void aMultiSectionChargeAlsoCountsTheSections() {
        CuboidSet region = CuboidSet.of(Cuboid.of(15, 0, 0, 17, 1, 1));
        ContextKey<Long> output = key("aggregate/budget_sections");
        AtomicBoolean called = new AtomicBoolean();
        Blueprint blueprint = blueprint("aggregate/budget_sections", region, aggregate(output, region, context -> {
            called.set(true);
            return Outcome.found(1L);
        }));
        TestWorld.View view = MagicFixtures.view().sectionSize(16);
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.of(100, 1),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.budgetExhausted, undecided.reason());
        assertFalse(called.get(), "two sections do not fit one section of budget");
    }

    @Test
    void aRefusedRegionIsNeverTreatedAsAnEmptyRead() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        ContextKey<Long> output = key("aggregate/refused_missing");
        AggregateContext[] seen = new AggregateContext[1];
        Blueprint blueprint = blueprint("aggregate/refused_missing", region, aggregate(output, region, context -> {
            seen[0] = context;
            return context.unreadable().isEmpty() ? Outcome.found(0L) : Outcome.missing();
        }));
        TestWorld.View view = MagicFixtures.view().bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).refuse(LatticePoint.origin);
        MatchRequest request = MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.unreadable, undecided.reason());
        assertTrue(MagicFixtures.hasCode(undecided, Codes.computationFailed), "the aggregate produced no value");
        assertTrue(MagicFixtures.hasCode(undecided, Codes.keyAbsent), "the required key stays absent");
        MatchOutcome outcome = Objects.requireNonNull(undecided.partial());
        assertTrue(outcome.value(output).fold(value -> false, () -> true), "a refused region is not a count of zero");
        assertEquals(2, Objects.requireNonNull(seen[0], "the aggregate ran").unreadable().size());
    }

    private static ContextKey<Long> key(String path) {
        return ContextKeys.of(MagicFixtures.id(path), Long.class, Retention.instance);
    }

    private static AggregateComputation<Long> aggregate(
        ContextKey<Long> output,
        CuboidSet region,
        Function<AggregateContext, Outcome<Long>> body
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
                return Set.of();
            }

            @Override
            public CuboidSet region() {
                return region;
            }

            @Override
            public Outcome<Long> compute(AggregateContext context) {
                return body.apply(context);
            }
        };
    }

    private static Blueprint blueprint(String path, CuboidSet region, AggregateComputation<Long> aggregate) {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id(path + "_bay"),
            CuboidSet.of(checkCell),
            Coverage.anySubject,
            Footprint.none(),
            Sharing.shared
        );
        Cuboid bounds = Objects.requireNonNull(region.union(checkCell).bounds(), "bounds");
        ContextKey<Long> output = aggregate.output();
        return Blueprint.builder(MagicFixtures.id(path)).bounds(bounds).computation(aggregate)
                .check(
                    Check.of(
                        MagicFixtures.id(path + "_check"),
                        bay,
                        Focuses.context(output),
                        Expectations.context(output, value -> true)
                    )
                ).build();
    }

}
