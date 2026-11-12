package dev.vfyjxf.cumulus.mc.preview.scene;

import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The axis gizmo of design 17 §6: the hull's own corner, the length law and the equal isometric axes. */
class AxisGizmoTest {

    private static final float eps = 0.01F;

    @Test
    void theIsometricAxesHaveEqualApparentLength() {
        Hull box = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F);
        SceneCamera camera = SceneCamera.isometric().frame(box, 1.0F);
        AxisGizmo gizmo = AxisGizmo.of(box, camera, 0, 0, 100, 100);
        float x = length(gizmo.x());
        float y = length(gizmo.y());
        float z = length(gizmo.z());
        assertEquals(x, y, 0.05F);
        assertEquals(x, z, 0.05F);
        assertTrue(x > 0.0F);
    }

    @Test
    void theOriginIsTheDrawnHullMinimumCorner() {
        Hull box = new Hull(3.0F, 2.0F, 1.0F, 5.0F, 4.0F, 3.0F);
        SceneCamera camera = SceneCamera.isometric().frame(box, 1.0F);
        AxisGizmo gizmo = AxisGizmo.of(box, camera, 0, 0, 100, 100);
        Vector2f expected = Fit.of(box, 0, 0, 100, 100, camera).project(3.0F, 2.0F, 1.0F);
        assertEquals(expected.x, gizmo.x().start().x, eps);
        assertEquals(expected.y, gizmo.x().start().y, eps);
        assertEquals(gizmo.x().start(), gizmo.y().start());
        assertEquals(gizmo.x().start(), gizmo.z().start());
    }

    @Test
    void theLengthIsAQuarterOfTheDiagonalWithAOneCellFloor() {
        Hull small = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F);
        SceneCamera camera = SceneCamera.isometric().frame(small, 1.0F);
        Fit fit = Fit.of(small, 0, 0, 100, 100, camera);
        AxisGizmo gizmo = AxisGizmo.of(small, camera, 0, 0, 100, 100);
        Vector2f unit = fit.project(1.0F, 0.0F, 0.0F);
        assertEquals(distance(gizmo.x().start(), unit), length(gizmo.x()), 0.05F, "a small hull gets one-cell axes");

        Hull big = new Hull(0.0F, 0.0F, 0.0F, 10.0F, 10.0F, 10.0F);
        SceneCamera bigCamera = SceneCamera.isometric().frame(big, 1.0F);
        Fit bigFit = Fit.of(big, 0, 0, 100, 100, bigCamera);
        AxisGizmo bigGizmo = AxisGizmo.of(big, bigCamera, 0, 0, 100, 100);
        float quarter = 0.25F * (float) Math.sqrt(3.0) * 10.0F;
        Vector2f end = bigFit.project(quarter, 0.0F, 0.0F);
        assertEquals(distance(bigGizmo.x().start(), end), length(bigGizmo.x()), 0.05F);
    }

    @Test
    void theFrontViewProjectsTheDepthAxisToAPoint() {
        Hull box = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F);
        SceneCamera camera = SceneCamera.isometric().snap(SceneCamera.Snap.front);
        AxisGizmo gizmo = AxisGizmo.of(box, camera, 0, 0, 100, 100);
        assertEquals(gizmo.z().start().x, gizmo.z().end().x, eps);
        assertEquals(gizmo.z().start().y, gizmo.z().end().y, eps);
        assertEquals(gizmo.x().start().y, gizmo.x().end().y, eps, "X is horizontal");
        assertEquals(gizmo.y().start().x, gizmo.y().end().x, eps, "Y is vertical");
    }

    private static float length(AxisGizmo.Segment segment) {
        return distance(segment.start(), segment.end());
    }

    private static float distance(Vector2f from, Vector2f to) {
        return (float) Math.hypot(to.x - from.x, to.y - from.y);
    }
}
