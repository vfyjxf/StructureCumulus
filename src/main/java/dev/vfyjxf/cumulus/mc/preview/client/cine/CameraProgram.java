package dev.vfyjxf.cumulus.mc.preview.client.cine;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

/**
 * An ordered camera program: dwell on each leg's shot, then fly to the next leg with that leg's
 * move span and easing. Sampling is a pure function of the continuous tick, so a program replays
 * identically every time.
 */
public record CameraProgram(List<Leg> legs) {

    /** One stop: hold {@code shot} for {@code dwellTicks}, reached from the previous shot over {@code moveTicks}. */
    public record Leg(CameraShot shot, int dwellTicks, int moveTicks, Ease moveEase) {

        public Leg {
            Objects.requireNonNull(shot, "shot");
            if (dwellTicks < 0 || moveTicks < 0) {
                throw new IllegalArgumentException("negative span: dwell " + dwellTicks + ", move " + moveTicks);
            }
            moveEase = Objects.requireNonNull(moveEase, "moveEase");
        }
    }

    public CameraProgram {
        legs = List.copyOf(legs);
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("a program has at least one leg");
        }
    }

    public int totalTicks() {
        int total = 0;
        for (int i = 0; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            total += leg.dwellTicks() + (i + 1 < legs.size() ? legs.get(i + 1).moveTicks() : 0);
        }
        return total;
    }

    /** The shot at {@code tick}; past the end the program holds its final shot. */
    public CameraShot at(double tick) {
        double t = Math.max(0.0, tick);
        CameraShot previous = legs.get(0).shot();
        for (int i = 0; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            if (t < leg.dwellTicks()) {
                return leg.shot();
            }
            t -= leg.dwellTicks();
            if (i + 1 < legs.size()) {
                Leg next = legs.get(i + 1);
                if (t < next.moveTicks()) {
                    double progress = next.moveEase().applyClamped(t / next.moveTicks());
                    return lerp(leg.shot(), next.shot(), progress);
                }
                t -= next.moveTicks();
            }
            previous = leg.shot();
        }
        return previous;
    }

    private static CameraShot lerp(CameraShot from, CameraShot to, double t) {
        return new CameraShot(lerp(from.eye(), to.eye(), t), lerp(from.target(), to.target(), t));
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
