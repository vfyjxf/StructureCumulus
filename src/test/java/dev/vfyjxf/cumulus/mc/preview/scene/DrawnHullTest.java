package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drawn-hull law of design 17 §3.3: a cell that draws nothing contributes no corner. */
class DrawnHullTest {

    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");
    private static final NamespacedId air = NamespacedId.of("minecraft", "air");
    private static final float eps = 1.0e-5F;
    private static final Predicate<PreviewScene.Cell> drawsStone = cell -> cell.ghost().block().equals(stone);

    @Test
    void anAirCellContributesNoCorner() {
        PreviewScene.Cell corner = cell(0, 0, 0, stone);
        PreviewScene.Cell padding = cell(9, 9, 9, air);
        Hull hull = PreviewScene.hullOf(List.of(corner, padding), drawsStone);
        assertEquals(0.0F, hull.minX(), eps);
        assertEquals(1.0F, hull.maxX(), eps);
        assertEquals(1.0F, hull.maxY(), eps);
        assertEquals(1.0F, hull.maxZ(), eps);
    }

    @Test
    void aPaddingCellBetweenDrawnCellsDoesNotWidenTheBox() {
        Hull hull = PreviewScene
                .hullOf(List.of(cell(0, 0, 0, stone), cell(5, 0, 0, air), cell(1, 0, 0, stone)), drawsStone);
        assertEquals(0.0F, hull.minX(), eps);
        assertEquals(2.0F, hull.maxX(), eps);
    }

    @Test
    void anEntryWhoseEveryCellIsAirHasAnEmptyHull() {
        Hull hull = PreviewScene.hullOf(List.of(cell(0, 0, 0, air), cell(2, 2, 2, air)), drawsStone);
        assertTrue(hull.isEmpty());
        assertEquals(1.0F, hull.fitBox().maxX() - hull.fitBox().minX(), eps);
    }

    @Test
    void cellsAreBoxedCornerToCorner() {
        Hull hull = PreviewScene.hullOf(List.of(cell(2, 3, 4, stone)), drawsStone);
        assertFalse(hull.isEmpty());
        assertEquals(2.0F, hull.minX(), eps);
        assertEquals(3.0F, hull.minY(), eps);
        assertEquals(4.0F, hull.minZ(), eps);
        assertEquals(3.0F, hull.maxX(), eps);
        assertEquals(4.0F, hull.maxY(), eps);
        assertEquals(5.0F, hull.maxZ(), eps);
    }

    private static PreviewScene.Cell cell(int x, int y, int z, NamespacedId block) {
        return new PreviewScene.Cell(new GhostCell(LatticePoint.of(x, y, z), block, Map.of(), 0), null);
    }
}
