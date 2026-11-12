package dev.vfyjxf.cumulus.mc.preview.client.screen;

/**
 * The screen's own clock (design 17 §4.6): a fractional tick accumulator fed by the frame's real-time
 * delta, because a screen pauses the game tick. Two numbers, so a test advances it by hand.
 */
public record ScreenClock(int ticks, float fraction) {

    private static final float maxFraction = 0.999999F;

    public static ScreenClock start() {
        return new ScreenClock(0, 0.0F);
    }

    /** Adds one frame's real-time tick delta; a negative delta never moves the clock back. */
    public ScreenClock advance(float realtimeDeltaTicks) {
        if (!(realtimeDeltaTicks > 0.0F)) {
            return this;
        }
        float total = fraction + realtimeDeltaTicks;
        int whole = (int) total;
        float rest = total - whole;
        if (rest > maxFraction) {
            rest = maxFraction;
        }
        return new ScreenClock(ticks + whole, rest);
    }

    /** The frame's sample: the tick cursor plus its fraction, the shape every animation reads. */
    public float time() {
        return ticks + fraction;
    }
}
