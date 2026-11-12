package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.BayView;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stage derivation of design 16 §4: bay order, layer order, provenance, the cap and determinism. */
class StageModelTest {

    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");
    private static final NamespacedId iron = NamespacedId.of("minecraft", "iron_bars");
    private static final NamespacedId stairs = NamespacedId.of("minecraft", "oak_stairs");

    @Test
    void twoBaysKeepTheDeclarationOrder() {
        Blueprint blueprint = Blueprint.builder(NamespacedId.of("structurecumulus", "furnace"))
                .bounds(Cuboid.of(0, 0, 0, 3, 2, 3))
                .check(bayCheck("hearth", bay("hearth", Cuboid.ofSize(0, 0, 0, 3, 1, 3), "The firebox"), stone))
                .check(bayCheck("stack", bay("stack", Cuboid.ofSize(1, 1, 1, 1, 1, 1), "The chimney"), iron)).build();
        StageModel.Table table = StageModel.of(blueprint, declared(blueprint));
        assertEquals(2, table.stages().size());
        assertEquals("structurecumulus:hearth", table.stages().get(0).name());
        assertEquals(9, table.stages().get(0).cells().size());
        assertEquals("The firebox", table.stages().get(0).description());
        assertEquals("structurecumulus:stack", table.stages().get(1).name());
        assertEquals(1, table.stages().get(1).cells().size());
        assertEquals("The chimney", table.stages().get(1).description());
        assertEquals(NamespacedId.of("structurecumulus", "hearth"), table.stages().get(0).bay());
        assertEquals(NamespacedId.of("structurecumulus", "stack"), table.stages().get(1).bay());
        assertFalse(table.truncated());
    }

    @Test
    void aBayWhoseCheckExpectsNothingContributesNoStage() {
        Blueprint blueprint = Blueprint.builder(NamespacedId.of("structurecumulus", "void_bay"))
                .bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .check(bayCheck("kept", bay("kept", Cuboid.ofSize(0, 0, 0, 1, 1, 1), null), stone))
                .check(
                    Check.of(
                        NamespacedId.of("structurecumulus", "check_void"),
                        bay("void", Cuboid.ofSize(1, 0, 0, 1, 1, 1), "nothing here"),
                        Focuses.bay(),
                        Expectations.eachCell(Expectations.anyOccupant())
                    )
                ).build();
        StageModel.Table table = StageModel.of(blueprint, declared(blueprint));
        assertEquals(2, blueprint.bays().size());
        assertEquals(1, table.stages().size());
        assertEquals("structurecumulus:kept", table.stages().get(0).name());
    }

    @Test
    void aSingleBayWithThreeLayersGetsThreeStagesBottomUp() {
        Blueprint blueprint = Blueprint.builder(NamespacedId.of("structurecumulus", "tower"))
                .bounds(Cuboid.of(0, 0, 0, 1, 3, 1))
                .check(bayCheck("tower", bay("tower", Cuboid.ofSize(0, 0, 0, 1, 3, 1), "The tower"), stone)).build();
        StageModel.Table table = StageModel.of(blueprint, declared(blueprint));
        assertEquals(3, table.stages().size());
        assertEquals(List.of("layer 1", "layer 2", "layer 3"), names(table));
        assertEquals(0, table.stages().get(0).cells().get(0).y());
        assertEquals(2, table.stages().get(2).cells().get(0).y());
        assertEquals("The tower", table.stages().get(1).description());
        NamespacedId tower = NamespacedId.of("structurecumulus", "tower");
        for (StageModel.Stage stage : table.stages()) {
            assertEquals(tower, stage.bay());
        }
    }

    @Test
    void aLayerWithoutExpectedCellsDropsItsRank() {
        CuboidSet cells = CuboidSet.of(List.of(Cuboid.ofSize(0, 0, 0, 1, 1, 1), Cuboid.ofSize(0, 2, 0, 1, 1, 1)));
        Blueprint blueprint = Blueprint.builder(NamespacedId.of("structurecumulus", "gap"))
                .bounds(Cuboid.of(0, 0, 0, 1, 3, 1)).check(bayCheck("gap", bay("gap", cells, null), stone)).build();
        StageModel.Table table = StageModel.of(blueprint, declared(blueprint));
        assertEquals(List.of("layer 1", "layer 2"), names(table));
    }

