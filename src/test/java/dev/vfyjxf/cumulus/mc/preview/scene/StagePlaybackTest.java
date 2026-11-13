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
    void thePlayOpensOnTheEmptyPageAndTheFirstBatchLandsAfterTheHold() {
        StagePlayback opened = page().togglePlay(normal);
        assertEquals(0, opened.revealed(0), "the play opens with no cell revealed");
        StagePlayback firstFrame = opened.advance(1.0F, normal);
        assertEquals(0, firstFrame.revealed(0), "nothing lands on the play's first frame");
        assertEquals(1.0F, firstFrame.pause(), eps, "the opening hold is the zero state");
        assertEquals(0.0F, firstFrame.elapsed(), eps);
        assertEquals(4.0F, firstFrame.advance(3.0F, normal).pause(), eps);
        StagePlayback landed = firstFrame.advance(4.0F, normal);
        assertEquals(3, landed.revealed(0), "the hold's end lands the whole batch at one tick");
        assertEquals(0.0F, landed.pause(), eps, "the batch opens the dwell, not the pause");
        assertEquals(0.0F, landed.elapsed(), eps);
        assertEquals(0, landed.cursor());
        assertTrue(landed.playing());
        for (StagePlayback.Placed cell : landed.cells()) {
            if (cell.stage() == 0) {
                assertEquals(StagePlayback.currentAlpha, landed.alpha(cell), eps, "every cell lands at once");
            }
        }
    }

    @Test
    void aHoldThatOverrunsSpendsTheRestOfTheFrameInTheDwell() {
        StagePlayback firstFrame = page().togglePlay(normal).advance(1.0F, normal);
        StagePlayback held = firstFrame.advance(6.0F, normal);
        assertEquals(3, held.revealed(0), "the batch is in");
        assertEquals(0.0F, held.pause(), eps);
        assertEquals(2.0F, held.elapsed(), eps, "one tick of hold plus six is five held and two dwelt");
    }

    @Test
    void everyStageIsHeldBeforeItsBatchAndTheLastDwellEndsThePlay() {
        StagePlayback landed = page().togglePlay(normal).advance(5.0F, normal);
        assertEquals(3, landed.revealed(0));
        StagePlayback dwell = landed.advance(5.0F, normal);
        assertEquals(0, dwell.cursor(), "the stage still holds its dwell");
        assertEquals(5.0F, dwell.elapsed(), eps);
        StagePlayback empty = dwell.advance(2.0F, normal);
        assertEquals(1, empty.cursor(), "the dwell's end moves the cursor to the next stage");
        assertEquals(1.0F, empty.pause(), eps, "the dwell's overrun is the next stage's hold");
        assertEquals(0, empty.revealed(1), "the next stage begins empty, like the play's opening");
        StagePlayback landedNext = empty.advance(4.0F, normal);
        assertEquals(2, landedNext.revealed(1), "the next stage's batch lands after its own hold");
        assertEquals(0.0F, landedNext.advance(6.0F, normal).elapsed(), eps);
        assertFalse(landedNext.advance(6.0F, normal).playing(), "the last dwell ends the play, no trailing hold");
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
                assertEquals(0, next.revealed(next.cursor()), "a stage that begins is the empty page");
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
        StagePlayback playback = page().togglePlay(normal).advance(5.0F, normal).advance(4.0F, normal);
        assertEquals(3, playback.revealed(0), "four ticks into the dwell the stage is still whole");
        assertEquals(0, playback.cursor());
        assertEquals(4.0F, playback.elapsed(), eps);
    }

    @Test
    void aResumedPlayDoesNotReRunAHoldThatHasPassed() {
        StagePlayback landed = page().togglePlay(normal).advance(5.0F, normal);
        assertEquals(3, landed.revealed(0));
        StagePlayback paused = landed.togglePlay(normal);
        assertFalse(paused.playing());
        StagePlayback resumed = paused.togglePlay(normal);
        assertEquals(3, resumed.revealed(0), "the batch stays landed across the pause");
        StagePlayback dwell = resumed.advance(1.0F, normal);
        assertEquals(3, dwell.revealed(0), "the resume does not empty the stage");
        assertEquals(1.0F, dwell.elapsed(), eps, "the dwell resumes; no second hold is run");
        assertEquals(0.0F, dwell.pause(), eps);
    }

    @Test
    void aManualStepDripsThenThePlayFillsTheBatchWithoutAHold() {
        StagePlayback stepped = page().step();
        assertEquals(1, stepped.revealed(0));
        StagePlayback play = stepped.togglePlay(normal).advance(1.0F, normal);
        assertEquals(3, play.revealed(0), "the step's stage fills its whole batch");
        assertEquals(1.0F, play.elapsed(), eps, "a dripped stage carries no lead-in");
        assertEquals(0.0F, play.pause(), eps);
    }

    @Test
    void aWholePlayTakesExactlyItsHoldsAndDwells() {
        StagePlayback playback = page().togglePlay(normal);
        int total = page().pauseSpan(normal) * page().stageCount()
                + page().revealSpan(0, normal)
                + page().revealSpan(1, normal);
        int ticks = 0;
        while (playback.playing() && ticks < 400) {
            playback = playback.advance(1.0F, normal);
            ticks++;
        }
        assertFalse(playback.playing(), "the play ends at the table's end");
        assertEquals(total, ticks, "the whole play is the sum of its holds and dwells, with no trailing hold");
        assertEquals(3, playback.revealed(0));
        assertEquals(2, playback.revealed(1));
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
