package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cancellation polls: before the claim prediction, inside the conflict scan, and on the settle retry. */
class CancelPollScenarioTest {

    private static final CuboidSet field = CuboidSet.of(Cuboid.of(0, 0, 0, 8, 1, 8));
    private static final Siting siting = MagicFixtures.identity(LatticePoint.origin);

    @Test
    void aCancellationRaisedByTheLastCheckStopsTheClaimPrediction() {
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.air(view, siting, LatticePoint.origin);
        AtomicBoolean cancelled = new AtomicBoolean();
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id("cancel/hook"),
            CuboidSet.of(Cuboid.of(LatticePoint.origin)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.shared
        );
        Blueprint blueprint = Blueprint.builder(MagicFixtures.id("cancel/hook_structure"))
                .bounds(Cuboid.of(0, 0, 0, 1, 1, 1))
                .check(
                    Check.of(
                        MagicFixtures.id("cancel/hook_check"),
                        bay,
                        Focuses.occupant(LatticePoint.origin),
                        Expectations.custom(OccupantSnapshot.class, ExpectationMeta.anonymous(), ctx -> {
                            cancelled.set(true);
                            return Reply.match();
                        })
                    )
                ).build();
        MatchRequest request = MatchRequest.of(
            blueprint,
            siting,
            new TestWorld.Lease(view),
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true),
            cancelled::get,
            null
        );
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.cancelled, undecided.reason());
        assertTrue(MagicFixtures.hasCode(undecided, Codes.cancelled), "the prediction poll is SC-ENG-0005");
    }

    @Test
    void theConflictScanCancelsAtItsPoll() {
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.air(view, siting, LatticePoint.origin);
        MatchRequest request = MatchRequest.of(
            fieldBlueprint(),
            siting,
            new TestWorld.Lease(view),
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.unbounded(),
            new ClaimView(heldRows(64, 1000, MagicFixtures.rivalInstance), 0, true),
            new CountingToken(2),
            null
        );
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.cancelled, undecided.reason());
        assertTrue(MagicFixtures.hasCode(undecided, Codes.cancelled), "the scan poll is SC-ENG-0005");
    }

    @Test
    void aSettleRetryStopsInTheConflictScanWhenCancelled() {
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.air(view, siting, LatticePoint.origin);
        ClaimLedger ledger = MagicFixtures.ledger();
        AtomicBoolean cancelled = new AtomicBoolean();
        MatchRequest request = MatchRequest.of(
            fieldBlueprint(),
            siting,
            new TestWorld.Lease(view),
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true),
            cancelled::get,
            null
        );
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        ClaimVerdict.Settled rival = assertInstanceOf(
            ClaimVerdict.Settled.class,
            ledger.settle(new SettleRequest(MagicFixtures.instance, heldRows(64, 0, MagicFixtures.instance), 0))
        );
        assertEquals(1L, rival.revision(), "the earlier write moved the revision");
        cancelled.set(true);
        TestAttempt attempt = TestAttempt.of(request);
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.settle(request, admitted, ledger, attempt)
        );
        assertEquals(UndecidedReason.cancelled, undecided.reason());
        assertTrue(MagicFixtures.hasCode(undecided, Codes.cancelled), "the retry poll is SC-ENG-0005");
        assertEquals(1L, ledger.revision(), "the retry wrote nothing");
        assertEquals(64, TestWorld.rows(ledger).size(), "only the earlier rows remain");
        assertEquals(0L, attempt.store().context(MagicFixtures.instance).revision(), "nothing was published");
    }

    private static Blueprint fieldBlueprint() {
        Bay bay = MagicFixtures
                .bay(MagicFixtures.id("cancel/field"), field, Coverage.everyCell, Footprint.box(), Sharing.shared);
        return Blueprint.builder(MagicFixtures.id("cancel/field_structure")).bounds(Cuboid.of(0, 0, 0, 8, 1, 8))
                .check(
                    Check.of(
                        MagicFixtures.id("cancel/field_check"),
                        bay,
                        Focuses.bay(),
                        Expectations.eachCell(Expectations.anyOccupant())
                    )
                ).build();
    }

    private static List<ClaimRow> heldRows(int count, int xBase, InstanceId holder) {
        List<ClaimRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(
                new ClaimRow(
                    LatticeKey.of(LatticePoint.of(xBase + i % 8, 0, i / 8)),
                    Sharing.shared,
                    siting,
                    MagicFixtures.id("cancel/held"),
                    holder
                )
            );
        }
        return rows;
    }

    private static final class CountingToken implements CancelToken {

        private final int allowed;
        private int calls;

        private CountingToken(int allowed) {
            this.allowed = allowed;
        }

        @Override
        public boolean isCancelled() {
            calls++;
            return calls > allowed;
        }
    }
}