    @Test
    void aSingleLayerBayCollapsesToOneStage() {
        Blueprint blueprint = Blueprint.builder(NamespacedId.of("structurecumulus", "pair"))
                .bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .check(bayCheck("cells", bay("cells", Cuboid.ofSize(0, 0, 0, 2, 1, 1), null), stone)).build();
        StageModel.Table table = StageModel.of(blueprint, declared(blueprint));
        assertEquals(1, table.stages().size());
        assertEquals("structurecumulus:cells", table.stages().get(0).name());
        assertEquals(2, table.stages().get(0).cells().size());
    }

    @Test
    void anEmptyExpectedSetGivesAnEmptyTable() {
        Blueprint blueprint = Blueprint.builder(NamespacedId.of("structurecumulus", "pair"))
                .bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .check(bayCheck("cells", bay("cells", Cuboid.ofSize(0, 0, 0, 2, 1, 1), null), stone)).build();
        StageModel.Table table = StageModel.of(blueprint, List.of());
        assertTrue(table.stages().isEmpty());
        assertEquals(0, table.expectedStages());
        assertFalse(table.truncated());
    }

    @Test
    void aStripInsideABodyKeepsItsOwnStage() {
        // the body is the ring around the port, so its box contains the port's cell without declaring it
        CuboidSet ring = CuboidSet.of(Cuboid.ofSize(0, 0, 0, 3, 1, 3))
                .subtract(CuboidSet.of(Cuboid.ofSize(1, 0, 1, 1, 1, 1)));
        Bay body = bay("body", ring, "the body");
        Bay port = bay("port", Cuboid.ofSize(1, 0, 1, 1, 1, 1), "the port");
        Blueprint blueprint = Blueprint.builder(NamespacedId.of("structurecumulus", "ported"))
                .bounds(Cuboid.of(0, 0, 0, 3, 1, 3)).check(bayCheck("body", body, stone))
                .check(bayCheck("port", port, iron)).build();
        StageModel.Table table = StageModel.of(blueprint, declared(blueprint));
        assertEquals(List.of("structurecumulus:body", "structurecumulus:port"), names(table));
        assertEquals(8, table.stages().get(0).cells().size());
        assertEquals(List.of(LatticePoint.of(1, 0, 1)), table.stages().get(1).cells());
    }

    @Test
    void theCellsArriveInCanonicalOrderWhateverTheInputOrder() {
        Blueprint blueprint = Blueprint.builder(NamespacedId.of("structurecumulus", "unordered"))
                .bounds(Cuboid.of(0, 0, 0, 2, 2, 2))
                .check(bayCheck("cells", bay("cells", Cuboid.ofSize(0, 0, 0, 2, 2, 2), null), stone)).build();
        List<LatticePoint> declared = declared(blueprint);
        StageModel.Table table = StageModel.of(blueprint, declared);
        for (StageModel.Stage stage : table.stages()) {
            List<LatticePoint> sorted = new ArrayList<>(stage.cells());
            sorted.sort(LatticePoint::compareTo);
            assertEquals(sorted, stage.cells());
        }
        List<LatticePoint> scrambled = new ArrayList<>(declared);
        Collections.reverse(scrambled);
        Collections.rotate(scrambled, 3);
        assertEquals(table, StageModel.of(blueprint, scrambled));
    }

