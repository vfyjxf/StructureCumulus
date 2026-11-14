package dev.vfyjxf.cumulus.mc.preview.jei;

import java.util.ArrayList;
import java.util.List;

/**
 * The material summary's grid metric of design 15 §6.2: four 20-px columns of two 25-px units, each a
 * 16-px icon over its centred count line, so no two units touch; a page over the eight units trades
 * its last one for the red +N marker, which then answers the tooltip itself.
 */
final class MaterialGrid {

    /** The face's 80-px material block: four columns of 20-px units. */
    static final int columns = 4;
    /** The face's 50-px material block: two rows of 25-px units. */
    static final int rows = 2;
    static final int cellWidth = 20;
    static final int iconSize = 16;
    /** The icon sits centred in its unit, so the count under it shares the same centre. */
    static final int iconInset = (cellWidth - iconSize) / 2;
    /** Vanilla's 9-px line, the count's and the marker's one line under the icon. */
    static final int textHeight = 9;
    static final int cellHeight = iconSize + textHeight;
    /** The eight slots: seven materials and the +N marker when the page overflows. */
    static final int capacity = columns * rows;

    private MaterialGrid() {}

    /** One unit of the grid: the material at {@code index}, or the +N marker when {@code overflow}. */
    record Unit(int index, int x, int y, int width, int height, boolean overflow) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean overlaps(Unit other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
    }

    /** How many material icons the grid draws: one fewer than its slots when the page overflows. */
    static int shownMaterials(int materialCount) {
        if (materialCount <= 0) {
            return 0;
        }
        return materialCount > capacity ? capacity - 1 : materialCount;
    }

    /** The materials behind the +N marker; 0 when every one is drawn. */
    static int overflow(int materialCount) {
        return Math.max(0, materialCount - shownMaterials(materialCount));
    }

    /** The grid's units at one face origin, row-major: the shown materials, then the +N marker. */
    static List<Unit> units(int materialCount, int originX, int originY) {
        int shown = shownMaterials(materialCount);
        List<Unit> units = new ArrayList<>(capacity);
        for (int index = 0; index < shown; index++) {
            units.add(unit(index, index, originX, originY, false));
        }
        if (overflow(materialCount) > 0) {
            units.add(unit(shown, -1, originX, originY, true));
        }
        return List.copyOf(units);
    }

    /** The material index under a face-local point, or -1; the +N marker answers with no material. */
    static int materialAt(int materialCount, int originX, int originY, double mouseX, double mouseY) {
        for (Unit unit : units(materialCount, originX, originY)) {
            if (!unit.overflow() && contains(unit, mouseX, mouseY)) {
                return unit.index();
            }
        }
        return -1;
    }

    /** The hidden count when the +N marker is under a face-local point, else 0. */
    static int overflowAt(int materialCount, int originX, int originY, double mouseX, double mouseY) {
        int hidden = overflow(materialCount);
        if (hidden <= 0) {
            return 0;
        }
        List<Unit> units = units(materialCount, originX, originY);
        Unit marker = units.get(units.size() - 1);
        return contains(marker, mouseX, mouseY) ? hidden : 0;
    }

    private static boolean contains(Unit unit, double mouseX, double mouseY) {
        return mouseX >= unit.x()
                && mouseX < unit.x() + unit.width()
                && mouseY >= unit.y()
                && mouseY < unit.y() + unit.height();
    }

    private static Unit unit(int slot, int index, int originX, int originY, boolean overflow) {
        return new Unit(
            index,
            originX + (slot % columns) * cellWidth,
            originY + (slot / columns) * cellHeight,
            cellWidth,
            cellHeight,
            overflow
        );
    }
}
