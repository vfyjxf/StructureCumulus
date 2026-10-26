package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimTest {

    private static final Siting siting = Siting.of(LatticePoint.origin, Orientation.identity);
    private static final OwnerId ownerA = OwnerId.of(NamespacedId.of("a"));
    private static final OwnerId ownerB = OwnerId.of(NamespacedId.of("b"));

    private static ClaimRow row(OwnerId owner, Sharing sharing) {
        return new ClaimRow(LatticeKey.of(LatticePoint.origin), sharing, owner, siting, NamespacedId.of("bay"));
    }

    @Test
    void matrixIsSymmetricAndSelfExcluded() {
        assertFalse(ClaimMatrix.conflicts(row(ownerA, Sharing.shared), row(ownerA, Sharing.shared)));
        assertFalse(ClaimMatrix.conflicts(row(ownerA, Sharing.shared), row(ownerB, Sharing.shared)));
        assertTrue(ClaimMatrix.conflicts(row(ownerA, Sharing.exclusive), row(ownerB, Sharing.shared)));
        assertTrue(ClaimMatrix.conflicts(row(ownerA, Sharing.shared), row(ownerB, Sharing.exclusive)));
        assertTrue(ClaimMatrix.conflicts(row(ownerA, Sharing.exclusive), row(ownerB, Sharing.exclusive)));
    }

    @Test
    void claimSetOrdersAndViewHidesUnavailableRows() {
        ClaimSet set = new ClaimSet(List.of(row(ownerB, Sharing.shared), row(ownerA, Sharing.shared)), 3);
        assertEquals(ownerA, set.rows().get(0).owner());
        assertEquals(3, set.revision());
        ClaimView unavailable = ClaimView.unavailable();
        assertFalse(unavailable.available());
        assertTrue(unavailable.rows().isEmpty());
        assertEquals(
            1,
            new ClaimView(List.of(row(ownerA, Sharing.shared)), 1, true).rowsIn(Cuboid.of(0, 0, 0, 1, 1, 1)).size()
        );
    }

    @Test
    void settleRequestRefusesForeignRows() {
        ClaimRow foreign = new ClaimRow(
            LatticeKey.of(LatticePoint.origin),
            Sharing.shared,
            ownerB,
            siting,
            NamespacedId.of("bay")
        );
        assertThrows(IllegalArgumentException.class, () -> new SettleRequest(ownerA, siting, List.of(foreign), 0));
    }

    @Test
    void footprintFactories() {
        assertTrue(Footprint.box() instanceof Footprint.Box);
        assertTrue(Footprint.bound() instanceof Footprint.Bound);
        assertTrue(Footprint.none() instanceof Footprint.None);
        assertEquals(
            Cuboid.of(0, 0, 0, 1, 1, 1),
            ((Footprint.Declared) Footprint.declared(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)))).cells().bounds()
        );
    }

    @Test
    void aNoOpReleaseKeepsRevisionZero() {
        ClaimLedger ledger = new ClaimLedger() {
            @Override
            public World world() {
                return new World(NamespacedId.of("w"));
            }

            @Override
            public long revision() {
                return 0;
            }

            @Override
            public ClaimVerdict settle(SettleRequest request) {
                return new ClaimVerdict.Settled(request.expectedRevision(), List.of());
            }

            @Override
            public ClaimVerdict release(OwnerId owner, Siting siting) {
                return new ClaimVerdict.Settled(0, List.of());
            }

            @Override
            public ClaimView read(Cuboid box) {
                return new ClaimView(List.of(), 0, true);
            }
        };
        ClaimVerdict released = ledger.release(ownerB, siting);
        assertTrue(released instanceof ClaimVerdict.Settled);
        assertEquals(0, ((ClaimVerdict.Settled) released).revision());
        assertEquals(0, ledger.revision());
    }
}
