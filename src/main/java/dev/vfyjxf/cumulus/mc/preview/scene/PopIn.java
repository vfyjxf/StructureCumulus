package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.mc.preview.client.cine.Ease;
import dev.vfyjxf.cumulus.mc.preview.client.cine.Tween;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The staged playback's pop-in (design 17 §7.6): a cell that just became revealed grows from
 * {@link #startScale} to 1.0 about its own centre while its alpha fades in, sampled from the screen's
 * clock. A cell's stamp is the tick its reveal happened, so a play staggers the pops by its own reveal
 * cadence and a stage jump pops the stage at once.
 */
public final class PopIn {

    /** The scale a popping cell starts at; the world's E2 pop-in starts at 0.5 (design 16 §9.2). */
    public static final float startScale = 0.55F;
    /** One cell's own pop, in ticks, before {@code animation.speed} divides it. */
    public static final int popInTicks = 4;

    private static final PopIn none = new PopIn(Map.of());

    private final Map<GhostCell, Double> revealedAt;

    private PopIn(Map<GhostCell, Double> revealedAt) {
        this.revealedAt = Map.copyOf(revealedAt);
    }

    /** No cell is popping: the whole view, a fresh page and an animation-free client. */
    public static PopIn none() {
        return none;
    }

    /** The scale leg: {@link #startScale} to 1.0 with the pack's pop ease. */
    public static Tween scaleLeg(int durationTicks) {
        return new Tween(startScale, 1.0D, durationTicks, Ease.outBack);
    }

    /** The alpha leg: 0 to 1, so a cell fades in over the same span. */
    public static Tween alphaLeg(int durationTicks) {
        return new Tween(0.0D, 1.0D, durationTicks, Ease.outQuad);
    }

    /** Stamps every cell revealed at {@code now} that carried no stamp, and forgets a hidden one. */
    public PopIn advance(Set<GhostCell> revealed, double now) {
        Map<GhostCell, Double> next = new HashMap<>(revealed.size());
        for (GhostCell ghost : revealed) {
            Double at = revealedAt.get(ghost);
            next.put(ghost, at == null ? now : at);
        }
        return new PopIn(next);
    }

    /** True while the cell's pop leg is still running at {@code now}. */
    public boolean animating(GhostCell cell, double now, int durationTicks) {
        Double at = revealedAt.get(cell);
        return durationTicks > 0 && at != null && now - at < durationTicks;
    }

    /** The cell's scale at {@code now}: the full-size model when it never popped or the leg is done. */
    public float scale(GhostCell cell, double now, Tween leg) {
        Double at = revealedAt.get(cell);
        return at == null ? 1.0F : (float) leg.at(now - at);
    }

    /** The cell's alpha multiplier at {@code now}: one when it never popped or the leg is done. */
    public float alpha(GhostCell cell, double now, Tween leg) {
        Double at = revealedAt.get(cell);
        return at == null ? 1.0F : (float) leg.at(now - at);
    }

    /** How many cells still pop at {@code now}; the harness reads this instead of pixels. */
    public int count() {
        return revealedAt.size();
    }
}
