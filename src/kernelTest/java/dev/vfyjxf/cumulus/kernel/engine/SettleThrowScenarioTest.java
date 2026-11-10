package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Characterizes the settle throw contract: a publish failure after the ledger write propagates and
 * leaves the written rows in place, so cleaning them up is the calling host's responsibility.
 */
@NullMarked
class SettleThrowScenarioTest {

    private static final LatticePoint cell = LatticePoint.origin;
    private static final Siting siting = MagicFixtures.identity(LatticePoint.origin);
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);

    @Test
    void aPublishFailureAfterTheLedgerWriteKeepsTheRows() {
        ClaimLedger ledger = MagicFixtures.ledger();
        TestWorld.Lease lease = new TestWorld.Lease(airView());
        MatchRequest request = request(lease);
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        ThrowingPublishAttempt attempt = new ThrowingPublishAttempt(request);

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> MatchEngine.settle(request, admitted, ledger, attempt),
            "the publish failure propagates out of the settle"
        );
        assertEquals("the host publish failed", thrown.getMessage(), "the host's own failure");
        List<ClaimRow> rows = TestWorld.rows(ledger);
        assertEquals(1, rows.size(), "the kernel leaves the written rows with the caller");
        assertEquals(1L, ledger.revision(), "the ledger write bumped the revision");

        ledger.release(rows.get(0).instance());
        assertEquals(0, TestWorld.rows(ledger).size(), "the caller's release clears them");
    }

    private static MatchRequest request(TestWorld.Lease lease) {
        return MatchRequest.of(
            blueprint(),
            siting,
            lease,
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
    }

    private static Blueprint blueprint() {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("throw/bay"),
            CuboidSet.of(bounds),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(MagicFixtures.id("throw/air")).bounds(bounds)
                .check(Check.of(MagicFixtures.id("throw/air_check"), bay, Focuses.occupant(cell), Expectations.air()))
                .build();
    }

    private static TestWorld.View airView() {
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.air(view, siting, cell);
        return view;
    }

    /** Delegates everything but publish, which throws like a broken host attempt. */
    private static final class ThrowingPublishAttempt implements StructureAttempt {

        private final TestAttempt delegate;

        private ThrowingPublishAttempt(MatchRequest request) {
            this.delegate = TestAttempt.of(request);
        }

        @Override
        public InstanceId id() {
            return delegate.id();
        }

        @Override
        public StructureSpec spec() {
            return delegate.spec();
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
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
            throw new RuntimeException("the host publish failed");
        }

        @Override
        public StructureInstance commit(CuboidSet bounds) {
            return delegate.commit(bounds);
        }

        @Override
        public void abort() {
            delegate.abort();
        }
    }
}
