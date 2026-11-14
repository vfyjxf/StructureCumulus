package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
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
 * the only marks are the cursor, the hover and the playhead. A pure function of the table, the width,
 * the text metrics and the page's display labels (19 §3).
 */
public record StageTimeline(List<Lane> lanes, List<Segment> segments, int width, int contentHeight, int height) {

    /** One row's height: the segment strip and the lane head share it. */
    public static final int laneHeight = 13;
    /** Vanilla's font line height, which a lane leaves room for above and below. */
    public static final int textHeight = 9;
    /** The air above a name, and above a lane's label; the rest of the row is the air below. */
    public static final int nameInset = 2;
    /** The air between two lanes. */
    public static final int laneGap = 2;
    /** The air between two segments. */
    public static final int segmentGap = 1;
    /** The narrowest legible segment; a stage never shrinks below it. */
    public static final int minSegmentWidth = 8;
    /** The widest a lane's head may grow; past it the label keeps its tail and the hover the whole. */
    public static final int headMaxWidth = 192;
    /** A wrapped row starts under the lane's label, indented by this much. */
    public static final int wrapIndent = 10;
    public static final int namePad = 2;
    /** The code constant of design 19 §3: the tallest the strip may grow before it scrolls. */
    public static final int timelineMaxHeight = 52;
    /** One wheel notch's scroll. */
    public static final int scrollStepPx = 12;

    /**
     * The timeline's chrome scale of design 19 §3: the strip's ground, a segment's fill, the hover, the
     * cursor and the text, each a step lighter than the one before so a segment reads as a box on a
     * strip that reads as a panel.
     */
    public record Chrome(int ground, int fill, int hover, int cursor, int text) {

        /** The screen's own scale: a near-black ground, an opaque fill and the cursor as the brightest. */
        public static final Chrome screen = new Chrome(0xE00E1114, 0xFF39434E, 0xFF586574, 0xFFE8E8E8, 0xFFD8DDE2);
    }

    /**
     * The display-label law of design 19 §3: a lane head and a segment name strip the page's own
     * namespace, a set page's segment also drops the member prefix the head already carries, and a
     * foreign namespace keeps its prefix because that prefix is the distinction. The full id is the
     * hover's, never a drawn label's.
     */
    public record Labels(@Nullable String namespace) {

        /** No page namespace to strip: the drawn labels are the wire's ids, the pure baseline. */
        public static final Labels none = new Labels(null);

        public static Labels of(NamespacedId pageId) {
            return new Labels(Objects.requireNonNull(pageId, "pageId").namespace());
        }

        /** The id without the page's own namespace; an id from anywhere else is already short. */
        public String strip(String id) {
            if (namespace == null || namespace.isBlank()) {
                return id;
            }
            String prefix = namespace + ":";
            return id.startsWith(prefix) ? id.substring(prefix.length()) : id;
        }

        /** A segment's name: the stage's own name, without the member prefix a set page's head carries. */
        public String stage(Stage stage) {
            String name = stage.name();
            String group = stage.group();
            if (group != null && name.startsWith(group + " \u00B7 ")) {
                return strip(name.substring(group.length() + 3));
            }
            return strip(name);
        }
    }

