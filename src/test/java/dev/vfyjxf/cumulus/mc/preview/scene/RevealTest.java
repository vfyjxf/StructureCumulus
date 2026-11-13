package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The staged playback's reveal (design 17 §7.6): the stamp and its direction, both legs, and speed zero. */
class RevealTest {

    private static final float eps = 1.0e-4F;
    private static final float plateBottom = -0.5F;
    private static final int duration = Reveal.revealTicks;
    private static final GhostCell cell = new GhostCell(
        LatticePoint.of(1, 2, 3),
        NamespacedId.of("minecraft", "stone"),
        Map.of(),
        0
    );

    private static GhostCell ghost(int y) {
        return new GhostCell(LatticePoint.of(1, y, 3), NamespacedId.of("minecraft", "stone"), Map.of(), 0);
    }

    @Test
    void aCellThatNeverRevealedSitsOnItsSlotAtFullAlpha() {
        Reveal none = Reveal.none();
        assertEquals(0.0F, none.offset(cell, 10.0D, duration, plateBottom), eps);
        assertEquals(1.0F, none.alpha(cell, 10.0D, duration), eps);
        assertFalse(none.animating(cell, 10.0D, duration));
        assertNull(none.directionOf(cell));
        assertEquals(0, none.count());
    }

    @Test
    void aDroppedCellStartsHighAndTransparent() {
        Reveal dropped = Reveal.none().advance(Set.of(cell), 10.0D, Reveal.Direction.drop);
        assertTrue(dropped.animating(cell, 10.0D, duration));
        assertEquals(Reveal.dropCells, dropped.offset(cell, 10.0D, duration, plateBottom), eps);
        assertEquals(0.0F, dropped.alpha(cell, 10.0D, duration), eps);
        assertEquals(Reveal.Direction.drop, dropped.directionOf(cell));
        assertEquals(1, dropped.count());
    }

    @Test
    void theDropLandsAndTheFadeArrivesOnTheSameTick() {
        Reveal dropped = Reveal.none().advance(Set.of(cell), 10.0D, Reveal.Direction.drop);
        assertEquals(0.0F, dropped.offset(cell, 10.0D + duration, duration, plateBottom), eps);
        assertEquals(1.0F, dropped.alpha(cell, 10.0D + duration, duration), eps);
        assertFalse(dropped.animating(cell, 10.0D + duration, duration));
    }

    @Test
    void theDropIsFastOffTheTopAndSlowOntoItsSlot() {
        Reveal dropped = Reveal.none().advance(Set.of(cell), 10.0D, Reveal.Direction.drop);
        float previous = Float.MAX_VALUE;
        for (int step = 0; step <= duration * 10; step++) {
            float height = dropped.offset(cell, 10.0D + step / 10.0D, duration, plateBottom);
            assertTrue(height <= previous, "the cell never rises during its drop");
            assertTrue(height >= 0.0F && height <= Reveal.dropCells, "the drop stays between the slot and its start");
            previous = height;
        }
        float halfway = dropped.offset(cell, 10.0D + duration * 0.5D, duration, plateBottom);
        assertTrue(halfway < Reveal.dropCells * 0.2F, "out-cubic spends most of its span arriving, not leaving");
        float halfAlpha = dropped.alpha(cell, 10.0D + duration * 0.5D, duration);
        assertTrue(halfAlpha > 0.0F && halfAlpha < 1.0F, "the fade runs across the same span");
    }

    @Test
    void aRiseStartsAtThePlatesUndersideUnderTheCellsOwnHeight() {
        Reveal rising = Reveal.none().advance(Set.of(cell), 10.0D, Reveal.Direction.rise);
        assertEquals(Reveal.Direction.rise, rising.directionOf(cell));
        assertEquals(plateBottom - cell.cell().y(), rising.offset(cell, 10.0D, duration, plateBottom), eps);
        assertEquals(-2.5F, rising.offset(cell, 10.0D, duration, plateBottom), eps);
        GhostCell groundFloor = ghost(0);
        Reveal ground = Reveal.none().advance(Set.of(groundFloor), 10.0D, Reveal.Direction.rise);
        assertEquals(
            plateBottom,
            ground.offset(groundFloor, 10.0D, duration, plateBottom),
            eps,
            "a ground-floor cell travels the platform's own thickness"
        );
        assertEquals(0.0F, rising.alpha(cell, 10.0D, duration), eps);
    }

    @Test
    void theRiseClimbsToItsSlotAndDeadStops() {
        Reveal rising = Reveal.none().advance(Set.of(cell), 10.0D, Reveal.Direction.rise);
        float start = plateBottom - cell.cell().y();
        float previous = start;
        for (int step = 0; step <= duration * 10; step++) {
            float offset = rising.offset(cell, 10.0D + step / 10.0D, duration, plateBottom);
            assertTrue(offset >= previous, "the cell only ever climbs");
            assertTrue(offset <= 0.0F && offset >= start, "the rise stays between the plate's underside and the slot");
            previous = offset;
        }
        float halfway = rising.offset(cell, 10.0D + duration * 0.5D, duration, plateBottom);
        assertTrue(Math.abs(halfway) < Math.abs(start) * 0.2F, "out-cubic seats the cell early, not late");
        assertEquals(0.0F, rising.offset(cell, 10.0D + duration, duration, plateBottom), eps);
        assertEquals(1.0F, rising.alpha(cell, 10.0D + duration, duration), eps);
        assertFalse(rising.animating(cell, 10.0D + duration, duration));
    }

