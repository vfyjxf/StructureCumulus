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
 * The catalogue half of design 17 §11's test law: the five shipped demonstration blueprints reach the
 * built payload with their stages, their cells and their material lines.
 */
class PreviewCatalogTest {

    private static final StructureKernel kernel = DemoDocuments.kernel();

    @Test
    void theFiveDemoIdsReachTheBuiltPayload() {
        PreviewCatalogPayload catalog = PreviewCatalog.build(kernel, DemoDocuments.ids);
        assertEquals(5, catalog.entries().size(), () -> catalog.entries().toString());
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
        assertEntry(
            catalog.entries().get(3),
            DemoDocuments.station,
            473,
            List.of(144, 96, 48, 8, 24, 27, 27, 27, 27, 45),
            stationMaterials()
        );
        assertEntry(
            catalog.entries().get(4),
            DemoDocuments.stationCompact,
            323,
            List.of(120, 72, 32, 12, 21, 21, 45),
            stationCompactMaterials()
        );
    }

    @Test
    void theTwoGroupsShipTheirSummariesAndTheTowerCarriesNone() {
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

        VariantSummary station = catalog.entries().get(3).variants();
        VariantSummary stationCompact = catalog.entries().get(4).variants();
        assertNotNull(station, "the station page is a group member");
        assertEquals(station, stationCompact, "both station members ship the same group summary");
        assertEquals(NamespacedId.of("structurecumulus", "demo_station"), station.group());
        assertEquals(
            List.of(
                NamespacedId.of("structurecumulus", "demo_station"),
                NamespacedId.of("structurecumulus", "demo_station_compact")
            ),
            station.members().stream().map(VariantSummary.Member::id).toList()
        );
        assertEquals(List.of("full", "compact"), station.members().stream().map(VariantSummary.Member::label).toList());
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

    private static Map<NamespacedId, Integer> stationMaterials() {
        return Map.ofEntries(
            Map.entry(NamespacedId.of("minecraft", "chiseled_copper"), 8),
            Map.entry(NamespacedId.of("minecraft", "chiseled_quartz_block"), 4),
            Map.entry(NamespacedId.of("minecraft", "chiseled_stone_bricks"), 12),
            Map.entry(NamespacedId.of("minecraft", "copper_block"), 28),
            Map.entry(NamespacedId.of("minecraft", "cut_copper"), 28),
            Map.entry(NamespacedId.of("minecraft", "deepslate_tiles"), 72),
            Map.entry(NamespacedId.of("minecraft", "end_rod"), 8),
            Map.entry(NamespacedId.of("minecraft", "glass"), 36),
            Map.entry(NamespacedId.of("minecraft", "lantern"), 8),
            Map.entry(NamespacedId.of("minecraft", "lightning_rod"), 8),
            Map.entry(NamespacedId.of("minecraft", "polished_andesite"), 36),
            Map.entry(NamespacedId.of("minecraft", "sea_lantern"), 17),
            Map.entry(NamespacedId.of("minecraft", "smooth_stone"), 68),
            Map.entry(NamespacedId.of("minecraft", "stone_brick_slab"), 56),
            Map.entry(NamespacedId.of("minecraft", "stone_brick_stairs"), 4),
            Map.entry(NamespacedId.of("minecraft", "stone_bricks"), 80)
        );
    }

    private static Map<NamespacedId, Integer> stationCompactMaterials() {
        return Map.ofEntries(
            Map.entry(NamespacedId.of("minecraft", "chiseled_copper"), 4),
            Map.entry(NamespacedId.of("minecraft", "chiseled_stone_bricks"), 12),
            Map.entry(NamespacedId.of("minecraft", "copper_block"), 16),
            Map.entry(NamespacedId.of("minecraft", "cut_copper"), 16),
            Map.entry(NamespacedId.of("minecraft", "deepslate_tiles"), 52),
            Map.entry(NamespacedId.of("minecraft", "end_rod"), 6),
            Map.entry(NamespacedId.of("minecraft", "glass"), 20),
            Map.entry(NamespacedId.of("minecraft", "lantern"), 8),
            Map.entry(NamespacedId.of("minecraft", "lightning_rod"), 4),
            Map.entry(NamespacedId.of("minecraft", "polished_andesite"), 36),
            Map.entry(NamespacedId.of("minecraft", "sea_lantern"), 11),
            Map.entry(NamespacedId.of("minecraft", "smooth_stone"), 22),
            Map.entry(NamespacedId.of("minecraft", "stone_brick_slab"), 32),
            Map.entry(NamespacedId.of("minecraft", "stone_brick_stairs"), 4),
            Map.entry(NamespacedId.of("minecraft", "stone_bricks"), 80)
        );
    }

}
