package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.mc.preview.scene.Hull;
import dev.vfyjxf.cumulus.mc.preview.scene.SceneCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The linkage law of design 17 §5.2: one shared value, three fixed panels, four projections. */
class ViewportCameraTest {

    private static final float eps = 1.0e-5F;
    private static final Hull box = new Hull(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F);

    private static SceneCamera shared() {
        return SceneCamera.isometric().frame(box, 1.5F).zoom(2.0F);
    }

    @Test
    void theMainViewportIsTheViewersCamera() {
        SceneCamera shared = shared();
        assertSame(shared, PreviewScreen.cameraFor(shared, PreviewScreen.Panel.main));
    }

    @Test
    void theThreePanelsAreAxisLockedAndAlwaysOrthographic() {
        SceneCamera shared = shared().withProjection(SceneCamera.Projection.perspective, box);
        SceneCamera front = PreviewScreen.cameraFor(shared, PreviewScreen.Panel.front);
        SceneCamera side = PreviewScreen.cameraFor(shared, PreviewScreen.Panel.side);
        SceneCamera top = PreviewScreen.cameraFor(shared, PreviewScreen.Panel.top);
        assertEquals(180.0F, front.yaw(), eps);
        assertEquals(0.0F, front.pitch(), eps);
        assertEquals(270.0F, side.yaw(), eps);
        assertEquals(0.0F, side.pitch(), eps);
        assertEquals(0.0F, top.yaw(), eps);
        assertEquals(SceneCamera.maxPitch, top.pitch(), eps);
        assertEquals(SceneCamera.Projection.orthographic, front.projection());
        assertEquals(SceneCamera.Projection.orthographic, side.projection());
        assertEquals(SceneCamera.Projection.orthographic, top.projection());
    }

    @Test
    void thePanelsShareTheTargetTheDistanceAndTheZoom() {
        SceneCamera shared = shared();
        for (PreviewScreen.Panel panel : new PreviewScreen.Panel[]{PreviewScreen.Panel.front, PreviewScreen.Panel.side,
                PreviewScreen.Panel.top}) {
            SceneCamera panelCamera = PreviewScreen.cameraFor(shared, panel);
            assertEquals(shared.targetX(), panelCamera.targetX(), eps, panel.name());
            assertEquals(shared.targetY(), panelCamera.targetY(), eps, panel.name());
            assertEquals(shared.targetZ(), panelCamera.targetZ(), eps, panel.name());
            assertEquals(shared.distance(), panelCamera.distance(), eps, panel.name());
            assertEquals(shared.zoom(), panelCamera.zoom(), eps, panel.name());
        }
    }
}
