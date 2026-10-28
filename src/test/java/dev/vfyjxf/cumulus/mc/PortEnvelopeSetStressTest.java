package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.bayOnlyRoot;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.codes;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.compiledPortPipe;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.envelopeBounds;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.envelopeOnlyRoot;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.envelopeOnlyRootId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.envelopePort;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.envelopePortRoot;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.envelopePortRootId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillRoot;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.logicalSatellite;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.port;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.portPipeId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.portSet;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.world;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A blast-furnace controller and port: the parent cell is a read bay, the envelope stays closed. */
class PortEnvelopeSetStressTest {

    @Test
    void aPortBayAndEnvelopeFormWithTheSatelliteOutside() {
        StructureSetTestSupport.Fixture fixture = fixture(
            portSet(
                envelopePortRoot(),
                compiledPortPipe(pipeSpec(LatticePoint.of(1, 1, 3), LinkSpec.face(envelopePort, LatticePoint.origin)))
            )
        );
        fillRoot(fixture, envelopeBounds, local -> local.equals(envelopePort) ? port : null);
        fillPipe(fixture);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty(), () -> result.setFindings().toString());
        assertEquals(List.of(envelopePortRootId, portPipeId), result.settled());
        assertEquals(1, result.claimsOf(envelopePortRootId).size(), "the envelope owns no row");
        assertEquals(1, result.claimsOf(portPipeId).size());
        assertEquals(2, fixture.ledger.rows().size());

        StructureSet.Satellite pipe = fixture.plan.satellite(portPipeId);
        assertNotNull(pipe);
        assertFalse(fixture.plan.rootBounds().intersects(pipe.worldBounds()));
        assertEquals(
            1,
            manhattan(world(fixture.plan.rootSiting(), envelopePort), world(pipe.siting(), LatticePoint.origin))
        );
    }

    @Test
    void aSatelliteInsideTheEnvelopeFailsScExp0006() {
        StructureSetTestSupport.Fixture fixture = fixture(
            portSet(
                envelopePortRoot(),
                compiledPortPipe(pipeSpec(LatticePoint.of(0, 1, 2), LinkSpec.face(envelopePort, LatticePoint.origin)))
            )
        );
        fillRoot(fixture, envelopeBounds, local -> local.equals(envelopePort) ? port : null);
        fillPipe(fixture);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.envelopeOpen.code()), codes(result));
        assertEquals(1, fixture.instances.size(), "the root check is attempted and fails before the satellite");
        assertTrue(result.outcomes().isEmpty());
        assertTrue(result.settled().isEmpty());
        assertTrue(result.claims().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void anEnvelopeReadParentCellSatisfiesCoverageWithoutABay() {
        StructureSetTestSupport.Fixture fixture = fixture(
            portSet(
                envelopeOnlyRoot(),
                compiledPortPipe(pipeSpec(LatticePoint.of(1, 1, 3), LinkSpec.face(envelopePort, LatticePoint.origin)))
            )
        );
        fillPipe(fixture);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty(), () -> result.setFindings().toString());
        assertEquals(List.of(envelopeOnlyRootId, portPipeId), result.settled());
        assertTrue(result.claimsOf(envelopeOnlyRootId).isEmpty(), "the envelope is unclaimed");
        assertEquals(1, result.claimsOf(portPipeId).size());
        assertEquals(1, fixture.ledger.rows().size());
    }

    @Test
    void aBayCellNoCheckReadsFailsScSet0003() {
        StructureSetTestSupport.Fixture fixture = fixture(
            portSet(
                bayOnlyRoot(),
                compiledPortPipe(
                    pipeSpec(LatticePoint.of(2, 0, 3), LinkSpec.face(LatticePoint.of(2, 0, 2), LatticePoint.origin))
                )
            )
        );
        fillPipe(fixture);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.linkCoverageInvalid.code()), codes(result));
        assertTrue(fixture.instances.isEmpty(), "link coverage is re-checked before every structure check");
        assertTrue(result.settled().isEmpty());
        assertTrue(result.claims().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    private static SatelliteSpec pipeSpec(LatticePoint at, LinkSpec link) {
        return logicalSatellite(portPipeId, portPipeId, at, false, null, link);
    }

    private static void fillPipe(StructureSetTestSupport.Fixture fixture) {
        StructureSet.Satellite pipe = fixture.plan.satellite(portPipeId);
        assertNotNull(pipe);
        fixture.view.put(world(pipe.siting(), LatticePoint.origin), StructureSetStressFixtures.plug);
    }

    private static StructureSetTestSupport.Fixture fixture(CompiledStructureSet set) {
        return new StructureSetTestSupport.Fixture(set);
    }

    private static int manhattan(LatticePoint first, LatticePoint second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.y() - second.y()) + Math.abs(first.z() - second.z());
    }
}
