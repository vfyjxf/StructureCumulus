package dev.vfyjxf.cumulus.mc.preview.client.cine;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The camera's program law: an opening orbit, one framing flight per stage and a closing pull-back,
 * each derived from the geometry in the payload and the side the viewer was standing on. Every
 * builder is pure, so two calls with the same arguments produce equal programs and a program
 * samples identically every time (design 16 §6.7).
 */
public final class CinematicPrograms {

    /** The framing law's vertical fov: the shipped default, so a program is a function of the payload alone. */
    static final double framingFovDeg = 70.0D;
    /** The opening orbit's sixteen 22.5-degree steps; the seventeenth shot closes the circle on the first. */
    static final int orbitSteps = 16;
    static final int orbitStepTicks = 5;
    /** The flight a stage focus takes: the design's 12-tick stage transition. */
    static final int stageMoveTicks = 12;
    static final int closingMoveTicks = 20;
    private static final double orbitElevation = 0.35D;
    private static final double closingPullBack = 1.8D;
    private static final double closingElevation = 0.9D;

    private CinematicPrograms() {}

    /** The subject's centre: the midpoint of its half-open box, the point every shot looks at. */
    public static Vec3 centre(Cuboid bounds) {
        Objects.requireNonNull(bounds, "bounds");
        return CameraShot.center(bounds);
    }

    /** The opening orbit: one full turn around the subject at its fit distance, closing on its start. */
    public static CameraProgram opening(Cuboid bounds, Vec3 approach) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(approach, "approach");
        Vec3 centre = CameraShot.center(bounds);
        double radius = CameraShot.framing(bounds, approach, framingFovDeg).eye().distanceTo(centre);
        double height = centre.y() + (bounds.max().y() - bounds.min().y()) * orbitElevation;
        Vec3 side = flat(approach);
        List<CameraProgram.Leg> legs = new ArrayList<>(orbitSteps + 1);
        for (int step = 0; step <= orbitSteps; step++) {
            double angle = Math.toRadians(360.0D * step / orbitSteps);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 direction = new Vec3(side.x * cos - side.z * sin, 0.0D, side.x * sin + side.z * cos);
            Vec3 eye = new Vec3(centre.x + direction.x * radius, height, centre.z + direction.z * radius);
            legs.add(
                new CameraProgram.Leg(new CameraShot(eye, centre), 0, step == 0 ? 0 : orbitStepTicks, Ease.linear)
            );
        }
        return new CameraProgram(legs);
    }

    /** The one framing shot of a stage: its own fit-to-view eye, looking at its centre. */
    public static CameraShot stageShot(Cuboid bounds, Vec3 approach) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(approach, "approach");
        return CameraShot.framing(bounds, approach, framingFovDeg);
    }

    /** The flight from wherever the camera is to one stage's framing shot: 12 ticks, in and out. */
    public static CameraProgram focus(CameraShot from, Cuboid bounds, Vec3 approach) {
        Objects.requireNonNull(from, "from");
        CameraShot target = stageShot(bounds, approach);
        return new CameraProgram(
            List.of(
                new CameraProgram.Leg(from, 0, 0, Ease.linear),
                new CameraProgram.Leg(target, 0, stageMoveTicks, Ease.inOutCubic)
            )
        );
    }

    /** The closing pull-back: the whole subject from farther away and higher, reached in 20 ticks. */
    public static CameraProgram closing(CameraShot from, Cuboid bounds, Vec3 approach) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(approach, "approach");
        Vec3 centre = CameraShot.center(bounds);
        CameraShot fitted = CameraShot.framing(bounds, approach, framingFovDeg);
        Vec3 side = flat(approach);
        double distance = fitted.eye().distanceTo(centre) * closingPullBack;
        double height = centre.y() + (bounds.max().y() - bounds.min().y()) * closingElevation;
        Vec3 eye = new Vec3(centre.x + side.x * distance, height, centre.z + side.z * distance);
        return new CameraProgram(
            List.of(
                new CameraProgram.Leg(from, 0, 0, Ease.linear),
                new CameraProgram.Leg(new CameraShot(eye, centre), 0, closingMoveTicks, Ease.inOutCubic)
            )
        );
    }

    /**
     * The world cuboid a cell list occupies: the union of its unit cells, or {@code fallback} for an
     * empty list. A stage whose ghosts are all present has no cells, and framing it would mean framing
     * nothing, so the caller's whole-subject box stands in.
     */
    public static Cuboid hull(List<LatticePoint> cells, Cuboid fallback) {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(fallback, "fallback");
        if (cells.isEmpty()) {
            return fallback;
        }
        LatticePoint first = cells.get(0);
        int minX = first.x();
        int minY = first.y();
        int minZ = first.z();
        int maxX = first.x();
        int maxY = first.y();
        int maxZ = first.z();
        for (LatticePoint cell : cells) {
            minX = Math.min(minX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
        }
        return Cuboid.of(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    /** The horizontal approach direction; a viewer standing at the centre falls back to the +Z side. */
    private static Vec3 flat(Vec3 approach) {
        Vec3 flat = new Vec3(approach.x, 0.0D, approach.z);
        return flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
    }
}
