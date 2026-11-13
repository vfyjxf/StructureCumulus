package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import dev.vfyjxf.cumulus.mc.preview.net.VariantSummary;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The catalogue half of design 17 §11's test law: the three shipped demonstration documents reach the
 * built payload with their stages, their cells and their material lines.
 */
class PreviewCatalogTest {

    private static final StructureKernel kernel = DemoDocuments.kernel();

    @Test
    void theThreeDemoIdsReachTheBuiltPayload() {
        PreviewCatalogPayload catalog = PreviewCatalog.build(kernel, DemoDocuments.ids);
        assertEquals(3, catalog.entries().size(), () -> catalog.entries().toString());
        assertEquals(
            DemoDocuments.ids,
            catalog.entries().stream().map(PreviewCatalogPayload.Entry::blueprintId).toList()
        );
    }

    @Test
    void eachEntryCarriesItsStagesCellsAndMaterials() {
        PreviewCatalogPayload catalog = PreviewCatalog.build(kernel, DemoDocuments.ids);
        assertEntry(catalog.entries().get(0), DemoDocuments.machine, 100, List.of(25, 32, 18, 25), machineMaterials());
        assertEntry(catalog.entries().get(1), DemoDocuments.compact, 54, List.of(9, 32, 4, 9), compactMaterials());
        assertEntry(catalog.entries().get(2), DemoDocuments.tower, 128, List.of(25, 78, 25), towerMaterials());
    }

    @Test
    void theMachinePairCarriesOneGroupAndTheTowerCarriesNone() {
        PreviewCatalogPayload catalog = PreviewCatalog.build(kernel, DemoDocuments.ids);
        VariantSummary machine = catalog.entries().get(0).variants();
        VariantSummary compact = catalog.entries().get(1).variants();
        assertNotNull(machine, "the machine page is a group member");
        assertEquals(machine, compact, "both members ship the same group summary");
        assertEquals(NamespacedId.of("structurecumulus", "demo_machine"), machine.group());
        assertEquals(
            List.of(
                NamespacedId.of("structurecumulus", "demo_machine"),
                NamespacedId.of("structurecumulus", "demo_machine_compact")
            ),
            machine.members().stream().map(VariantSummary.Member::id).toList()
        );
        assertEquals(List.of("full", "compact"), machine.members().stream().map(VariantSummary.Member::label).toList());
        assertNull(catalog.entries().get(2).variants(), "the tower is its own page");
    }

    private static void assertEntry(
        PreviewCatalogPayload.Entry entry,
        NamespacedId id,
        int cells,
        List<Integer> stageCells,
        Map<NamespacedId, Integer> materials
    ) {
        assertEquals(id, entry.blueprintId());
        assertEquals(cells, entry.cells().size(), () -> id + " ships its drawn cells");
        assertEquals(stageCells.size(), entry.presentation().stages().size(), () -> id + " ships its stage table");
        assertEquals(0, entry.presentation().currentStage(), () -> id + " opens on its first stage");
        assertNotNull(entry.presentation().description(), () -> id + " ships its root description");
        assertEquals(materials, materials(entry), () -> id + " ships its material lines");

        Map<Integer, Integer> perStage = new LinkedHashMap<>();
        for (var cell : entry.cells()) {
            perStage.merge(cell.stageIndex(), 1, Integer::sum);
        }
        for (int index = 0; index < stageCells.size(); index++) {
            int at = index;
            Stage stage = entry.presentation().stages().get(at);
            assertEquals(stageCells.get(at), stage.expectedCells(), () -> id + " stage " + at + " count");
            assertEquals(stageCells.get(at), perStage.get(at), () -> id + " stage " + at + " ghosts");
        }
    }

    private static Map<NamespacedId, Integer> materials(PreviewCatalogPayload.Entry entry) {
        Map<NamespacedId, Integer> lines = new LinkedHashMap<>();
        for (PreviewCatalogPayload.MaterialLine line : entry.materials()) {
            lines.put(line.item(), line.count());
        }
        return lines;
    }

    private static Map<NamespacedId, Integer> machineMaterials() {
        return Map.of(
            NamespacedId.of("minecraft", "stone_bricks"),
            21,
            NamespacedId.of("minecraft", "chiseled_stone_bricks"),
            4,
            NamespacedId.of("minecraft", "glass"),
            28,
            NamespacedId.of("minecraft", "iron_bars"),
            4,
            NamespacedId.of("minecraft", "copper_block"),
            17,
            NamespacedId.of("minecraft", "sea_lantern"),
            1,
            NamespacedId.of("minecraft", "oak_stairs"),
            24,
            NamespacedId.of("minecraft", "hopper"),
            1
        );
    }

    private static Map<NamespacedId, Integer> compactMaterials() {
        return Map.of(
            NamespacedId.of("minecraft", "stone_bricks"),
            5,
            NamespacedId.of("minecraft", "chiseled_stone_bricks"),
            4,
            NamespacedId.of("minecraft", "glass"),
            28,
            NamespacedId.of("minecraft", "iron_bars"),
            4,
            NamespacedId.of("minecraft", "copper_block"),
            3,
            NamespacedId.of("minecraft", "sea_lantern"),
            1,
            NamespacedId.of("minecraft", "oak_stairs"),
            8,
            NamespacedId.of("minecraft", "hopper"),
            1
        );
    }

    private static Map<NamespacedId, Integer> towerMaterials() {
        return Map.of(
            NamespacedId.of("minecraft", "stone_bricks"),
            21,
            NamespacedId.of("minecraft", "chiseled_stone_bricks"),
            8,
            NamespacedId.of("minecraft", "oak_planks"),
            70,
            NamespacedId.of("minecraft", "glass"),
            4,
            NamespacedId.of("minecraft", "oak_stairs"),
            25
        );
    }
}
