package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.PresenceSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.document.StructureSetSpec;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCodecProbe;
import dev.vfyjxf.cumulus.mc.preview.scene.SetPage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalogue's set half of design 18 §3: one entry per set that compiles and validates, composed on
 * the server into pure geometry, with the composite 1024 cap and the 4096 whole-catalogue cap.
 */
class PreviewSetCatalogTest {

    private static final NamespacedId campus = DemoDocuments.campus;
    private static final NamespacedId machine = DemoDocuments.machine;
    private static final NamespacedId tower = DemoDocuments.tower;
    private static final NamespacedId station = DemoDocuments.station;
    private static final NamespacedId stationCompact = DemoDocuments.stationCompact;
    private static final NamespacedId stoneCell = NamespacedId.of("structurecumulus", "test_cell");
    private static final NamespacedId exactSet = NamespacedId.of("structurecumulus", "exact_set");
    private static final NamespacedId overSet = NamespacedId.of("structurecumulus", "over_set");
    private static final NamespacedId invalidSet = NamespacedId.of("structurecumulus", "invalid_set");

    @Test
    void theCampusIsOneComposedSetEntryWithItsMembersInBuildOrder() {
        PreviewCatalogPayload catalog = catalog();

        assertEquals(5, catalog.entries().size(), "the five blueprints still ship");
        assertEquals(1, catalog.sets().size(), () -> catalog.sets().toString());
        PreviewCatalogPayload.SetEntry set = catalog.sets().get(0);
        assertEquals(campus, set.setId());
        assertEquals(3, set.members().size(), "the root and its two satellites");
        assertMember(set, 0, station, "root", true, 473);
        assertMember(set, 1, machine, "structurecumulus:campus_machine", true, 100);
        assertMember(set, 2, tower, "structurecumulus:campus_tower", true, 128);
        assertEquals(701, set.members().stream().mapToInt(member -> member.cells().size()).sum());
        int materials = set.materials().stream().mapToInt(PreviewCatalogPayload.MaterialLine::count).sum();
        assertEquals(701, materials, "the material union counts every composed cell");
        assertTrue(set.materials().size() <= PreviewCatalog.maxMaterials);
    }

    @Test
    void theComposedCellsFollowTheMembersRelativePlacement() {
        PreviewCatalogPayload.SetEntry set = catalog().sets().get(0);
        PreviewCatalogPayload.Member towerMember = set.members().get(2);

        // the tower sits at [4,0,13] turned clockwise: (x,y,z) becomes (-z, y, x)
        GhostCell origin = assertCell(towerMember, LatticePoint.of(4, 0, 13));
        assertEquals(NamespacedId.of("minecraft", "chiseled_stone_bricks"), origin.block());
        // the local corner [4,0,4] composes to [4-4, 0, 13+4]
        assertNotNull(assertCell(towerMember, LatticePoint.of(0, 0, 17)), "the clockwise corner");
        // the machine is at [13,0,0] unturned
        assertCell(set.members().get(1), LatticePoint.of(13, 0, 0));
        for (int member = 0; member < set.members().size(); member++) {
            int stage = member;
            assertTrue(
                set.members().get(member).cells().stream().allMatch(cell -> cell.stageIndex() == stage),
                () -> "member " + stage + " cells name their member stage"
            );
        }
    }

    @Test
    void theSameSourceComposesTheSameCellsEveryBuild() {
        PreviewCatalogPayload first = catalog();
        PreviewCatalogPayload second = catalog();
        assertEquals(first, second, "a pure function of the source and the registry");
        assertArrayEquals(
            PreviewCodecProbe.encode(first),
            PreviewCodecProbe.encode(second),
            "equal geometry encodes to equal bytes"
        );
    }

    @Test
    void theCompositeCapAccepts1024AndLeavesOut1025() {
        StructureKernel kernel = DemoDocuments.kernel(
            Map.of(stoneCell, DemoDocuments.cell(stoneCell.text())),
            Map.of(exactSet, exactSet(), overSet, overSet())
        );

        PreviewCatalogPayload atCap = PreviewCatalog.build(kernel, List.of(), List.of(exactSet));
        assertEquals(1, atCap.sets().size(), "1024 composed cells fit the composite cap");
        assertEquals(1024, atCap.sets().get(0).members().stream().mapToInt(m -> m.cells().size()).sum());

        PreviewCatalogPayload over = PreviewCatalog.build(kernel, List.of(), List.of(overSet));
        assertTrue(over.sets().isEmpty(), "1025 composed cells are absent, never truncated");
    }

