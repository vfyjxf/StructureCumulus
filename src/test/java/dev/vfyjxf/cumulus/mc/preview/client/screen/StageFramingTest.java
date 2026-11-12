package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.mc.preview.scene.SceneCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stage-framing leg of design 17 §7.5: inOutCubic over 12 ticks, the shortest yaw arc, a total sample. */
class StageFramingTest {

    private static final float eps = 1.0e-4F;
    private static final SceneCamera from = new SceneCamera(
        0.0F,
        0.0F,
        0.0F,
        10.0F,
        5.0F,
        32.0F,
        1.0F,
        SceneCamera.Projection.orthographic
    );
    private static final SceneCamera to = new SceneCamera(
        10.0F,
        4.0F,
        0.0F,
        30.0F,
        15.0F,
        32.0F,
        2.0F,
        SceneCamera.Projection.orthographic
    );

    @Test
    void theLegStartsOnTheCameraAndEndsOnTheStageFit() {
        StageFraming framing = StageFraming.of(from, to, 100.0F, 12);
        assertEquals(0.0F, framing.at(100.0F).targetX(), eps);
        assertEquals(10.0F, framing.at(112.0F).targetX(), eps);
        assertEquals(2.0F, framing.at(112.0F).zoom(), eps);
        assertEquals(15.0F, framing.at(112.0F).pitch(), eps);
    }

    @Test
    void theMidpointIsTheEasedHalfWayPoint() {
        StageFraming framing = StageFraming.of(from, to, 0.0F, 12);
        SceneCamera middle = framing.at(6.0F);
        assertEquals(5.0F, middle.targetX(), eps, "inOutCubic puts the half-way tick at the half-way point");
        assertEquals(2.0F, middle.targetY(), eps);
        assertEquals(20.0F, middle.yaw(), eps);
        assertEquals(10.0F, middle.pitch(), eps);
        assertEquals(1.5F, middle.zoom(), eps);
    }

    @Test
    void theYawTakesTheShortestArc() {
        SceneCamera west = new SceneCamera(
            0.0F,
            0.0F,
            0.0F,
            350.0F,
            0.0F,
            32.0F,
            1.0F,
            SceneCamera.Projection.orthographic
        );
        SceneCamera east = new SceneCamera(
            0.0F,
            0.0F,
            0.0F,
            10.0F,
            0.0F,
            32.0F,
            1.0F,
            SceneCamera.Projection.orthographic
        );
        assertEquals(20.0F, StageFraming.shortestArc(350.0F, 10.0F), eps);
        assertEquals(-20.0F, StageFraming.shortestArc(10.0F, 350.0F), eps);
        assertEquals(0.0F, StageFraming.of(west, east, 0.0F, 12).at(6.0F).yaw(), 1.0E-3F, "350 and 10 meet at 0");
    }

    @Test
    void aZeroTickLegIsItsTargetAtOnce() {
        StageFraming framing = StageFraming.of(from, to, 100.0F, 0);
        assertEquals(to, framing.at(100.0F));
        assertTrue(framing.done(100.0F));
    }

    @Test
    void theLegIsDoneOnlyAtItsEnd() {
        StageFraming framing = StageFraming.of(from, to, 40.0F, 12);
        assertFalse(framing.done(51.9F));
        assertTrue(framing.done(52.0F));
        assertTrue(framing.done(60.0F));
    }

    @Test
    void theSameClockSampleGivesTheSameCamera() {
        StageFraming framing = StageFraming.of(from, to, 7.0F, 12);
        assertEquals(framing.at(9.5F), framing.at(9.5F));
    }
}