    @Test
    void aPoseChangesTheStatesButNotTheTable() {
        Blueprint blueprint = Blueprint.builder(NamespacedId.of("structurecumulus", "posed"))
                .bounds(Cuboid.of(0, 0, 0, 2, 2, 2))
                .check(bayCheck("cells", bay("cells", Cuboid.ofSize(0, 0, 0, 2, 2, 2), null), stairs)).build();
        PreviewExpected.Outcome upright = PreviewExpected.of(blueprint, Orientation.identity);
        PreviewExpected.Outcome turned = PreviewExpected
                .of(blueprint, Orientation.of(Turn.clockwise, Tilt.level, Flip.none));
        assertEquals(upright.states().keySet(), turned.states().keySet());
        assertNotEquals(upright.states(), turned.states());
        assertEquals(
            StageModel.of(blueprint, upright.states().keySet()),
            StageModel.of(blueprint, turned.states().keySet())
        );
    }

    @Test
    void theTableIsCappedAndKeepsTheFullCount() {
        Blueprint blueprint = Blueprint.builder(NamespacedId.of("structurecumulus", "tall"))
                .bounds(Cuboid.of(0, 0, 0, 1, 300, 1))
                .check(bayCheck("tall", bay("tall", Cuboid.ofSize(0, 0, 0, 1, 300, 1), null), stone)).build();
        StageModel.Table table = StageModel.of(blueprint, declared(blueprint));
        assertEquals(StageModel.maxStages, table.stages().size());
        assertEquals(300, table.expectedStages());
        assertTrue(table.truncated());
    }

    @Test
    void aBayHintReachesEveryStageTheBayProduces() {
        Blueprint blueprint = Blueprint.builder(NamespacedId.of("structurecumulus", "hinted"))
                .bounds(Cuboid.of(0, 0, 0, 3, 2, 1))
                .check(
                    bayCheck(
                        "base",
                        bay("base", CuboidSet.of(Cuboid.ofSize(0, 0, 0, 3, 1, 1)), "the floor", "lay it first"),
                        stone
                    )
                )
                .check(
                    bayCheck(
                        "top",
                        bay("top", CuboidSet.of(Cuboid.ofSize(0, 1, 0, 3, 1, 1)), null, "cap it last"),
                        iron
                    )
                ).build();
        StageModel.Table table = StageModel.of(blueprint, declared(blueprint));
        assertEquals("lay it first", table.stages().get(0).hint());
        assertEquals("cap it last", table.stages().get(1).hint());

        Blueprint layered = Blueprint.builder(NamespacedId.of("structurecumulus", "layered"))
                .bounds(Cuboid.of(0, 0, 0, 1, 2, 1))
                .check(
                    bayCheck(
                        "tower",
                        bay("tower", CuboidSet.of(Cuboid.ofSize(0, 0, 0, 1, 2, 1)), null, "one ring per layer"),
                        stone
                    )
                ).build();
        StageModel.Table rings = StageModel.of(layered, declared(layered));
        assertEquals(2, rings.stages().size());
        assertEquals("one ring per layer", rings.stages().get(0).hint());
        assertEquals("one ring per layer", rings.stages().get(1).hint());
    }

    private static List<LatticePoint> declared(Blueprint blueprint) {
        List<LatticePoint> cells = new ArrayList<>();
        for (Check<?> check : blueprint.checks()) {
            cells.addAll(PreviewExpected.declared(check));
        }
        return cells;
    }

    private static List<String> names(StageModel.Table table) {
        List<String> names = new ArrayList<>(table.stages().size());
        for (StageModel.Stage stage : table.stages()) {
            names.add(stage.name());
        }
        return names;
    }

    private static Bay bay(String id, Cuboid cells, @Nullable String description) {
        return bay(id, CuboidSet.of(cells), description);
    }

    private static Bay bay(String id, CuboidSet cells, @Nullable String description) {
        return bay(id, cells, description, null);
    }

    private static Bay bay(String id, CuboidSet cells, @Nullable String description, @Nullable String hint) {
        return new Bay(
            NamespacedId.of("structurecumulus", id),
            cells,
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared,
            description,
            hint
        );
    }

    private static Check<BayView> bayCheck(String id, Bay bay, NamespacedId block) {
        return Check.of(
            NamespacedId.of("structurecumulus", "check_" + id),
            bay,
            Focuses.bay(),
            Expectations.eachCell(Expectations.block(block))
        );
    }
}
