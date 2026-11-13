package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.mc.preview.client.cine.Ease;
import dev.vfyjxf.cumulus.mc.preview.client.cine.Tween;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The staged playback's drop-in (design 17 §7.6): a cell that just became revealed falls from
 * {@link #dropInCells} above its own slot onto the base plate while its alpha fades in, sampled from
 * the screen's clock. A cell's stamp is the tick its reveal happened, so a play drops its stage as one
 * batch and a step drops one cell.
 */
public final class DropIn {

    /** The height a dropping cell falls from, in cells (§7.6). */
    public static final float dropInCells = 2.5F;
    /** One cell's own fall, in ticks, before {@code animation.speed} divides it (§7.5). */
    public static final int dropInTicks = 5;

    private static final DropIn none = new DropIn(Map.of());

    private final Map<GhostCell, Double> revealedAt;

    private DropIn(Map<GhostCell, Double> revealedAt) {
        this.revealedAt = Map.copyOf(revealedAt);
    }

    /** No cell is falling: the whole view, a fresh page and an animation-free client. */
    public static DropIn none() {
        return none;
    }

    /** The fall leg: {@link #dropInCells} down to the slot, fast at the start and dead at the stop. */
    public static Tween fallLeg(int durationTicks) {
        return new Tween(dropInCells, 0.0D, durationTicks, Ease.outCubic);
    }

    /** The alpha leg: 0 to 1, so a cell fades in over the same span. */
    public static Tween alphaLeg(int durationTicks) {
        return new Tween(0.0D, 1.0D, durationTicks, Ease.outQuad);
    }

    /** Stamps every cell revealed at {@code now} that carried no stamp, and forgets a hidden one. */
    public DropIn advance(Set<GhostCell> revealed, double now) {
        Map<GhostCell, Double> next = new HashMap<>(revealed.size());
        for (GhostCell ghost : revealed) {
            Double at = revealedAt.get(ghost);
            next.put(ghost, at == null ? now : at);
        }
        return new DropIn(next);
    }

    /** True while the cell's fall leg is still running at {@code now}. */
    public boolean animating(GhostCell cell, double now, int durationTicks) {
        Double at = revealedAt.get(cell);
        return durationTicks > 0 && at != null && now - at < durationTicks;
    }

    /** The cell's remaining height above its slot at {@code now}; zero once it has landed or never fell. */
    public float drop(GhostCell cell, double now, Tween leg) {
        Double at = revealedAt.get(cell);
        return at == null ? 0.0F : (float) leg.at(now - at);
    }

    /** The cell's alpha multiplier at {@code now}: one when it never fell or the leg is done. */
    public float alpha(GhostCell cell, double now, Tween leg) {
        Double at = revealedAt.get(cell);
        return at == null ? 1.0F : (float) leg.at(now - at);
    }

    /** How many cells still fall at {@code now}; the harness reads this instead of pixels. */
    public int count() {
        return revealedAt.size();
    }
}
