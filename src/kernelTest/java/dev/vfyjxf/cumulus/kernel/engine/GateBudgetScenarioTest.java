package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gate and budget: stale, mid-attempt invalidation, settle drop, budget exhaustion and no writes. */
class GateBudgetScenarioTest {

    private static final LatticePoint cell = LatticePoint.origin;
    private static final Siting siting = MagicFixtures.identity(LatticePoint.origin);
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);

    @Test
    void aStaleLeaseIsDroppedAtCheck() {
        TestWorld.View view = airView();
        TestWorld.Lease lease = new TestWorld.Lease(view);
        lease.invalidate();
        MatchRequest request = request(blueprint(), lease, Budget.unbounded());
        MatchResult.Dropped dropped = assertInstanceOf(MatchResult.Dropped.class, MatchEngine.check(request));
        assertTrue(MagicFixtures.hasCode(dropped, Codes.snapshotStale), "the stale gate is SC-ENG-0002");
    }

    @Test
    void aLeaseInvalidatedDuringTheAttemptIsDropped() {
        TestWorld.View view = airView();
        FlakyLease lease = new FlakyLease(view);
        MatchRequest request = request(blueprint(), lease, Budget.unbounded());
        MatchResult.Dropped dropped = assertInstanceOf(MatchResult.Dropped.class, MatchEngine.check(request));
        assertTrue(MagicFixtures.hasCode(dropped, Codes.snapshotStale), "the mid-attempt gate is SC-ENG-0002");
    }

    @Test
    void aLeaseInvalidatedBeforeSettleDropsTheWrite() {
        TestWorld.View view = airView();
        TestWorld.Lease lease = new TestWorld.Lease(view);
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchRequest request = request(blueprint(), lease, Budget.unbounded());
        TestAttempt attempt = TestAttempt.of(request);
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        lease.invalidate();
        MatchResult.Dropped dropped = assertInstanceOf(
            MatchResult.Dropped.class,
            MatchEngine.settle(request, admitted, ledger, attempt)
        );
        assertTrue(MagicFixtures.hasCode(dropped, Codes.snapshotStale), "the settle drop is SC-ENG-0002");
        assertNoWrites(ledger, attempt.store());
    }

    @Test
    void aTinyBudgetIsUndecidedAndWritesNothing() {
        TestWorld.View view = airView();
        TestWorld.Lease lease = new TestWorld.Lease(view);
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchRequest request = request(blueprint(), lease, Budget.of(0, 0));
        TestAttempt attempt = TestAttempt.of(request);
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.budgetExhausted, undecided.reason());
        assertTrue(MagicFixtures.hasCode(undecided, Codes.budgetExhausted), "the budget gate is SC-ENG-0001");
        assertNoWrites(ledger, attempt.store());
    }

    private static void assertNoWrites(ClaimLedger ledger, ContextStore contexts) {
        assertEquals(0L, ledger.revision(), "no ledger revision");
        assertEquals(0, TestWorld.rows(ledger).size(), "no ledger rows");
        assertEquals(0L, contexts.context(MagicFixtures.instance).revision(), "no context revision");
        assertTrue(contexts.context(MagicFixtures.instance).values().isEmpty(), "no context values");
    }

    private static MatchRequest request(Blueprint blueprint, SnapshotLease lease, Budget budget) {
        return MatchRequest.of(
            blueprint,
            siting,
            lease,
            MagicFixtures.instance,
            MagicFixtures.owner,
            budget,
            new ClaimView(List.of(), 0, true)
        );
    }

    private static Blueprint blueprint() {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("gate/bay"),
            CuboidSet.of(Cuboid.of(cell)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(MagicFixtures.id("gate/air")).bounds(bounds)
                .check(Check.of(MagicFixtures.id("gate/air_check"), bay, Focuses.occupant(cell), Expectations.air()))
                .build();
    }

    private static TestWorld.View airView() {
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.air(view, siting, cell);
        return view;
    }

    private static final class FlakyLease implements SnapshotLease {

        private final WorldView view;
        private int currentCalls;

        private FlakyLease(WorldView view) {
            this.view = view;
        }

        @Override
        public SnapshotId id() {
            return TestWorld.snapshot;
        }

        @Override
        public WorldView view() {
            return view;
        }

        @Override
        public boolean isCurrent() {
            return currentCalls++ == 0;
        }

        @Override
        public void close() {
            currentCalls = Integer.MAX_VALUE;
        }
    }
}
