package dev.vfyjxf.cumulus.mc.preview.client.cine;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** The {@link Tween} shape over a vector: component-wise eased interpolation. */
public record VecTween(Vec3 from, Vec3 to, int durationTicks, Ease ease) {

    public VecTween {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (durationTicks < 0) {
            throw new IllegalArgumentException("durationTicks must be >= 0: " + durationTicks);
        }
        ease = Objects.requireNonNull(ease, "ease");
    }

    public Vec3 at(double tick) {
        if (durationTicks == 0) {
            return to;
        }
        double t = ease.applyClamped(tick / durationTicks);
        return new Vec3(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, from.z + (to.z - from.z) * t);
    }

    public boolean done(double tick) {
        return tick >= durationTicks;
    }
}
