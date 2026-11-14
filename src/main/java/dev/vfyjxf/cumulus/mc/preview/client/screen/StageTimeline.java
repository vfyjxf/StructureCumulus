package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * The stage timeline of design 19 §3: consecutive stages of one group form a lane on its own row, the
 * lane's head carries the group's label, its stages are segments in index order, a lane wraps when it
 * overflows and the strip grows to a cap and then scrolls. A table of contents, never a progress bar:
 * the only marks are the cursor, the hover and the playhead. A pure function of the table, the width
 * and the text metrics.
 */
public record StageTimeline(List<Lane> lanes, List<Segment> segments, int width, int contentHeight, int height) {

    /** One row's height: the segment strip and the lane head share it. */
    public static final int laneHeight = 11;
    /** The air between two lanes. */
    public static final int laneGap = 2;
    /** The air between two segments. */
    public static final int segmentGap = 1;
    /** The narrowest legible segment; a stage never shrinks below it. */
    public static final int minSegmentWidth = 8;
    public static final int headMinWidth = 20;
    public static final int headMaxWidth = 88;
    /** A wrapped row starts under the lane's label, indented by this much. */
    public static final int wrapIndent = 10;
    public static final int namePad = 2;
    /** The code constant of design 19 §3: the tallest the strip may grow before it scrolls. */
    public static final int timelineMaxHeight = 52;
    /** One wheel notch's scroll. */
    public static final int scrollStepPx = 12;

    /** The text metrics the layout reads: one measured string width, nothing of the GUI. */
    public record Metrics(
        int laneHeight,
        int laneGap,
        int segmentGap,
        int minSegmentWidth,
        int headMinWidth,
        int headMaxWidth,
        int wrapIndent,
        int maxHeight,
        ToIntFunction<String> textWidth
    ) {

        public Metrics {
            Objects.requireNonNull(textWidth, "textWidth");
        }

        /** The screen's own metrics: the code constants and the font. */
        public static Metrics of(ToIntFunction<String> textWidth) {
            return new Metrics(
                StageTimeline.laneHeight,
                StageTimeline.laneGap,
                StageTimeline.segmentGap,
                StageTimeline.minSegmentWidth,
                StageTimeline.headMinWidth,
                StageTimeline.headMaxWidth,
                StageTimeline.wrapIndent,
                timelineMaxHeight,
                textWidth
            );
        }
    }

    /** One group's row block: its label, its first row's head width and how many rows it wraps to. */
    public record Lane(String group, int y, int headWidth, int rows, int firstStage, int stageCount, int rowHeight) {

        public int bottom() {
            return y + rows * rowHeight;
        }
    }

    /** One stage's segment: its row position in the strip, its width, its name's fit and its height. */
    public record Segment(int index, int x, int y, int width, boolean named, int height) {}

    public StageTimeline {
        lanes = List.copyOf(lanes);
        segments = List.copyOf(segments);
        if (width < 0 || contentHeight < 0 || height < 0) {
            throw new IllegalArgumentException("a timeline's measurements cannot be negative");
        }
    }

    /** The lanes, the segments and this frame's strip height; no GUI state beyond the text metrics. */
    public static StageTimeline of(List<Stage> stages, int width, Metrics metrics) {
        if (stages.isEmpty() || width <= 0 || metrics.maxHeight() <= 0) {
            return new StageTimeline(List.of(), List.of(), Math.max(0, width), 0, 0);
        }
        List<Lane> lanes = new ArrayList<>();
        List<Segment> segments = new ArrayList<>(stages.size());
        int y = 0;
        int first = 0;
        while (first < stages.size()) {
            String group = groupOf(stages.get(first));
            int last = first + 1;
            while (last < stages.size() && groupOf(stages.get(last)).equals(group)) {
                last++;
            }
            int count = last - first;
            int head = headWidth(group, width, metrics);
            int totalCells = 0;
            for (int index = first; index < last; index++) {
                totalCells += Math.max(1, stages.get(index).expectedCells());
            }
            int shareBudget = Math.max(0, width - head - metrics.segmentGap() * (count - 1));
            int row = 0;
            int x = head;
            int rowStart = head;
            int cellsSoFar = 0;
            int boundary = 0;
            for (int index = first; index < last; index++) {
                int cells = Math.max(1, stages.get(index).expectedCells());
                // cumulative boundaries, so rounding never sums past the row it was measured against
                cellsSoFar += cells;
                int next = (int) Math.round((double) shareBudget * cellsSoFar / Math.max(1, totalCells));
                int segmentWidth = Math.max(metrics.minSegmentWidth(), next - boundary);
                boundary += segmentWidth;
                if (x > rowStart && x + segmentWidth > width) {
                    row++;
                    x = Math.min(metrics.wrapIndent(), Math.max(0, width - metrics.minSegmentWidth()));
                    rowStart = x;
                }
                if (x + segmentWidth > width) {
                    segmentWidth = Math.max(1, width - x);
                }
                boolean named = metrics.textWidth().applyAsInt(stages.get(index).name()) + 2 * namePad <= segmentWidth;
                segments.add(
                    new Segment(index, x, y + row * metrics.laneHeight(), segmentWidth, named, metrics.laneHeight())
                );
                x += segmentWidth + metrics.segmentGap();
            }
            int rows = row + 1;
            lanes.add(new Lane(group, y, head, rows, first, count, metrics.laneHeight()));
            y += rows * metrics.laneHeight() + metrics.laneGap();
            first = last;
        }
        int content = Math.max(0, y - metrics.laneGap());
        return new StageTimeline(lanes, segments, width, content, Math.min(content, metrics.maxHeight()));
    }

