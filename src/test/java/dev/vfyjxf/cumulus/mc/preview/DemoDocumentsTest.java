package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompileResult;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompiler;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.VariantGroup;
import dev.vfyjxf.cumulus.kernel.structure.VariantMember;
import dev.vfyjxf.cumulus.mc.StructureSet;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The demo content of design 17 §11: the five shipped blueprints compile, keep the catalogue's
 * identity projection drawable, carry the bay and stage counts the design names, repeat their group
 * declaration verbatim, and the station's four pods are one module at four turns; the campus set
 * compiles, places and passes the set's siting rules.
 */
class DemoDocumentsTest {

    /** The orientation-bearing families the flagship's authoring law counts (design 17 §11). */
    private static final Set<NamespacedId> orientationBearing = Set.of(
        NamespacedId.of("minecraft", "stone_brick_slab"),
        NamespacedId.of("minecraft", "stone_brick_stairs"),
        NamespacedId.of("minecraft", "end_rod"),
        NamespacedId.of("minecraft", "lantern"),
        NamespacedId.of("minecraft", "lightning_rod")
    );

    /** The station's pods, in the order they are placed, with the turn each one is the north pod at. */
    private static final Map<NamespacedId, Integer> podTurns = new LinkedHashMap<>();

    static {
        podTurns.put(NamespacedId.of("structurecumulus", "pod_north"), 0);
        podTurns.put(NamespacedId.of("structurecumulus", "pod_east"), 1);
        podTurns.put(NamespacedId.of("structurecumulus", "pod_south"), 2);
        podTurns.put(NamespacedId.of("structurecumulus", "pod_west"), 3);
    }

    private static final StructureKernel kernel = DemoDocuments.kernel();

    @Test
    void theFiveShippedDocumentsCompileWithoutFindings() {
        for (NamespacedId id : DemoDocuments.ids) {
            Blueprint blueprint = DemoDocuments.compiled(kernel, id);
            assertEquals(id, blueprint.id(), () -> id + " keeps its declared root id");
            assertFalse(blueprint.bays().isEmpty(), () -> id + " declares bays");
            assertFalse(blueprint.checks().isEmpty(), () -> id + " declares checks");
        }
    }

    @Test
    void theIdentityProjectionReportsNoFilter() {
        for (NamespacedId id : DemoDocuments.ids) {
            PreviewExpected.Outcome outcome = PreviewExpected
                    .of(DemoDocuments.compiled(kernel, id), Orientation.identity);
            assertNull(outcome.filter(), () -> id + " cannot express the identity orientation");
            assertFalse(outcome.states().isEmpty(), () -> id + " draws nothing");
            assertTrue(
                hasDirectionalState(outcome.states()),
                () -> id + " carries at least one direction-sensitive block"
            );
        }
    }

    @Test
    void theBayAndStageCountsAreTheOnesTheDesignNames() {
        assertEquals(
            List.of(
                "structurecumulus:plinth",
                "structurecumulus:housing",
                "structurecumulus:core",
                "structurecumulus:press"
            ),
            stageNames(DemoDocuments.machine)
        );
        assertEquals(List.of(25, 32, 18, 25), stageCells(DemoDocuments.machine));
        assertEquals(100, DemoDocuments.compiled(kernel, DemoDocuments.machine).bounds().volume());

        assertEquals(
            List.of(
                "structurecumulus:base",
                "structurecumulus:shell",
                "structurecumulus:boiler",
                "structurecumulus:cap"
            ),
            stageNames(DemoDocuments.compact)
        );
        assertEquals(List.of(9, 32, 4, 9), stageCells(DemoDocuments.compact));

        assertEquals(
            List.of("structurecumulus:plinth", "structurecumulus:shell", "structurecumulus:roof"),
            stageNames(DemoDocuments.tower)
        );
        assertEquals(List.of(25, 78, 25), stageCells(DemoDocuments.tower));

        assertEquals(
            List.of(
                "structurecumulus:plinth",
                "structurecumulus:core_low",
                "structurecumulus:core_mid",
                "structurecumulus:core_high",
                "structurecumulus:arms",
                "structurecumulus:pod_north",
                "structurecumulus:pod_east",
                "structurecumulus:pod_south",
                "structurecumulus:pod_west",
                "structurecumulus:crown"
            ),
            stageNames(DemoDocuments.station)
        );
        assertEquals(
            List.of(144, 96, 48, 8, 24, 27, 27, 27, 27, 45),
            stageCells(DemoDocuments.station),
            "ten bays, ten stages, and each stage is its own part"
        );
        assertArrayEquals(new int[]{0, 0, 0, 13, 11, 13}, bounds(DemoDocuments.station));

        assertEquals(
            List.of(
                "structurecumulus:plinth",
                "structurecumulus:core_low",
                "structurecumulus:core_mid",
                "structurecumulus:arms",
                "structurecumulus:pod_north",
                "structurecumulus:pod_south",
                "structurecumulus:crown"
            ),
            stageNames(DemoDocuments.stationCompact)
        );
        assertEquals(List.of(120, 72, 32, 12, 21, 21, 45), stageCells(DemoDocuments.stationCompact));
    }

