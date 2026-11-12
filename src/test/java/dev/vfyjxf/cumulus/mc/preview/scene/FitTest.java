package dev.vfyjxf.cumulus.mc.preview.scene;

import org.joml.Vector2f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three-armed fit of design 17 §4.3: the orthographic scale, the pinhole distance and the degeneracy. */
class FitTest {

    private static final float eps = 1.0e-3F;
    private static final Hull box = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F);

    private static SceneCamera level() {
        return new SceneCamera(1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 32.0F, 1.0F, SceneCamera.Projection.orthographic);
    }

    @Test
    void theOrthographicArmFitsTheHullIntoTheRectangle() {
        Fit fit = Fit.of(box, 0, 0, 100, 100, level());
        assertEquals(100.0F * Fit.margin / 2.0F, fit.scale(), eps);
        assertPoint(50.0F, 50.0F, fit.project(1.0F, 1.0F, 1.0F));
        assertPoint(7.0F, 93.0F, fit.project(0.0F, 0.0F, 0.0F));
        assertEquals(2.0F / (100.0F * Fit.margin), fit.unitsPerPixel(), 1.0e-4F);
    }

    @Test
    void theRectangleOffsetMovesTheCentre() {
        Fit fit = Fit.of(box, 10, 20, 100, 100, level());
        assertPoint(60.0F, 70.0F, fit.project(1.0F, 1.0F, 1.0F));
    }

    @Test
    void theZoomScalesTheOrthographicFit() {
        Fit plain = Fit.of(box, 0, 0, 100, 100, level());
        Fit zoomed = Fit.of(box, 0, 0, 100, 100, level().zoom(2.0F));
        assertEquals(plain.scale() * 2.0F, zoomed.scale(), eps);
    }

    @Test
    void theOrthographicFitIgnoresTheEyeDistance() {
        Fit near = Fit.of(box, 0, 0, 100, 100, level());
        SceneCamera far = new SceneCamera(
            1.0F,
            1.0F,
            1.0F,
            0.0F,
            0.0F,
            300.0F,
            1.0F,
            SceneCamera.Projection.orthographic
        );
        assertEquals(near.scale(), Fit.of(box, 0, 0, 100, 100, far).scale(), eps);
    }

    @Test
    void aDegenerateHullStillFitsWithoutDividingByZero() {
        Hull empty = new Hull(0.0F, 0.0F, 0.0F, -1.0F, -1.0F, -1.0F);
        SceneCamera camera = SceneCamera.isometric().frame(empty, 1.0F);
        Fit fit = Fit.of(empty, 0, 0, 100, 100, camera);
        assertTrue(fit.scale() > 0.0F);
        assertTrue(Float.isFinite(fit.scale()));
        assertPoint(50.0F, 50.0F, fit.project(0.5F, 0.5F, 0.5F));

        Hull cell = new Hull(4.0F, 0.0F, 3.0F, 5.0F, 1.0F, 4.0F);
        SceneCamera cellCamera = SceneCamera.isometric().frame(cell, 1.0F);
        Fit cellFit = Fit.of(cell, 0, 0, 100, 100, cellCamera);
        assertTrue(cellFit.scale() > 0.0F);
        assertPoint(50.0F, 50.0F, cellFit.project(4.5F, 0.5F, 3.5F));
    }

    @Test
    void thePerspectiveArmProjectsTheNearCornerLarger() {
        Hull big = new Hull(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 4.0F);
        SceneCamera camera = SceneCamera.isometric().withProjection(SceneCamera.Projection.perspective, big)
                .frame(big, 1.0F);
        Fit fit = Fit.of(big, 0, 0, 100, 100, camera);
        float focal = 50.0F / (float) Math.tan(Math.toRadians(SceneCamera.fovDegrees) * 0.5);
        assertEquals(focal, fit.focal(), 1.0e-2F);
        assertEquals(focal / camera.distance(), fit.scale(), 1.0e-3F);
        assertPoint(50.0F, 50.0F, fit.project(2.0F, 2.0F, 2.0F));

        float nearDepth = fit.depth(2.0F, 2.0F, 4.0F);
        float farDepth = fit.depth(2.0F, 2.0F, 0.0F);
        assertTrue(nearDepth > farDepth, "depth says larger is nearer");
    }

    @Test
    void theOrthographicDepthSortsFarToNear() {
        Fit fit = Fit.of(box, 0, 0, 100, 100, level());
        assertTrue(fit.depth(1.0F, 1.0F, 2.0F) > fit.depth(1.0F, 1.0F, 0.0F));
    }

    @Test
    void theOrthographicRenderMatrixMatchesTheProjection() {
        Fit fit = Fit.of(box, 10, 20, 100, 100, level());
        assertMatrixPoint(17.0F, 113.0F, fit, 0.0F, 0.0F, 0.0F);
        assertMatrixPoint(60.0F, 70.0F, fit, 1.0F, 1.0F, 1.0F);
        assertTrue(
            fit.matrix().transform(new Vector4f(1.0F, 1.0F, 2.0F, 1.0F)).z
                    > fit.matrix().transform(new Vector4f(1.0F, 1.0F, 0.0F, 1.0F)).z,
            "nearer has the larger pose z"
        );
    }

    @Test
    void thePerspectiveRenderMatrixMatchesTheProjection() {
        Hull big = new Hull(0.0F, 0.0F, 0.0F, 4.0F, 4.0F, 4.0F);
        SceneCamera camera = SceneCamera.isometric().withProjection(SceneCamera.Projection.perspective, big)
                .frame(big, 1.0F);
        Fit fit = Fit.of(big, 0, 0, 100, 100, camera);
        assertMatrixPoint(50.0F, 50.0F, fit, 2.0F, 2.0F, 2.0F);
        Vector4f near = fit.matrix().transform(new Vector4f(2.0F, 2.0F, 4.0F, 1.0F));
        Vector4f far = fit.matrix().transform(new Vector4f(2.0F, 2.0F, 0.0F, 1.0F));
        assertTrue(near.z / near.w > far.z / far.w, "the perspective depth says nearer is larger");
    }

    private static void assertMatrixPoint(float x, float y, Fit fit, float worldX, float worldY, float worldZ) {
        Vector4f clip = fit.matrix().transform(new Vector4f(worldX, worldY, worldZ, 1.0F));
        assertPoint(x, y, new Vector2f(clip.x / clip.w, clip.y / clip.w));
    }

    private static void assertPoint(float x, float y, Vector2f point) {
        assertEquals(x, point.x, eps);
        assertEquals(y, point.y, eps);
    }
}
