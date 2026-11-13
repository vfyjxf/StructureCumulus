package dev.vfyjxf.cumulus.mc.preview.scene;

import org.joml.Matrix4f;
import org.joml.Vector2f;

/**
 * The fit of one hull into one viewport through one camera (design 17 §4.3): the scale the scene
 * draws at, the 2D projection the axes and the picking share, and the depth the cells sort by. A pure
 * value; no client, no world, no clock.
 */
public record Fit(Hull hull, SceneCamera camera, int x, int y, int width, int height, float scale, float focal) {

    /** The scene's fitted-scale margin (design 15 §6.2). */
    public static final float margin = 0.86F;

    /** The fit of {@code hull} into the rectangle at {@code (x, y)} through {@code camera}. */
    public static Fit of(Hull hull, int x, int y, int width, int height, SceneCamera camera) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        Hull box = hull.fitBox();
        if (camera.projection() == SceneCamera.Projection.orthographic) {
            float minU = Float.MAX_VALUE;
            float maxU = -Float.MAX_VALUE;
            float minV = Float.MAX_VALUE;
            float maxV = -Float.MAX_VALUE;
            for (int corner = 0; corner < 8; corner++) {
                float[] view = camera.view(
                    (corner & 1) == 0 ? box.minX() : box.maxX(),
                    (corner & 2) == 0 ? box.minY() : box.maxY(),
                    (corner & 4) == 0 ? box.minZ() : box.maxZ()
                );
                minU = Math.min(minU, view[0]);
                maxU = Math.max(maxU, view[0]);
                minV = Math.min(minV, view[1]);
                maxV = Math.max(maxV, view[1]);
            }
            float fitted = Math.min(
                safeWidth * margin / Math.max(0.001F, maxU - minU),
                safeHeight * margin / Math.max(0.001F, maxV - minV)
            ) * camera.zoom();
            return new Fit(hull, camera, x, y, width, height, fitted, 0.0F);
        }
        float focal = safeHeight * 0.5F / (float) Math.tan(Math.toRadians(SceneCamera.fovDegrees) * 0.5);
        return new Fit(hull, camera, x, y, width, height, focal / camera.distance(), focal);
    }

    /** How many cells one pixel covers: the pan gesture's own unit (design 17 §4.2). */
    public float unitsPerPixel() {
        return 1.0F / scale;
    }

    /** The viewport point a world point projects to. */
    public Vector2f project(float worldX, float worldY, float worldZ) {
        float[] view = camera.view(worldX, worldY, worldZ);
        float centreX = x + width * 0.5F;
        float centreY = y + height * 0.5F;
        if (camera.projection() == SceneCamera.Projection.orthographic) {
            return new Vector2f(centreX + view[0] * scale, centreY - view[1] * scale);
        }
        float depth = Math.max(0.01F, camera.distance() - view[2]);
        return new Vector2f(centreX + focal * view[0] / depth, centreY - focal * view[1] / depth);
    }

    /** The cell sort key: larger is nearer, so an ascending sort draws far-to-near. */
    public float depth(float worldX, float worldY, float worldZ) {
        return camera.view(worldX, worldY, worldZ)[2];
    }

    /**
     * The world-to-GUI matrix the scene draws through: pixels in x/y and the GUI pass's depth in z.
     * Both projections put the models inside {@link PreviewScene#sceneSpan}, above the scene's floor
     * and below {@link PreviewScene#chromeDepth} (design 17 §4.1).
     */
    public Matrix4f matrix() {
        float centreX = x + width * 0.5F;
        float centreY = y + height * 0.5F;
        float halfSpan = PreviewScene.sceneSpan * 0.5F;
        Matrix4f view = viewMatrix();
        if (camera.projection() == SceneCamera.Projection.orthographic) {
            return new Matrix4f().translate(centreX, centreY, halfSpan).scale(scale, -scale, orthographicDepth())
                    .mul(view);
        }
        float extent = perspectiveExtent();
        float near = Math.max(0.01F, camera.distance() - extent);
        float far = camera.distance() + extent;
        return new Matrix4f().translate(centreX, centreY, halfSpan).scale(width * 0.5F, -height * 0.5F, -halfSpan).mul(
            new Matrix4f()
                    .perspective((float) Math.toRadians(SceneCamera.fovDegrees), width / (float) height, near, far)
        ).translate(0.0F, 0.0F, -camera.distance()).mul(view);
    }

    /**
     * How many GUI depth units one cell of view depth buys: the page's sphere fills half the band and
     * an exploded cell's own radius still fits inside it (design 17 §4.1, §8.1).
     */
    private float orthographicDepth() {
        return 0.4F * PreviewScene.sceneSpan / Math.max(1.0F, 2.0F * hull.fitBox().radius());
    }

    /** The near/far half-extent: the hull's own view depth plus an exploded cell's radius. */
    private float perspectiveExtent() {
        Hull box = hull.fitBox();
        float extent = 0.0F;
        for (int corner = 0; corner < 8; corner++) {
            float[] point = camera.view(
                (corner & 1) == 0 ? box.minX() : box.maxX(),
                (corner & 2) == 0 ? box.minY() : box.maxY(),
                (corner & 4) == 0 ? box.minZ() : box.maxZ()
            );
            extent = Math.max(extent, Math.abs(point[2]));
        }
        return Math.max(1.0F, extent + box.radius() + 1.0F);
    }

    /** The camera-relative view matrix: the turntable rotation and the target translation. */
    private Matrix4f viewMatrix() {
        float[] origin = camera.view(0.0F, 0.0F, 0.0F);
        float[] xAxis = camera.view(1.0F, 0.0F, 0.0F);
        float[] yAxis = camera.view(0.0F, 1.0F, 0.0F);
        float[] zAxis = camera.view(0.0F, 0.0F, 1.0F);
        return new Matrix4f(
            xAxis[0] - origin[0],
            xAxis[1] - origin[1],
            xAxis[2] - origin[2],
            0.0F,
            yAxis[0] - origin[0],
            yAxis[1] - origin[1],
            yAxis[2] - origin[2],
            0.0F,
            zAxis[0] - origin[0],
            zAxis[1] - origin[1],
            zAxis[2] - origin[2],
            0.0F,
            origin[0],
            origin[1],
            origin[2],
            1.0F
        );
    }
}