    /** The stage's group, or its bay when a payload predates the carried group (design 19 §2). */
    public static String groupOf(Stage stage) {
        String group = stage.group();
        return group == null || group.isBlank() ? stage.bay().text() : group;
    }

    /** The hover's line: the stage, its group (unless the name already carries it) and its cells. */
    public static String hoverText(Stage stage) {
        String name = stage.name();
        String group = groupOf(stage);
        String head = name.equals(group) || name.startsWith(group + " \u00B7 ") ? name : name + " \u00B7 " + group;
        return head + " \u00B7 " + stage.expectedCells() + " cells";
    }

    /**
     * The playhead's place inside the cursor's segment: the revealed share while a play runs, the start
     * when it is stopped (design 19 §3). A batch reveal moves it from 0 to 1, never in between.
     */
    public static float playheadFraction(boolean playing, int revealed, int cells) {
        if (!playing || cells <= 0 || revealed <= 0) {
            return 0.0F;
        }
        if (revealed >= cells) {
            return 1.0F;
        }
        return revealed / (float) cells;
    }

    public boolean scrollable() {
        return contentHeight > height;
    }

    public int maxScroll() {
        return Math.max(0, contentHeight - height);
    }

    public int clampScroll(int scroll) {
        return Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** One stage's segment, or null when the table carries no such index. */
    public @Nullable Segment segment(int index) {
        return index < 0 || index >= segments.size() ? null : segments.get(index);
    }

    /** One lane, or null when the table carries no such lane. */
    public @Nullable Lane lane(int index) {
        return index < 0 || index >= lanes.size() ? null : lanes.get(index);
    }

    /** The stage under a strip-local point, with the scroll already taken out, or -1. */
    public int hit(int localX, int localY) {
        for (Segment segment : segments) {
            if (localX >= segment.x()
                    && localX < segment.x() + segment.width()
                    && localY >= segment.y()
                    && localY < segment.y() + segment.height()) {
                return segment.index();
            }
        }
        return -1;
    }

    /** The lane whose head is under a strip-local point, or null; only the lane's first row has one. */
    public @Nullable Lane headAt(int localX, int localY) {
        if (localX < 0) {
            return null;
        }
        for (Lane lane : lanes) {
            if (localX < lane.headWidth() && localY >= lane.y() && localY < lane.y() + lane.rowHeight()) {
                return lane;
            }
        }
        return null;
    }

    /** The smallest scroll that shows the segment whole, clamped; the cursor never rides off the strip. */
    public int scrollForVisible(int scroll, int index) {
        Segment segment = segment(index);
        if (segment == null) {
            return clampScroll(scroll);
        }
        int offset = clampScroll(scroll);
        if (segment.y() - offset < 0) {
            return clampScroll(segment.y());
        }
        if (segment.y() + segment.height() - offset > height) {
            return clampScroll(segment.y() + segment.height() - height);
        }
        return offset;
    }

    /** The playhead's x inside one segment: the start when stopped, the revealed share while playing. */
    public int playheadX(int index, boolean playing, int revealed, int cells) {
        Segment segment = segment(index);
        if (segment == null) {
            return -1;
        }
        int offset = Math.round(segment.width() * playheadFraction(playing, revealed, cells));
        return segment.x() + Math.min(segment.width() - 1, Math.max(0, offset));
    }

    private static int headWidth(String group, int width, Metrics metrics) {
        int wanted = metrics.textWidth().applyAsInt(group) + 2 * namePad;
        int head = Math.max(metrics.headMinWidth(), Math.min(metrics.headMaxWidth(), wanted));
        return Math.min(head, Math.max(0, width - metrics.minSegmentWidth() - metrics.segmentGap()));
    }
}