    @Test
    void aSetThatFailsValidationIsAbsentAndNeverPartial() {
        StructureKernel kernel = DemoDocuments.kernel(Map.of(), Map.of(invalidSet, invalidSet()));

        PreviewCatalogPayload catalog = PreviewCatalog.build(kernel, List.of(), List.of(invalidSet));
        assertTrue(catalog.sets().isEmpty(), "a satellite exactly on the root is a validation failure");
    }

    @Test
    void aSetComposesEvenWhenNoneOfItsMembersIsABlueprintEntry() {
        // the set's scene is resolved from the document, not from the member's catalogue entry (§3)
        PreviewCatalogPayload catalog = PreviewCatalog.build(kernel(), List.of(), List.of(campus));
        assertTrue(catalog.entries().isEmpty());
        assertEquals(1, catalog.sets().size());
        assertEquals(701, catalog.sets().get(0).members().stream().mapToInt(m -> m.cells().size()).sum());
    }

    @Test
    void theEntryCarriesItsRequiredFlagPresenceProbesAndLinks() {
        PreviewCatalogPayload.SetEntry set = catalog().sets().get(0);
        PreviewCatalogPayload.Member machineMember = set.members().get(1);
        assertEquals(1, machineMember.links().size());
        PreviewCatalogPayload.Link link = machineMember.links().get(0);
        assertEquals(LinkMode.face, link.mode());
        assertEquals(LatticePoint.of(12, 0, 2), link.parentCell());
        assertEquals(LatticePoint.of(13, 0, 2), link.satelliteCell(), "the port is composed into the set frame");
        assertEquals(0, link.maxDistance());
        assertEquals(DistanceMetric.chebyshev, link.metric());
        assertTrue(machineMember.presence().isEmpty(), "no declared probe travels as none");
    }

    @Test
    void theSetPageBuildsOneStagePerMemberWithTheMemberLabels() {
        SetPage page = SetPage.of(catalog().sets().get(0));
        assertEquals(3, page.stages().size());
        assertEquals(
            List.of("root", "structurecumulus:campus_machine", "structurecumulus:campus_tower"),
            page.stages().stream().map(stage -> stage.name()).toList()
        );
        assertEquals(List.of(473, 100, 128), page.stages().stream().map(stage -> stage.expectedCells()).toList());
        assertEquals(0, page.presentation().currentStage());
        assertEquals(701, page.cells().size());
        assertEquals(3, page.bayLabels().size());
    }

    @Test
    void aValidatedPresenceProbeTravelsWithItsMember() {
        PreviewCatalogPayload.SetEntry set = presenceSetEntry();
        PreviewCatalogPayload.Member optional = set.members().get(1);
        assertFalse(optional.required());
        assertEquals(List.of(Cuboid.of(6, 0, 0, 7, 1, 1)), optional.presence());
        assertEquals(1, optional.links().size());
        assertEquals(LinkMode.range, optional.links().get(0).mode());
        assertEquals(6, optional.links().get(0).maxDistance());
        assertEquals(LatticePoint.of(0, 0, 0), optional.links().get(0).parentCell());
        assertEquals(LatticePoint.of(6, 0, 0), optional.links().get(0).satelliteCell());
    }

    @Test
    void theMemberCapCountsIntoTheWholeCatalogCap() {
        // the whole-catalog cap is unchanged and counts set entries (design 18 §3)
        StructureKernel kernel = DemoDocuments.kernel(
            Map.of(stoneCell, DemoDocuments.cell(stoneCell.text())),
            Map.of(exactSet, exactSet(), overSet, overSet())
        );
        PreviewCatalogPayload catalog = PreviewCatalog.build(kernel, List.of(), List.of(exactSet, overSet));
        assertEquals(1, catalog.sets().size());
        assertEquals(exactSet, catalog.sets().get(0).setId());
    }

    private static PreviewCatalogPayload catalog() {
        return PreviewCatalog.build(kernel(), DemoDocuments.ids, List.of(campus));
    }

    private static StructureKernel kernel() {
        return DemoDocuments.kernel(Map.of(), Map.of());
    }

    private static PreviewCatalogPayload.SetEntry presenceSetEntry() {
        StructureKernel kernel = DemoDocuments
                .kernel(Map.of(stoneCell, DemoDocuments.cell(stoneCell.text())), Map.of(presenceSet, presenceSet()));
        PreviewCatalogPayload catalog = PreviewCatalog.build(kernel, List.of(), List.of(presenceSet));
        assertEquals(1, catalog.sets().size());
        return catalog.sets().get(0);
    }