    @Test
    void theStationSitsInsideTheCatalogueBandFromBothSides() {
        PreviewExpected.Outcome outcome = PreviewExpected
                .of(DemoDocuments.compiled(kernel, DemoDocuments.station), Orientation.identity);
        assertNull(outcome.filter());
        assertTrue(
            outcome.states().size() >= 450,
            () -> "the flagship cannot shrink into another machine: " + outcome.states().size()
        );
        assertTrue(
            outcome.states().size() <= 512,
            () -> "the flagship cannot overflow the catalogue cap: " + outcome.states().size()
        );
        assertEquals(473, outcome.states().size());

        long orientationBearingCells = outcome.states().values().stream()
                .map(state -> PreviewExpected.blockId(state.getBlock())).filter(orientationBearing::contains).count();
        assertTrue(
            orientationBearingCells >= 40,
            () -> "the flagship carries " + orientationBearingCells + " orientation-bearing cells"
        );

        Set<NamespacedId> materials = new HashSet<>();
        for (BlockState state : outcome.states().values()) {
            materials.add(PreviewExpected.blockId(state.getBlock()));
        }
        assertTrue(materials.size() <= 64, () -> materials.size() + " materials exceed the catalogue cap");
    }

    @Test
    void theFourStationPodsAreOneModuleAtFourTurns() {
        Blueprint station = DemoDocuments.compiled(kernel, DemoDocuments.station);
        PreviewExpected.Outcome outcome = PreviewExpected.of(station, Orientation.identity);
        assertNull(outcome.filter());
        int center = station.bounds().min().x() + (station.bounds().sizeX() - 1) / 2;

        Map<LatticePoint, NamespacedId> north = null;
        Set<LatticePoint> claimed = new HashSet<>();
        for (Map.Entry<NamespacedId, Integer> pod : podTurns.entrySet()) {
            Bay bay = bay(station, pod.getKey());
            assertNotNull(bay, () -> pod.getKey() + " is a station bay");
            Map<LatticePoint, NamespacedId> local = new TreeMap<>();
            for (LatticePoint cell : PreviewExpected.cells(bay.cells())) {
                BlockState state = outcome.states().get(cell);
                assertNotNull(state, () -> pod.getKey() + " leaves " + cell + " undrawn");
                assertTrue(claimed.add(cell), () -> pod.getKey() + " repeats the cell " + cell);
                local.put(unrotate(cell, pod.getValue(), center), PreviewExpected.blockId(state.getBlock()));
            }
            assertEquals(27, local.size(), () -> pod.getKey() + " is one module of 27 cells");
            if (north == null) {
                north = local;
            } else {
                assertEquals(north, local, () -> pod.getKey() + " is not the north pod at turn " + pod.getValue());
            }
        }
        assertEquals(108, claimed.size(), "the four pods are four disjoint copies");
    }

    @Test
    void theTwoStationMembersRepeatTheirGroupDeclarationVerbatim() {
        Blueprint station = DemoDocuments.compiled(kernel, DemoDocuments.station);
        Blueprint compact = DemoDocuments.compiled(kernel, DemoDocuments.stationCompact);

        assertEquals(
            DemoDocuments.variants(DemoDocuments.station),
            DemoDocuments.variants(DemoDocuments.stationCompact),
            "the two members write the same variants block"
        );

        VariantGroup group = station.variants();
        assertNotNull(group, "the station declares a group");
        assertEquals(NamespacedId.of("structurecumulus", "demo_station"), group.group());
        assertEquals(
            List.of(
                NamespacedId.of("structurecumulus", "demo_station"),
                NamespacedId.of("structurecumulus", "demo_station_compact")
            ),
            group.members().stream().map(VariantMember::id).toList()
        );
        assertEquals(List.of("full", "compact"), group.members().stream().map(VariantMember::label).toList());
        assertEquals(group, compact.variants(), "the compact member carries the same compiled group");
    }

    @Test
    void theTwoMachineMembersRepeatTheirGroupDeclarationVerbatim() {
        Blueprint machine = DemoDocuments.compiled(kernel, DemoDocuments.machine);
        Blueprint compact = DemoDocuments.compiled(kernel, DemoDocuments.compact);

        assertEquals(
            DemoDocuments.variants(DemoDocuments.machine),
            DemoDocuments.variants(DemoDocuments.compact),
            "the two members write the same variants block"
        );

        VariantGroup group = machine.variants();
        assertNotNull(group, "the machine declares a group");
        assertEquals(NamespacedId.of("structurecumulus", "demo_machine"), group.group());
        assertEquals(
            List.of(
                NamespacedId.of("structurecumulus", "demo_machine"),
                NamespacedId.of("structurecumulus", "demo_machine_compact")
            ),
            group.members().stream().map(VariantMember::id).toList()
        );
        assertEquals(List.of("full", "compact"), group.members().stream().map(VariantMember::label).toList());
        assertEquals(group, compact.variants(), "the compact member carries the same compiled group");
        assertNull(DemoDocuments.compiled(kernel, DemoDocuments.tower).variants(), "the tower stands alone");
    }

