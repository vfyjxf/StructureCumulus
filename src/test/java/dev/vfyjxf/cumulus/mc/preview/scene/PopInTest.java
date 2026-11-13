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

/** The staged playback's pop-in (design 17 §7.6): the stamp, the two legs and the speed-free arm. */
class PopInTest {

    private static final float eps = 1.0e-4F;
    private static final GhostCell cell = new GhostCell(
        LatticePoint.of(1, 2, 3),
        NamespacedId.of("minecraft", "stone"),
        Map.of(),
        0
    );
    private static final int duration = PopIn.popInTicks;
    private static final Tween scaleLeg = PopIn.scaleLeg(duration);
    private static final Tween alphaLeg = PopIn.alphaLeg(duration);

    @Test
    void aCellThatNeverPoppedDrawsAtFullSizeAndAlpha() {
        PopIn none = PopIn.none();
        assertEquals(1.0F, none.scale(cell, 10.0D, scaleLeg), eps);
        assertEquals(1.0F, none.alpha(cell, 10.0D, alphaLeg), eps);
        assertFalse(none.animating(cell, 10.0D, duration));
        assertEquals(0, none.count());
    }

    @Test
    void aNewlyRevealedCellStartsSmallAndTransparent() {
        PopIn popped = PopIn.none().advance(Set.of(cell), 10.0D);
        assertTrue(popped.animating(cell, 10.0D, duration));
        assertEquals(PopIn.startScale, popped.scale(cell, 10.0D, scaleLeg), eps);
        assertEquals(0.0F, popped.alpha(cell, 10.0D, alphaLeg), eps);
        assertEquals(1, popped.count());
    }

    @Test
    void theLegOvershootsAndSettlesAtFullSize() {
        PopIn popped = PopIn.none().advance(Set.of(cell), 10.0D);
        float peak = 0.0F;
        for (int step = 0; step <= duration * 10; step++) {
            peak = Math.max(peak, popped.scale(cell, 10.0D + step / 10.0D, scaleLeg));
        }
        assertTrue(peak > 1.0F, "the out-back leg overshoots before it settles");
        assertEquals(1.0F, popped.scale(cell, 10.0D + duration, scaleLeg), eps);
        assertEquals(1.0F, popped.alpha(cell, 10.0D + duration, alphaLeg), eps);
        assertFalse(popped.animating(cell, 10.0D + duration, duration));
    }

    @Test
    void aSecondFrameKeepsTheStampSoTheStaggerSurvives() {
        PopIn first = PopIn.none().advance(Set.of(cell), 10.0D);
        PopIn second = first.advance(Set.of(cell), 11.0D);
        assertEquals(first.scale(cell, 11.0D, scaleLeg), second.scale(cell, 11.0D, scaleLeg), eps);
        assertTrue(second.animating(cell, 11.0D, duration));
    }

    @Test
    void aHiddenCellForgetsItsStampAndPopsAgainWhenRevealed() {
        PopIn popped = PopIn.none().advance(Set.of(cell), 10.0D);
        PopIn hidden = popped.advance(Set.of(), 11.0D);
        assertEquals(1.0F, hidden.scale(cell, 11.0D, scaleLeg), eps);
        assertEquals(0, hidden.count());
        PopIn again = hidden.advance(Set.of(cell), 20.0D);
        assertEquals(PopIn.startScale, again.scale(cell, 20.0D, scaleLeg), eps);
    }

    @Test
    void aZeroLengthLegShowsTheCellAtOnce() {
        PopIn popped = PopIn.none().advance(Set.of(cell), 10.0D);
        assertEquals(1.0F, popped.scale(cell, 10.0D, PopIn.scaleLeg(0)), eps);
        assertEquals(1.0F, popped.alpha(cell, 10.0D, PopIn.alphaLeg(0)), eps);
        assertFalse(popped.animating(cell, 10.0D, 0));
    }
}
