package dev.vfyjxf.cumulus.kernel.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.OwnerId;
import dev.vfyjxf.cumulus.kernel.base.Severity;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ClaimMatrixTest {

    private static final OwnerId OWNER_A = OwnerId.of(NamespacedId.of("test", "overworld"),
            NamespacedId.of("test", "a"));
    private static final OwnerId OWNER_B = OwnerId.of(NamespacedId.of("test", "overworld"),
            NamespacedId.of("test", "b"));
    private static final Siting AT_A = Siting.of(LatticePoint.ORIGIN, Orientation.IDENTITY);
    private static final Siting AT_B = Siting.of(LatticePoint.of(8, 0, 0), Orientation.IDENTITY);
    private static final LatticeKey CELL = LatticeKey.of(LatticePoint.of(1, 2, 3));
    private static final LatticeKey OTHER_CELL = LatticeKey.of(LatticePoint.of(2, 2, 3));
    private static final NamespacedId BAY_A = NamespacedId.of("test", "bay_a");
    private static final NamespacedId BAY_B = NamespacedId.of("test", "bay_b");

    private static ClaimRow row(LatticeKey cell, ClaimMode mode, OwnerId owner, Siting siting,
                                NamespacedId bay) {
        return new ClaimRow(cell, mode, owner, siting, bay);
    }

    @Test
    void sharedAndReservedCoexist() {
        ClaimRow sharedA = row(CELL, ClaimMode.SHARED, OWNER_A, AT_A, BAY_A);
        ClaimRow sharedB = row(CELL, ClaimMode.SHARED, OWNER_B, AT_B, BAY_B);
        ClaimRow reservedB = row(CELL, ClaimMode.RESERVED, OWNER_B, AT_B, BAY_B);
        ClaimRow reservedA = row(CELL, ClaimMode.RESERVED, OWNER_A, AT_A, BAY_A);
        assertFalse(ClaimMatrix.conflicts(sharedA, sharedB));
        assertFalse(ClaimMatrix.conflicts(sharedA, reservedB));
        assertFalse(ClaimMatrix.conflicts(reservedA, sharedB));
        assertFalse(ClaimMatrix.conflicts(reservedA, reservedB));
    }

    @Test
    void exclusiveConflictsWithEveryModeAndIsSymmetric() {
        ClaimRow exclusive = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY_A);
        for (ClaimMode mode : ClaimMode.values()) {
            ClaimRow other = row(CELL, mode, OWNER_B, AT_B, BAY_B);
            assertTrue(ClaimMatrix.conflicts(exclusive, other), "exclusive vs " + mode);
            assertTrue(ClaimMatrix.conflicts(other, exclusive), mode + " vs exclusive");
        }
    }

    @Test
    void oneBayOfOneSettlementNeverConflictsWithItself() {
        ClaimRow left = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY_A);
        ClaimRow right = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY_A);
        assertFalse(ClaimMatrix.conflicts(left, right));
        ClaimRow otherBay = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY_B);
        assertFalse(ClaimMatrix.conflicts(left, otherBay),
                "22 §10.1: rows of one (owner, siting) are excluded; internal overlaps use internalOverlaps");
        ClaimRow movedSiting = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_B, BAY_A);
        assertTrue(ClaimMatrix.conflicts(left, movedSiting));
    }

    @Test
    void twoBaysOfOneSettlementOverlapInternally() {
        ClaimRow first = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY_A);
        ClaimRow second = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY_B);
        ClaimRow third = row(OTHER_CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY_B);

        List<ClaimMatrix.Overlap> overlaps = ClaimMatrix.internalOverlaps(List.of(first, second, third));

        assertEquals(1, overlaps.size(), "one finding per claimant pair, never per cell");
        ClaimMatrix.Overlap overlap = overlaps.get(0);
        assertEquals(BAY_A, overlap.incumbent().bay());
        assertEquals(BAY_B, overlap.challenger().bay());
        assertEquals(1L, overlap.cells().volume());
        List<Finding> findings = ClaimMatrix.overlapFindings(overlaps);
        assertEquals("SC-CLM-0001", findings.get(0).code().text());
        assertEquals(Severity.ERROR, findings.get(0).severity());
        assertEquals(List.of(first, second), ClaimMatrix.rowsOf(overlaps));
    }

    @Test
    void aSharedPairOfOneSettlementOverlapsInSilence() {
        ClaimRow first = row(CELL, ClaimMode.SHARED, OWNER_A, AT_A, BAY_A);
        ClaimRow second = row(CELL, ClaimMode.RESERVED, OWNER_A, AT_A, BAY_B);

        assertTrue(ClaimMatrix.internalOverlaps(List.of(first, second)).isEmpty());
    }

    @Test
    void rowsOnDifferentCellsNeverConflict() {
        ClaimRow exclusive = row(CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY_A);
        ClaimRow shared = row(OTHER_CELL, ClaimMode.SHARED, OWNER_B, AT_B, BAY_B);
        assertFalse(ClaimMatrix.conflicts(exclusive, shared));
    }

    @Test
    void conflictsWithKeepsIncumbentsOnly() {
        ClaimRow candidate = row(CELL, ClaimMode.EXCLUSIVE, OWNER_B, AT_B, BAY_B);
        ClaimRow sharedIncumbent = row(CELL, ClaimMode.SHARED, OWNER_A, AT_A, BAY_A);
        ClaimRow reservedIncumbent = row(CELL, ClaimMode.RESERVED, OWNER_A, AT_A, BAY_A);
        ClaimRow elsewhere = row(OTHER_CELL, ClaimMode.EXCLUSIVE, OWNER_A, AT_A, BAY_A);
        List<ClaimRow> conflicting = ClaimMatrix.conflictsWith(candidate,
                List.of(sharedIncumbent, elsewhere, reservedIncumbent));
        assertEquals(List.of(sharedIncumbent, reservedIncumbent), conflicting);
    }

    @Test
    void conflictFindingNamesBothClaimantsAndOneCell() {
        ClaimRow challenger = row(CELL, ClaimMode.EXCLUSIVE, OWNER_B, AT_B, BAY_B);
        ClaimRow incumbent = row(CELL, ClaimMode.SHARED, OWNER_A, AT_A, BAY_A);
        Finding finding = ClaimMatrix.conflictFinding(challenger, incumbent);
        assertEquals("SC-CLM-0002", finding.code().text());
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(Stage.SETTLE, finding.stage());
        assertEquals(2, finding.labels().size());
        assertTrue(finding.message().contains(BAY_A.toString()));
        assertTrue(finding.message().contains(BAY_B.toString()));
        assertTrue(finding.message().contains(CELL.toString()));
        assertEquals(1L, Objects.requireNonNull(finding.primaryLocus()).cells().volume());
    }

    @Test
    void canonicalOrderIsCellThenOwnerThenBay() {
        ClaimRow later = row(OTHER_CELL, ClaimMode.SHARED, OWNER_A, AT_A, BAY_A);
        ClaimRow first = row(CELL, ClaimMode.SHARED, OWNER_B, AT_B, BAY_B);
        ClaimRow middle = row(CELL, ClaimMode.SHARED, OWNER_A, AT_A, BAY_A);
        assertEquals(List.of(middle, first, later), ClaimRow.ordered(List.of(later, first, middle)));
        assertEquals(new ClaimSet(List.of(later, first, middle), 0L).rows(),
                List.of(middle, first, later));
    }
}
