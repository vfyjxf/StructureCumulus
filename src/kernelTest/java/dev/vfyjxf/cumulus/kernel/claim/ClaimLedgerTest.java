package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The in-memory ledger's compare-and-set, conflict, replacement, release and revision rules. */
class ClaimLedgerTest {

    private static final World world = World.of(NamespacedId.of("test", "ledger"));
    private static final Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
    private static final Siting peerSiting = Siting.of(LatticePoint.of(40, 0, 0), Orientation.identity);
    private static final Cuboid everything = Cuboid.of(-8, -8, -8, 64, 8, 8);
    private static final InstanceId first = new InstanceId(world, 1);
    private static final InstanceId second = new InstanceId(world, 2);

    private static ClaimRow row(InstanceId instance, Sharing sharing, int x, String bay) {
        return row(instance, sharing, siting, x, bay);
    }

    private static ClaimRow row(InstanceId instance, Sharing sharing, Siting at, int x, String bay) {
        return new ClaimRow(
            LatticeKey.of(LatticePoint.of(x, 0, 0)),
            sharing,
            at,
            NamespacedId.of("test", bay),
            instance
        );
    }

    private static ClaimVerdict settle(ClaimLedger ledger, InstanceId instance, long expected, ClaimRow... rows) {
        return ledger.settle(new SettleRequest(instance, List.of(rows), expected));
    }

