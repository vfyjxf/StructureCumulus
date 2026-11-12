package dev.vfyjxf.cumulus.mc.preview.scene;

import java.util.Objects;

/**
 * One viewport's camera: an orbit pose about a target, the projection it feeds and the orthographic
 * zoom. A pure value; every operation returns a new camera and no operation reads the world
 * (design 17 §4.2). The orbit is a turntable (yaw about the world Y, pitch above the horizon); the
 * JEI face keeps its own yaw/pitch tilt (design 16 §11) so its picture does not change.
 */
public record SceneCamera(
    float targetX,
    float targetY,
    float targetZ,
    float yaw,
    float pitch,
    float distance,
    float zoom,
    Projection projection
) {

    /** The two projections a camera can feed; both share the one pose (design 17 §4.1). */
    public enum Projection {
        orthographic, perspective
    }

    /** The viewer's six axis snapshots (design 17 §4.2). */
    public enum Snap {
        front, back, right, left, top, bottom
    }

    public static final float defaultYaw = 45.0F;
    /** {@code atan(1/sqrt(2))} in degrees: the isometric elevation. */
    public static final float defaultPitch = 35.26439F;
    public static final float minZoom = 0.25F;
    public static final float maxZoom = 8.0F;
    public static final float minDistance = 1.0F;
    public static final float maxDistance = 512.0F;
    public static final float fovDegrees = 70.0F;
    /** The steepest pitch: the orbit stops short of the pole, where yaw stops meaning anything. */
    public static final float maxPitch = 89.9F;

    private static final float pitchLimit = maxPitch;
    private static final float defaultDistance = 32.0F;
    private static final float overscan = 1.2F;

    public SceneCamera {
        Objects.requireNonNull(projection, "projection");
        yaw = wrap(yaw);
        pitch = clamp(pitch, -pitchLimit, pitchLimit);
        distance = clamp(distance, minDistance, maxDistance);
        zoom = clamp(zoom, minZoom, maxZoom);
    }

    /** The reset state: true isometric about the origin, before any hull is fitted. */
    public static SceneCamera isometric() {
        return new SceneCamera(
            0.0F,
            0.0F,
            0.0F,
            defaultYaw,
            defaultPitch,
            defaultDistance,
            1.0F,
            Projection.orthographic
        );
    }

    /** Turns the orbit; the pitch never reaches the pole and the yaw wraps into {@code [0, 360)}. */
    public SceneCamera orbit(float deltaYaw, float deltaPitch) {
        return new SceneCamera(
            targetX,
            targetY,
            targetZ,
            yaw + deltaYaw,
            pitch + deltaPitch,
            distance,
            zoom,
            projection
        );
    }

    /** Moves the target in the view plane, in cells; {@code up} is the camera's own up axis. */
    public SceneCamera pan(float right, float up) {
        float[] rightAxis = world(1.0F, 0.0F, 0.0F);
        float[] upAxis = world(0.0F, 1.0F, 0.0F);
        return new SceneCamera(
            targetX + rightAxis[0] * right + upAxis[0] * up,
            targetY + rightAxis[1] * right + upAxis[1] * up,
            targetZ + rightAxis[2] * right + upAxis[2] * up,
            yaw,
            pitch,
            distance,
            zoom,
            projection
        );
    }

    /** Multiplies the orthographic zoom or the perspective distance, clamped to its range. */
    public SceneCamera zoom(float factor) {
        return projection == Projection.orthographic
                ? new SceneCamera(targetX, targetY, targetZ, yaw, pitch, distance, zoom * factor, projection)
                : new SceneCamera(targetX, targetY, targetZ, yaw, pitch, distance * factor, zoom, projection);
    }

    /** Frames the hull: target at its centre, zoom back to one, and the pinhole distance refitted. */
    public SceneCamera frame(Hull hull, float aspect) {
        Hull box = hull.fitBox();
        float fitted = projection == Projection.perspective ? fitDistance(box, aspect) : distance;
        return new SceneCamera(box.centreX(), box.centreY(), box.centreZ(), yaw, pitch, fitted, 1.0F, projection);
    }

    /** The camera's own axis snapshot; it moves the eye and never the target or the zoom. */
    public SceneCamera snap(Snap snap) {
        return switch (snap) {
            case front -> pose(180.0F, 0.0F);
            case back -> pose(0.0F, 0.0F);
            case right -> pose(270.0F, 0.0F);
            case left -> pose(90.0F, 0.0F);
            case top -> pose(0.0F, pitchLimit);
            case bottom -> pose(0.0F, -pitchLimit);
        };
    }

    /** The default isometric framed to the hull: a lost viewer's way back to the starting state. */
    public SceneCamera reset(Hull hull, float aspect) {
        return isometric().frame(hull, aspect);
    }

    /**
     * Switches the projection. Only the pinhole distance is refitted; the target, the pose and the
     * orthographic zoom stay, so a framed page stays framed (design 17 §4.1).
     */
    public SceneCamera withProjection(Projection next, Hull hull) {
        float fitted = next == Projection.perspective ? fitDistance(hull.fitBox(), 1.0F) : distance;
        return new SceneCamera(targetX, targetY, targetZ, yaw, pitch, fitted, zoom, next);
    }

    /** The eye sits at {@code distance} behind the target along the orbit ray, on the view's +Z. */
    float[] eye() {
        float[] ray = world(0.0F, 0.0F, distance);
        return new float[]{targetX + ray[0], targetY + ray[1], targetZ + ray[2]};
    }

    /** The world point in view space: the turntable rotation about the target, +Z toward the eye. */
    float[] view(float x, float y, float z) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        float cosYaw = (float) Math.cos(yawRadians);
        float sinYaw = (float) Math.sin(yawRadians);
        float cosPitch = (float) Math.cos(pitchRadians);
        float sinPitch = (float) Math.sin(pitchRadians);
        float dx = x - targetX;
        float dy = y - targetY;
        float dz = z - targetZ;
        return new float[]{cosYaw * dx - sinYaw * dz, cosPitch * dy - sinPitch * (sinYaw * dx + cosYaw * dz),
                sinPitch * dy + cosPitch * (sinYaw * dx + cosYaw * dz)};
    }

    /** A view-space point in world coordinates: the inverse rotation plus the target. */
    float[] worldPoint(float x, float y, float z) {
        float[] point = world(x, y, z);
        return new float[]{targetX + point[0], targetY + point[1], targetZ + point[2]};
    }

    /** The inverse rotation: a view-space axis in world coordinates. */
    float[] world(float x, float y, float z) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        float cosYaw = (float) Math.cos(yawRadians);
        float sinYaw = (float) Math.sin(yawRadians);
        float cosPitch = (float) Math.cos(pitchRadians);
        float sinPitch = (float) Math.sin(pitchRadians);
        return new float[]{cosYaw * x - sinYaw * sinPitch * y + sinYaw * cosPitch * z, cosPitch * y + sinPitch * z,
                -sinYaw * x - cosYaw * sinPitch * y + cosYaw * cosPitch * z};
    }

    /** §4.3's pinhole fit: the hull's sphere at {@code radius / tan(fov/2) × 1.2}, widened for a tall rect. */
    static float fitDistance(Hull box, float aspect) {
        float halfFov = (float) Math.tan(Math.toRadians(fovDegrees) * 0.5);
        float narrow = Math.max(0.01F, Math.min(1.0F, aspect));
        return clamp(box.radius() / (halfFov * narrow) * overscan, minDistance, maxDistance);
    }

    private SceneCamera pose(float nextYaw, float nextPitch) {
        return new SceneCamera(targetX, targetY, targetZ, nextYaw, nextPitch, distance, zoom, projection);
    }

    private static float wrap(float value) {
        float wrapped = value % 360.0F;
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
