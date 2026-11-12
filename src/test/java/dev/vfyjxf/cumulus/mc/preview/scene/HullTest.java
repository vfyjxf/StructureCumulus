package dev.vfyjxf.cumulus.mc.preview.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drawn hull of design 17 §3.3: a box in cell units, and the fit box its degenerate cases use. */
class HullTest {

    private static final float eps = 1.0e-5F;

    @Test
    void aBoxInvertsWhenNoCellDraws() {
        assertTrue(new Hull(0.0F, 0.0F, 0.0F, -1.0F, -1.0F, -1.0F).isEmpty());
        assertFalse(new Hull(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F).isEmpty());
    }

    @Test
    void aUnionHoldsBothBoxes() {
        Hull left = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 1.0F);
        Hull right = new Hull(1.0F, 1.0F, 0.0F, 4.0F, 3.0F, 2.0F);
        Hull union = left.union(right);
        assertEquals(0.0F, union.minX(), eps);
        assertEquals(0.0F, union.minY(), eps);
        assertEquals(4.0F, union.maxX(), eps);
        assertEquals(3.0F, union.maxY(), eps);
        assertEquals(2.0F, union.maxZ(), eps);
    }

    @Test
    void anEmptyOperandContributesNothing() {
        Hull box = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 1.0F, 1.0F);
        Hull empty = new Hull(0.0F, 0.0F, 0.0F, -1.0F, -1.0F, -1.0F);
        assertEquals(box, box.union(empty));
        assertEquals(box, empty.union(box));
    }

    @Test
    void theFitBoxOfAnEmptyHullIsTheOneCellBox() {
        Hull box = new Hull(0.0F, 0.0F, 0.0F, -1.0F, -1.0F, -1.0F).fitBox();
        assertEquals(0.0F, box.minX(), eps);
        assertEquals(1.0F, box.maxX(), eps);
        assertEquals(0.5F, box.centreX(), eps);
        assertEquals(0.5F, box.centreY(), eps);
    }

    @Test
    void theFitBoxKeepsAOneCellHullExactlyAndWidensAZeroAxis() {
        Hull cell = new Hull(4.0F, 0.0F, 3.0F, 5.0F, 1.0F, 4.0F).fitBox();
        assertEquals(4.5F, cell.centreX(), eps);
        assertEquals(0.5F, cell.centreY(), eps);
        assertEquals(1.0F, cell.maxX() - cell.minX(), eps);

        Hull flat = new Hull(0.0F, 2.0F, 0.0F, 2.0F, 2.0F, 1.0F).fitBox();
        assertEquals(2.0F, flat.centreY(), eps);
        assertEquals(1.0F, flat.maxY() - flat.minY(), eps);
    }

    @Test
    void theDiagonalIsCornerToCorner() {
        assertEquals((float) Math.sqrt(3.0), new Hull(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F).diagonal(), eps);
        assertEquals(2.0F, new Hull(0.0F, 0.0F, 0.0F, 2.0F, 0.0F, 0.0F).diagonal(), eps);
    }
}