    @Test
    void aNewLevelStartsWithAnEmptyLedger() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);

        assertEquals(world, ledger.world());
        assertEquals(0, ledger.revision());
        assertTrue(ledger.read(everything).rows().isEmpty());
        assertTrue(ledger.read(everything).available());
    }

    @Test
    void settleReplacesTheWholeInstanceAndBumpsTheRevision() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        ClaimRow firstRow = row(first, Sharing.exclusive, 0, "bay");
        ClaimVerdict firstVerdict = settle(ledger, first, 0, firstRow);

        ClaimVerdict.Settled settled = assertInstanceOf(ClaimVerdict.Settled.class, firstVerdict);
        assertEquals(1, settled.revision());
        assertEquals(List.of(firstRow), settled.rows());

        ClaimRow secondRow = row(first, Sharing.exclusive, 1, "bay");
        assertInstanceOf(ClaimVerdict.Settled.class, settle(ledger, first, 1, secondRow));
        assertEquals(2, ledger.revision());
        assertEquals(List.of(secondRow), ledger.read(everything).rows());
    }

    @Test
    void anEmptySettlementStillClearsTheInstanceAndBumps() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        settle(ledger, first, 0, row(first, Sharing.exclusive, 0, "bay"));

        ClaimVerdict removed = settle(ledger, first, 1);

        assertInstanceOf(ClaimVerdict.Settled.class, removed);
        assertEquals(2, ledger.revision());
        assertTrue(ledger.read(everything).rows().isEmpty());
    }

    @Test
    void anIdenticalReplacementStillBumpsTheRevision() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        ClaimRow row = row(first, Sharing.exclusive, 17, "bay");
        settle(ledger, first, 0, row);

        assertInstanceOf(ClaimVerdict.Settled.class, settle(ledger, first, 1, row));
        assertEquals(2, ledger.revision());
        assertEquals(List.of(row), ledger.read(everything).rows());
    }

    @Test
    void aMovedRevisionAndAConflictLeaveTheLedgerUntouched() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        ClaimRow incumbent = row(first, Sharing.exclusive, 18, "bay");
        settle(ledger, first, 0, incumbent);
        assertEquals(1, ledger.revision());

        ClaimVerdict moved = settle(ledger, second, 0, row(second, Sharing.exclusive, peerSiting, 19, "bay"));
        assertEquals(new ClaimVerdict.RevisionMoved(0, 1), moved);
        assertEquals(1, ledger.revision());

        ClaimVerdict conflict = settle(ledger, second, 1, row(second, Sharing.exclusive, peerSiting, 18, "bay"));
        assertInstanceOf(ClaimVerdict.Conflict.class, conflict);
        assertEquals(1, ledger.revision());
        assertEquals(List.of(incumbent), ledger.read(everything).rows());
    }

    @Test
    void settleRefusesAConflictAndLeavesTheLedgerUntouched() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        ClaimRow incumbent = row(first, Sharing.exclusive, 0, "bay");
        settle(ledger, first, 0, incumbent);

        ClaimVerdict verdict = settle(ledger, second, 1, row(second, Sharing.exclusive, peerSiting, 0, "bay"));

        ClaimVerdict.Conflict conflict = assertInstanceOf(ClaimVerdict.Conflict.class, verdict);
        assertEquals(List.of(incumbent), conflict.incumbents());
        assertTrue(!conflict.findings().findings().isEmpty());
        assertEquals(1, ledger.revision());
        assertEquals(List.of(incumbent), ledger.read(everything).rows());
    }

    @Test
    void sharedClaimsCoexistButAnExclusiveOneConflicts() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        settle(ledger, first, 0, row(first, Sharing.shared, 3, "a"));
        assertInstanceOf(
            ClaimVerdict.Settled.class,
            settle(ledger, second, 1, row(second, Sharing.shared, peerSiting, 3, "b"))
        );
        assertEquals(2, ledger.revision());
        assertEquals(2, ledger.read(everything).rows().size());

        ClaimVerdict verdict = settle(ledger, second, 2, row(second, Sharing.exclusive, peerSiting, 3, "b"));
        assertInstanceOf(ClaimVerdict.Conflict.class, verdict);
        assertEquals(2, ledger.revision());
    }

    @Test
    void aSharedClaimStillConflictsWithAnExclusiveIncumbent() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        ClaimRow incumbent = row(first, Sharing.exclusive, 3, "a");
        settle(ledger, first, 0, incumbent);

        ClaimVerdict verdict = settle(ledger, second, 1, row(second, Sharing.shared, peerSiting, 3, "b"));

        assertEquals(List.of(incumbent), assertInstanceOf(ClaimVerdict.Conflict.class, verdict).incumbents());
        assertEquals(1, ledger.revision());
    }

    @Test
    void aDifferentCellOrTheSameInstanceDoesNotConflict() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        settle(ledger, first, 0, row(first, Sharing.exclusive, 4, "bay"));

        assertInstanceOf(
            ClaimVerdict.Settled.class,
            settle(ledger, first, 1, row(first, Sharing.exclusive, 4, "other"))
        );
        assertInstanceOf(
            ClaimVerdict.Settled.class,
            settle(ledger, second, 2, row(second, Sharing.exclusive, peerSiting, 5, "bay"))
        );
        assertEquals(3, ledger.revision());
    }

    @Test
    void twoRowsOfOneInstanceDoNotConflict() {
        ClaimRow left = row(first, Sharing.shared, 0, "left");
        ClaimRow right = row(first, Sharing.exclusive, 0, "right");

        assertEquals(false, ClaimMatrix.conflicts(left, right));
        assertEquals(false, ClaimMatrix.conflicts(right, left));
    }

    @Test
    void aMovedRevisionWritesNothingAndOneRetrySucceeds() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        ClaimRow incumbent = row(first, Sharing.exclusive, 6, "bay");
        settle(ledger, first, 0, incumbent);

        ClaimVerdict moved = settle(ledger, second, 0, row(second, Sharing.exclusive, peerSiting, 6, "bay"));
        assertEquals(new ClaimVerdict.RevisionMoved(0, 1), moved);
        assertEquals(1, ledger.revision());
        assertEquals(List.of(incumbent), ledger.read(everything).rows());

        assertInstanceOf(
            ClaimVerdict.Settled.class,
            settle(ledger, second, 1, row(second, Sharing.exclusive, peerSiting, 7, "bay"))
        );
        assertEquals(2, ledger.revision());
    }

    @Test
    void aMovedInstanceReplacesItsRowsInOneCas() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        Siting from = siting;
        Siting to = Siting.of(LatticePoint.of(10, 0, 0), Orientation.identity);
        ClaimRow before = new ClaimRow(
            LatticeKey.of(LatticePoint.of(1, 0, 0)),
            Sharing.shared,
            from,
            NamespacedId.of("test", "bay"),
            first
        );
        settle(ledger, first, 0, before);
        ClaimRow after = new ClaimRow(
            LatticeKey.of(LatticePoint.of(11, 0, 0)),
            Sharing.shared,
            to,
            NamespacedId.of("test", "bay"),
            first
        );

        ClaimVerdict verdict = settle(ledger, first, 1, after);

        assertInstanceOf(ClaimVerdict.Settled.class, verdict);
        assertEquals(2, ledger.revision(), "the move is one compare-and-set");
        assertEquals(List.of(after), ledger.read(everything).rows());
    }

    @Test
    void aSameSitingSettleReplacesTheIncumbentRows() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        settle(ledger, first, 0, row(first, Sharing.exclusive, 30, "left"), row(first, Sharing.exclusive, 31, "right"));

        ClaimRow replacement = row(second, Sharing.exclusive, 30, "left");
        ClaimVerdict verdict = settle(ledger, second, 1, replacement);

        assertInstanceOf(ClaimVerdict.Settled.class, verdict);
        assertEquals(2, ledger.revision());
        assertEquals(List.of(replacement), ledger.read(everything).rows());
    }

    @Test
    void aSameSitingSettleKeepsADistantInstance() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        settle(ledger, first, 0, row(first, Sharing.exclusive, 32, "left"), row(first, Sharing.exclusive, 33, "right"));
        InstanceId peer = new InstanceId(world, 3);
        ClaimRow peerRow = row(peer, Sharing.exclusive, peerSiting, 60, "peer");
        settle(ledger, peer, 1, peerRow);

        ClaimRow replacement = row(second, Sharing.exclusive, 32, "left");
        ClaimVerdict verdict = settle(ledger, second, 2, replacement);

        assertInstanceOf(ClaimVerdict.Settled.class, verdict);
        assertEquals(3, ledger.revision());
        assertEquals(List.of(replacement, peerRow), ledger.read(everything).rows());
    }

    @Test
    void aFailedSameSitingSettleKeepsTheIncumbent() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        ClaimRow incumbentLeft = row(first, Sharing.exclusive, 34, "left");
        ClaimRow incumbentRight = row(first, Sharing.exclusive, 35, "right");
        settle(ledger, first, 0, incumbentLeft, incumbentRight);
        InstanceId peer = new InstanceId(world, 3);
        ClaimRow peerRow = row(peer, Sharing.exclusive, peerSiting, 36, "peer");
        settle(ledger, peer, 1, peerRow);
        long before = ledger.revision();

        ClaimVerdict verdict = settle(
            ledger,
            second,
            2,
            row(second, Sharing.exclusive, 34, "left"),
            row(second, Sharing.exclusive, 36, "peer")
        );

        ClaimVerdict.Conflict conflict = assertInstanceOf(ClaimVerdict.Conflict.class, verdict);
        assertEquals(List.of(peerRow), conflict.incumbents());
        assertEquals(before, ledger.revision());
        assertEquals(List.of(incumbentLeft, incumbentRight, peerRow), ledger.read(everything).rows());
    }

    @Test
    void releaseTakesTheWholeInstanceAndBumpsOnlyWhenItRemovesRows() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        settle(ledger, first, 0, row(first, Sharing.exclusive, 8, "a"), row(first, Sharing.shared, 9, "b"));
        ClaimRow other = row(second, Sharing.shared, peerSiting, 10, "b");
        settle(ledger, second, 1, other);

        ClaimVerdict absent = ledger.release(new InstanceId(world, 99));
        assertEquals(new ClaimVerdict.Settled(2, List.of()), absent);
        assertEquals(2, ledger.revision());

        ClaimVerdict released = ledger.release(first);
        assertEquals(new ClaimVerdict.Settled(3, List.of()), released);
        assertEquals(3, ledger.revision());
        assertEquals(List.of(other), ledger.read(everything).rows());

        assertEquals(new ClaimVerdict.Settled(3, List.of()), ledger.release(first));
        assertEquals(3, ledger.revision());
    }

    @Test
    void readReturnsACopyAndCanonicalOrder() {
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        ClaimRow a = row(first, Sharing.shared, 11, "b");
        ClaimRow b = row(second, Sharing.shared, peerSiting, 11, "a");
        settle(ledger, first, 0, a);
        settle(ledger, second, 1, b);

        ClaimView view = ledger.read(everything);
        assertEquals(List.of(a, b), view.rows());
        assertEquals(List.of(a, b), ledger.read(everything).rows());
        assertEquals(2, view.revision());
        assertThrows(RuntimeException.class, () -> view.rows().add(a));
        assertTrue(ledger.read(Cuboid.of(20, 20, 20, 21, 21, 21)).rows().isEmpty());
    }
}
