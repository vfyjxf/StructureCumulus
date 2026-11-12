package dev.vfyjxf.cumulus.mc.preview.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The screen's clock of design 17 §4.6: a fractional tick accumulator a test advances by hand. */
class ScreenClockTest {

    private static final float eps = 1.0e-5F;

    @Test
    void theClockStartsAtZero() {
        ScreenClock clock = ScreenClock.start();
        assertEquals(0, clock.ticks());
        assertEquals(0.0F, clock.fraction(), eps);
        assertEquals(0.0F, clock.time(), eps);
    }

    @Test
    void theFractionCarriesIntoTheTickCursor() {
        ScreenClock clock = ScreenClock.start().advance(0.5F).advance(0.6F);
        assertEquals(1, clock.ticks());
        assertEquals(0.1F, clock.fraction(), eps);
        assertEquals(1.1F, clock.time(), eps);
    }

    @Test
    void aNegativeOrZeroDeltaNeverMovesTheClockBack() {
        ScreenClock clock = ScreenClock.start().advance(1.5F);
        assertEquals(clock, clock.advance(0.0F));
        assertEquals(clock, clock.advance(-2.0F));
    }

    @Test
    void manyFramesAdvanceTheSameTotalAsOne() {
        ScreenClock stepped = ScreenClock.start();
        for (int frame = 0; frame < 20; frame++) {
            stepped = stepped.advance(0.05F);
        }
        ScreenClock once = ScreenClock.start().advance(1.0F);
        assertEquals(once.ticks(), stepped.ticks());
        assertEquals(once.fraction(), stepped.fraction(), 1.0e-4F);
    }
}
