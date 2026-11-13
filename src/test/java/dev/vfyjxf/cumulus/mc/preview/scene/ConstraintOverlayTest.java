package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The constraint overlay's pure geometry of design 18 §5: strokes at the faces, probes, dashes. */
class ConstraintOverlayTest {

    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");

    @Test
    void aFaceLinkMeetsTheSharedFaceAndARangeLinkMeetsBothFaces() {
        SetPage page = SetPage.of(twoSatelliteSet());
        ExplodeModel explode = explode(page);
        List<ConstraintOverlay.LinkLine> links = ConstraintOverlay.links(page, 0.0F, explode);
        assertEquals(2, links.size());

        ConstraintOverlay.LinkLine face = links.get(0);
        assertEquals(LinkMode.face, face.mode());
        assertTrue(face.segment().degenerate(), "two adjacent cells share one face, not a line");
        assertEquals(1.0F, face.segment().ax(), 1.0E-4F);
        assertEquals(0.5F, face.segment().ay(), 1.0E-4F);
        assertEquals(0.5F, face.segment().az(), 1.0E-4F);
        assertEquals("1 \u2264 1", face.caption());

        ConstraintOverlay.LinkLine range = links.get(1);
        assertEquals(LinkMode.range, range.mode());
        assertFalse(range.segment().degenerate());
        assertEquals(1.0F, range.segment().ax(), 1.0E-4F, "the root cell's face");
        assertEquals(5.0F, range.segment().bx(), 1.0E-4F, "the satellite cell's face");
        assertEquals(5, range.actual());
        assertEquals(5, range.maxDistance());
        assertEquals("5 \u2264 5", range.caption());
        assertTrue(range.tooltip().contains("chebyshev"), "the metric names itself in the tooltip");
    }

    @Test
    void theOptionalMembersAreTheDashedOnesAndTheProbesAreTheirDeclaredCells() {
        SetPage page = SetPage.of(twoSatelliteSet());
        assertEquals(List.of(1), ConstraintOverlay.optionalMembers(page));
        List<ConstraintOverlay.Probe> probes = ConstraintOverlay.probes(page, 0.0F, explode(page));
        assertEquals(1, probes.size());
        assertEquals(1.5F, probes.get(0).x(), 1.0E-4F);
        assertEquals(0.5F, probes.get(0).y(), 1.0E-4F);
        assertEquals(0.5F, probes.get(0).z(), 1.0E-4F);
    }

    @Test
    void theStrokesFollowAnExplodedMember() {
        SetPage page = SetPage.of(twoSatelliteSet());
        ExplodeModel explode = explode(page);
        ConstraintOverlay.LinkLine still = ConstraintOverlay.links(page, 0.0F, explode).get(0);
        ConstraintOverlay.LinkLine moved = ConstraintOverlay.links(page, 1.0F, explode).get(0);
        assertNotEquals(still.segment().ax(), moved.segment().ax(), "the satellite side follows its part");
    }

    private static ExplodeModel explode(SetPage page) {
        return ExplodeModel
                .of(page.stages(), page.cells(), PreviewScene.ofCells(page.cells()).hull(), page.bayLabels());
    }

    private static PreviewCatalogPayload.SetEntry twoSatelliteSet() {
        List<PreviewCatalogPayload.Member> members = new ArrayList<>();
        members.add(
            new PreviewCatalogPayload.Member(
                NamespacedId.of("structurecumulus", "root"),
                "root",
                true,
                List.of(new GhostCell(LatticePoint.of(0, 0, 0), stone, Map.of(), 0)),
                List.of(),
                List.of()
            )
        );
        members.add(
            new PreviewCatalogPayload.Member(
                NamespacedId.of("structurecumulus", "face"),
                "structurecumulus:face",
                false,
                List.of(new GhostCell(LatticePoint.of(1, 0, 0), stone, Map.of(), 1)),
                List.of(Cuboid.of(1, 0, 0, 2, 1, 1)),
                List.of(
                    new PreviewCatalogPayload.Link(
                        LinkMode.face,
                        LatticePoint.of(0, 0, 0),
                        LatticePoint.of(1, 0, 0),
                        0,
                        DistanceMetric.chebyshev
                    )
                )
            )
        );
        members.add(
            new PreviewCatalogPayload.Member(
                NamespacedId.of("structurecumulus", "range"),
                "structurecumulus:range",
                true,
                List.of(new GhostCell(LatticePoint.of(5, 0, 0), stone, Map.of(), 2)),
                List.of(),
                List.of(
                    new PreviewCatalogPayload.Link(
                        LinkMode.range,
                        LatticePoint.of(0, 0, 0),
                        LatticePoint.of(5, 0, 0),
                        5,
                        DistanceMetric.chebyshev
                    )
                )
            )
        );
        return new PreviewCatalogPayload.SetEntry(NamespacedId.of("structurecumulus", "overlay"), members, List.of());
    }
}
