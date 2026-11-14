package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The timeline math of design 19 §3: the lanes in build order, the proportional widths with a legible
 * minimum, the wrap, the height cap and the scroll, the playhead. A pure function, no client.
 */
class StageTimelineTest {

    private static final NamespacedId bay = NamespacedId.of("structurecumulus", "bay");

    private static Stage stage(String name, String group, int cells) {
        return new Stage(name, cells, 0, null, null, bay, group);
    }

    private static StageTimeline.Metrics metrics(int headMin, int headMax, int maxHeight) {
        return new StageTimeline.Metrics(11, 2, 1, 8, headMin, headMax, 10, maxHeight, name -> name.length() * 6);
    }

    @Test
    void theLanesFollowTheBuildOrderAndConsecutiveStagesShareOneLane() {
        StageTimeline timeline = StageTimeline.of(
            List.of(stage("a1", "a", 1), stage("a2", "a", 1), stage("b1", "b", 1), stage("a3", "a", 1)),
            200,
            metrics(0, 0, 52)
        );
        assertEquals(List.of("a", "b", "a"), timeline.lanes().stream().map(StageTimeline.Lane::group).toList());
        assertEquals(List.of(2, 1, 1), timeline.lanes().stream().map(StageTimeline.Lane::stageCount).toList());
        assertEquals(List.of(0, 2, 3), timeline.lanes().stream().map(StageTimeline.Lane::firstStage).toList());
        assertEquals(List.of(0, 1, 2, 3), timeline.segments().stream().map(StageTimeline.Segment::index).toList());
        assertEquals(4, timeline.segments().size(), "a lane is never truncated");
        assertEquals(37, timeline.contentHeight(), "three rows of 11 and the two lane gaps");
    }

    @Test
    void aSegmentsWidthIsProportionalToItsExpectedCellsWithALegibleFloor() {
        StageTimeline timeline = StageTimeline.of(
            List.of(stage("floor", "g", 9), stage("chimney", "g", 3)),
            100,
            new StageTimeline.Metrics(11, 2, 1, 8, 0, 0, 0, 52, name -> 0)
        );
        StageTimeline.Segment floor = timeline.segments().get(0);
        StageTimeline.Segment chimney = timeline.segments().get(1);
        assertEquals(74, floor.width(), "three quarters of the 99 pixels between the gap");
        assertEquals(25, chimney.width());
        assertEquals(75, chimney.x(), "one pixel of air between two segments");
        assertEquals(floor.y(), chimney.y(), "same lane, same row");

        StageTimeline floorless = StageTimeline.of(
            List.of(stage("a", "g", 1), stage("b", "g", 10000)),
            100,
            new StageTimeline.Metrics(11, 2, 1, 8, 0, 0, 0, 52, name -> 0)
        );
        assertEquals(8, floorless.segments().get(0).width(), "a one-cell stage keeps the legible minimum");
        assertTrue(floorless.segments().get(1).width() > 8, "the big stage keeps the room the floor leaves");
    }

