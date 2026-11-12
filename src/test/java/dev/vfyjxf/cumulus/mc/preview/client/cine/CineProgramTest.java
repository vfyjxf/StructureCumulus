package dev.vfyjxf.cumulus.mc.preview.client.cine;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the program law: the orbit, the stage framing flight and the closing pull-back are pure. */
class CineProgramTest {

    private static final double eps = 1.0e-9;

    @Test
    void theOpeningOrbitTurnsOnceAtTheFitDistance() {
        Cuboid bounds = Cuboid.ofSize(0, 100, 0, 4, 3, 4);
        Vec3 approach = new Vec3(0, 0, 1);
        CameraProgram orbit = CinematicPrograms.opening(bounds, approach);
        Vec3 centre = new Vec3(2.0, 101.5, 2.0);
        assertEquals(80, orbit.totalTicks());
        assertEquals(17, orbit.legs().size());
        double radius = orbit.at(0.0).eye().distanceTo(centre);
        for (CameraProgram.Leg leg : orbit.legs()) {
            assertEquals(radius, leg.shot().eye().distanceTo(centre), 1.0e-6, "every shot keeps the fit distance");
        }
        // a leg is a chord between two shots, so the sampled circle dips by at most the half-step angle
        assertEquals(radius, orbit.at(40.0).eye().distanceTo(centre), 1.0e-6);
        assertTrue(orbit.at(2.5).eye().distanceTo(centre) > radius * 0.98, "the chords stay near the circle");
        assertEquals(centre, orbit.at(0.0).target());
        assertEquals(centre, orbit.at(60.0).target());
        assertEquals(orbit.at(0.0).eye().y(), orbit.at(50.0).eye().y(), eps, "the orbit keeps one height");
        assertTrue(orbit.at(0.0).eye().z() > centre.z(), "the orbit opens on the viewer's side");
        assertEquals(orbit.at(0.0).eye().x(), orbit.at(80.0).eye().x(), 1.0e-6, "the circle closes");
        assertEquals(orbit.at(0.0).eye().z(), orbit.at(80.0).eye().z(), 1.0e-6, "the circle closes");
    }

    @Test
    void aStageShotFramesItsOwnBoundsDeterministically() {
        Cuboid stage = Cuboid.ofSize(0, 100, 0, 2, 1, 2);
        Vec3 approach = new Vec3(-1, 0, 0);
        CameraShot first = CinematicPrograms.stageShot(stage, approach);
        CameraShot second = CinematicPrograms.stageShot(stage, approach);
        assertEquals(first, second);
        assertEquals(new Vec3(1.0, 100.5, 1.0), first.target());
        assertTrue(first.eye().x < 1.0, "the eye sits on the approach side");
    }

    @Test
    void aFocusFlightMovesFromTheCurrentShotToTheStageShot() {
        CameraShot from = new CameraShot(new Vec3(20, 130, 20), new Vec3(0, 100, 0));
        Cuboid stage = Cuboid.ofSize(0, 100, 0, 3, 2, 3);
        Vec3 approach = new Vec3(0, 0, 1);
        CameraProgram focus = CinematicPrograms.focus(from, stage, approach);
        CameraShot target = CinematicPrograms.stageShot(stage, approach);
        assertEquals(12, focus.totalTicks());
        assertEquals(from, focus.at(0.0));
        assertEquals(target, focus.at(12.0));
        assertEquals(target, focus.at(500.0));
        double previous = distance(focus.at(0.0), target);
        for (double tick = 1.0; tick <= 12.0; tick += 1.0) {
            double distance = distance(focus.at(tick), target);
            assertTrue(distance <= previous + eps, "the flight only closes in at " + tick);
            previous = distance;
        }
    }

    @Test
    void theClosingPullBackStandsFartherThanTheFramingShot() {
        CameraShot from = new CameraShot(new Vec3(0, 120, 10), new Vec3(2, 101, 2));
        Cuboid bounds = Cuboid.ofSize(0, 100, 0, 4, 3, 4);
        Vec3 approach = new Vec3(0, 0, 1);
        CameraProgram closing = CinematicPrograms.closing(from, bounds, approach);
        Vec3 centre = new Vec3(2.0, 101.5, 2.0);
        CameraShot fitted = CinematicPrograms.stageShot(bounds, approach);
        assertEquals(20, closing.totalTicks());
        assertEquals(from, closing.at(0.0));
        assertTrue(
            closing.at(20.0).eye().distanceTo(centre) > fitted.eye().distanceTo(centre),
            "the closing leg pulls back"
        );
        assertTrue(closing.at(20.0).eye().y() > fitted.eye().y(), "and lifts");
    }

    @Test
    void aHullIsTheUnionOfItsCellsOrTheFallback() {
        Cuboid fallback = Cuboid.ofSize(4, 100, 4, 2, 2, 2);
        assertEquals(fallback, CinematicPrograms.hull(List.of(), fallback));
        Cuboid hull = CinematicPrograms.hull(List.of(LatticePoint.of(0, 100, 2), LatticePoint.of(3, 102, 0)), fallback);
        assertEquals(Cuboid.of(0, 100, 0, 4, 103, 3), hull);
    }

    private static double distance(CameraShot shot, CameraShot target) {
        return shot.eye().distanceTo(target.eye()) + shot.target().distanceTo(target.target());
    }
}
