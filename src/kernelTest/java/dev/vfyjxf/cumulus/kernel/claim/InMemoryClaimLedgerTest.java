package dev.vfyjxf.cumulus.kernel.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class InMemoryClaimLedgerTest {

    private static final OwnerId OWNER_A = OwnerId.of(NamespacedId.of("test", "overworld"),
            NamespacedId.of("test", "a"));
    private static final OwnerId OWNER_B = OwnerId.of(NamespacedId.of("test", "overworld"),
            NamespacedId.of("test", "b"));
    private static final Siting AT_A = Siting.of(LatticePoint.ORIGIN, Orientation.IDENTITY);
    private static final Siting AT_A_MOVED = Siting.of(LatticePoint.of(16, 0, 0), Orientation.IDENTITY);
    private static final Siting AT_B = Siting.of(LatticePoint.of(8, 0, 0), Orientation.IDENTITY);
    private static final NamespacedId BAY = NamespacedId.of("test", "bay");
    private static final NamespacedId OTHER_BAY = NamespacedId.of("test", "other");
    private static final LatticeKey CELL = LatticeKey.of(LatticePoint.of(1, 2, 3));
    private static final LatticeKey NEIGHBOUR = LatticeKey.of(LatticePoint.of(2, 2, 3));
    private static final Cuboid EVERYTHING = Cuboid.of(-16, -16, -16, 32, 32, 32);

    private static ClaimRow row(LatticeKey cell, ClaimMode mode, OwnerId owner, Siting siting,
                                NamespacedId bay) {
        return new ClaimRow(cell, mode, owner, siting, bay);
    }

    @Test
    void freshLedgerAnswersAtRevisionZero() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        ClaimView view = ledger.read(EVERYTHING);
        assertTrue(view.available());
        assertEquals(0L, view.revision());
        assertTrue(view.rows().isEmpty());
    }

    @Test
    void sharedRowsOfTwoOwnersBothSettle() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        ClaimVerdict first = ledger.settle(new SettleRequest(OWNER_A, AT_A,
                List.of(row(CELL, ClaimMode.SHARED, OWNER_A, AT_A, BAY)), 0L));
        assertEquals(new ClaimVerdict.Settled(1L, List.of(row(CELL, ClaimMode.SHARED, OWNER_A, AT_A, BAY))),
                first);
        ClaimVerdict second = ledger.settle(new SettleRequest(OWNER_B, AT_B,
                List.of(row(CELL, ClaimMode.SHARED, OWNER_B, AT_B, OTHER_BAY)), 1L));
        ClaimVerdict.Settled settled = assertInstanceOf(ClaimVerdict.Settled.class, second);
        assertEquals(2L, settled.revision());
        assertEquals(2, settled.rows().size());
    }

    @Test
    void exclusiveConflictWritesNothing() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        ledger.settle(new SettleRequest(OWNER_A, AT_A,
                List.of(row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY)), 0L));
        ClaimRow incumbent = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY);
        ClaimRow challenger = row(CELL, ClaimMode.EXCLUSIVE, OWNER_B, AT_B, OTHER_BAY);

        ClaimVerdict verdict = ledger.settle(new SettleRequest(OWNER_B, AT_B, List.of(challenger), 1L));
        ClaimVerdict.ClaimConflict conflict = assertInstanceOf(ClaimVerdict.ClaimConflict.class, verdict);
        assertEquals(List.of(incumbent), conflict.rows());
        assertEquals(1, conflict.findings().size());
        assertEquals("SC-CLM-0002", Objects.requireNonNull(conflict.findings().primary()).code().text());

        ClaimView after = ledger.read(EVERYTHING);
        assertEquals(1L, after.revision());
        assertEquals(List.of(incumbent), after.rows());
    }

    @Test
    void oneFindingPerClaimantPairNotPerCell() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        ledger.settle(new SettleRequest(OWNER_A, AT_A,
                List.of(row(CELL, ClaimMode.SHARED, OWNER_A, AT_A, BAY),
                        row(NEIGHBOUR, ClaimMode.SHARED, OWNER_A, AT_A, BAY)), 0L));
        ClaimRow challenger = row(CELL, ClaimMode.EXCLUSIVE, OWNER_B, AT_B, OTHER_BAY);
        ClaimRow challengerNext = row(NEIGHBOUR, ClaimMode.EXCLUSIVE, OWNER_B, AT_B, OTHER_BAY);

        ClaimVerdict verdict = ledger.settle(new SettleRequest(OWNER_B, AT_B,
                List.of(challengerNext, challenger), 1L));
        ClaimVerdict.ClaimConflict conflict = assertInstanceOf(ClaimVerdict.ClaimConflict.class, verdict);
        assertEquals(2, conflict.rows().size());
        assertEquals(1, conflict.findings().size());
        Finding primary = Objects.requireNonNull(conflict.findings().primary());
        assertEquals(2L, Objects.requireNonNull(primary.primaryLocus()).cells().volume());
    }

    @Test
    void twoBaysOfOneSettlementConflictWithEachOther() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        ClaimRow first = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY);
        ClaimRow second = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, OTHER_BAY);

        ClaimVerdict verdict = ledger.settle(new SettleRequest(OWNER_A, AT_A, List.of(second, first), 0L));

        ClaimVerdict.ClaimConflict conflict = assertInstanceOf(ClaimVerdict.ClaimConflict.class, verdict);
        assertEquals(List.of(first, second), conflict.rows());
        assertEquals(1, conflict.findings().size(), "one finding per claimant pair");
        assertEquals("SC-CLM-0001", Objects.requireNonNull(conflict.findings().primary()).code().text());
        assertEquals(1L, Objects.requireNonNull(conflict.findings().primary().primaryLocus())
                .cells().volume());
        assertEquals(0L, ledger.read(EVERYTHING).revision(), "an internal overlap writes nothing");
    }

    @Test
    void staleRevisionMovesInsteadOfConflicting() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        ledger.settle(new SettleRequest(OWNER_A, AT_A,
                List.of(row(CELL, ClaimMode.SHARED, OWNER_A, AT_A, BAY)), 0L));
        ClaimVerdict verdict = ledger.settle(new SettleRequest(OWNER_B, AT_B,
                List.of(row(NEIGHBOUR, ClaimMode.SHARED, OWNER_B, AT_B, OTHER_BAY)), 0L));
        assertEquals(new ClaimVerdict.RevisionMoved(0L, 1L), verdict);
        assertEquals(1L, ledger.read(EVERYTHING).revision());
    }

    @Test
    void resettleReplacesEveryRowOfTheSameClaimant() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        ledger.settle(new SettleRequest(OWNER_A, AT_A,
                List.of(row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY),
                        row(NEIGHBOUR, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY)), 0L));
        ClaimRow kept = row(NEIGHBOUR, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY);
        ClaimVerdict verdict = ledger.settle(new SettleRequest(OWNER_A, AT_A, List.of(kept), 1L));
        ClaimVerdict.Settled settled = assertInstanceOf(ClaimVerdict.Settled.class, verdict);
        assertEquals(2L, settled.revision());
        assertEquals(List.of(kept), settled.rows());
    }

    @Test
    void movedSitingDoesNotReachTheOldRows() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        ledger.settle(new SettleRequest(OWNER_A, AT_A,
                List.of(row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY)), 0L));
        ClaimRow movedRow = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A_MOVED, BAY);
        ClaimVerdict verdict = ledger.settle(new SettleRequest(OWNER_A, AT_A_MOVED, List.of(movedRow), 1L));
        ClaimVerdict.ClaimConflict conflict = assertInstanceOf(ClaimVerdict.ClaimConflict.class, verdict);
        assertEquals(1, conflict.findings().size());
        assertEquals(1L, ledger.read(EVERYTHING).revision());
    }

    @Test
    void readKeepsOnlyRowsInsideTheBox() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        ledger.settle(new SettleRequest(OWNER_A, AT_A,
                List.of(row(CELL, ClaimMode.SHARED, OWNER_A, AT_A, BAY),
                        row(NEIGHBOUR, ClaimMode.SHARED, OWNER_A, AT_A, BAY)), 0L));
        ClaimView view = ledger.read(Cuboid.of(1, 2, 3, 2, 3, 4));
        assertTrue(view.available());
        assertEquals(1, view.rows().size());
        assertEquals(CELL, view.rows().get(0).cell());
    }

    @Test
    void unavailableViewHoldsNoRows() {
        ClaimView view = ClaimView.unavailable();
        assertFalse(view.available());
        assertTrue(view.rows().isEmpty());
        assertTrue(view.rowsIn(EVERYTHING).isEmpty());
    }
}
