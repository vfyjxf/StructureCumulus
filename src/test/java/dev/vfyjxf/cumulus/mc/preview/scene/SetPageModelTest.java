package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The set page's member mapping of design 18 §4: stages are the members (root first), the explode
 * separates members, and the parts carry the member labels.
 */
class SetPageModelTest {

    private static final NamespacedId rootBlueprint = NamespacedId.of("structurecumulus", "root_bp");
    private static final NamespacedId aBlueprint = NamespacedId.of("structurecumulus", "a_bp");
    private static final NamespacedId bBlueprint = NamespacedId.of("structurecumulus", "b_bp");

    @Test
    void theStagesAreTheMembersRootFirst() {
        SetPage page = SetPage.of(threeMemberSet());
        assertEquals(3, page.stages().size());
        assertEquals(
            List.of("root", "structurecumulus:a", "structurecumulus:b"),
            page.stages().stream().map(stage -> stage.name()).toList()
        );
        assertEquals(List.of(1, 2, 1), page.stages().stream().map(stage -> stage.expectedCells()).toList());
        assertEquals(4, page.cells().size());
        assertEquals(3, page.bayLabels().size());
        assertEquals("root", page.bayLabels().get(page.stages().get(0).bay()));
        assertEquals(0, page.memberOf(page.cells().get(0)));
        assertEquals(1, page.memberOf(page.cells().get(1)));
        assertEquals(aBlueprint, page.memberBlueprint(1));
        assertEquals(-1, page.memberOf(new GhostCell(LatticePoint.origin, rootBlueprint, Map.of(), 9)));
    }

    @Test
    void theExplodeSeparatesMembersAlongTheirOwnDirections() {
        SetPage page = SetPage.of(threeMemberSet());
        ExplodeModel explode = ExplodeModel
                .of(page.stages(), page.cells(), PreviewScene.ofCells(page.cells()).hull(), page.bayLabels());
        assertEquals(3, explode.groups().size(), "one part per member");
        assertTrue(explode.separable());
        ExplodeModel.Group root = explode.groups().get(0);
        ExplodeModel.Group a = explode.groups().get(1);
        ExplodeModel.Group b = explode.groups().get(2);
        assertEquals("root", root.text());
        assertEquals("structurecumulus:a", a.text());
        assertEquals("structurecumulus:b", b.text());
        assertTrue(root.directionX() < 0.0F, "the root sits left of the set centroid");
        assertTrue(b.directionX() > 0.0F, "the far member sits right of the set centroid");
        float distance = 0.5F * PreviewScene.ofCells(page.cells()).hull().diagonal();
        ExplodeModel.Offset offset = explode.offset(2, 1.0F);
        assertEquals(b.directionX() * distance, offset.x(), 1.0E-4F);
        assertEquals(b.directionY() * distance, offset.y(), 1.0E-4F);
        assertEquals(b.directionZ() * distance, offset.z(), 1.0E-4F);
        assertEquals(ExplodeModel.Offset.zero, explode.offset(2, 0.0F));
    }

    @Test
    void oneMemberFallsBackToTheDegeneratePlusY() {
        PreviewCatalogPayload.SetEntry one = new PreviewCatalogPayload.SetEntry(
            NamespacedId.of("structurecumulus", "one"),
            List.of(member(rootBlueprint, "root", true, List.of(LatticePoint.of(0, 0, 0)), List.of())),
            List.of()
        );
        SetPage page = SetPage.of(one);
        ExplodeModel explode = ExplodeModel
                .of(page.stages(), page.cells(), PreviewScene.ofCells(page.cells()).hull(), page.bayLabels());
        assertFalse(explode.separable());
        assertEquals(1, explode.groups().size());
        ExplodeModel.Group group = explode.groups().get(0);
        float dx = group.directionX();
        float dy = group.directionY();
        float dz = group.directionZ();
        assertNotEquals(0.0F, dy);
        assertEquals(0.0F, dx, 0.0F);
        assertEquals(1.0F, dy, 0.0F);
        assertEquals(0.0F, dz, 0.0F);
    }

