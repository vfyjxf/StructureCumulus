package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.client.cine.Tween;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The staged playback's drop-in (design 17 §7.6): the stamp, the fall leg, the alpha leg and speed zero. */
class DropInTest {

    private static final float eps = 1.0e-4F;
    private static final GhostCell cell = new GhostCell(
        LatticePoint.of(1, 2, 3),
        NamespacedId.of("minecraft", "stone"),
        Map.of(),
        0
    );
    private static final int duration = DropIn.dropInTicks;
    private static final Tween fallLeg = DropIn.fallLeg(duration);
    private static final Tween alphaLeg = DropIn.alphaLeg(duration);

    @Test
    void aCellThatNeverFellSitsOnItsSlotAtFullAlpha() {
        DropIn none = DropIn.none();
        assertEquals(0.0F, none.drop(cell, 10.0D, fallLeg), eps);
        assertEquals(1.0F, none.alpha(cell, 10.0D, alphaLeg), eps);
        assertFalse(none.animating(cell, 10.0D, duration));
        assertEquals(0, none.count());
    }

    @Test
    void aNewlyRevealedCellStartsHighAndTransparent() {
        DropIn dropped = DropIn.none().advance(Set.of(cell), 10.0D);
        assertTrue(dropped.animating(cell, 10.0D, duration));
        assertEquals(DropIn.dropInCells, dropped.drop(cell, 10.0D, fallLeg), eps);
        assertEquals(0.0F, dropped.alpha(cell, 10.0D, alphaLeg), eps);
        assertEquals(1, dropped.count());
    }

    @Test
    void theFallLandsAndTheFadeArrivesOnTheSameTick() {
        DropIn dropped = DropIn.none().advance(Set.of(cell), 10.0D);
        assertEquals(0.0F, dropped.drop(cell, 10.0D + duration, fallLeg), eps, "the slot is the landing height");
        assertEquals(1.0F, dropped.alpha(cell, 10.0D + duration, alphaLeg), eps);
        assertFalse(dropped.animating(cell, 10.0D + duration, duration));
    }

    @Test
    void theFallIsFastOffTheTopAndSlowOntoItsSlot() {
        DropIn dropped = DropIn.none().advance(Set.of(cell), 10.0D);
        float previous = Float.MAX_VALUE;
        for (int step = 0; step <= duration * 10; step++) {
            float height = dropped.drop(cell, 10.0D + step / 10.0D, fallLeg);
            assertTrue(height <= previous, "the cell never rises during its fall");
            assertTrue(height >= 0.0F && height <= DropIn.dropInCells, "the fall stays between the slot and its start");
            previous = height;
        }
        float halfway = dropped.drop(cell, 10.0D + duration * 0.5D, fallLeg);
        assertTrue(halfway < DropIn.dropInCells * 0.2F, "out-cubic spends most of its span arriving, not leaving");
        float halfAlpha = dropped.alpha(cell, 10.0D + duration * 0.5D, alphaLeg);
        assertTrue(halfAlpha > 0.0F && halfAlpha < 1.0F, "the fade runs across the same span");
    }

    @Test
    void aBatchStampedInOneFrameFallsInLockstep() {
        GhostCell second = new GhostCell(LatticePoint.of(2, 2, 3), NamespacedId.of("minecraft", "stone"), Map.of(), 0);
        DropIn batch = DropIn.none().advance(Set.of(cell, second), 10.0D);
        assertEquals(2, batch.count());
        for (double tick : new double[]{10.0D, 10.5D, 12.0D, 14.9D}) {
            assertEquals(batch.drop(cell, tick, fallLeg), batch.drop(second, tick, fallLeg), eps, "one stamp, one leg");
            assertEquals(batch.alpha(cell, tick, alphaLeg), batch.alpha(second, tick, alphaLeg), eps);
        }
    }

    @Test
    void aSecondFrameKeepsTheStampSoTheBatchFallsTogether() {
        DropIn first = DropIn.none().advance(Set.of(cell), 10.0D);
        DropIn second = first.advance(Set.of(cell), 11.0D);
        assertEquals(first.drop(cell, 11.0D, fallLeg), second.drop(cell, 11.0D, fallLeg), eps);
        assertTrue(second.animating(cell, 11.0D, duration));
    }

    @Test
    void aHiddenCellForgetsItsStampAndFallsAgainWhenRevealed() {
        DropIn dropped = DropIn.none().advance(Set.of(cell), 10.0D);
        DropIn hidden = dropped.advance(Set.of(), 11.0D);
        assertEquals(0.0F, hidden.drop(cell, 11.0D, fallLeg), eps);
        assertEquals(0, hidden.count());
        DropIn again = hidden.advance(Set.of(cell), 20.0D);
        assertEquals(DropIn.dropInCells, again.drop(cell, 20.0D, fallLeg), eps);
    }

    @Test
    void aZeroLengthLegLandsTheCellAtOnce() {
        DropIn dropped = DropIn.none().advance(Set.of(cell), 10.0D);
        assertEquals(0.0F, dropped.drop(cell, 10.0D, DropIn.fallLeg(0)), eps);
        assertEquals(1.0F, dropped.alpha(cell, 10.0D, DropIn.alphaLeg(0)), eps);
        assertFalse(dropped.animating(cell, 10.0D, 0));
    }
}
