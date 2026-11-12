package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.mc.preview.scene.SceneCamera;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The camera's cardinal reading: four horizons, and a 45° boundary takes the declared-earlier one. */
class PreviewScreenHeadingTest {

    @Test
    void theFourSnapshotsReadTheirOwnHorizon() {
        assertEquals(Heading.north, PreviewScreen.headingOf(0.0F));
        assertEquals(Heading.west, PreviewScreen.headingOf(90.0F));
        assertEquals(Heading.south, PreviewScreen.headingOf(180.0F));
        assertEquals(Heading.east, PreviewScreen.headingOf(270.0F));
    }

    @Test
    void eachHorizonOwnsTheSectorAroundItself() {
        assertEquals(Heading.north, PreviewScreen.headingOf(10.0F));
        assertEquals(Heading.west, PreviewScreen.headingOf(60.0F));
        assertEquals(Heading.south, PreviewScreen.headingOf(200.0F));
        assertEquals(Heading.east, PreviewScreen.headingOf(300.0F));
        assertEquals(Heading.north, PreviewScreen.headingOf(359.0F));
    }

    @Test
    void aFortyFiveDegreeBoundaryTakesTheEarlierDeclaration() {
        assertEquals(Heading.north, PreviewScreen.headingOf(45.0F), "north is declared before west");
        assertEquals(Heading.south, PreviewScreen.headingOf(135.0F), "south is declared before west");
        assertEquals(Heading.south, PreviewScreen.headingOf(225.0F), "south is declared before east");
        assertEquals(Heading.north, PreviewScreen.headingOf(315.0F), "north is declared before east");
    }

    @Test
    void theDefaultIsometricCameraSendsNorth() {
        assertEquals(Heading.north, PreviewScreen.headingOf(SceneCamera.defaultYaw));
    }

    @Test
    void theSameCameraAlwaysReadsTheSameHeading() {
        for (float yaw = 0.0F; yaw < 360.0F; yaw += 7.5F) {
            assertEquals(PreviewScreen.headingOf(yaw), PreviewScreen.headingOf(yaw));
        }
        assertEquals(PreviewScreen.headingOf(-45.0F), PreviewScreen.headingOf(315.0F));
        assertEquals(PreviewScreen.headingOf(450.0F), PreviewScreen.headingOf(90.0F));
    }
}
