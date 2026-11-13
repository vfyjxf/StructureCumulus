package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.mc.preview.client.cine.Ease;
import dev.vfyjxf.cumulus.mc.preview.client.cine.Tween;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The staged playback's reveal (design 17 §7.6): a cell that just became revealed is placed from a
 * direction — a drop falls {@link #dropCells} onto its slot, a rise climbs out of the base plate's
 * underside — while its alpha fades in, sampled from the screen's clock. A stamp is the tick and the
 * direction its reveal happened, so a play reveals its stage as one batch and a mid-play toggle
 * never re-narrates a cell that has already landed.
 */
public final class Reveal {

    /** The two directions a placement is narrated from (§7.6). */
    public enum Direction {
        drop, rise
    }

    /** The height a dropping cell falls from, in cells (§7.6). */
    public static final float dropCells = 2.5F;
    /** One cell's own reveal leg, in ticks, before {@code animation.speed} divides it (§7.5). */
    public static final int revealTicks = 5;

    private record Stamp(double tick, Direction direction) {}

    private static final Reveal none = new Reveal(Map.of());

    private final Map<GhostCell, Stamp> stamps;

    private Reveal(Map<GhostCell, Stamp> stamps) {
        this.stamps = Map.copyOf(stamps);
    }

    /** Nothing is revealing: the whole view, a fresh page and an animation-free client. */
    public static Reveal none() {
        return none;
    }

    /** The alpha leg: 0 to 1 over the same span as the offset, so a cell fades in as it travels. */
    public static Tween alphaLeg(int durationTicks) {
        return new Tween(0.0D, 1.0D, durationTicks, Ease.outQuad);
    }

    /** Stamps every cell revealed at {@code now} that carries no stamp, and forgets a hidden one. */
    public Reveal advance(Set<GhostCell> revealed, double now, Direction direction) {
        Map<GhostCell, Stamp> next = new HashMap<>(revealed.size());
        for (GhostCell ghost : revealed) {
            Stamp stamp = stamps.get(ghost);
            next.put(ghost, stamp == null ? new Stamp(now, direction) : stamp);
        }
        return new Reveal(next);
    }

    /** The direction a cell's reveal was stamped with, or null when it carries no stamp. */
    @Nullable
    Direction directionOf(GhostCell cell) {
        Stamp stamp = stamps.get(cell);
        return stamp == null ? null : stamp.direction();
    }

    /** True while the cell's reveal leg is still running at {@code now}. */
    public boolean animating(GhostCell cell, double now, int durationTicks) {
        Stamp stamp = stamps.get(cell);
        return durationTicks > 0 && stamp != null && now - stamp.tick() < durationTicks;
    }

    /**
     * The cell's offset above its slot (drop) or below it (rise) at {@code now}; zero once the leg is
     * done or the cell never revealed. The rise's start is {@code plateBottom} minus the cell's own
     * height, so a ground-floor cell travels the platform's thickness and a high one climbs further.
     */
    public float offset(GhostCell cell, double now, int durationTicks, float plateBottom) {
        Stamp stamp = stamps.get(cell);
        if (stamp == null || now - stamp.tick() >= durationTicks) {
            return 0.0F;
        }
        float start = stamp.direction() == Direction.drop ? dropCells : plateBottom - cell.cell().y();
        return (float) new Tween(start, 0.0D, durationTicks, Ease.outCubic).at(now - stamp.tick());
    }

    /** The cell's alpha multiplier at {@code now}: one when it never revealed or the leg is done. */
    public float alpha(GhostCell cell, double now, int durationTicks) {
        Stamp stamp = stamps.get(cell);
        if (stamp == null || now - stamp.tick() >= durationTicks) {
            return 1.0F;
        }
        return (float) alphaLeg(durationTicks).at(now - stamp.tick());
    }

    /** How many cells carry a stamp; the harness reads this instead of pixels. */
    public int count() {
        return stamps.size();
    }
}
