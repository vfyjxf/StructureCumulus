package dev.vfyjxf.cumulus.mc.preview.client;

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

/**
 * The guide's one formatter and its pulse: a fixed vocabulary over payload facts, decided without a
 * client (design 16 §12.3, §12.4).
 */
class GuideTextTest {

    private static final NamespacedId baseBay = NamespacedId.of("structurecumulus", "base");
    private static final NamespacedId stackBay = NamespacedId.of("structurecumulus", "stack");
    private static final Stage base = new Stage(
        "structurecumulus:base",
        9,
        0,
        "lay the ring first",
        "the floor",
        baseBay
    );
    private static final Stage stack = new Stage(
        "structurecumulus:stack",
        3,
        0,
        "place it after the floor",
        null,
        stackBay
    );
    private static final NamespacedId bricks = NamespacedId.of("minecraft", "stone_bricks");
    private static final NamespacedId bars = NamespacedId.of("minecraft", "iron_bars");

    @Test
    void theLineNamesTheVerbTheCountTheItemAndTheStage() {
        String line = GuideController.line(ghosts(bricks, 9), 9, base, 0, 2, false, false);
        assertEquals(
            "place 9\u00D7 minecraft:stone_bricks (stage 1/2: structurecumulus:base) \u00B7 lay the ring first",
            line
        );
    }

    @Test
    void theRootDescriptionIsNotPartOfTheLine() {
        String line = GuideController.line(ghosts(bars, 3), 3, stack, 1, 2, false, false);
        assertEquals(
            "place 3\u00D7 minecraft:iron_bars (stage 2/2: structurecumulus:stack) \u00B7 place it after the floor",
            line
        );
    }

    @Test
    void aWrongBlockInTheStageSwitchesTheVerbToReplace() {
        String line = GuideController.line(ghosts(bricks, 8), 9, base, 0, 2, false, true);
        assertTrue(line.startsWith("replace 8\u00D7 minecraft:stone_bricks "), line);
    }

    @Test
    void aShortGhostListAloneDoesNotSwitchTheVerb() {
        // the render cap shortens a ghost list; with no wrong or blocked mark the world is still placeable
        String line = GuideController.line(ghosts(bricks, 8), 9, base, 0, 2, false, false);
        assertTrue(line.startsWith("place 8\u00D7 minecraft:stone_bricks "), line);
    }

    @Test
    void anEmptyGroupWithCellsLeftNamesReplace() {
        // every unsatisfied cell draws no ghost: something else holds it, or it cannot be read
        String line = GuideController.line(List.of(), 2, stack, 1, 2, false, false);
        assertTrue(line.startsWith("replace (stage 2/2: structurecumulus:stack) "), line);
    }

    @Test
    void anEmptyGroupNamesTheClaimPathAndTheEmptyReport() {
        String line = GuideController.line(List.of(), 0, stack, 1, 2, true, false);
        assertEquals(
            "nothing left to place (stage 2/2: structurecumulus:stack) \u00B7 settle takes the claim; a confirm builds nothing",
            line
        );
    }

    @Test
    void anEmptyTableDropsTheParenthetical() {
        assertEquals("nothing left to place", GuideController.line(List.of(), 0, null, -1, 0, true, false));
    }

    @Test
    void theLargestDistinctItemWinsAndTiesKeepTheWireOrder() {
        List<GhostCell> group = new ArrayList<>(ghosts(bricks, 2));
        group.addAll(ghosts(bars, 3));
        String line = GuideController.line(group, 5, base, 0, 2, false, false);
        assertTrue(line.startsWith("place 3\u00D7 minecraft:iron_bars "), line);
        List<GhostCell> tied = new ArrayList<>(ghosts(bricks, 2));
        tied.addAll(ghosts(bars, 2));
        assertTrue(
            GuideController.line(tied, 4, base, 0, 2, false, false).startsWith("place 2\u00D7 minecraft:stone_bricks ")
        );
    }

    @Test
    void thePulseOscillatesBetweenTheFloorAndThePeakEveryPeriod() {
        assertEquals(GuideController.pulsePeak, GuideController.pulseAt(10), 1.0E-4F);
        assertEquals(GuideController.pulseFloor, GuideController.pulseAt(30), 1.0E-4F);
        assertEquals(
            (GuideController.pulseFloor + GuideController.pulsePeak) / 2.0F,
            GuideController.pulseAt(0),
            1.0E-4F
        );
        assertEquals(
            GuideController.pulseAt(3),
            GuideController.pulseAt(3 + GuideController.pulsePeriodTicks),
            1.0E-4F
        );
        for (int tick = 0; tick < 2 * GuideController.pulsePeriodTicks; tick++) {
            float factor = GuideController.pulseAt(tick);
            assertTrue(factor >= GuideController.pulseFloor && factor <= GuideController.pulsePeak, "tick " + tick);
        }
    }

    private static List<GhostCell> ghosts(NamespacedId block, int count) {
        List<GhostCell> cells = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cells.add(new GhostCell(LatticePoint.of(index % 3, 100, index / 3), block, Map.of(), 0));
        }
        return cells;
    }
}