    @Test
    void eachDocumentMarksItselfAsADemonstrationInItsRootDescription() {
        for (NamespacedId id : DemoDocuments.ids) {
            String description = DemoDocuments.compiled(kernel, id).description();
            assertNotNull(description, () -> id + " carries a root description");
            assertTrue(
                description.startsWith("a demonstration document:"),
                () -> id + " opens its description with the demonstration marking: " + description
            );
        }
    }

    @Test
    void everyBayCarriesItsDescriptionAndHint() {
        for (NamespacedId id : DemoDocuments.ids) {
            for (Bay bay : DemoDocuments.compiled(kernel, id).bays()) {
                assertNotNull(bay.description(), () -> id + " " + bay.id() + " carries a description");
                assertNotNull(bay.hint(), () -> id + " " + bay.id() + " carries a hint");
            }
        }
    }

    @Test
    void theCampusSetCompilesPlacesAndPassesTheSitingRules() {
        StructureSetDocument document = DemoDocuments.set(DemoDocuments.campus);
        Catalogs catalogs = Catalogs.builtin();
        CompilerContext context = new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            DocumentSource.of(DemoDocuments.load())
        );
        StructureSetCompileResult result = StructureSetCompiler.compile(document, context);
        CompiledStructureSet compiled = assertInstanceOf(
            CompiledStructureSet.class,
            result.set(),
            () -> "the campus compiles: " + result.findings().findings()
        );
        assertEquals(DemoDocuments.station, compiled.root().id());
        assertEquals(
            List.of(DemoDocuments.machine, DemoDocuments.tower),
            compiled.satellites().stream().map(satellite -> satellite.blueprint().id()).toList()
        );

        StructureSet.Placement placement = StructureSet
                .place(compiled, Siting.of(LatticePoint.of(0, 0, 0), Orientation.identity));
        assertTrue(placement.succeeded(), () -> "the campus places: " + placement.findings());
        StructureSet placed = assertInstanceOf(StructureSet.class, placement.set());
        List<Finding> findings = StructureSet.validate(placed);
        assertTrue(findings.isEmpty(), findings::toString);

        Set<Siting> sitings = new HashSet<>();
        for (StructureSet.Satellite satellite : placed.satellites()) {
            assertTrue(sitings.add(satellite.siting()), () -> satellite.id() + " repeats a siting");
            assertFalse(
                satellite.siting().equals(placed.rootSiting()),
                () -> satellite.id() + " sits exactly on the root"
            );
            List<Finding> linkFindings = StructureSet.linkFindings(placed, satellite);
            assertTrue(linkFindings.isEmpty(), linkFindings::toString);
        }
        assertEquals(2, sitings.size());
    }

    private static @Nullable Bay bay(Blueprint blueprint, NamespacedId id) {
        for (Bay bay : blueprint.bays()) {
            if (bay.id().equals(id)) {
                return bay;
            }
        }
        return null;
    }

    /** Maps a world cell back to the north pod's frame by the inverse of the pod's turn. */
    private static LatticePoint unrotate(LatticePoint cell, int turn, int center) {
        int dx = cell.x() - center;
        int dz = cell.z() - center;
        int rx;
        int rz;
        switch (turn) {
            case 0 -> {
                rx = dx;
                rz = dz;
            }
            case 1 -> {
                rx = dz;
                rz = -dx;
            }
            case 2 -> {
                rx = -dx;
                rz = -dz;
            }
            default -> {
                rx = -dz;
                rz = dx;
            }
        }
        return LatticePoint.of(center + rx, cell.y(), center + rz);
    }

    private static int[] bounds(NamespacedId id) {
        var bounds = DemoDocuments.compiled(kernel, id).bounds();
        return new int[]{bounds.min().x(), bounds.min().y(), bounds.min().z(), bounds.sizeX(), bounds.sizeY(),
                bounds.sizeZ()};
    }

    private static List<String> stageNames(NamespacedId id) {
        return stages(id).stream().map(StageModel.Stage::name).toList();
    }

    private static List<Integer> stageCells(NamespacedId id) {
        return stages(id).stream().map(stage -> stage.cells().size()).toList();
    }

    private static List<StageModel.Stage> stages(NamespacedId id) {
        Blueprint blueprint = DemoDocuments.compiled(kernel, id);
        PreviewExpected.Outcome outcome = PreviewExpected.of(blueprint, Orientation.identity);
        assertNull(outcome.filter(), () -> id + " cannot express the identity orientation");
        return StageModel.of(blueprint, outcome.states().keySet()).stages();
    }

    private static boolean hasDirectionalState(Map<LatticePoint, BlockState> states) {
        for (BlockState state : states.values()) {
            for (Property<?> property : state.getProperties()) {
                if (property.getValueClass() == Direction.class) {
                    return true;
                }
            }
        }
        return false;
    }
}
