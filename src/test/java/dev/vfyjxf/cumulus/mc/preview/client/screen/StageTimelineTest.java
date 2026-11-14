package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The timeline math of design 19 §3: the lanes in build order, the proportional widths with a legible
 * minimum, the wrap, the height cap and the scroll, the playhead, and the chrome scale a segment reads
 * by. A pure function, no client.
 */
class StageTimelineTest {

    private static final NamespacedId bay = NamespacedId.of("structurecumulus", "bay");

    private static Stage stage(String name, @Nullable String group, int cells) {
        return new Stage(name, cells, 0, null, null, bay, group);
    }

    private static StageTimeline.Metrics metrics(int headMax, int maxHeight) {
        return new StageTimeline.Metrics(
            StageTimeline.laneHeight,
            StageTimeline.laneGap,
            StageTimeline.segmentGap,
            StageTimeline.minSegmentWidth,
            headMax,
            StageTimeline.wrapIndent,
            StageTimeline.nameInset,
            maxHeight,
            name -> name.length() * 6
        );
    }

    private static StageTimeline.Metrics metricless(int headMax, int maxHeight) {
        return new StageTimeline.Metrics(
            StageTimeline.laneHeight,
            StageTimeline.laneGap,
            StageTimeline.segmentGap,
            StageTimeline.minSegmentWidth,
            headMax,
            StageTimeline.wrapIndent,
            StageTimeline.nameInset,
            maxHeight,
            name -> 0
        );
    }

    @Test
    void theLanesFollowTheBuildOrderAndConsecutiveStagesShareOneLane() {
        StageTimeline timeline = StageTimeline.of(
            List.of(stage("a1", "a", 1), stage("a2", "a", 1), stage("b1", "b", 1), stage("a3", "a", 1)),
            200,
            metricless(0, 52)
        );
        assertEquals(List.of("a", "b", "a"), timeline.lanes().stream().map(StageTimeline.Lane::group).toList());
        assertEquals(List.of(2, 1, 1), timeline.lanes().stream().map(StageTimeline.Lane::stageCount).toList());
        assertEquals(List.of(0, 2, 3), timeline.lanes().stream().map(StageTimeline.Lane::firstStage).toList());
        assertEquals(List.of(0, 1, 2, 3), timeline.segments().stream().map(StageTimeline.Segment::index).toList());
        assertEquals(4, timeline.segments().size(), "a lane is never truncated");
        assertEquals(3 * StageTimeline.laneHeight + 2 * StageTimeline.laneGap, timeline.contentHeight());
    }

