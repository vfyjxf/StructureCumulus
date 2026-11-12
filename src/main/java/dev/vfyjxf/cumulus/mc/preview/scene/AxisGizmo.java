package dev.vfyjxf.cumulus.mc.preview.scene;

import org.joml.Vector2f;

/**
 * The three axes as scene geometry (design 17 §6): the drawn hull's minimum corner as origin, each
 * axis {@code max(1, 0.25 × diagonal)} cells long, projected through one viewport's camera. Pure
 * geometry; the screen draws the segments and their labels.
 */
public record AxisGizmo(Segment x, Segment y, Segment z) {

    /** One axis: its projected start and end, and the anchor of its name at the end. */
    public record Segment(Vector2f start, Vector2f end, Vector2f label) {}

    public static final int xColor = 0xFFFF5555;
    public static final int yColor = 0xFF55FF55;
    public static final int zColor = 0xFF5599FF;

    /** The gizmo of {@code hull} through one viewport rectangle. */
    public static AxisGizmo of(Hull hull, SceneCamera camera, int x, int y, int width, int height) {
        Hull box = hull.isEmpty() ? new Hull(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F) : hull;
        float length = Math.max(1.0F, 0.25F * box.diagonal());
        float originX = box.minX();
        float originY = box.minY();
        float originZ = box.minZ();
        Fit fit = Fit.of(hull, x, y, width, height, camera);
        Vector2f endX = fit.project(originX + length, originY, originZ);
        Vector2f endY = fit.project(originX, originY + length, originZ);
        Vector2f endZ = fit.project(originX, originY, originZ + length);
        Vector2f origin = fit.project(originX, originY, originZ);
        return new AxisGizmo(
            new Segment(origin, endX, endX),
            new Segment(origin, endY, endY),
            new Segment(origin, endZ, endZ)
        );
    }
}
