package dev.vfyjxf.cumulus.mc.preview.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The camera value of design 17 §4.2: total operations, the clamps, the snaps and the fits. */
class SceneCameraTest {

    private static final float eps = 1.0e-3F;
    private static final Hull box = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F);

    @Test
    void theDefaultIsTrueIsometricAboutTheOrigin() {
        SceneCamera camera = SceneCamera.isometric();
        assertEquals(SceneCamera.defaultYaw, camera.yaw(), eps);
        assertEquals(SceneCamera.defaultPitch, camera.pitch(), eps);
        assertEquals(1.0F, camera.zoom(), eps);
        assertEquals(SceneCamera.Projection.orthographic, camera.projection());
        assertEquals(0.0F, camera.targetX(), eps);
        assertEquals(0.0F, camera.targetY(), eps);
        assertEquals(0.0F, camera.targetZ(), eps);
    }

    @Test
    void theIsometricEyeStandsOnTheThreeEqualOffsets() {
        SceneCamera camera = SceneCamera.isometric();
        float[] eye = camera.world(0.0F, 0.0F, 1.0F);
        assertEquals(0.57735F, eye[0], 1.0e-4F);
        assertEquals(0.57735F, eye[1], 1.0e-4F);
        assertEquals(0.57735F, eye[2], 1.0e-4F);
        assertTrue(eye[1] > 0.0F, "the default looks down at the page");
    }

    @Test
    void orbitWrapsTheYawAndStopsShortOfThePole() {
        SceneCamera camera = SceneCamera.isometric();
        assertEquals(55.0F, camera.orbit(370.0F, 0.0F).yaw(), eps);
        assertEquals(35.0F, camera.orbit(-10.0F, 0.0F).yaw(), eps);
        assertEquals(89.9F, camera.orbit(0.0F, 500.0F).pitch(), eps);
        assertEquals(-89.9F, camera.orbit(0.0F, -500.0F).pitch(), eps);
    }

    @Test
    void theZoomClampsItsOwnArm() {
        SceneCamera orthographic = SceneCamera.isometric();
        assertEquals(2.0F, orthographic.zoom(2.0F).zoom(), eps);
        assertEquals(SceneCamera.maxZoom, orthographic.zoom(100.0F).zoom(), eps);
        assertEquals(SceneCamera.minZoom, orthographic.zoom(0.001F).zoom(), eps);

        SceneCamera perspective = orthographic.withProjection(SceneCamera.Projection.perspective, box);
        float distance = perspective.distance();
        assertEquals(Math.min(SceneCamera.maxDistance, distance * 2.0F), perspective.zoom(2.0F).distance(), eps);
        assertEquals(SceneCamera.maxDistance, perspective.zoom(1000.0F).distance(), eps);
        assertEquals(SceneCamera.minDistance, perspective.zoom(0.0001F).distance(), eps);
        assertEquals(1.0F, perspective.zoom(1000.0F).zoom(), eps, "the perspective zoom leaves zoom alone");
    }

    @Test
    void panMovesTheTargetAlongTheCamerasOwnAxes() {
        SceneCamera level = new SceneCamera(
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            32.0F,
            1.0F,
            SceneCamera.Projection.orthographic
        );
        SceneCamera moved = level.pan(2.0F, 3.0F);
        assertEquals(2.0F, moved.targetX(), eps);
        assertEquals(3.0F, moved.targetY(), eps);
        assertEquals(0.0F, moved.targetZ(), eps);

        SceneCamera side = new SceneCamera(
            0.0F,
            0.0F,
            0.0F,
            90.0F,
            0.0F,
            32.0F,
            1.0F,
            SceneCamera.Projection.orthographic
        );
        SceneCamera panned = side.pan(1.0F, 0.0F);
        assertEquals(0.0F, panned.targetX(), eps);
        assertEquals(0.0F, panned.targetY(), eps);
        assertEquals(-1.0F, panned.targetZ(), eps, "looking from +X, right is -Z");
    }

    @Test
    void aSnapMovesTheEyeAndLeavesThePage() {
        SceneCamera camera = new SceneCamera(
            1.0F,
            2.0F,
            3.0F,
            10.0F,
            20.0F,
            40.0F,
            2.0F,
            SceneCamera.Projection.orthographic
        );
        SceneCamera front = camera.snap(SceneCamera.Snap.front);
        assertEquals(180.0F, front.yaw(), eps);
        assertEquals(0.0F, front.pitch(), eps);
        assertEquals(1.0F, front.targetX(), eps);
        assertEquals(2.0F, front.targetY(), eps);
        assertEquals(3.0F, front.targetZ(), eps);
        assertEquals(40.0F, front.distance(), eps);
        assertEquals(2.0F, front.zoom(), eps);
        assertEquals(SceneCamera.Projection.orthographic, front.projection());

        assertEquals(0.0F, camera.snap(SceneCamera.Snap.back).yaw(), eps);
        assertEquals(270.0F, camera.snap(SceneCamera.Snap.right).yaw(), eps);
        assertEquals(90.0F, camera.snap(SceneCamera.Snap.left).yaw(), eps);
        assertEquals(89.9F, camera.snap(SceneCamera.Snap.top).pitch(), eps);
        assertEquals(-89.9F, camera.snap(SceneCamera.Snap.bottom).pitch(), eps);
    }

    @Test
    void eachSnapLooksAlongItsDeclaredAxis() {
        SceneCamera camera = SceneCamera.isometric();
        assertVector(0.0F, 0.0F, -1.0F, camera.snap(SceneCamera.Snap.front).world(0.0F, 0.0F, 1.0F));
        assertVector(0.0F, 0.0F, 1.0F, camera.snap(SceneCamera.Snap.back).world(0.0F, 0.0F, 1.0F));
        assertVector(-1.0F, 0.0F, 0.0F, camera.snap(SceneCamera.Snap.right).world(0.0F, 0.0F, 1.0F));
        assertVector(1.0F, 0.0F, 0.0F, camera.snap(SceneCamera.Snap.left).world(0.0F, 0.0F, 1.0F));
        assertTrue(camera.snap(SceneCamera.Snap.top).world(0.0F, 0.0F, 1.0F)[1] > 0.99F);
        assertTrue(camera.snap(SceneCamera.Snap.bottom).world(0.0F, 0.0F, 1.0F)[1] < -0.99F);
    }

    @Test
    void aFrameCentresTheHullAndResetsTheZoom() {
        SceneCamera camera = SceneCamera.isometric().zoom(3.0F);
        SceneCamera framed = camera.frame(box, 1.5F);
        assertEquals(1.0F, framed.targetX(), eps);
        assertEquals(1.0F, framed.targetY(), eps);
        assertEquals(1.0F, framed.targetZ(), eps);
        assertEquals(1.0F, framed.zoom(), eps);
        assertEquals(camera.yaw(), framed.yaw(), eps);
        assertEquals(camera.pitch(), framed.pitch(), eps);
    }

    @Test
    void thePerspectiveFrameFitsTheHullsSphere() {
        SceneCamera perspective = SceneCamera.isometric().withProjection(SceneCamera.Projection.perspective, box);
        SceneCamera framed = perspective.frame(box, 1.0F);
        float radius = (float) Math.sqrt(3.0) / 2.0F * 2.0F;
        float expected = radius / (float) Math.tan(Math.toRadians(SceneCamera.fovDegrees) * 0.5) * 1.2F;
        assertEquals(expected, framed.distance(), 1.0e-3F);
        assertEquals(1.0F, framed.zoom(), eps);
    }

    @Test
    void aNarrowViewportWidensThePinholeFit() {
        float wide = SceneCamera.fitDistance(box.fitBox(), 2.0F);
        float square = SceneCamera.fitDistance(box.fitBox(), 1.0F);
        float narrow = SceneCamera.fitDistance(box.fitBox(), 0.5F);
        assertEquals(square, wide, eps);
        assertEquals(square * 2.0F, narrow, 1.0e-3F);
    }

    @Test
    void theProjectionSwitchRefitsOnlyTheDistance() {
        SceneCamera orthographic = SceneCamera.isometric().frame(box, 1.0F);
        SceneCamera perspective = orthographic.withProjection(SceneCamera.Projection.perspective, box);
        assertEquals(SceneCamera.Projection.perspective, perspective.projection());
        assertEquals(orthographic.targetX(), perspective.targetX(), eps);
        assertEquals(orthographic.targetY(), perspective.targetY(), eps);
        assertEquals(orthographic.yaw(), perspective.yaw(), eps);
        assertEquals(orthographic.pitch(), perspective.pitch(), eps);
        assertEquals(orthographic.zoom(), perspective.zoom(), eps);
        assertEquals(SceneCamera.fitDistance(box.fitBox(), 1.0F), perspective.distance(), 1.0e-3F);

        SceneCamera back = perspective.withProjection(SceneCamera.Projection.orthographic, box);
        assertEquals(SceneCamera.Projection.orthographic, back.projection());
        assertEquals(perspective.zoom(), back.zoom(), eps);
        assertEquals(perspective.distance(), back.distance(), eps);
    }

    @Test
    void theResetIsTheIsometricFramed() {
        SceneCamera reset = new SceneCamera(
            9.0F,
            9.0F,
            9.0F,
            90.0F,
            80.0F,
            40.0F,
            4.0F,
            SceneCamera.Projection.orthographic
        ).reset(box, 1.0F);
        assertEquals(SceneCamera.defaultYaw, reset.yaw(), eps);
        assertEquals(SceneCamera.defaultPitch, reset.pitch(), eps);
        assertEquals(1.0F, reset.zoom(), eps);
        assertEquals(1.0F, reset.targetX(), eps);
    }

    private static void assertVector(float x, float y, float z, float[] actual) {
        assertEquals(x, actual[0], eps);
        assertEquals(y, actual[1], eps);
        assertEquals(z, actual[2], eps);
    }
}
