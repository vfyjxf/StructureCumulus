package dev.vfyjxf.cumulus.mc.preview.client.cine;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the cinematics core: every curve and path is a pure function of the continuous tick. */
class CineCoreTest {

    private static final double eps = 1.0e-9;

    @Test
    void easesPinTheirEndpoints() {
        for (Ease ease : Ease.values()) {
            assertEquals(0.0, ease.applyClamped(0.0), 1.0e-6, ease.name());
            assertEquals(1.0, ease.applyClamped(1.0), 1.0e-6, ease.name());
        }
    }

    @Test
    void easingClampsOutOfRangeInput() {
        assertEquals(0.0, Ease.linear.applyClamped(-0.5), eps);
        assertEquals(1.0, Ease.linear.applyClamped(1.5), eps);
    }

    @Test
    void aTweenInterpolatesAndEnds() {
        Tween tween = new Tween(2.0, 6.0, 10, Ease.linear);
        assertEquals(4.0, tween.at(5.0), eps);
        assertEquals(6.0, tween.at(10.0), eps);
        assertEquals(6.0, tween.at(99.0), eps);
        assertTrue(tween.done(10.0));
        assertEquals(6.0, new Tween(2.0, 6.0, 0, Ease.linear).at(0.0), eps);
    }

    @Test
    void aVecTweenMovesComponentWise() {
        VecTween tween = new VecTween(new Vec3(0, 0, 0), new Vec3(2, 4, 6), 10, Ease.linear);
        assertEquals(new Vec3(1, 2, 3), tween.at(5.0));
    }

    @Test
    void framingFitsTheSubjectFromTheApproachSide() {
        CameraShot shot = CameraShot.framing(Cuboid.of(0, 0, 0, 4, 2, 4), new Vec3(0, 0, 1), 70.0);
        assertEquals(new Vec3(2.0, 1.0, 2.0), shot.target());
        assertTrue(shot.eye().z > 2.0, "the eye sits on the +Z approach side");
        assertTrue(shot.eye().y > 1.0, "the eye rises for the top-down read");
        assertEquals(180.0, shot.yaw(), 1.0e-3, "looking from +Z at the center faces north");
        assertTrue(shot.pitch() > 0.0, "the read is slightly downward; Minecraft's xRot is positive down");
    }

    @Test
    void aZeroApproachFallsBackToSouth() {
        CameraShot shot = CameraShot.framing(Cuboid.of(0, 0, 0, 2, 2, 2), Vec3.ZERO, 70.0);
        assertTrue(shot.eye().z > 1.0, "a directionless approach falls back to the +Z side");
    }

    @Test
    void aProgramDwellsMovesAndHolds() {
        CameraShot a = new CameraShot(Vec3.ZERO, new Vec3(0, 0, 10));
        CameraShot b = new CameraShot(new Vec3(10, 0, 0), new Vec3(0, 0, 10));
        CameraProgram program = new CameraProgram(
            List.of(new CameraProgram.Leg(a, 4, 0, Ease.linear), new CameraProgram.Leg(b, 4, 4, Ease.linear))
        );
        assertEquals(a, program.at(0.0));
        assertEquals(a, program.at(3.9));
        assertEquals(new Vec3(5, 0, 0), program.at(6.0).eye());
        assertEquals(b, program.at(8.0));
        assertEquals(b, program.at(1000.0));
        assertEquals(12, program.totalTicks());
    }

    @Test
    void aProgramRejectsAnEmptyLegList() {
        assertThrows(IllegalArgumentException.class, () -> new CameraProgram(List.of()));
    }

    @Test
    void theTurntableTakesTheShortestArc() {
        assertEquals(1, new TurntableTransition(Turn.none, Turn.clockwise, 6, Ease.linear).shortestArc());
        assertEquals(-1, new TurntableTransition(Turn.none, Turn.counterclockwise, 6, Ease.linear).shortestArc());
        assertEquals(2, new TurntableTransition(Turn.none, Turn.half, 6, Ease.linear).shortestArc());
        assertEquals(-1, new TurntableTransition(Turn.clockwise, Turn.none, 6, Ease.linear).shortestArc());
    }

    @Test
    void theTurntableSwingsNinetyDegreesPerQuarter() {
        TurntableTransition swing = new TurntableTransition(Turn.none, Turn.clockwise, 6, Ease.linear);
        assertEquals(0.0, swing.angleDeg(0.0), eps);
        assertEquals(45.0, swing.angleDeg(3.0), eps);
        assertEquals(90.0, swing.angleDeg(6.0), eps);
        Vec3 rotated = swing.rotate(new Vec3(1, 0, 0), 6.0);
        assertEquals(0.0, rotated.x, 1.0e-6);
        assertEquals(1.0, rotated.z, 1.0e-6);
        assertEquals(Turn.none, swing.modelTurn(5.9));
        assertEquals(Turn.clockwise, swing.modelTurn(6.0));
    }
}
