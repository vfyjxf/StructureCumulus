package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.PresenceSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.clockwise;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.compiled;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.half;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.id;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.leftId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.leftParent;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.leftSatellite;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.port;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.preheaterBlueprint;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.preheaterSatellite;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rightId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rightParent;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rightSatellite;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rootBlueprint;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rootSiting;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.setId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureSetPlanTest {

    @Test
    void placesIdentityAndHalfSatellitesFromTheHostRootSiting() {
        CompiledStructureSet set = compiled(
            compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
            compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
        );
        StructureSet.Placement placement = StructureSet.place(set, rootSiting());
        assertTrue(placement.succeeded(), () -> placement.findings().toString());
        StructureSet placed = placement.set();
        assertNotNull(placed);
        assertEquals(setId, placed.id());
        assertEquals(Cuboid.of(100, 10, 100, 103, 11, 103), placed.rootBounds());
        assertEquals(List.of(leftId, rightId), placed.satellites().stream().map(StructureSet.Satellite::id).toList());

        StructureSet.Satellite left = placed.satellite(leftId);
        assertNotNull(left);
        assertEquals(Siting.of(LatticePoint.of(98, 10, 101), Orientation.identity), left.siting());
        assertEquals(Cuboid.of(98, 10, 101, 100, 11, 103), left.worldBounds());
        assertEquals(LatticePoint.of(98, 10, 101), left.siting().toWorld(LatticePoint.origin));

        StructureSet.Satellite right = placed.satellite(rightId);
        assertNotNull(right);
        assertEquals(Siting.of(LatticePoint.of(104, 10, 101), half), right.siting());
        assertEquals(Cuboid.of(103, 10, 100, 105, 11, 102), right.worldBounds());
        assertEquals(LatticePoint.of(103, 10, 101), right.siting().toWorld(port));
    }

    @Test
    void aRotatedRootRotatesTheWholeSatelliteFrame() {
        Siting root = Siting.of(LatticePoint.of(5, 0, 5), clockwise);
        SatelliteSpec atOrigin = preheaterSatellite(id("origin_satellite"), LatticePoint.origin, half, false, null);
        SatelliteSpec turned = preheaterSatellite(
            id("turned_satellite"),
            LatticePoint.of(0, 0, -3),
            clockwise,
            false,
            null
        );
        CompiledStructureSet set = compiled(
            compiled(atOrigin, preheaterBlueprint()),
            compiled(turned, preheaterBlueprint())
        );
        StructureSet.Placement placement = StructureSet.place(set, root);
        assertTrue(placement.succeeded(), () -> placement.findings().toString());
        StructureSet placed = placement.set();
        assertNotNull(placed);
        StructureSet.Satellite originSatellite = placed.satellite(id("origin_satellite"));
        StructureSet.Satellite turnedSatellite = placed.satellite(id("turned_satellite"));
        assertNotNull(originSatellite);
        assertNotNull(turnedSatellite);
        assertEquals(Siting.of(LatticePoint.of(5, 0, 5), clockwise.compose(half)), originSatellite.siting());
        assertEquals(Siting.of(LatticePoint.of(8, 0, 5), half), turnedSatellite.siting());
    }

    @Test
    void presenceDefaultsToTheWholeSatelliteBoundsAndAnOverrideMapsCellByCell() {
        CompiledStructureSet set = compiled(
            compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
            compiled(
                preheaterSatellite(rightId, LatticePoint.of(4, 0, 1), half, false, PresenceSpec.cell(port)),
                preheaterBlueprint()
            )
        );
        StructureSet.Placement placement = StructureSet.place(set, rootSiting());
        assertTrue(placement.succeeded(), () -> placement.findings().toString());
        StructureSet placed = placement.set();
        assertNotNull(placed);
        StructureSet.Satellite left = placed.satellite(leftId);
        StructureSet.Satellite right = placed.satellite(rightId);
        assertNotNull(left);
        assertNotNull(right);
        assertEquals(left.worldBounds().asSet(), left.presenceCells());
        assertEquals(CuboidSet.of(Cuboid.of(LatticePoint.of(103, 10, 101))), right.presenceCells());
    }

    @Test
    void aHandBuiltSatelliteOnTheRootSitingIsRejected() {
        SatelliteSpec onRoot = preheaterSatellite(
            id("on_the_root"),
            LatticePoint.origin,
            Orientation.identity,
            false,
            null
        );
        CompiledStructureSet set = compiled(compiled(onRoot, preheaterBlueprint()));
        StructureSet.Placement placement = StructureSet.place(set, rootSiting());
        assertNull(placement.set());
        assertEquals(1, placement.findings().size());
        assertEquals(Codes.rootPlacementCollision.code(), placement.findings().get(0).code());
    }

    @Test
    void aSatelliteAtTheRootOriginWithANonIdentityOrientationIsAllowed() {
        SatelliteSpec turned = preheaterSatellite(id("turned"), LatticePoint.origin, half, false, null);
        CompiledStructureSet set = compiled(compiled(turned, preheaterBlueprint()));
        StructureSet.Placement placement = StructureSet.place(set, rootSiting());
        assertTrue(placement.succeeded(), () -> placement.findings().toString());
        StructureSet placed = placement.set();
        assertNotNull(placed);
        StructureSet.Satellite satellite = placed.satellite(id("turned"));
        assertNotNull(satellite);
        assertEquals(half, satellite.siting().placement().orientation());
        assertEquals(placed.rootSiting().origin(), satellite.siting().origin());
    }

    @Test
    void aLatticeOverflowIsScSpc0001NotACrash() {
        Siting edge = Siting.of(LatticePoint.of(LatticePoint.maxXz - 3, 0, 0), Orientation.identity);
        SatelliteSpec far = preheaterSatellite(
            id("far_satellite"),
            LatticePoint.of(8, 0, 1),
            Orientation.identity,
            false,
            null
        );
        CompiledStructureSet set = compiled(compiled(far, preheaterBlueprint()));
        StructureSet.Placement placement = StructureSet.place(set, edge);
        assertNull(placement.set());
        assertEquals(1, placement.findings().size());
        assertEquals(Codes.locusUnmapped.code(), placement.findings().get(0).code());
    }

    @Test
    void aSatelliteIdThatCollidesWithTheRootKeyIsRejected() {
        SatelliteSpec colliding = preheaterSatellite(
            rootBlueprint().id(),
            LatticePoint.of(-2, 0, 1),
            Orientation.identity,
            false,
            null
        );
        CompiledStructureSet set = compiled(compiled(colliding, preheaterBlueprint()));
        StructureSet.Placement placement = StructureSet.place(set, rootSiting());
        assertNull(placement.set());
        assertEquals(1, placement.findings().size());
        assertEquals(Codes.documentDuplicate.code(), placement.findings().get(0).code());
    }

    @Test
    void aPresenceRegionOutsideTheSatelliteBoundsIsADocumentFinding() {
        PresenceSpec outside = PresenceSpec.cell(LatticePoint.of(5, 0, 5));
        SatelliteSpec invalid = preheaterSatellite(
            id("invalid_presence"),
            LatticePoint.of(-2, 0, 1),
            Orientation.identity,
            false,
            outside
        );
        CompiledStructureSet set = compiled(compiled(invalid, preheaterBlueprint()));
        StructureSet.Placement placement = StructureSet.place(set, rootSiting());
        assertNull(placement.set());
        assertEquals(1, placement.findings().size());
        Finding finding = placement.findings().get(0);
        assertEquals(Codes.documentField.code(), finding.code());
        assertTrue(finding.message().contains("presence"), finding.message());
    }
}
