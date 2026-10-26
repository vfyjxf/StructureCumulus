package dev.vfyjxf.cumulus.kernel.claim;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.List;
class ClaimTest {

    private static final Siting SITING = Siting.of(LatticePoint.ORIGIN, Orientation.IDENTITY);
    private static final OwnerId A = OwnerId.of(NamespacedId.of("a"));
    private static final OwnerId B = OwnerId.of(NamespacedId.of("b"));

    private static ClaimRow row(OwnerId owner, Sharing sharing) {
        return new ClaimRow(LatticeKey.of(LatticePoint.ORIGIN), sharing, owner, SITING,
                NamespacedId.of("bay"));
    }

    @Test
    void matrixIsSymmetricAndSelfExcluded() {
        assertFalse(ClaimMatrix.conflicts(row(A, Sharing.SHARED), row(A, Sharing.SHARED)));
        assertFalse(ClaimMatrix.conflicts(row(A, Sharing.SHARED), row(B, Sharing.SHARED)));
        assertTrue(ClaimMatrix.conflicts(row(A, Sharing.EXCLUSIVE), row(B, Sharing.SHARED)));
        assertTrue(ClaimMatrix.conflicts(row(A, Sharing.SHARED), row(B, Sharing.EXCLUSIVE)));
        assertTrue(ClaimMatrix.conflicts(row(A, Sharing.EXCLUSIVE), row(B, Sharing.EXCLUSIVE)));
    }

    @Test
    void claimSetOrdersAndViewHidesUnavailableRows() {
        ClaimSet set = new ClaimSet(List.of(row(B, Sharing.SHARED), row(A, Sharing.SHARED)), 3);
        assertEquals(A, set.rows().get(0).owner());
        assertEquals(3, set.revision());
        ClaimView unavailable = ClaimView.unavailable();
        assertFalse(unavailable.available());
        assertTrue(unavailable.rows().isEmpty());
        assertEquals(1, new ClaimView(List.of(row(A, Sharing.SHARED)), 1, true)
                .rowsIn(Cuboid.of(0, 0, 0, 1, 1, 1)).size());
    }

    @Test
    void settleRequestRefusesForeignRows() {
        ClaimRow foreign = new ClaimRow(LatticeKey.of(LatticePoint.ORIGIN), Sharing.SHARED, B,
                SITING, NamespacedId.of("bay"));
        assertThrows(IllegalArgumentException.class,
                () -> new SettleRequest(A, SITING, List.of(foreign), 0));
    }

    @Test
    void footprintFactories() {
        assertTrue(Footprint.box() instanceof Footprint.Box);
        assertTrue(Footprint.bound() instanceof Footprint.Bound);
        assertTrue(Footprint.none() instanceof Footprint.None);
        assertEquals(Cuboid.of(0, 0, 0, 1, 1, 1),
                ((Footprint.Declared) Footprint.declared(
                        CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))))
                        .cells().bounds());
    }
}