    private static final NamespacedId presenceSet = NamespacedId.of("structurecumulus", "presence_set");

    private static GhostCell assertCell(PreviewCatalogPayload.Member member, LatticePoint at) {
        for (GhostCell cell : member.cells()) {
            if (cell.cell().equals(at)) {
                return cell;
            }
        }
        throw new AssertionError("no composed cell at " + at + " in " + member.label());
    }

    private static void assertMember(
        PreviewCatalogPayload.SetEntry set,
        int index,
        NamespacedId blueprint,
        String label,
        boolean required,
        int cells
    ) {
        PreviewCatalogPayload.Member member = set.members().get(index);
        assertEquals(blueprint, member.blueprintId(), () -> "member " + index + " blueprint");
        assertEquals(label, member.label(), () -> "member " + index + " label");
        assertEquals(required, member.required(), () -> "member " + index + " required");
        assertEquals(cells, member.cells().size(), () -> "member " + index + " composed cells");
    }

    private static StructureSetDocument exactSet() {
        List<SatelliteSpec> satellites = new ArrayList<>();
        satellites.add(satellite("a", stationCompact, LatticePoint.of(20, 0, 0), Orientation.identity, true, null));
        satellites.add(satellite("b", tower, LatticePoint.of(40, 0, 0), Orientation.identity, true, null));
        satellites.add(satellite("c", machine, LatticePoint.of(60, 0, 0), Orientation.identity, true, null));
        // 473 + 323 + 128 + 100 = 1024 composed cells
        return structureSet(exactSet, station, satellites);
    }

    private static StructureSetDocument overSet() {
        List<SatelliteSpec> satellites = new ArrayList<>();
        satellites.add(satellite("a", station, LatticePoint.of(10, 0, 0), Orientation.identity, true, null));
        satellites.add(satellite("b", stationCompact, LatticePoint.of(40, 0, 0), Orientation.identity, true, null));
        satellites.add(satellite("c", tower, LatticePoint.of(60, 0, 0), Orientation.identity, true, null));
        satellites.add(satellite("d", machine, LatticePoint.of(80, 0, 0), Orientation.identity, true, null));
        // 1 + 473 + 323 + 128 + 100 = 1025 composed cells
        return structureSet(overSet, stoneCell, satellites);
    }

    private static StructureSetDocument invalidSet() {
        List<SatelliteSpec> satellites = List
                .of(satellite("a", machine, LatticePoint.of(0, 0, 0), Orientation.identity, true, null));
        return structureSet(invalidSet, station, satellites);
    }

    private static StructureSetDocument presenceSet() {
        PresenceSpec presence = PresenceSpec.cell(LatticePoint.origin);
        List<SatelliteSpec> satellites = List.of(
            satellite(
                "optional",
                machine,
                LatticePoint.of(6, 0, 0),
                Orientation.identity,
                false,
                presence,
                LinkSpec.range(LatticePoint.origin, LatticePoint.origin, 6)
            ),
            satellite("required", machine, LatticePoint.of(1, 0, 0), Orientation.identity, true, null)
        );
        return structureSet(presenceSet, stoneCell, satellites);
    }

    private static SatelliteSpec satellite(
        String id,
        NamespacedId blueprint,
        LatticePoint at,
        Orientation orientation,
        boolean required,
        @Nullable PresenceSpec presence
    ) {
        return satellite(id, blueprint, at, orientation, required, presence, null);
    }

    private static SatelliteSpec satellite(
        String id,
        NamespacedId blueprint,
        LatticePoint at,
        Orientation orientation,
        boolean required,
        @Nullable PresenceSpec presence,
        @Nullable LinkSpec link
    ) {
        return new SatelliteSpec(
            NamespacedId.of("structurecumulus", "satellite_" + id),
            blueprint,
            at,
            orientation,
            required,
            Value.object(),
            presence,
            link == null ? List.of() : List.of(link),
            null
        );
    }

    private static StructureSetDocument structureSet(
        NamespacedId id,
        NamespacedId root,
        List<SatelliteSpec> satellites
    ) {
        return new StructureSetDocument(
            StructureSetDocument.currentVersion,
            new StructureSetSpec(id, root, satellites, null),
            Map.of()
        );
    }

}
