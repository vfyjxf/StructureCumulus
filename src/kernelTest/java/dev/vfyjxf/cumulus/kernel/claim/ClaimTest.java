package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
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
    private static final World world = World.of(NamespacedId.of("w"));
    private static final InstanceId instanceA = new InstanceId(world, 1);
    private static final InstanceId instanceB = new InstanceId(world, 2);

    private static ClaimRow row(InstanceId instance, Sharing sharing) {
        return new ClaimRow(LatticeKey.of(LatticePoint.origin), sharing, siting, NamespacedId.of("bay"), instance);
    }

    @Test
    void matrixIsSymmetricAndSelfExcluded() {
        assertFalse(ClaimMatrix.conflicts(row(instanceA, Sharing.shared), row(instanceA, Sharing.shared)));
        assertFalse(ClaimMatrix.conflicts(row(instanceA, Sharing.shared), row(instanceB, Sharing.shared)));
        assertTrue(ClaimMatrix.conflicts(row(instanceA, Sharing.exclusive), row(instanceB, Sharing.shared)));
        assertTrue(ClaimMatrix.conflicts(row(instanceA, Sharing.shared), row(instanceB, Sharing.exclusive)));
        assertTrue(ClaimMatrix.conflicts(row(instanceA, Sharing.exclusive), row(instanceB, Sharing.exclusive)));
    }

    @Test
    void claimSetOrdersAndViewHidesUnavailableRows() {
        ClaimSet set = new ClaimSet(List.of(row(instanceB, Sharing.shared), row(instanceA, Sharing.shared)), 3);
        assertEquals(instanceA, set.rows().get(0).instance());
        assertEquals(3, set.revision());
        ClaimView unavailable = ClaimView.unavailable();
        assertFalse(unavailable.available());
        assertTrue(unavailable.rows().isEmpty());
        assertEquals(
            1,
            new ClaimView(List.of(row(instanceA, Sharing.shared)), 1, true).rowsIn(Cuboid.of(0, 0, 0, 1, 1, 1)).size()
        );
    }

    @Test
    void settleRequestRefusesForeignRows() {
        ClaimRow foreign = new ClaimRow(
            LatticeKey.of(LatticePoint.origin),
            Sharing.shared,
            siting,
            NamespacedId.of("bay"),
            instanceB
        );
        assertThrows(IllegalArgumentException.class, () -> new SettleRequest(instanceA, List.of(foreign), 0));
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
        ClaimLedger ledger = ClaimLedger.inMemory(world);
        ClaimVerdict released = ledger.release(instanceB);
        assertTrue(released instanceof ClaimVerdict.Settled);
        assertEquals(0, ((ClaimVerdict.Settled) released).revision());
        assertEquals(0, ledger.revision());
    }
}