    @Test
    void aPageThatDoesNotNestIsOneHeadlessLaneAcrossTheWholeStrip() {
        List<Stage> stages = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            stages.add(stage("structurecumulus:part" + index, null, 9));
        }
        StageTimeline timeline = StageTimeline.of(stages, 200, metrics(StageTimeline.headMaxWidth, 52));
        assertEquals(1, timeline.lanes().size(), "a blueprint page is one lane, never one lane per bay");
        StageTimeline.Lane lane = timeline.lanes().get(0);
        assertNull(lane.group(), "a page that does not nest carries no group");
        assertNull(lane.head(), "no group, no head");
        assertEquals(0, lane.headWidth(), "a headless lane gives its whole width to the segments");
        assertEquals(0, timeline.segments().get(0).x(), "the first segment starts at the strip's edge");
        for (StageTimeline.Segment segment : timeline.segments()) {
            assertEquals(0, segment.y(), "one lane, one row: ten stages side by side");
        }
        assertEquals(StageTimeline.laneHeight, timeline.contentHeight());
        assertFalse(timeline.scrollable(), "a single row never scrolls");
    }

    @Test
    void aSetsLanesAreItsMembersEachHoldingItsOwnStages() {
        List<Stage> stages = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            stages.add(stage("structurecumulus:part" + index, "root", 9));
        }
        for (int index = 0; index < 4; index++) {
            stages.add(stage("structurecumulus:b" + index, "minecraft:machine", 3));
        }
        for (int index = 0; index < 3; index++) {
            stages.add(stage("structurecumulus:t" + index, "minecraft:tower", 5));
        }
        StageTimeline timeline = StageTimeline.of(stages, 400, metrics(192, 200));
        assertEquals(
            List.of("root", "minecraft:machine", "minecraft:tower"),
            timeline.lanes().stream().map(StageTimeline.Lane::group).toList()
        );
        assertEquals(List.of(10, 4, 3), timeline.lanes().stream().map(StageTimeline.Lane::stageCount).toList());
        assertEquals(List.of(0, 10, 14), timeline.lanes().stream().map(StageTimeline.Lane::firstStage).toList());
        assertEquals(17, timeline.segments().size());
    }

    @Test
    void aSegmentsWidthIsProportionalToItsExpectedCellsWithALegibleFloor() {
        StageTimeline timeline = StageTimeline
                .of(List.of(stage("floor", "g", 9), stage("chimney", "g", 3)), 100, metricless(0, 52));
        StageTimeline.Segment floor = timeline.segments().get(0);
        StageTimeline.Segment chimney = timeline.segments().get(1);
        assertEquals(74, floor.width(), "three quarters of the 99 pixels between the gap");
        assertEquals(25, chimney.width());
        assertEquals(75, chimney.x(), "one pixel of air between two segments");
        assertEquals(floor.y(), chimney.y(), "same lane, same row");

        StageTimeline floorless = StageTimeline
                .of(List.of(stage("a", "g", 1), stage("b", "g", 10000)), 100, metricless(0, 52));
        assertEquals(8, floorless.segments().get(0).width(), "a one-cell stage keeps the legible minimum");
        assertTrue(floorless.segments().get(1).width() > 8, "the big stage keeps the room the floor leaves");
    }

    @Test
    void anOverflowingLaneWrapsIndentedInsteadOfShrinkingOrTruncating() {
        List<Stage> stages = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            stages.add(stage("s" + index, "g", 1));
        }
        StageTimeline timeline = StageTimeline.of(stages, 40, metricless(0, 52));
        StageTimeline.Lane lane = timeline.lanes().get(0);
        assertTrue(lane.rows() > 1, "the lane wraps");
        assertEquals(20, timeline.segments().size(), "wrapping never drops a stage");
        for (StageTimeline.Segment segment : timeline.segments()) {
            assertTrue(
                segment.width() >= StageTimeline.minSegmentWidth,
                "a segment never shrinks below the legible minimum"
            );
            assertTrue(segment.x() + segment.width() <= 40, "a segment never leaves the strip");
            if (segment.y() > lane.y()) {
                assertTrue(segment.x() >= StageTimeline.wrapIndent, "a wrapped row is indented under the head");
            }
        }
        assertEquals(lane.rows() * StageTimeline.laneHeight, timeline.contentHeight());
        assertEquals(StageTimeline.timelineMaxHeight, timeline.height(), "the strip grows to the cap");
        assertTrue(timeline.scrollable());
    }

    @Test
    void theStripGrowsToTheCapThenScrollsWithTheCursorInView() {
        List<Stage> stages = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            stages.add(stage("s" + index, "g" + index, 1));
        }
        StageTimeline timeline = StageTimeline.of(stages, 100, metricless(0, 52));
        assertEquals(
            5 * StageTimeline.laneHeight + 4 * StageTimeline.laneGap,
            timeline.contentHeight(),
            "five one-row lanes and their gaps"
        );
        assertEquals(52, timeline.height());
        assertEquals(timeline.contentHeight() - 52, timeline.maxScroll());
        assertEquals(timeline.maxScroll(), timeline.clampScroll(1000));
        assertEquals(0, timeline.clampScroll(-5));
        assertEquals(
            timeline.maxScroll(),
            timeline.scrollForVisible(0, 4),
            "the last lane's segment is scrolled into view"
        );
        assertEquals(
            0,
            timeline.scrollForVisible(timeline.maxScroll(), 0),
            "the first lane's segment scrolls back to the top"
        );
        assertEquals(4, timeline.scrollForVisible(4, 2), "a visible segment does not move the strip");
    }

    @Test
    void aCompactMetricLaneHeightDrivesTheRowsTheHitTestAndTheScroll() {
        StageTimeline.Metrics compact = new StageTimeline.Metrics(6, 1, 1, 4, 0, 4, 0, 20, name -> 0);
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
        assertEquals(
            -1,
            timeline.hit(third.x() + 1, third.y() + 6),
            "the row's height is the metric's, not the constant"
        );
        assertEquals(7, timeline.scrollForVisible(0, 3));
    }

    @Test
    void aNameIsDrawnOnlyWhereTheTextFits() {
        StageTimeline timeline = StageTimeline.of(
            List.of(stage("a", "g", 1), stage("a name far too long for its segment", "g", 1)),
            30,
            metrics(0, 52)
        );
        assertTrue(timeline.segments().get(0).named());
        assertFalse(timeline.segments().get(1).named(), "a narrow segment names nothing rather than truncating");
    }

    @Test
    void aLanesHeadIsItsDrawnLabelAndTheFirstSegmentFollowsItAtOnce() {
        StageTimeline shortHead = StageTimeline.of(List.of(stage("s", "root", 9)), 400, metrics(192, 52));
        StageTimeline.Lane.Head root = Objects.requireNonNull(shortHead.lanes().get(0).head());
        assertEquals("root", root.group());
        assertEquals("root", root.label());
        assertEquals(4 * 6 + 2 * StageTimeline.namePad, root.width(), "the head is the label plus its pads");
        assertEquals(root.width(), shortHead.segments().get(0).x(), "no air between the head and its first segment");

        StageTimeline longHead = StageTimeline
                .of(List.of(stage("s", "structurecumulus:a_long_member_label", 9)), 600, metrics(192, 52));
        StageTimeline.Lane.Head capped = Objects.requireNonNull(longHead.lanes().get(0).head());
        assertTrue(capped.group().length() > 20, "the group is longer than the cap");
        assertTrue(capped.label().length() < capped.group().length(), "the label is truncated");
        assertTrue(capped.label().startsWith("\u2026"), "the tail keeps the distinguishing suffix");
        assertTrue(capped.width() <= StageTimeline.headMaxWidth, "a long group stops at the cap");
        assertEquals(
            capped.label().length() * 6 + 2 * StageTimeline.namePad,
            capped.width(),
            "even a truncated head is no wider than the label drawn"
        );
        assertEquals(capped.width(), longHead.segments().get(0).x(), "the segment starts where the head ends");
    }

    @Test
    void aNameSitsInsideItsRowAndNeverOnTheEdge() {
        assertEquals(
            StageTimeline.textHeight + 2 * StageTimeline.nameInset,
            StageTimeline.laneHeight,
            "a lane leaves the font its line and air above and below"
        );
        StageTimeline timeline = StageTimeline
                .of(List.of(stage("a", "g", 9), stage("b", "h", 9)), 200, metrics(192, 52));
        for (StageTimeline.Segment segment : timeline.segments()) {
            assertTrue(segment.nameY() - segment.y() >= 1, "a name is never flush with the segment's top");
            assertTrue(
                segment.nameY() + StageTimeline.textHeight <= segment.y() + segment.height(),
                "a name's line fits inside its segment"
            );
        }
        for (StageTimeline.Lane lane : timeline.lanes()) {
            StageTimeline.Lane.Head head = Objects.requireNonNull(lane.head());
            assertTrue(head.labelY() - lane.y() >= 1, "a label is never flush with the lane's top");
            assertTrue(head.labelY() + StageTimeline.textHeight <= lane.y() + lane.rowHeight());
        }
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
        assertEquals(
            "layer 1 \u00B7 9 cells",
            StageTimeline.hoverText(stage("layer 1", null, 9)),
            "a page that does not nest has no group to name"
        );
    }

    @Test
    void thePlayheadStartsStoppedAndRidesTheRevealedShareWhilePlaying() {
        assertEquals(0.0F, StageTimeline.playheadFraction(false, 9, 9), "stopped, the playhead is the start");
        assertEquals(0.0F, StageTimeline.playheadFraction(true, 0, 9), "the hold has revealed nothing");
        assertEquals(1.0F, StageTimeline.playheadFraction(true, 9, 9), "the batch has landed whole");
        StageTimeline timeline = StageTimeline.of(List.of(stage("a", "g", 9)), 100, metricless(0, 52));
        assertEquals(0, timeline.playheadX(0, false, 0, 9));
        assertEquals(99, timeline.playheadX(0, true, 9, 9), "the last pixel of the segment, never past it");
        assertEquals(-1, timeline.playheadX(3, true, 0, 1));
    }

    @Test
    void theHitTestNamesTheSegmentUnderAPointAndTheLanesHead() {
        StageTimeline timeline = StageTimeline.of(
            List.of(stage("a", "group", 1), stage("b", "group", 1), stage("c", "other", 1)),
            100,
            metricless(40, 52)
        );
        StageTimeline.Segment first = timeline.segments().get(0);
        assertEquals(0, timeline.hit(first.x() + 1, first.y() + 1));
        assertEquals(-1, timeline.hit(first.x() + first.width(), first.y() + 1), "a gap is no segment");
        StageTimeline.Lane head = Objects.requireNonNull(timeline.headAt(3, 2));
        assertEquals("group", head.group());
        assertNull(timeline.headAt(60, 2), "past the head column no lane answers");
    }

    @Test
    void onlyALanesFirstRowCarriesItsHead() {
        List<Stage> stages = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            stages.add(stage("s" + index, "g", 1));
        }
        StageTimeline timeline = StageTimeline.of(stages, 40, metricless(40, 52));
        StageTimeline.Lane lane = timeline.lanes().get(0);
        assertTrue(lane.rows() > 1, "the lane wraps");
        assertNotNull(timeline.headAt(3, lane.y() + 1), "the first row carries the head");
        assertNull(
            timeline.headAt(3, lane.y() + StageTimeline.laneHeight + 1),
            "a wrapped row is indented under the head and has none of its own"
        );
    }

    @Test
    void aHeadlessLaneAnswersNoHeadHover() {
        StageTimeline timeline = StageTimeline.of(List.of(stage("a", null, 9)), 100, metrics(192, 52));
        assertNull(timeline.headAt(0, 1), "a lane without a head never answers as one");
        assertEquals(0, timeline.hit(0, 1), "the segment under it still answers");
    }

    @Test
    void aPagesOwnNamespaceIsStrippedFromTheDrawnLabelsAndAForeignOneIsKept() {
        StageTimeline.Labels labels = StageTimeline.Labels.of(NamespacedId.of("structurecumulus", "demo_station"));
        assertEquals("core_low", labels.strip("structurecumulus:core_low"), "the page's own namespace is the page's");
        assertEquals("minecraft:stone", labels.strip("minecraft:stone"), "a foreign prefix is the distinction");
        assertEquals("layer 3", labels.strip("layer 3"), "a name with no namespace is already short");

        Stage home = stage("structurecumulus:core_low", null, 9);
        Stage foreign = stage("othermod:core_low", null, 9);
        StageTimeline timeline = StageTimeline.of(List.of(home, foreign), 400, metrics(192, 52), labels);
        assertEquals(
            List.of("core_low", "othermod:core_low"),
            timeline.segments().stream().map(StageTimeline.Segment::label).toList(),
            "the segment draws the short form and keeps a foreign prefix"
        );
        assertEquals("structurecumulus:core_low", home.name(), "the wire's name never moves");
        assertEquals(
            "structurecumulus:core_low \u00B7 9 cells",
            StageTimeline.hoverText(home),
            "the hover still names the whole id"
        );
    }

    @Test
    void aSetsHeadAndSegmentCarryTheShortLabelsAndTheHoverKeepsTheFullId() {
        StageTimeline.Labels labels = StageTimeline.Labels.of(NamespacedId.of("structurecumulus", "demo_campus"));
        Stage root = stage("layer 1", "root", 9);
        Stage machine = stage("structurecumulus:plinth", "structurecumulus:campus_machine", 25);
        Stage foreign = stage("othermod:base", "othermod:campus_machine", 4);
        StageTimeline timeline = StageTimeline.of(List.of(root, machine, foreign), 400, metrics(192, 52), labels);
        assertEquals(
            List.of("root", "campus_machine", "othermod:campus_machine"),
            timeline.lanes().stream().map(lane -> Objects.requireNonNull(lane.head()).label()).toList(),
            "the head is the group without the page's namespace"
        );
        assertEquals(
            List.of("root", "structurecumulus:campus_machine", "othermod:campus_machine"),
            timeline.lanes().stream().map(StageTimeline.Lane::group).toList(),
            "the lane still answers with the wire's whole group"
        );
        assertEquals("plinth", timeline.segments().get(1).label(), "a segment never repeats its head's member");
        assertEquals("othermod:base", timeline.segments().get(2).label(), "a foreign member keeps its prefix");
        assertEquals(
            "structurecumulus:plinth \u00B7 structurecumulus:campus_machine \u00B7 25 cells",
            StageTimeline.hoverText(machine),
            "the hover names the stage and its member in full"
        );
        assertEquals(
            "plinth",
            labels.stage(
                stage(
                    "structurecumulus:campus_machine \u00B7 structurecumulus:plinth",
                    "structurecumulus:campus_machine",
                    5
                )
            ),
            "an older payload's member-prefixed name drops the prefix the head carries"
        );
    }

    @Test
    void aShortLabelThatStillDoesNotFitItsSegmentIsNotDrawn() {
        StageTimeline.Labels labels = StageTimeline.Labels.of(NamespacedId.of("structurecumulus", "station"));
        Stage stage = stage("structurecumulus:an_extremely_long_stage_name", null, 1);
        StageTimeline timeline = StageTimeline.of(List.of(stage), 30, metrics(192, 52), labels);
        StageTimeline.Segment segment = timeline.segments().get(0);
        assertEquals("an_extremely_long_stage_name", segment.label(), "the label is short only by its namespace");
        assertFalse(segment.named(), "a short label can still overflow, and then it is not drawn");
        assertEquals(
            "structurecumulus:an_extremely_long_stage_name \u00B7 1 cells",
            StageTimeline.hoverText(stage),
            "the hover carries the whole id the segment cannot draw"
        );
    }

    @Test
    void aHeadsShortLabelTakesLessRoomThanItsWholeGroup() {
        List<Stage> stages = List.of(stage("structurecumulus:plinth", "structurecumulus:campus_machine", 9));
        StageTimeline full = StageTimeline.of(stages, 400, metrics(192, 52));
        StageTimeline shortLabels = StageTimeline
                .of(stages, 400, metrics(192, 52), StageTimeline.Labels.of(NamespacedId.of("structurecumulus", "c")));
        StageTimeline.Lane.Head before = Objects.requireNonNull(full.lanes().get(0).head());
        StageTimeline.Lane.Head after = Objects.requireNonNull(shortLabels.lanes().get(0).head());
        assertEquals("structurecumulus:campus_machine", before.label(), "no page namespace strips nothing");
        assertEquals("campus_machine", after.label());
        assertTrue(after.width() < before.width(), "the short head takes less of the lane");
        assertEquals(after.width(), shortLabels.segments().get(0).x(), "the first segment follows the short head");
    }

    @Test
    void theChromeScaleIsALadderThatReadsOverTheBlurredWorld() {
        StageTimeline.Chrome chrome = StageTimeline.Chrome.screen;
        assertTrue((chrome.ground() >>> 24) >= 0xC0, "the strip's ground hides enough of the world to read on");
        assertTrue((chrome.fill() >>> 24) >= 0x90, "a segment's fill is opaque enough that two segments differ");
        assertTrue(luminance(chrome.ground()) < luminance(chrome.fill()), "a fill is lighter than the ground");
        assertTrue(luminance(chrome.fill()) < luminance(chrome.hover()), "the hover is lighter than a fill");
        assertTrue(luminance(chrome.hover()) < luminance(chrome.text()), "the name is lighter than the hover");
        assertTrue(luminance(chrome.text()) < luminance(chrome.cursor()), "the cursor is the brightest chrome");
    }

    @Test
    void anEmptyTableOrAnEmptyWidthDrawsNothing() {
        assertTrue(StageTimeline.of(List.of(), 100, metrics(192, 52)).segments().isEmpty());
        assertEquals(0, StageTimeline.of(List.of(), 100, metrics(192, 52)).height());
        StageTimeline flat = StageTimeline.of(List.of(stage("a", "g", 1)), 0, metrics(192, 52));
        assertTrue(flat.segments().isEmpty());
        assertEquals(0, flat.height());
    }

    @Test
    void theSameTableAndMetricsGiveTheSameTimeline() {
        List<Stage> stages = List.of(stage("a", "g", 9), stage("b", "g", 3), stage("c", "h", 2));
        assertEquals(StageTimeline.of(stages, 120, metrics(192, 52)), StageTimeline.of(stages, 120, metrics(192, 52)));
    }

    /** The tone's linear brightness: the four chrome steps are far enough apart for a plain sum to order them. */
    private static double luminance(int color) {
        return 0.2126 * ((color >> 16) & 0xFF) + 0.7152 * ((color >> 8) & 0xFF) + 0.0722 * (color & 0xFF);
    }
}
