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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 16 §12.3's one formatter at its new home: the page's line and the session's branches. */
class NextStepLineTest {

    private static final NamespacedId bay = NamespacedId.of("structurecumulus", "base");
    private static final NamespacedId bricks = NamespacedId.of("minecraft", "stone_bricks");
    private static final NamespacedId bars = NamespacedId.of("minecraft", "iron_bars");
    private static final Stage stage = new Stage("structurecumulus:base", 9, 0, "lay the ring first", "the floor", bay);

    private static List<GhostCell> ghosts(NamespacedId block, int count) {
        List<GhostCell> cells = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cells.add(new GhostCell(LatticePoint.of(index, 0, 0), block, Map.of(), 0));
        }
        return List.copyOf(cells);
    }

    @Test
    void thePageLineReadsAsAPlaceWithItsStageAndHint() {
        assertEquals(
            "place 9\u00D7 minecraft:stone_bricks (stage 1/2: structurecumulus:base) \u00B7 lay the ring first",
            NextStepLine.render(ghosts(bricks, 9), 9, stage, 0, 2, false, false)
        );
    }

    @Test
    void theLargestDistinctItemWinsAndTiesKeepTheWireOrder() {
        List<GhostCell> group = new ArrayList<>(ghosts(bricks, 2));
        group.addAll(ghosts(bars, 3));
        assertEquals(
            "place 3\u00D7 minecraft:iron_bars (stage 1/2: structurecumulus:base) \u00B7 lay the ring first",
            NextStepLine.render(group, 5, stage, 0, 2, false, false)
        );
        List<GhostCell> tied = new ArrayList<>(ghosts(bricks, 2));
        tied.addAll(ghosts(bars, 2));
        assertTrue(
            NextStepLine.render(tied, 4, stage, 0, 2, false, false).startsWith("place 2\u00D7 minecraft:stone_bricks ")
        );
    }

    @Test
    void theSessionBranchesStayTheSameWording() {
        assertTrue(NextStepLine.render(ghosts(bricks, 8), 9, stage, 0, 2, false, true).startsWith("replace 8\u00D7 "));
        assertTrue(NextStepLine.render(ghosts(bricks, 8), 9, stage, 0, 2, false, false).startsWith("place 8\u00D7 "));
        assertEquals(
            "replace (stage 1/2: structurecumulus:base) \u00B7 lay the ring first",
            NextStepLine.render(List.of(), 2, stage, 0, 2, false, false)
        );
        assertTrue(NextStepLine.render(List.of(), 0, stage, 0, 2, true, false).startsWith("nothing left to place "));
        assertEquals("nothing left to place", NextStepLine.render(List.of(), 0, null, -1, 0, true, false));
    }
}
