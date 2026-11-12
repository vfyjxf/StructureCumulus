package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stage bar of design 17 §7.3: proportionality, the 1 px floor, the gap and the hit test. */
class StageBarTest {

    private static final NamespacedId bay = NamespacedId.of("structurecumulus", "bay");

    private static Stage stage(String name, int cells) {
        return new Stage(name, cells, 0, null, null, bay);
    }

    @Test
    void theSegmentsKeepTheStageOrderAndProportionalWidths() {
        StageBar bar = StageBar.of(List.of(stage("floor", 9), stage("chimney", 3)), 4, 10, 100, 9);
        assertEquals(2, bar.segments().size());
        assertEquals(0, bar.segments().get(0).index());
        assertEquals(1, bar.segments().get(1).index());
        assertEquals(74, bar.segments().get(0).width(), "three quarters of 99 pixels");
        assertEquals(25, bar.segments().get(1).width());
        assertEquals(74 + 4 + 1, bar.segments().get(1).x(), "one pixel of air between two segments");
    }

    @Test
    void theSegmentsNeverLeaveTheStrip() {
        List<Stage> many = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            many.add(stage("stage " + index, 1));
        }
        StageBar bar = StageBar.of(many, 4, 10, 30, 9);
        int end = Integer.MIN_VALUE;
        for (StageBar.Segment segment : bar.segments()) {
            assertTrue(segment.width() >= 1, "a segment is at least one pixel wide");
            assertTrue(segment.x() >= 4, "a segment starts inside the strip");
            assertTrue(segment.x() + segment.width() <= 4 + 30, "a segment ends inside the strip");
            assertTrue(segment.x() > end, "segments never overlap");
            end = segment.x() + segment.width();
        }
        assertTrue(bar.segments().size() < 20, "the strip drops the segments it cannot hold");
    }

    @Test
    void theHitTestNamesTheSegmentUnderTheCursorAndNothingInTheGap() {
        StageBar bar = StageBar.of(List.of(stage("floor", 1), stage("chimney", 1)), 4, 10, 60, 9);
        assertEquals(0, bar.hit(5, 15));
        assertEquals(-1, bar.hit(bar.segments().get(0).x() + bar.segments().get(0).width(), 15), "a gap is no segment");
        assertEquals(1, bar.hit(bar.segments().get(1).x() + 1, 12));
        assertEquals(-1, bar.hit(5, 9), "above the bar is outside it");
        assertEquals(-1, bar.hit(5, 19), "below the bar is outside it");
        assertEquals(-1, bar.hit(200, 15));
    }

    @Test
    void aLabelFitsOnlyWhereItsTextFits() {
        StageBar bar = StageBar.of(List.of(stage("a", 1), stage("b", 40)), 0, 0, 40, 9);
        assertTrue(bar.fits(1, 30));
        assertFalse(bar.fits(0, 5), "a one-pixel segment names nothing");
    }

    @Test
    void anEmptyTableOrAnEmptyStripDrawsNothing() {
        assertTrue(StageBar.of(List.of(), 0, 0, 100, 9).segments().isEmpty());
        assertTrue(StageBar.of(List.of(stage("a", 1)), 0, 0, 0, 9).segments().isEmpty());
        assertTrue(StageBar.of(List.of(stage("a", 1)), 0, 0, 10, 0).segments().isEmpty());
    }

    @Test
    void theSameTableAndRectangleGiveTheSameBar() {
        List<Stage> stages = List.of(stage("floor", 9), stage("chimney", 3), stage("cap", 2));
        assertEquals(StageBar.of(stages, 3, 4, 120, 9), StageBar.of(stages, 3, 4, 120, 9));
    }
}
