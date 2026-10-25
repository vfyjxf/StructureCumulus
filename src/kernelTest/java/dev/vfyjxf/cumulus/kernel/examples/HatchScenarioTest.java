package dev.vfyjxf.cumulus.kernel.examples;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMatrix;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMode;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.InMemoryClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 22 §10.1; 08 §4.1–4.5: one shared cell, three modes, one replacement rule and one retry. */
class HatchScenarioTest {

    private static final NamespacedId WORLD = NamespacedId.of("mymod", "overworld");
    private static final OwnerId OWNER_A = OwnerId.of(WORLD, NamespacedId.of("mymod", "furnace_a"));
    private static final OwnerId OWNER_B = OwnerId.of(WORLD, NamespacedId.of("mymod", "furnace_b"));
    private static final OwnerId OWNER_C = OwnerId.of(WORLD, NamespacedId.of("mymod", "furnace_c"));
    private static final OwnerId OWNER_D = OwnerId.of(WORLD, NamespacedId.of("mymod", "furnace_d"));
    private static final Siting AT_A = Siting.of(LatticePoint.of(0, 0, 0), Orientation.IDENTITY);
    private static final Siting AT_B = Siting.of(LatticePoint.of(8, 0, 0), Orientation.IDENTITY);
    private static final Siting AT_C = Siting.of(LatticePoint.of(16, 0, 0), Orientation.IDENTITY);
    private static final Siting AT_D = Siting.of(LatticePoint.of(24, 0, 0), Orientation.IDENTITY);
    private static final LatticePoint HATCH = LatticePoint.of(1, 3, 1);
    private static final LatticePoint MOVED = LatticePoint.of(1, 4, 1);
    private static final LatticePoint FAR = LatticePoint.of(20, 0, 0);
    private static final LatticePoint FARTHER = LatticePoint.of(22, 0, 0);
    private static final Cuboid ALL = Cuboid.of(-1, -1, -1, 32, 8, 8);

    @Test
    void sharedCoexistsWithSharedAndReservedWhileExclusiveConflicts() {
        ClaimRow sharedA = row(HATCH, ClaimMode.SHARED, OWNER_A, AT_A, "hatch");
        ClaimRow sharedB = row(HATCH, ClaimMode.SHARED, OWNER_B, AT_B, "hatch");
        ClaimRow exclusiveB = row(HATCH, ClaimMode.EXCLUSIVE, OWNER_B, AT_B, "hatch");
        ClaimRow reservedA = row(HATCH, ClaimMode.RESERVED, OWNER_A, AT_A, "hatch");

        assertFalse(ClaimMatrix.conflicts(sharedA, sharedB),
                "08 §4.2: two SHARED rows on one cell coexist — the mode is not a strength order");
        assertTrue(ClaimMatrix.conflicts(sharedA, exclusiveB),
                "08 §4.2: an EXCLUSIVE row conflicts with a SHARED row on the same cell");
        assertFalse(ClaimMatrix.conflicts(reservedA, sharedB),
                "08 §4.6: a RESERVED row behaves like SHARED against anything not EXCLUSIVE");
        assertTrue(ClaimMatrix.conflicts(reservedA, exclusiveB),
                "08 §4.6: a RESERVED row is a negative right against an EXCLUSIVE settlement");

        ClaimRow twin = row(HATCH, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, "hatch_service");
        assertFalse(ClaimMatrix.conflicts(reservedA, twin),
                "08 §4.2: rows of one (owner, siting) are excluded, so a replacement never conflicts with itself");
        assertFalse(ClaimMatrix.conflicts(sharedA, row(MOVED, ClaimMode.EXCLUSIVE, OWNER_B, AT_B, "hatch")),
                "08 §4.2: the matrix is one cell, so a different cell never conflicts");
    }

    @Test
    void settlementReplacesEveryRowOfOneOwnerAtOneSiting() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();

        assertInstanceOf(ClaimVerdict.Settled.class, ledger.settle(new SettleRequest(OWNER_A, AT_A,
                List.of(row(HATCH, ClaimMode.SHARED, OWNER_A, AT_A, "hatch")), 0L)));
        assertInstanceOf(ClaimVerdict.Settled.class, ledger.settle(new SettleRequest(OWNER_B, AT_B,
                List.of(row(MOVED, ClaimMode.SHARED, OWNER_B, AT_B, "hatch")), 1L)));
        assertInstanceOf(ClaimVerdict.Settled.class, ledger.settle(new SettleRequest(OWNER_A, AT_A,
                List.of(row(MOVED, ClaimMode.SHARED, OWNER_A, AT_A, "hatch_v2")), 2L)));

