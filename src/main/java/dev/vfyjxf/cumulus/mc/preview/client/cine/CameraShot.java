package dev.vfyjxf.cumulus.mc.preview.client.cine;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * One camera framing: an eye and a look-at target. Shots are computed, never hand-tuned — the
 * framing law derives a fit-to-view eye from the subject's bounds and an approach direction.
 */
public record CameraShot(Vec3 eye, Vec3 target) {

    /** Overscan so the subject's silhouette clears the frame edges. */
    private static final double margin = 1.2;
    /** The eye rises by this fraction of the subject's height for a mild downward tilt. */
    private static final double elevation = 0.6;

    public CameraShot {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(target, "target");
    }

    /**
     * Frames {@code bounds} (a half-open world cuboid) from {@code approach}: the eye sits on the
     * approach ray at the distance where the subject's largest dimension fits the vertical fov,
     * raised for a mild top-down read.
     */
    public static CameraShot framing(Cuboid bounds, Vec3 approach, double fovDeg) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(approach, "approach");
        Vec3 center = center(bounds);
        double sizeX = bounds.max().x() - bounds.min().x();
        double sizeY = bounds.max().y() - bounds.min().y();
        double sizeZ = bounds.max().z() - bounds.min().z();
        double maxDim = Math.max(sizeX, Math.max(sizeY, sizeZ));
        double distance = margin * maxDim / (2.0 * Math.tan(Math.toRadians(fovDeg) / 2.0));
        Vec3 flat = new Vec3(approach.x, 0.0, approach.z);
        if (flat.lengthSqr() < 1.0e-6) {
            flat = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 eye = center.add(flat.normalize().scale(Math.max(distance, 2.0))).add(0.0, sizeY * elevation, 0.0);
        return new CameraShot(eye, center);
    }

    /** Minecraft yaw (degrees, 0 = south, clockwise) looking from eye at target. */
    public float yaw() {
        Vec3 d = target.subtract(eye);
        double yaw = Math.toDegrees(Math.atan2(-d.x, d.z));
        return (float) (yaw < 0.0 ? yaw + 360.0 : yaw);
    }

    /** Minecraft pitch (degrees, positive = down) looking from eye at target. */
    public float pitch() {
        Vec3 d = target.subtract(eye);
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        // the frame's xRot is positive when looking down (its forward is -sin(xRot) on Y)
        return (float) -Math.toDegrees(Math.atan2(d.y, horizontal));
    }

    static Vec3 center(Cuboid bounds) {
        return new Vec3(
            (bounds.min().x() + bounds.max().x()) / 2.0,
            (bounds.min().y() + bounds.max().y()) / 2.0,
            (bounds.min().z() + bounds.max().z()) / 2.0
        );
    }
}