    /** The text metrics the layout reads: one measured string width, nothing of the GUI. */
    public record Metrics(
        int laneHeight,
        int laneGap,
        int segmentGap,
        int minSegmentWidth,
        int headMaxWidth,
        int wrapIndent,
        int nameInset,
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
                StageTimeline.headMaxWidth,
                StageTimeline.wrapIndent,
                StageTimeline.nameInset,
                timelineMaxHeight,
                textWidth
            );
        }
    }

    /**
     * One group's row block: its head (absent on a page that does not nest), where the block starts and
     * how many stages it carries over how many rows.
     */
    public record Lane(@Nullable Head head, int y, int rows, int firstStage, int stageCount, int rowHeight) {

        /** The group's label as drawn, and the column it takes: the label's own width, plus the pads. */
        public record Head(String group, String label, int width, int labelY) {}

        /** The lane's group, or null when it has no head; a blueprint page is one headless lane (19 §2). */
        public @Nullable String group() {
            return head == null ? null : head.group();
        }

        /** The width of the label's column, 0 for a lane without a head. */
        public int headWidth() {
            return head == null ? 0 : head.width();
        }

        public int bottom() {
            return y + rows * rowHeight;
        }
    }

    /** One stage's segment: its row position, its width, its drawn label's fit and where it is drawn. */
    public record Segment(int index, int x, int y, int width, boolean named, int nameY, int height, String label) {}

    public StageTimeline {
        lanes = List.copyOf(lanes);
        segments = List.copyOf(segments);
        if (width < 0 || contentHeight < 0 || height < 0) {
            throw new IllegalArgumentException("a timeline's measurements cannot be negative");
        }
    }

    /** The lanes, the segments and this frame's strip height; no GUI state beyond the text metrics. */
    public static StageTimeline of(List<Stage> stages, int width, Metrics metrics) {
        return of(stages, width, metrics, Labels.none);
    }

    /**
     * The timeline under the page's display-label law: the heads and segments are measured and named
     * with their short forms, while the wire's full ids stay on the stages for the hover (19 §3).
     */
    public static StageTimeline of(List<Stage> stages, int width, Metrics metrics, Labels labels) {
        Objects.requireNonNull(labels, "labels");
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
            while (last < stages.size() && Objects.equals(groupOf(stages.get(last)), group)) {
                last++;
            }
            int count = last - first;
            Lane.@Nullable Head head = head(group, y, width, metrics, labels);
            int headWidth = head == null ? 0 : head.width();
            int totalCells = 0;
            for (int index = first; index < last; index++) {
                totalCells += Math.max(1, stages.get(index).expectedCells());
            }
            int shareBudget = Math.max(0, width - headWidth - metrics.segmentGap() * (count - 1));
            int row = 0;
            int x = headWidth;
            int rowStart = x;
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
                int segmentY = y + row * metrics.laneHeight();
                String label = labels.stage(stages.get(index));
                boolean named = metrics.textWidth().applyAsInt(label) + 2 * namePad <= segmentWidth;
                segments.add(
                    new Segment(
                        index,
                        x,
                        segmentY,
                        segmentWidth,
                        named,
                        segmentY + metrics.nameInset(),
                        metrics.laneHeight(),
                        label
                    )
                );
                x += segmentWidth + metrics.segmentGap();
            }
            int rows = row + 1;
            lanes.add(new Lane(head, y, rows, first, count, metrics.laneHeight()));
            y += rows * metrics.laneHeight() + metrics.laneGap();
            first = last;
        }
        int content = Math.max(0, y - metrics.laneGap());
        return new StageTimeline(lanes, segments, width, content, Math.min(content, metrics.maxHeight()));
    }

    /** The stage's group, or null when the page does not nest: a blueprint page is one lane (design 19 §2). */
    public static @Nullable String groupOf(Stage stage) {
        return stage.group();
    }

    /** The hover's line: the stage, its group (unless the name already carries it) and its cells. */
    public static String hoverText(Stage stage) {
        String name = stage.name();
        String group = groupOf(stage);
        String head = group == null || name.equals(group) || name.startsWith(group + " \u00B7 ")
                ? name
                : name + " \u00B7 " + group;
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
            if (lane.headWidth() > 0
                    && localX < lane.headWidth()
                    && localY >= lane.y()
                    && localY < lane.y() + lane.rowHeight()) {
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

    /** A lane's head, or none; the column is the short label's own width plus the pads, never wider than it. */
    private static Lane.@Nullable Head head(@Nullable String group, int y, int width, Metrics metrics, Labels labels) {
        if (group == null) {
            return null;
        }
        int column = Math
                .max(0, Math.min(metrics.headMaxWidth(), width - metrics.minSegmentWidth() - metrics.segmentGap()));
        String label = fitHead(labels.strip(group), Math.max(0, column - 2 * namePad), metrics.textWidth());
        int labelWidth = metrics.textWidth().applyAsInt(label) + 2 * namePad;
        return new Lane.Head(group, label, Math.min(column, labelWidth), y + metrics.nameInset());
    }

    /** The whole label when it fits its column, else its tail behind an ellipsis because the hover has the whole. */
    private static String fitHead(String group, int room, ToIntFunction<String> textWidth) {
        if (textWidth.applyAsInt(group) <= room) {
            return group;
        }
        int budget = room - textWidth.applyAsInt("\u2026");
        if (budget <= 0) {
            return "";
        }
        int start = group.length();
        int used = 0;
        while (start > 0) {
            int character = textWidth.applyAsInt(group.substring(start - 1, start));
            if (used + character > budget) {
                break;
            }
            used += character;
            start--;
        }
        return "\u2026" + group.substring(start);
    }
}