        ClaimView view = ledger.read(ALL);
        assertEquals(3L, view.revision(), "three settles, three revisions");
        assertEquals(List.of(MOVED), cellsOf(ownedBy(view.rows(), OWNER_A)),
                "08 §4.1: settle replaces every row of (owner, siting) in one CAS, never a per-row merge");
        assertEquals(List.of(MOVED), cellsOf(ownedBy(view.rows(), OWNER_B)),
                "another owner's rows are unreachable from that key");
        assertFalse(view.rows().stream().anyMatch(row -> row.cell().equals(LatticeKey.of(HATCH))),
                "the replaced row is gone rather than merged");
    }

    @Test
    void anExclusiveChallengerConflictsAndWritesNothing() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        assertInstanceOf(ClaimVerdict.Settled.class, ledger.settle(new SettleRequest(OWNER_A, AT_A,
                List.of(row(HATCH, ClaimMode.SHARED, OWNER_A, AT_A, "hatch")), 0L)));
        assertInstanceOf(ClaimVerdict.Settled.class, ledger.settle(new SettleRequest(OWNER_B, AT_B,
                List.of(row(HATCH, ClaimMode.SHARED, OWNER_B, AT_B, "hatch")), 1L)));
        assertInstanceOf(ClaimVerdict.Settled.class, ledger.settle(new SettleRequest(OWNER_D, AT_D,
                List.of(row(HATCH, ClaimMode.RESERVED, OWNER_D, AT_D, "hatch")), 2L)));

        long before = ledger.read(ALL).revision();
        ClaimVerdict verdict = ledger.settle(new SettleRequest(OWNER_C, AT_C,
                List.of(row(HATCH, ClaimMode.EXCLUSIVE, OWNER_C, AT_C, "hatch")), before));

        ClaimVerdict.ClaimConflict conflict = assertInstanceOf(ClaimVerdict.ClaimConflict.class, verdict,
                "22 §10.3: an EXCLUSIVE claim where another owner holds is Failed(SC-CLM-0002)");
        assertEquals(3, conflict.rows().size(), "the incumbent rows are named, one per SHARED or RESERVED holder");
        assertEquals(before, ledger.read(ALL).revision(), "08 §4.8: a failed settle leaves the store at its revision");
        assertTrue(ownedBy(ledger.read(ALL).rows(), OWNER_C).isEmpty(), "no row of a failed request is written");
        assertFalse(conflict.findings().ofCode(ClaimMatrix.CONFLICT_CODE).isEmpty(),
                "08 §4.7: the conflict is SC-CLM-0002 with the contested cell as its primary label");
    }

    @Test
    void revisionMovedIsRetriedOnceByTheCaller() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        List<ClaimRow> rows = List.of(row(HATCH, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, "hatch"));

        ClaimVerdict verdict = settleWithOneRetry(ledger, OWNER_A, AT_A, rows,
                () -> bump(ledger, FAR, OWNER_C, AT_C),
                () -> { });

        assertInstanceOf(ClaimVerdict.Settled.class, verdict,
                "08 §4.5: a moved revision is re-read and re-checked once inside the transaction");
        assertEquals(2L, ledger.read(ALL).revision(), "one competing settle plus the retry");
        assertEquals(cellsOf(rows), cellsOf(ownedBy(ledger.read(ALL).rows(), OWNER_A)));
    }

    @Test
    void aSecondMovementIsNoConclusionAndWritesNothing() {
        InMemoryClaimLedger ledger = new InMemoryClaimLedger();
        List<ClaimRow> rows = List.of(row(HATCH, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, "hatch"));

        ClaimVerdict verdict = settleWithOneRetry(ledger, OWNER_A, AT_A, rows,
                () -> bump(ledger, FAR, OWNER_C, AT_C),
                () -> bump(ledger, FARTHER, OWNER_D, AT_D));

        ClaimVerdict.RevisionMoved moved = assertInstanceOf(ClaimVerdict.RevisionMoved.class, verdict,
                "22 §10.1: a second movement is Undecided(LEDGER_MOVED), never an invented conflict");
        assertEquals(1L, moved.expected(), "the retry settled against the revision it read");
        assertEquals(2L, moved.actual(), "and a second writer moved the ledger under it");
        assertEquals(2L, ledger.read(ALL).revision(), "both attempts were refused, so neither wrote a row");
        assertTrue(ownedBy(ledger.read(ALL).rows(), OWNER_A).isEmpty(), "Undecided leaves the ledger untouched");
    }

    private static ClaimVerdict settleWithOneRetry(ClaimLedger ledger, OwnerId owner, Siting siting,
                                                   List<ClaimRow> rows, Runnable beforeFirst, Runnable beforeRetry) {
        ClaimView before = ledger.read(ALL);
        beforeFirst.run();
        ClaimVerdict first = ledger.settle(new SettleRequest(owner, siting, rows, before.revision()));
        if (!(first instanceof ClaimVerdict.RevisionMoved)) {
            return first;
        }
        ClaimView fresh = ledger.read(ALL);
        beforeRetry.run();
        return ledger.settle(new SettleRequest(owner, siting, rows, fresh.revision()));
    }

    private static void bump(ClaimLedger ledger, LatticePoint cell, OwnerId owner, Siting siting) {
        ClaimVerdict verdict = ledger.settle(new SettleRequest(owner, siting,
                List.of(row(cell, ClaimMode.SHARED, owner, siting, "bump")), ledger.read(ALL).revision()));
        assertInstanceOf(ClaimVerdict.Settled.class, verdict, "the competing writer must land at " + cell);
    }

    private static List<ClaimRow> ownedBy(List<ClaimRow> rows, OwnerId owner) {
        return rows.stream().filter(row -> row.owner().equals(owner)).toList();
    }

    private static ClaimRow row(LatticePoint cell, ClaimMode mode, OwnerId owner, Siting siting, String bay) {
        return new ClaimRow(LatticeKey.of(cell), mode, owner, siting, NamespacedId.of("mymod", bay));
    }

    private static List<LatticePoint> cellsOf(List<ClaimRow> rows) {
        return rows.stream().map(row -> row.cell().toPoint()).toList();
    }
}