    @Test
    void theFlattenedTableKeepsOnePartPerMemberAndItsOwnStageNames() {
        PreviewCatalogPayload.SetEntry entry = flattenedSet();
        SetPage page = SetPage.of(entry);
        assertEquals(4, page.stages().size(), "the server's flattened table, not one row per member");
        assertEquals(List.of("r", "a1", "a2", "b1"), page.stages().stream().map(stage -> stage.name()).toList());
        assertEquals(List.of("root", "a", "a", "b"), page.stages().stream().map(stage -> stage.group()).toList());
        assertEquals(
            List.of(0, 1, 1, 2),
            List.of(page.memberOfStage(0), page.memberOfStage(1), page.memberOfStage(2), page.memberOfStage(3))
        );
        for (GhostCell cell : page.cells()) {
            assertEquals(
                cell.stageIndex() < 3 ? (cell.stageIndex() == 0 ? 0 : 1) : 2,
                page.memberOf(cell),
                "a cell names the member whose own stage carries it"
            );
        }
        ExplodeModel explode = ExplodeModel
                .of(page.stages(), page.cells(), PreviewScene.ofCells(page.cells()).hull(), page.bayLabels());
        assertEquals(3, explode.groups().size(), "one part per member, however many stages it has");
        assertEquals(List.of("root", "a", "b"), explode.groups().stream().map(ExplodeModel.Group::text).toList());
        ExplodeModel.Offset byBay = explode.offset(SetPage.bay(entry.setId(), 1), 1.0F);
        assertEquals(byBay, explode.offset(1, 1.0F), "the member's stages share its displacement");
        assertEquals(byBay, explode.offset(2, 1.0F), "the member's stages share its displacement");
        assertEquals(ExplodeModel.Offset.zero, explode.offset(SetPage.bay(entry.setId(), 9), 1.0F));
    }

    private static PreviewCatalogPayload.SetEntry flattenedSet() {
        NamespacedId setId = NamespacedId.of("structurecumulus", "flat");
        NamespacedId rootBay = PreviewCatalogPayload.SetEntry.memberBay(setId, 0);
        NamespacedId aBay = PreviewCatalogPayload.SetEntry.memberBay(setId, 1);
        NamespacedId bBay = PreviewCatalogPayload.SetEntry.memberBay(setId, 2);
        List<PreviewCatalogPayload.Member> members = List.of(
            new PreviewCatalogPayload.Member(
                rootBlueprint,
                "root",
                true,
                List.of(new GhostCell(LatticePoint.of(0, 0, 0), NamespacedId.of("minecraft", "stone"), Map.of(), 0)),
                List.of(),
                List.of()
            ),
            new PreviewCatalogPayload.Member(
                aBlueprint,
                "a",
                true,
                List.of(
                    new GhostCell(LatticePoint.of(2, 0, 0), NamespacedId.of("minecraft", "stone"), Map.of(), 1),
                    new GhostCell(LatticePoint.of(2, 0, 1), NamespacedId.of("minecraft", "stone"), Map.of(), 2)
                ),
                List.of(),
                List.of()
            ),
            new PreviewCatalogPayload.Member(
                bBlueprint,
                "b",
                true,
                List.of(new GhostCell(LatticePoint.of(4, 0, 0), NamespacedId.of("minecraft", "stone"), Map.of(), 3)),
                List.of(),
                List.of()
            )
        );
        List<Stage> stages = List.of(
            new Stage("r", 1, 0, null, null, rootBay, "root"),
            new Stage("a1", 1, 0, null, null, aBay, "a"),
            new Stage("a2", 1, 0, null, null, aBay, "a"),
            new Stage("b1", 1, 0, null, null, bBay, "b")
        );
        return new PreviewCatalogPayload.SetEntry(setId, members, List.of(), stages);
    }

    private static PreviewCatalogPayload.SetEntry threeMemberSet() {
        List<PreviewCatalogPayload.Member> members = new ArrayList<>();
        members.add(member(rootBlueprint, "root", true, List.of(LatticePoint.of(0, 0, 0)), List.of()));
        members.add(
            member(
                aBlueprint,
                "structurecumulus:a",
                true,
                List.of(LatticePoint.of(2, 0, 0), LatticePoint.of(2, 0, 1)),
                List.of()
            )
        );
        members.add(member(bBlueprint, "structurecumulus:b", true, List.of(LatticePoint.of(4, 0, 0)), List.of()));
        return new PreviewCatalogPayload.SetEntry(NamespacedId.of("structurecumulus", "three"), members, List.of());
    }

    private static PreviewCatalogPayload.Member member(
        NamespacedId blueprint,
        String label,
        boolean required,
        List<LatticePoint> cells,
        List<PreviewCatalogPayload.Link> links
    ) {
        List<GhostCell> ghosts = new ArrayList<>(cells.size());
        for (LatticePoint at : cells) {
            int stage = label.equals("root") ? 0 : label.endsWith(":a") ? 1 : 2;
            ghosts.add(new GhostCell(at, NamespacedId.of("minecraft", "stone"), Map.of(), stage));
        }
        return new PreviewCatalogPayload.Member(blueprint, label, required, ghosts, List.of(), links);
    }
}