    @Test
    void oneBatchSharesItsSpanWhileEveryLegKeepsItsOwnStart() {
        GhostCell high = ghost(4);
        Reveal batch = Reveal.none().advance(Set.of(ghost(0), cell, high), 10.0D, Reveal.Direction.rise);
        assertEquals(plateBottom, batch.offset(ghost(0), 10.0D, duration, plateBottom), eps);
        assertEquals(plateBottom - 2.0F, batch.offset(cell, 10.0D, duration, plateBottom), eps);
        assertEquals(plateBottom - 4.0F, batch.offset(high, 10.0D, duration, plateBottom), eps);
        for (double tick : new double[]{10.0D, 12.0D, 14.9D}) {
            assertTrue(
                batch.offset(high, tick, duration, plateBottom) < batch.offset(cell, tick, duration, plateBottom),
                "the higher cell climbs the longer way"
            );
            assertTrue(
                batch.offset(cell, tick, duration, plateBottom) < batch.offset(ghost(0), tick, duration, plateBottom)
            );
        }
        for (GhostCell each : Set.of(ghost(0), cell, high)) {
            assertEquals(
                0.0F,
                batch.offset(each, 10.0D + duration, duration, plateBottom),
                eps,
                "one stamp settles the whole batch on one tick"
            );
            assertFalse(batch.animating(each, 10.0D + duration, duration));
            assertEquals(
                batch.alpha(cell, 12.0D, duration),
                batch.alpha(each, 12.0D, duration),
                eps,
                "the fade is one leg for the batch"
            );
        }
    }

    @Test
    void theStampCarriesItsDirectionAcrossAToggle() {
        Reveal dropped = Reveal.none().advance(Set.of(cell), 10.0D, Reveal.Direction.drop);
        Reveal toggled = dropped.advance(Set.of(cell), 11.0D, Reveal.Direction.rise);
        assertEquals(Reveal.Direction.drop, toggled.directionOf(cell), "a revealed cell is never re-narrated");
        assertEquals(
            dropped.offset(cell, 11.0D, duration, plateBottom),
            toggled.offset(cell, 11.0D, duration, plateBottom),
            eps
        );
        GhostCell later = ghost(0);
        Reveal next = toggled.advance(Set.of(cell, later), 12.0D, Reveal.Direction.rise);
        assertEquals(Reveal.Direction.rise, next.directionOf(later), "only later reveals take the new direction");
        assertEquals(plateBottom, next.offset(later, 12.0D, duration, plateBottom), eps);
    }

    @Test
    void aHiddenCellForgetsItsStampAndTakesTheDirectionOfItsSecondReveal() {
        Reveal dropped = Reveal.none().advance(Set.of(cell), 10.0D, Reveal.Direction.drop);
        Reveal hidden = dropped.advance(Set.of(), 11.0D, Reveal.Direction.drop);
        assertEquals(0.0F, hidden.offset(cell, 11.0D, duration, plateBottom), eps);
        assertNull(hidden.directionOf(cell));
        assertEquals(0, hidden.count());
        Reveal again = hidden.advance(Set.of(cell), 20.0D, Reveal.Direction.rise);
        assertEquals(Reveal.Direction.rise, again.directionOf(cell));
        assertEquals(plateBottom - cell.cell().y(), again.offset(cell, 20.0D, duration, plateBottom), eps);
    }

    @Test
    void aSecondFrameKeepsTheStampSoTheBatchTravelsTogether() {
        Reveal first = Reveal.none().advance(Set.of(cell), 10.0D, Reveal.Direction.drop);
        Reveal second = first.advance(Set.of(cell), 11.0D, Reveal.Direction.drop);
        assertEquals(
            first.offset(cell, 11.0D, duration, plateBottom),
            second.offset(cell, 11.0D, duration, plateBottom),
            eps
        );
        assertTrue(second.animating(cell, 11.0D, duration));
    }

    @Test
    void aZeroLengthLegArrivesAtOnceInBothDirections() {
        Reveal dropped = Reveal.none().advance(Set.of(cell), 10.0D, Reveal.Direction.drop);
        Reveal rising = Reveal.none().advance(Set.of(cell), 10.0D, Reveal.Direction.rise);
        for (Reveal reveal : new Reveal[]{dropped, rising}) {
            assertEquals(0.0F, reveal.offset(cell, 10.0D, 0, plateBottom), eps);
            assertEquals(1.0F, reveal.alpha(cell, 10.0D, 0), eps);
            assertFalse(reveal.animating(cell, 10.0D, 0));
        }
    }
}
