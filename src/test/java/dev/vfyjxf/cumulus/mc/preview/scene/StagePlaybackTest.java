package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The staged playback of design 17 §7: the two presentations, the four treatments and the cadence. */
class StagePlaybackTest {

    private static final float eps = 1.0e-5F;
    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");
    private static final NamespacedId base = NamespacedId.of("structurecumulus", "base");
    private static final NamespacedId stack = NamespacedId.of("structurecumulus", "stack");
    private static final StagePlayback.Cadence normal = StagePlayback.Cadence.of(1.0D, true);

    private static Stage stage(String name, int cells, NamespacedId bay) {
        return new Stage(name, cells, 0, null, null, bay);
    }

    private static GhostCell ghost(int x, int y, int z, int stage) {
        return new GhostCell(LatticePoint.of(x, y, z), stone, Map.of(), stage);
    }

    private static StagePlayback page() {
        List<Stage> stages = List.of(stage("base", 3, base), stage("stack", 2, stack));
        List<GhostCell> cells = List
                .of(ghost(0, 0, 0, 0), ghost(1, 0, 0, 0), ghost(2, 0, 0, 0), ghost(1, 1, 0, 1), ghost(1, 2, 0, 1));
        return StagePlayback.whole(stages, cells);
    }

    private static StagePlayback strip(int cursor, int revealed, boolean playing) {
        StagePlayback playback = page();
        List<Integer> counts = new ArrayList<>();
        counts.add(revealed);
        counts.add(0);
        return new StagePlayback(playback.stages(), playback.cells(), cursor, counts, playing, 0.0F, 0.0F);
    }

    @Test
    void theWholeViewDrawsEveryStageAtFullAlpha() {
        StagePlayback playback = page();
        assertTrue(playback.wholeView());
        assertEquals(-1, playback.cursor());
        assertFalse(playback.playing());
        for (StagePlayback.Placed cell : playback.cells()) {
            assertTrue(playback.drawn(cell));
            assertTrue(playback.revealed(cell));
            assertEquals(StagePlayback.currentAlpha, playback.alpha(cell), eps);
        }
        assertEquals(3, playback.cellCount(0));
        assertEquals(2, playback.cellCount(1));
        assertEquals(3, playback.stageCells(0).size());
    }

    @Test
    void aStageJumpShowsThatStageWholeAndPauses() {
        StagePlayback playback = strip(0, 3, true).enter(1);
        assertEquals(1, playback.cursor());
        assertEquals(2, playback.revealed(1));
        assertFalse(playback.playing());
        assertEquals(StagePlayback.doneAlpha, playback.alpha(playback.cells().get(0)), eps);
        assertEquals(StagePlayback.currentAlpha, playback.alpha(playback.cells().get(3)), eps);
        assertEquals(-1, page().enter(9).cursor(), "an out-of-table jump is a no-op on the cursor");
    }

    @Test
    void theDeltaTreatmentsFollowTheCursor() {
        StagePlayback playback = strip(0, 0, false);
        assertEquals(0.0F, playback.alpha(playback.cells().get(0)), eps, "an unrevealed cell is not drawn");
        assertFalse(playback.drawn(playback.cells().get(3)), "a future stage is hidden");
        assertEquals(0.0F, playback.alpha(playback.cells().get(3)), eps);

        StagePlayback stepped = playback.step();
        assertEquals(StagePlayback.currentAlpha, stepped.alpha(stepped.cells().get(0)), eps);
        assertEquals(0.0F, stepped.alpha(stepped.cells().get(1)), eps, "the rest of the stage is still empty");

        StagePlayback atTheNextStage = stepped.next();
        assertEquals(1, atTheNextStage.cursor());
        assertEquals(StagePlayback.doneAlpha, atTheNextStage.alpha(atTheNextStage.cells().get(0)), eps);
        assertEquals(StagePlayback.currentAlpha, atTheNextStage.alpha(atTheNextStage.cells().get(3)), eps);
        assertEquals(1, atTheNextStage.revealed(0), "a jump keeps the previous stage's own reveal count");
    }

    @Test
    void theSpanIsClampedFlooredAndSpeedDivided() {
        assertEquals(6, pageOf(4).revealSpan(0, normal), "a four-cell stage takes the floor");
        assertEquals(12, pageOf(12).revealSpan(0, normal), "twelve cells dwell twelve ticks");
        assertEquals(40, pageOf(400).revealSpan(0, normal), "a huge stage takes the ceiling");
        assertEquals(6, pageOf(12).revealSpan(0, StagePlayback.Cadence.of(2.0D, true)), "speed divides the span");
        assertEquals(20, pageOf(400).revealSpan(0, StagePlayback.Cadence.of(2.0D, true)));
        assertEquals(0, pageOf(4).revealSpan(0, StagePlayback.Cadence.of(1.0D, false)), "no animation, no span");
        assertEquals(5, pageOf(4).pauseSpan(normal), "the pause is the cadence's own");
        assertEquals(3, pageOf(4).pauseSpan(StagePlayback.Cadence.of(2.0D, true)));
        assertEquals(0, pageOf(4).pauseSpan(StagePlayback.Cadence.of(1.0D, false)));
    }

    private static StagePlayback pageOf(int cells) {
        List<GhostCell> ghosts = new ArrayList<>(cells);
        for (int index = 0; index < cells; index++) {
            ghosts.add(ghost(index, 0, 0, 0));
        }
        return StagePlayback.whole(List.of(stage("one", cells, base)), ghosts);
    }

