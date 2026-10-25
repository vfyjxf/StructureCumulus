package dev.vfyjxf.cumulus.kernel.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class SitingTest {

    private static final Orientation YAW = Orientation.of(Turn.CLOCKWISE, Tilt.LEVEL, Flip.NONE);
    private static final Placement PLACEMENT = new Placement(YAW, LatticePoint.of(2, 0, -3));
    private static final Siting SITING = Siting.of(LatticePoint.of(10, 64, -5), PLACEMENT);

    private static final List<LatticePoint> SAMPLES = List.of(
            LatticePoint.ORIGIN,
            LatticePoint.of(1, 0, 0),
            LatticePoint.of(0, 4, 0),
            LatticePoint.of(-3, 2, 7),
            LatticePoint.of(7, -2, -9));

    @Test
    void worldAndLocalRoundTrip() {
        for (LatticePoint local : SAMPLES) {
            LatticePoint world = SITING.toWorld(local);
            assertNotNull(world);
            assertEquals(local, SITING.toLocal(world));
        }
    }

    @Test
    void toWorldIsAffineNotLinear() {
        LatticePoint local = LatticePoint.of(0, 0, 0);
        LatticePoint world = SITING.toWorld(local);
        LatticePoint moved = local.offset(2, 0, 0);
        LatticePoint oriented = YAW.apply(LatticePoint.of(2, 0, 0));
        assertNotNull(world);
        assertNotNull(moved);
        assertNotNull(oriented);
        LatticePoint expected = SITING.toWorld(moved);
        LatticePoint shifted = world.offset(oriented.x(), oriented.y(), oriented.z());
        assertNotNull(expected);
        assertNotNull(shifted);
        assertEquals(expected, shifted);
    }

    @Test
    void anchoringPutsOneCellOnAnother() {
        LatticePoint target = LatticePoint.of(100, 70, 200);
        LatticePoint local = LatticePoint.of(3, 1, 0);
        Siting anchored = Siting.anchoring(target, local, PLACEMENT);
        assertNotNull(anchored);
        assertEquals(target, anchored.toWorld(local));
    }

    @Test
    void leavingTheLatticeIsAbsence() {
        Siting atEdge = Siting.of(LatticePoint.of(LatticePoint.MAX_XZ, 0, 0), YAW);
        assertNull(atEdge.toWorld(LatticePoint.of(0, 0, -1)));
        assertEquals(SITING, SITING.withOrigin(SITING.origin()).withPlacement(PLACEMENT));
        Placement back = PLACEMENT.inverse();
        assertNotNull(back);
        assertEquals(Placement.IDENTITY, back.compose(PLACEMENT));
    }
}
