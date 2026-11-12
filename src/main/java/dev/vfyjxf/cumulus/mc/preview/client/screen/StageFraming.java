package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.mc.preview.client.cine.Ease;
import dev.vfyjxf.cumulus.mc.preview.scene.SceneCamera;

/**
 * One stage-framing flight (design 17 §7.5): the eased 12-tick leg from the camera the play found to
 * that stage's fit, sampled at the screen's clock. It moves the camera value only.
 */
public record StageFraming(SceneCamera from, SceneCamera to, float start, int ticks) {

    /** The design's 12-tick stage transition (16 §6.7.3). */
    public static final int stageFocusTicks = 12;

    public StageFraming {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0: " + ticks);
        }
    }

    /** The leg from {@code from} to {@code to}, starting at {@code start} on the screen's clock. */
    public static StageFraming of(SceneCamera from, SceneCamera to, float start, int ticks) {
        return new StageFraming(from, to, start, ticks);
    }

    /** The camera at {@code time}; a finished leg sits exactly on its target. */
    public SceneCamera at(float time) {
        if (ticks <= 0) {
            return to;
        }
        double eased = Ease.inOutCubic.applyClamped((time - start) / ticks);
        float t = (float) eased;
        return new SceneCamera(
            from.targetX() + (to.targetX() - from.targetX()) * t,
            from.targetY() + (to.targetY() - from.targetY()) * t,
            from.targetZ() + (to.targetZ() - from.targetZ()) * t,
            from.yaw() + shortestArc(from.yaw(), to.yaw()) * t,
            from.pitch() + (to.pitch() - from.pitch()) * t,
            from.distance() + (to.distance() - from.distance()) * t,
            from.zoom() + (to.zoom() - from.zoom()) * t,
            to.projection()
        );
    }

    public boolean done(float time) {
        return ticks <= 0 || time - start >= ticks;
    }

    /** The signed arc of at most half a turn, so the pose never spins the long way. */
    static float shortestArc(float from, float to) {
        float delta = (to - from) % 360.0F;
        if (delta > 180.0F) {
            delta -= 360.0F;
        }
        if (delta < -180.0F) {
            delta += 360.0F;
        }
        return delta;
    }
}