    @Test
    void aPlayRevealsItsStageAsOneBatch() {
        StagePlayback opened = page().togglePlay(normal);
        assertEquals(0, opened.revealed(0), "the play opens with no cell revealed yet");
        StagePlayback firstFrame = opened.advance(1.0F, normal);
        assertEquals(3, firstFrame.revealed(0), "the tick the stage begins, its whole batch is revealed");
        assertEquals(0.0F, firstFrame.pause(), eps, "the batch opens the dwell, not the pause");
        assertEquals(0, firstFrame.cursor());
        assertTrue(firstFrame.playing());
        for (StagePlayback.Placed cell : firstFrame.cells()) {
            if (cell.stage() == 0) {
                assertEquals(StagePlayback.currentAlpha, firstFrame.alpha(cell), eps, "every cell lands at once");
            }
        }
    }

    @Test
    void theBatchDwellsThenPausesThenMovesOn() {
        StagePlayback playback = page().togglePlay(normal).advance(1.0F, normal);
        assertEquals(3, playback.revealed(0));
        StagePlayback dwell = playback.advance(4.0F, normal);
        assertEquals(0, dwell.cursor(), "the stage still holds its dwell");
        assertEquals(0.0F, dwell.pause(), eps);
        StagePlayback paused = dwell.advance(1.0F, normal);
        assertEquals(0, paused.cursor(), "the dwell is over; the pause holds the cursor");
        assertEquals(6.0F, paused.elapsed(), eps, "the dwell stops at its span and the hold begins");
        StagePlayback held = paused.advance(4.0F, normal);
        assertEquals(0, held.cursor());
        assertEquals(4.0F, held.pause(), eps);
        StagePlayback next = held.advance(1.0F, normal);
        assertEquals(1, next.cursor(), "the pause's end moves the cursor on");
        assertEquals(2, next.revealed(1), "the next stage arrives whole");
    }

    @Test
    void aPlayWalksTheTableAndStopsWithEverythingRevealed() {
        StagePlayback playback = page().togglePlay(normal);
        assertEquals(0, playback.cursor());
        assertTrue(playback.playing());
        assertEquals(0, playback.revealed(0));

        boolean heldPause = false;
        for (int tick = 0; tick < 400 && playback.playing(); tick++) {
            float before = playback.revealed(playback.cursor());
            StagePlayback next = playback.advance(1.0F, normal);
            if (next.cursor() != playback.cursor()) {
                heldPause = true;
                assertEquals(
                    next.cellCount(next.cursor()),
                    next.revealed(next.cursor()),
                    "a stage that begins is revealed whole, never cell by cell"
                );
            } else {
                assertTrue(next.revealed(playback.cursor()) >= before, "the reveal count never moves back");
            }
            playback = next;
        }
        assertFalse(playback.playing(), "the play stops at the table's end");
        assertTrue(heldPause, "the play holds its pause before the next stage");
        assertEquals(1, playback.cursor());
        assertEquals(3, playback.revealed(0));
        assertEquals(2, playback.revealed(1));
    }

    @Test
    void theDwellIsNotTheCellDrip() {
        StagePlayback playback = page().togglePlay(normal).advance(1.0F, normal).advance(4.0F, normal);
        assertEquals(3, playback.revealed(0), "four ticks into the dwell the stage is still whole");
        assertEquals(0, playback.cursor());
    }

    @Test
    void aPlayWithAnimationOffRevealsEverythingAtOnce() {
        StagePlayback playback = page().togglePlay(StagePlayback.Cadence.of(1.0D, false));
        assertFalse(playback.playing());
        assertEquals(0, playback.cursor());
        assertEquals(3, playback.revealed(0));
        assertEquals(2, playback.revealed(1));
    }

    @Test
    void aStepRevealsOneCellThenTheNextStagesFirst() {
        StagePlayback playback = page().step();
        assertEquals(0, playback.cursor(), "a step enters the staged view at the first stage");
        assertEquals(1, playback.revealed(0));
        playback = playback.step().step();
        assertEquals(3, playback.revealed(0));
        playback = playback.step();
        assertEquals(1, playback.cursor());
        assertEquals(1, playback.revealed(1));
        playback = playback.step().step();
        assertEquals(2, playback.revealed(1));
        assertEquals(1, playback.cursor(), "a step past the table's end stays on the last stage");
    }

    @Test
    void theArrowsShowAStageWholeAndPauseThePlay() {
        StagePlayback playing = page().togglePlay(normal);
        StagePlayback next = playing.next();
        assertFalse(next.playing());
        assertEquals(1, next.cursor());
        assertEquals(2, next.revealed(1));
        assertEquals(0, next.previous().cursor());
        assertEquals(0, page().previous().cursor(), "previous from the whole view enters the first stage");
    }

    @Test
    void allReturnsToTheWholeViewAndStops() {
        StagePlayback playback = page().togglePlay(normal).advance(6.0F, normal);
        StagePlayback whole = playback.all();
        assertTrue(whole.wholeView());
        assertFalse(whole.playing());
        for (StagePlayback.Placed cell : whole.cells()) {
            assertEquals(StagePlayback.currentAlpha, whole.alpha(cell), eps);
        }
    }

    @Test
    void aStoppedPlayAdvancesNothing() {
        StagePlayback playback = page().enter(0);
        assertEquals(playback, playback.advance(20.0F, normal));
        assertEquals(playback, playback.advance(-1.0F, normal));
    }

    @Test
    void theSamePageAndSecondsGiveTheSameState() {
        StagePlayback first = page().togglePlay(normal).advance(3.0F, normal).advance(4.0F, normal);
        StagePlayback second = page().togglePlay(normal).advance(7.0F, normal);
        assertEquals(second, first, "the same seconds in either slice give the same frame");
    }
}
