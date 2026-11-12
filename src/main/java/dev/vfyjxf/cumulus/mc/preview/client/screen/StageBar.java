package dev.vfyjxf.cumulus.mc.preview.client.screen;

import dev.vfyjxf.cumulus.mc.preview.net.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * The stage bar of design 17 §7.3: one segment per stage, wide in proportion to its expected cells
 * with a 1 px floor and a 1 px gap, left to right in index order. A table of contents, never a
 * progress bar. A pure function of the table and the strip's rectangle.
 */
public record StageBar(List<Segment> segments, int x, int y, int width, int height) {

    /** One stage's segment inside the strip. */
    public record Segment(int index, int x, int width) {}

    public StageBar {
        segments = List.copyOf(segments);
    }

    public static StageBar of(List<Stage> stages, int x, int y, int width, int height) {
        if (stages.isEmpty() || width <= 0 || height <= 0) {
            return new StageBar(List.of(), x, y, width, height);
        }
        int total = 0;
        for (Stage stage : stages) {
            total += stage.expectedCells();
        }
        int gaps = stages.size() - 1;
        int available = Math.max(0, width - gaps);
        List<Segment> segments = new ArrayList<>(stages.size());
        int cursor = x;
        for (int index = 0; index < stages.size(); index++) {
            int share = (int) Math.round((double) available * stages.get(index).expectedCells() / Math.max(1, total));
            int segmentWidth = Math.max(1, share);
            if (cursor + segmentWidth > x + width) {
                segmentWidth = x + width - cursor;
            }
            if (segmentWidth <= 0) {
                break;
            }
            segments.add(new Segment(index, cursor, segmentWidth));
            cursor += segmentWidth + 1;
            if (cursor >= x + width) {
                break;
            }
        }
        return new StageBar(segments, x, y, width, height);
    }

    /** The segment under the cursor, or -1 outside the bar. */
    public int hit(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return -1;
        }
        for (Segment segment : segments) {
            if (mouseX >= segment.x() && mouseX < segment.x() + segment.width()) {
                return segment.index();
            }
        }
        return -1;
    }

    /** True when the stage's name fits its segment. */
    public boolean fits(int index, int textWidth) {
        for (Segment segment : segments) {
            if (segment.index() == index) {
                return textWidth + 2 <= segment.width();
            }
        }
        return false;
    }
}
