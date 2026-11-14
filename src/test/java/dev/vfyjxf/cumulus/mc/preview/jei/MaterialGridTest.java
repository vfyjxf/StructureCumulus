package dev.vfyjxf.cumulus.mc.preview.jei;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The material summary's grid metric of design 15 §6.2: 1, 4 and 8 materials fill units that never
 * touch, and 16 trades the eighth for a +N that sits in the grid and answers its own tooltip.
 */
class MaterialGridTest {

    private static final int originX = BlueprintPreviewCategory.materialsX;
    private static final int originY = BlueprintPreviewCategory.materialsY;

    /** Every face button the summary must clear, in the face's own pixels. */
    private static final List<Rect> buttons = List.of(
        new Rect(
            BlueprintPreviewCategory.buttonX,
            BlueprintPreviewCategory.buttonY,
            BlueprintPreviewCategory.buttonWidth,
            BlueprintPreviewCategory.buttonHeight
        ),
        new Rect(
            BlueprintPreviewCategory.modeX,
            BlueprintPreviewCategory.modeY,
            BlueprintPreviewCategory.modeWidth,
            BlueprintPreviewCategory.modeHeight
        ),
        new Rect(
            BlueprintPreviewCategory.constraintsX,
            BlueprintPreviewCategory.constraintsY,
            BlueprintPreviewCategory.constraintsWidth,
            BlueprintPreviewCategory.constraintsHeight
        ),
        new Rect(
            BlueprintPreviewCategory.variantX,
            BlueprintPreviewCategory.variantY,
            BlueprintPreviewCategory.variantWidth,
            BlueprintPreviewCategory.variantHeight
        ),
        new Rect(
            BlueprintPreviewCategory.screenX,
            BlueprintPreviewCategory.screenY,
            BlueprintPreviewCategory.screenWidth,
            BlueprintPreviewCategory.screenHeight
        )
    );

    @Test
    void oneFourAndEightMaterialsFillDisjointUnitsClearOfEveryButton() {
        for (int materials : new int[]{1, 4, 8}) {
            List<MaterialGrid.Unit> units = MaterialGrid.units(materials, originX, originY);
            assertEquals(materials, units.size(), () -> materials + " materials draw one unit each");
            assertTrue(units.stream().noneMatch(MaterialGrid.Unit::overflow), () -> materials + " fits");
            assertDisjoint(units);
            assertInsideGrid(units);
            assertClearOfButtons(units);
        }
    }

    @Test
    void theEightUnitGridIsTwoRowsOfFourInTheFacesMaterialBlock() {
        List<MaterialGrid.Unit> units = MaterialGrid.units(MaterialGrid.capacity, originX, originY);
        assertEquals(MaterialGrid.columns * MaterialGrid.cellWidth, BlueprintPreviewCategory.materialsWidth);
        assertEquals(MaterialGrid.rows * MaterialGrid.cellHeight, BlueprintPreviewCategory.materialsHeight);
        assertEquals(originX + BlueprintPreviewCategory.materialsWidth, units.get(3).right(), "four columns fill it");
        assertEquals(originY + BlueprintPreviewCategory.materialsHeight, units.get(7).bottom(), "two rows fill it");
        assertDisjoint(units);
        assertClearOfButtons(units);
    }

    @Test
    void sixteenMaterialsTradeTheLastUnitForTheOverflowMarker() {
        List<MaterialGrid.Unit> units = MaterialGrid.units(16, originX, originY);
        assertEquals(MaterialGrid.capacity, units.size(), "the marker keeps the eight-unit footprint");
        assertEquals(7, units.stream().filter(unit -> !unit.overflow()).count(), "seven icons and the +N");
        MaterialGrid.Unit marker = units.get(7);
        assertTrue(marker.overflow(), "the marker is the last unit");
        assertEquals(-1, marker.index(), "the marker is not a material index");
        assertEquals(9, MaterialGrid.overflow(16), "sixteen less the seven drawn is nine");
        assertEquals(originX + 3 * MaterialGrid.cellWidth, marker.x());
        assertEquals(originY + MaterialGrid.cellHeight, marker.y());
        assertDisjoint(units);
        assertInsideGrid(units);
        assertClearOfButtons(units);
    }

    @Test
    void theMarkerExplainsTheMaterialsItHidesAndNothingElse() {
        MaterialGrid.Unit marker = MaterialGrid.units(16, originX, originY).get(7);
        assertEquals(9, MaterialGrid.overflowAt(16, originX, originY, marker.x() + 10, marker.y() + 12));
        assertEquals(0, MaterialGrid.overflowAt(8, originX, originY, marker.x() + 10, marker.y() + 12));
        assertEquals(0, MaterialGrid.overflowAt(16, originX, originY, originX, originY), "a material is not the +N");
        assertEquals(
            -1,
            MaterialGrid.materialAt(16, originX, originY, marker.x() + 10, marker.y() + 12),
            "the marker reports no material"
        );
    }

    @Test
    void everyOverCapacityCountStillDrawsSevenUnitsAndOneMarker() {
        for (int materials : new int[]{9, 16, 64}) {
            List<MaterialGrid.Unit> units = MaterialGrid.units(materials, originX, originY);
            assertEquals(MaterialGrid.capacity, units.size(), () -> materials + " keeps eight units");
            assertEquals(7, units.stream().filter(unit -> !unit.overflow()).count());
            assertEquals(materials - 7, MaterialGrid.overflow(materials));
            assertDisjoint(units);
            assertInsideGrid(units);
            assertClearOfButtons(units);
        }
    }

    private static void assertDisjoint(List<MaterialGrid.Unit> units) {
        for (int first = 0; first < units.size(); first++) {
            for (int second = first + 1; second < units.size(); second++) {
                MaterialGrid.Unit a = units.get(first);
                MaterialGrid.Unit b = units.get(second);
                assertFalse(a.overlaps(b), () -> a + " overlaps " + b);
            }
        }
    }

    private static void assertInsideGrid(List<MaterialGrid.Unit> units) {
        for (MaterialGrid.Unit unit : units) {
            assertTrue(
                unit.x() >= originX
                        && unit.y() >= originY
                        && unit.right() <= originX + BlueprintPreviewCategory.materialsWidth
                        && unit.bottom() <= originY + BlueprintPreviewCategory.materialsHeight,
                () -> unit + " leaves the material block"
            );
        }
    }

    private static void assertClearOfButtons(List<MaterialGrid.Unit> units) {
        for (MaterialGrid.Unit unit : units) {
            for (Rect button : buttons) {
                assertFalse(button.intersects(unit), () -> unit + " presses " + button);
            }
        }
    }

    private record Rect(int x, int y, int width, int height) {

        boolean intersects(MaterialGrid.Unit unit) {
            return x < unit.right() && unit.x() < x + width && y < unit.bottom() && unit.y() < y + height;
        }
    }
}