    @Test
    void anOverflowingLaneWrapsIndentedInsteadOfShrinkingOrTruncating() {
        List<Stage> stages = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            stages.add(stage("s" + index, "g", 1));
        }
        StageTimeline timeline = StageTimeline
                .of(stages, 40, new StageTimeline.Metrics(11, 2, 1, 8, 0, 0, 10, 52, name -> 0));
        StageTimeline.Lane lane = timeline.lanes().get(0);
        assertTrue(lane.rows() > 1, "the lane wraps");
        assertEquals(20, timeline.segments().size(), "wrapping never drops a stage");
        for (StageTimeline.Segment segment : timeline.segments()) {
            assertTrue(segment.width() >= 8, "a segment never shrinks below the legible minimum");
            assertTrue(segment.x() + segment.width() <= 40, "a segment never leaves the strip");
            if (segment.y() > lane.y()) {
                assertTrue(segment.x() >= 10, "a wrapped row is indented under the head");
            }
        }
        assertEquals(lane.rows() * 11, timeline.contentHeight());
        assertEquals(StageTimeline.timelineMaxHeight, timeline.height(), "the strip grows to the cap");
        assertTrue(timeline.scrollable());
    }

    @Test
    void theStripGrowsToTheCapThenScrollsWithTheCursorInView() {
        List<Stage> stages = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            stages.add(stage("s" + index, "g" + index, 1));
        }
        StageTimeline timeline = StageTimeline.of(stages, 100, metrics(0, 0, 52));
        assertEquals(5 * 11 + 4 * 2, timeline.contentHeight(), "five one-row lanes and their gaps");
        assertEquals(52, timeline.height());
        assertEquals(11, timeline.maxScroll());
        assertEquals(11, timeline.clampScroll(1000));
        assertEquals(0, timeline.clampScroll(-5));
        assertEquals(11, timeline.scrollForVisible(0, 4), "the last lane's segment is scrolled into view");
        assertEquals(0, timeline.scrollForVisible(11, 0), "the first lane's segment scrolls back to the top");
        assertEquals(4, timeline.scrollForVisible(4, 2), "a visible segment does not move the strip");
    }

    @Test
    void aCompactMetricLaneHeightDrivesTheRowsTheHitTestAndTheScroll() {
        StageTimeline.Metrics compact = new StageTimeline.Metrics(6, 1, 1, 4, 0, 0, 4, 20, name -> 0);
        List<Stage> stages = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            stages.add(stage("s" + index, "g" + index, 1));
        }
        StageTimeline timeline = StageTimeline.of(stages, 100, compact);
        assertEquals(4 * 6 + 3, timeline.contentHeight());
        assertEquals(20, timeline.height());
        assertEquals(7, timeline.maxScroll());
        assertEquals(6, timeline.lanes().get(0).rowHeight());
        assertEquals(6, timeline.segments().get(0).height());
        StageTimeline.Segment third = timeline.segments().get(3);
        assertEquals(3 * 7, third.y());
        assertEquals(3, timeline.hit(third.x() + 1, third.y() + 5));
        assertEquals(-1, timeline.hit(third.x() + 1, third.y() + 6), "the row's height is the metric's, not 11");
        assertEquals(7, timeline.scrollForVisible(0, 3));
    }

    @Test
    void aNameIsDrawnOnlyWhereTheTextFits() {
        StageTimeline timeline = StageTimeline.of(
            List.of(stage("a", "g", 1), stage("a name far too long for its segment", "g", 1)),
            30,
            metrics(0, 0, 52)
        );
        assertTrue(timeline.segments().get(0).named());
        assertFalse(timeline.segments().get(1).named(), "a narrow segment names nothing rather than truncating");
    }

    @Test
    void theLaneHeadCarriesTheGroupAndTruncatesToItsColumn() {
        StageTimeline timeline = StageTimeline
                .of(List.of(stage("structurecumulus:base", "structurecumulus:base", 9)), 400, metrics(20, 88, 52));
        StageTimeline.Lane lane = timeline.lanes().get(0);
        assertEquals(88, lane.headWidth(), "a long group is capped, and its tooltip carries the whole name");
        assertEquals(88, timeline.segments().get(0).x(), "the segment starts after the head");
        assertEquals("structurecumulus:base", lane.group());
    }

    @Test
    void aStageWithoutACarriedGroupFallsBackToItsBay() {
        Stage plain = new Stage("layer 1", 9, 0, null, null, bay);
        StageTimeline timeline = StageTimeline.of(List.of(plain), 200, metrics(20, 88, 52));
        assertEquals(bay.text(), timeline.lanes().get(0).group());
        assertEquals("layer 1 \u00B7 " + bay.text() + " \u00B7 9 cells", StageTimeline.hoverText(plain));
    }

    @Test
    void theHoverNamesTheStageItsGroupAndItsCellsWithoutRepeatingAName() {
        assertEquals(
            "root \u00B7 plinth \u00B7 9 cells",
            StageTimeline.hoverText(stage("root \u00B7 plinth", "root", 9)),
            "the name already carries the group"
        );
        assertEquals(
            "base \u00B7 structurecumulus:base \u00B7 9 cells",
            StageTimeline.hoverText(stage("base", "structurecumulus:base", 9))
        );
    }

    @Test
    void thePlayheadStartsStoppedAndRidesTheRevealedShareWhilePlaying() {
        assertEquals(0.0F, StageTimeline.playheadFraction(false, 9, 9), "stopped, the playhead is the start");
        assertEquals(0.0F, StageTimeline.playheadFraction(true, 0, 9), "the hold has revealed nothing");
        assertEquals(1.0F, StageTimeline.playheadFraction(true, 9, 9), "the batch has landed whole");
        StageTimeline timeline = StageTimeline
                .of(List.of(stage("a", "g", 9)), 100, new StageTimeline.Metrics(11, 2, 1, 8, 0, 0, 0, 52, name -> 0));
        assertEquals(0, timeline.playheadX(0, false, 0, 9));
        assertEquals(99, timeline.playheadX(0, true, 9, 9), "the last pixel of the segment, never past it");
        assertEquals(-1, timeline.playheadX(3, true, 0, 1));
    }

    @Test
    void theHitTestNamesTheSegmentUnderAPointAndTheLanesHead() {
        StageTimeline timeline = StageTimeline.of(
            List.of(stage("a", "group", 1), stage("b", "group", 1), stage("c", "other", 1)),
            100,
            metrics(40, 40, 52)
        );
        StageTimeline.Segment first = timeline.segments().get(0);
        assertEquals(0, timeline.hit(first.x() + 1, first.y() + 1));
        assertEquals(-1, timeline.hit(first.x() + first.width(), first.y() + 1), "a gap is no segment");
        StageTimeline.Lane head = Objects.requireNonNull(timeline.headAt(3, 2));
        assertEquals("group", head.group());
        assertNull(timeline.headAt(60, 2), "past the head column no lane answers");
    }

    @Test
    void anEmptyTableOrAnEmptyWidthDrawsNothing() {
        assertTrue(StageTimeline.of(List.of(), 100, metrics(20, 88, 52)).segments().isEmpty());
        assertEquals(0, StageTimeline.of(List.of(), 100, metrics(20, 88, 52)).height());
        StageTimeline flat = StageTimeline.of(List.of(stage("a", "g", 1)), 0, metrics(20, 88, 52));
        assertTrue(flat.segments().isEmpty());
        assertEquals(0, flat.height());
    }

    @Test
    void theSameTableAndMetricsGiveTheSameTimeline() {
        List<Stage> stages = List.of(stage("a", "g", 9), stage("b", "g", 3), stage("c", "h", 2));
        assertEquals(
            StageTimeline.of(stages, 120, metrics(20, 88, 52)),
            StageTimeline.of(stages, 120, metrics(20, 88, 52))
        );
    }
}
