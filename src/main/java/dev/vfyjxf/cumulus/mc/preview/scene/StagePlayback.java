package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * The staged playback of one page (design 17 §7): no cursor is the whole view, a cursor is the
 * staged view, and the reveal counts are what a play, a step and a jump move. A play reveals a stage
 * as one batch and the span is that stage's dwell (§7.5); only a manual step drips one cell.
 */
public record StagePlayback(
    List<Stage> stages,
    List<Placed> cells,
    int cursor,
    List<Integer> revealed,
    boolean playing,
    float elapsed,
    float pause
) {

    /** The done factor of design 17 §7.2. */
    public static final float doneAlpha = 0.25F;
    /** The cursor's revealed factor of design 17 §7.2. */
    public static final float currentAlpha = 1.0F;
    public static final int cellRevealTicks = 2;
    public static final int stageRevealMinTicks = 10;
    public static final int stageRevealMaxTicks = 100;
    public static final int stagePauseTicks = 10;

    /** One page cell with the facts the treatments read: its stage and its position inside it. */
    public record Placed(GhostCell ghost, int stage, int position) {}

    /** The two switches that divide every span (design 16 §13). */
    public record Cadence(double speed, boolean enabled) {

        public static Cadence of(double speed, boolean enabled) {
            return new Cadence(speed <= 0.0D ? 1.0D : speed, enabled);
        }
    }

    public StagePlayback {
        stages = List.copyOf(stages);
        cells = List.copyOf(cells);
        revealed = List.copyOf(revealed);
        if (revealed.size() != stages.size()) {
            throw new IllegalArgumentException("one reveal count per stage");
        }
        if (cursor >= stages.size()) {
            throw new IllegalArgumentException("cursor names a stage: " + cursor);
        }
    }

    /** The opening state and every return to it: every stage drawn, no cursor, no play. */
    public static StagePlayback whole(List<Stage> stages, List<GhostCell> pageCells) {
        int[] positions = new int[stages.size()];
        List<Placed> placed = new ArrayList<>(pageCells.size());
        for (GhostCell ghost : pageCells) {
            int stage = ghost.stageIndex();
            if (stage < 0 || stage >= stages.size()) {
                continue;
            }
            placed.add(new Placed(ghost, stage, positions[stage]++));
        }
        List<Integer> counts = new ArrayList<>(stages.size());
        for (int stage = 0; stage < stages.size(); stage++) {
            counts.add(0);
        }
        return new StagePlayback(stages, placed, -1, counts, false, 0.0F, 0.0F);
    }

    public boolean wholeView() {
        return cursor < 0;
    }

    public int stageCount() {
        return stages.size();
    }

    /** How many page cells the stage carries: the reveal set and the stage's own position order. */
    public int cellCount(int stage) {
        int count = 0;
        for (Placed cell : cells) {
            if (cell.stage() == stage) {
                count++;
            }
        }
        return count;
    }

    /** The stage's cells in the wire order the payload emitted. */
    public List<GhostCell> stageCells(int stage) {
        List<GhostCell> stageCells = new ArrayList<>();
        for (Placed cell : cells) {
            if (cell.stage() == stage) {
                stageCells.add(cell.ghost());
            }
        }
        return List.copyOf(stageCells);
    }

    public int revealed(int stage) {
        return stage < 0 || stage >= revealed.size() ? 0 : revealed.get(stage);
    }

    /** True when the cell's stage and its position inside it reveal the cell (design 17 §7.2). */
    public boolean revealed(Placed cell) {
        if (cursor < 0 || cell.stage() < cursor) {
            return true;
        }
        if (cell.stage() > cursor) {
            return false;
        }
        return cell.position() < revealed(cell.stage());
    }

    /** True when the stage treatment draws the cell at all: a future stage is hidden (design 17 §7.2). */
    public boolean drawn(Placed cell) {
        return cursor < 0 || cell.stage() <= cursor;
    }

    /**
     * The cell's alpha factor over the scene's base alpha (design 17 §7.2). An unrevealed cell is zero:
     * the scene is asked for no model, so the pop-in fills an empty slot.
     */
    public float alpha(Placed cell) {
        if (cursor < 0) {
            return currentAlpha;
        }
        if (cell.stage() < cursor) {
            return doneAlpha;
        }
        if (cell.stage() > cursor) {
            return 0.0F;
        }
        return revealed(cell) ? currentAlpha : 0.0F;
    }

    /** `all`: drops the cursor, draws every stage again and stops the play (design 17 §7.1). */
    public StagePlayback all() {
        return whole(stages, ghosts());
    }

    /** A stage jump, an arrow and a bar click: that stage whole, paused (design 17 §7.3). */
    public StagePlayback enter(int stage) {
        if (stage < 0 || stage >= stages.size()) {
            return this;
        }
        return new StagePlayback(stages, cells, stage, revealOnly(stage, cellCount(stage)), false, 0.0F, 0.0F);
    }

    public StagePlayback previous() {
        return cursor < 0 ? enter(0) : enter(Math.max(0, cursor - 1));
    }

    public StagePlayback next() {
        return cursor < 0 ? enter(0) : enter(Math.min(stages.size() - 1, cursor + 1));
    }

    /** `Space`: from the whole view the first stage and a play, otherwise the running play toggles. */
    public StagePlayback togglePlay(Cadence cadence) {
        if (stages.isEmpty()) {
            return this;
        }
        if (playing) {
            return withPlaying(false);
        }
        if (!cadence.enabled()) {
            return enter(cursor < 0 ? 0 : cursor).everythingRevealed();
        }
        int stage = cursor < 0 ? 0 : cursor;
        List<Integer> counts = cursor < 0 ? zeroCounts() : revealed;
        return new StagePlayback(stages, cells, stage, counts, true, 0.0F, 0.0F);
    }

    /** `B`: reveals one more cell of the cursor's stage, then the next stage's first (design 17 §7.3). */
    public StagePlayback step() {
        if (stages.isEmpty()) {
            return this;
        }
        int stage = cursor < 0 ? 0 : cursor;
        if (revealed(stage) < cellCount(stage)) {
            return new StagePlayback(
                stages,
                cells,
                stage,
                revealOnly(stage, revealed(stage) + 1),
                playing,
                elapsed,
                pause
            );
        }
        if (stage + 1 >= stages.size()) {
            return new StagePlayback(
                stages,
                cells,
                stage,
                revealOnly(stage, cellCount(stage)),
                playing,
                elapsed,
                pause
            );
        }
        int next = stage + 1;
        return new StagePlayback(
            stages,
            cells,
            next,
            revealOnly(next, Math.min(1, cellCount(next))),
            playing,
            0.0F,
            0.0F
        );
    }

    /**
     * One frame of a running play; a stopped play, the whole view and no delta change nothing. The
     * tick a stage begins, its whole batch is revealed, then the stage dwells for its span, holds the
     * pause and the cursor moves on (design 17 §7.5).
     */
    public StagePlayback advance(float deltaTicks, Cadence cadence) {
        if (!playing || cursor < 0 || stages.isEmpty() || !(deltaTicks > 0.0F)) {
            return this;
        }
        if (!cadence.enabled()) {
            return everythingRevealed();
        }
        int count = cellCount(cursor);
        List<Integer> counts = revealed;
        float elapsedNow = elapsed;
        float pauseNow = pause;
        if (revealed(cursor) < count) {
            counts = revealOnly(cursor, count);
            elapsedNow = 0.0F;
            pauseNow = 0.0F;
        }
        elapsedNow += deltaTicks;
        int dwell = revealSpan(cursor, cadence);
        if (elapsedNow < dwell) {
            return new StagePlayback(stages, cells, cursor, counts, true, elapsedNow, 0.0F);
        }
        float hold = pauseNow + (elapsedNow - dwell);
        if (hold < pauseSpan(cadence)) {
            return new StagePlayback(stages, cells, cursor, counts, true, dwell, hold);
        }
        if (cursor + 1 >= stages.size()) {
            return everythingRevealed();
        }
        int next = cursor + 1;
        return new StagePlayback(stages, cells, next, revealOnly(next, cellCount(next)), true, 0.0F, 0.0F);
    }

    /** The stage's dwell: the span one stage is held over once its batch has popped (design 17 §7.5). */
    public int revealSpan(int stage, Cadence cadence) {
        if (!cadence.enabled()) {
            return 0;
        }
        int raw = Math.max(stageRevealMinTicks, Math.min(stageRevealMaxTicks, cellCount(stage) * cellRevealTicks));
        return Math.max(1, (int) Math.ceil(raw / cadence.speed()));
    }

    /** The hold between a finished stage and the next one, speed divided (design 17 §7.5). */
    public int pauseSpan(Cadence cadence) {
        if (!cadence.enabled()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(stagePauseTicks / cadence.speed()));
    }

    private StagePlayback everythingRevealed() {
        List<Integer> counts = new ArrayList<>(stages.size());
        for (int stage = 0; stage < stages.size(); stage++) {
            counts.add(cellCount(stage));
        }
        return new StagePlayback(stages, cells, cursor < 0 ? 0 : cursor, counts, false, elapsed, 0.0F);
    }

    private StagePlayback withPlaying(boolean running) {
        return new StagePlayback(stages, cells, cursor, revealed, running, elapsed, 0.0F);
    }

    private List<Integer> revealOnly(int stage, int value) {
        List<Integer> counts = new ArrayList<>(revealed);
        counts.set(stage, Math.max(0, Math.min(cellCount(stage), value)));
        return counts;
    }

    private List<Integer> zeroCounts() {
        List<Integer> counts = new ArrayList<>(stages.size());
        for (int stage = 0; stage < stages.size(); stage++) {
            counts.add(0);
        }
        return counts;
    }

    private List<GhostCell> ghosts() {
        List<GhostCell> ghosts = new ArrayList<>(cells.size());
        for (Placed cell : cells) {
            ghosts.add(cell.ghost());
        }
        return ghosts;
    }
}
