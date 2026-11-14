package dev.vfyjxf.cumulus.mc.preview.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The speed slider's pure law (design 17 §4.5, §7.5): the range, the quarter step, the label. */
class SpeedSliderTest {

    private static final double eps = 1.0e-9D;

    @Test
    void theSliderIsTheKeysOwnRangeInQuarterSteps() {
        assertEquals(0.25D, SpeedSlider.min, eps);
        assertEquals(4.0D, SpeedSlider.max, eps);
        assertEquals(0.25D, SpeedSlider.step, eps);
        assertEquals(15, SpeedSlider.maxSteps);
    }

    @Test
    void aConfigValueSnapsToTheNearestQuarterAndClamps() {
        assertEquals(0.25D, SpeedSlider.of(0.1D).value(), eps);
        assertEquals(4.0D, SpeedSlider.of(9.0D).value(), eps);
        assertEquals(1.5D, SpeedSlider.of(1.49D).value(), eps);
        assertEquals(1.5D, SpeedSlider.of(1.6D).value(), eps);
        assertEquals(1.75D, SpeedSlider.of(1.63D).value(), eps);
        assertEquals(1.0D, SpeedSlider.of(1.0D).value(), eps);
    }

    @Test
    void aTrackPositionSnapsToAStepAndTheEndsClamp() {
        assertEquals(0.25D, SpeedSlider.at(-1.0D).value(), eps);
        assertEquals(4.0D, SpeedSlider.at(2.0D).value(), eps);
        assertEquals(1.5D, SpeedSlider.at(1.0D / 3.0D).value(), eps);
        assertEquals(2.25D, SpeedSlider.at(0.5D).value(), eps);
    }

    @Test
    void everyStepRoundTripsThroughItsValueAndItsFraction() {
        for (int steps = 0; steps <= SpeedSlider.maxSteps; steps++) {
            SpeedSlider slider = new SpeedSlider(steps);
            assertEquals(steps, SpeedSlider.of(slider.value()).steps());
            assertEquals(steps, SpeedSlider.at(slider.fraction()).steps());
        }
    }

    @Test
    void theLabelReadsTheValueAtItsOwnPrecision() {
        assertEquals("0.25\u00D7", new SpeedSlider(0).label());
        assertEquals("0.5\u00D7", new SpeedSlider(1).label());
        assertEquals("1.0\u00D7", new SpeedSlider(3).label());
        assertEquals("1.5\u00D7", new SpeedSlider(5).label());
        assertEquals("2.25\u00D7", new SpeedSlider(8).label());
        assertEquals("4.0\u00D7", new SpeedSlider(15).label());
    }

    @Test
    void aStepOutsideTheRangeIsClampedOnConstruction() {
        assertEquals(0, new SpeedSlider(-3).steps());
        assertEquals(SpeedSlider.maxSteps, new SpeedSlider(99).steps());
    }
}
