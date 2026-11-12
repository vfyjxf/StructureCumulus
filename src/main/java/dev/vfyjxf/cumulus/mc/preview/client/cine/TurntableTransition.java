package dev.vfyjxf.cumulus.mc.preview.client.cine;

import dev.vfyjxf.cumulus.kernel.space.Turn;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * The pose-transition math for a candidate switch: cells swing around the structure's vertical
 * axis from the old turn to the new turn along the shortest arc. Only cell positions interpolate;
 * the block model keeps the old pose until the transition's last frame (re-baking per frame is
 * not worth it for a six-tick swing).
 */
public record TurntableTransition(Turn from, Turn to, int durationTicks, Ease ease) {

    public TurntableTransition {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (durationTicks < 0) {
            throw new IllegalArgumentException("durationTicks must be >= 0: " + durationTicks);
        }
        ease = Objects.requireNonNull(ease, "ease");
    }

    /** The signed quarter-turn count of the shortest arc from {@link #from} to {@link #to}. */
    public int shortestArc() {
        int delta = (to.ordinal() - from.ordinal()) % 4;
        if (delta > 2) {
            delta -= 4;
        }
        return delta;
    }

    /** The swing angle in degrees at {@code tick}; the Y axis points up, clockwise is positive. */
    public double angleDeg(double tick) {
        double t = durationTicks == 0 ? 1.0 : ease.applyClamped(tick / durationTicks);
        return shortestArc() * 90.0 * t;
    }

    /** Rotates {@code offset} (a vector from the pivot) by the swing angle at {@code tick}. */
    public Vec3 rotate(Vec3 offset, double tick) {
        double rad = Math.toRadians(angleDeg(tick));
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3(offset.x * cos - offset.z * sin, offset.y, offset.x * sin + offset.z * cos);
    }

    /** The pose the model should bake with at {@code tick}: the old pose until the final frame. */
    public Turn modelTurn(double tick) {
        return tick >= durationTicks ? to : from;
    }
}
