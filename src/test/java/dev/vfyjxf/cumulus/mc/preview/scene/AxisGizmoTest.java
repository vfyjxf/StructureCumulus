package dev.vfyjxf.cumulus.mc.preview.scene;

import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The corner compass of design 17 §6: a pure function of the camera and the viewport rectangle. */
class AxisGizmoTest {

    private static final float eps = 0.01F;
    private static final Hull cell = new Hull(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);

    @Test
    void theTriadSitsInTheViewportsTopRightCorner() {
        AxisGizmo gizmo = AxisGizmo.of(SceneCamera.isometric(), 10, 20, 300, 200);
        assertEquals(10 + 300 - AxisGizmo.insetPx, gizmo.x().start().x, eps);
        assertEquals(20 + AxisGizmo.insetPx, gizmo.x().start().y, eps);
        assertEquals(gizmo.x().start(), gizmo.y().start());
        assertEquals(gizmo.x().start(), gizmo.z().start());
    }

    @Test
    void everyAxisIsTheFixedEighteenPixels() {
        for (SceneCamera camera : List.of(
            SceneCamera.isometric(),
            SceneCamera.isometric().orbit(37.0F, -12.0F),
            SceneCamera.isometric().orbit(-15.0F, 20.0F)
        )) {
            for (AxisGizmo.Segment axis : AxisGizmo.of(camera, 0, 0, 100, 100).ordered()) {
                assertEquals(AxisGizmo.gizmoAxisPx, length(axis), 1.0E-3F, "the triad is fixed pixels, not cells");
            }
        }
    }

    @Test
    void theSameRectangleAndCameraGiveTheSameCompass() {
        assertEquals(
            AxisGizmo.of(SceneCamera.isometric(), 4, 4, 100, 100),
            AxisGizmo.of(SceneCamera.isometric(), 4, 4, 100, 100)
        );
    }

    @Test
    void theOrbitTurnsItAndTheCameraValueDoesNot() {
        SceneCamera iso = SceneCamera.isometric();
        AxisGizmo reference = AxisGizmo.of(iso, 0, 0, 200, 200);
        assertEquals(reference, AxisGizmo.of(iso.zoom(2.0F), 0, 0, 200, 200), "a zoom never moves the compass");
        AxisGizmo panned = AxisGizmo.of(iso.pan(3.0F, -2.0F), 0, 0, 200, 200);
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(
                reference.ordered().get(axis).end().distance(panned.ordered().get(axis).end()),
                0.0F,
                1.0E-3F,
                "a pan never moves it (the rotation is target-free arithmetic, so only float noise)"
            );
        }
        assertEquals(
            reference,
            AxisGizmo.of(iso.withProjection(SceneCamera.Projection.perspective, cell), 0, 0, 200, 200),
            "the projection arm changes the picture, not the frame"
        );
        assertFalse(reference.equals(AxisGizmo.of(iso.orbit(30.0F, 0.0F), 0, 0, 200, 200)), "an orbit turns it");
    }

    @Test
    void theViewportSizeOnlyMovesTheAnchor() {
        AxisGizmo small = AxisGizmo.of(SceneCamera.isometric(), 0, 0, 100, 80);
        AxisGizmo large = AxisGizmo.of(SceneCamera.isometric(), 0, 0, 400, 300);
        assertEquals(AxisGizmo.gizmoAxisPx, length(small.x()), 1.0E-3F);
        assertEquals(AxisGizmo.gizmoAxisPx, length(large.x()), 1.0E-3F);
        assertEquals(100 - AxisGizmo.insetPx, small.x().start().x, eps);
        assertEquals(400 - AxisGizmo.insetPx, large.x().start().x, eps);
    }

    @Test
    void theFrontSnapShowsXHorizontalAndYVertical() {
        SceneCamera front = SceneCamera.isometric().snap(SceneCamera.Snap.front);
        AxisGizmo gizmo = AxisGizmo.of(front, 0, 0, 200, 200);
        assertEquals(gizmo.x().start().y, gizmo.x().end().y, eps, "X is horizontal");
        assertEquals(gizmo.y().start().x, gizmo.y().end().x, eps, "Y is vertical");
        assertTrue(gizmo.y().end().y < gizmo.y().start().y, "Y points up the screen");
        Vector2f flat = gizmo.z().end().sub(gizmo.z().start(), new Vector2f());
        assertEquals(0.0F, flat.length(), eps, "the depth axis projects to a point");
    }

    @Test
    void theSegmentsDrawFarToNear() {
        AxisGizmo gizmo = AxisGizmo.of(SceneCamera.isometric().orbit(37.0F, -12.0F), 0, 0, 200, 200);
        List<AxisGizmo.Segment> ordered = gizmo.ordered();
        assertEquals(3, ordered.size());
        assertTrue(ordered.containsAll(List.of(gizmo.x(), gizmo.y(), gizmo.z())));
        assertTrue(ordered.get(0).depth() <= ordered.get(1).depth());
        assertTrue(ordered.get(1).depth() <= ordered.get(2).depth());
        assertTrue(ordered.get(2).depth() > ordered.get(0).depth(), "the orbit separates the three depths");
    }

    @Test
    void theFrameItShowsIsTheFrameThePictureDraws() {
        SceneCamera camera = SceneCamera.isometric().frame(cell, 1.0F);
        Fit fit = Fit.of(cell, 0, 0, 100, 100, camera);
        AxisGizmo gizmo = AxisGizmo.of(camera, 0, 0, 100, 100);
        Vector2f projected = fit.project(1.0F, 0.5F, 0.5F).sub(fit.project(0.5F, 0.5F, 0.5F), new Vector2f());
        Vector2f compass = gizmo.x().end().sub(gizmo.x().start(), new Vector2f());
        float cosine = (projected.x * compass.x + projected.y * compass.y) / (projected.length() * compass.length());
        assertEquals(1.0F, cosine, 1.0E-4F, "the compass points the way the scene projects the same axis");
    }

    private static float length(AxisGizmo.Segment segment) {
        return segment.end().sub(segment.start(), new Vector2f()).length();
    }
}
