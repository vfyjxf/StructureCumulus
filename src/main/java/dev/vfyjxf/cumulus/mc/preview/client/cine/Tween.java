package dev.vfyjxf.cumulus.mc.preview.client.cine;

import java.util.Objects;

/**
 * A scalar interpolation across a tick span. The tween is sampled with a continuous tick
 * (integer ticks plus the frame's partial tick), so the simulation stays tick-authoritative while
 * the render stays smooth.
 */
public record Tween(double from, double to, int durationTicks, Ease ease) {

    public Tween {
        if (durationTicks < 0) {
            throw new IllegalArgumentException("durationTicks must be >= 0: " + durationTicks);
        }
        ease = Objects.requireNonNull(ease, "ease");
    }

    /** The eased value at {@code tick}; a zero-length tween is always at its target. */
    public double at(double tick) {
        if (durationTicks == 0) {
            return to;
        }
        double t = ease.applyClamped(tick / durationTicks);
        return from + (to - from) * t;
    }

    public boolean done(double tick) {
        return tick >= durationTicks;
    }
}
