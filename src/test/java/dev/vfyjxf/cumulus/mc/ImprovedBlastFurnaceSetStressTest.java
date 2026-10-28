package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.blastFurnaceSet;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.claimCells;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.codes;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.compiledPreheater;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.compiledWideMain;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillBlastFurnace;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillPreheater;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillWideMain;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.furnaceLeftPort;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.furnaceRightPort;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.leftLink;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.leftPreheater;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.preheaterPort;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.rightLink;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.rightPreheater;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.rootId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.wideId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.wideLinks;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.wideMain;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.leftId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rightId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.world;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IE improved blast furnace: two optional preheaters, a wide hot-blast main and disjoint claims. */
class ImprovedBlastFurnaceSetStressTest {

    @Test
    void bothOptionalPreheatersAndTheWideMainFormWithDisjointClaims() {
        StructureSetTestSupport.Fixture fixture = fixture(
            blastFurnaceSet(
                compiledPreheater(leftPreheater(false, leftLink())),
                compiledPreheater(rightPreheater(false, rightLink())),
                compiledWideMain(wideMain(false, wideLinks().toArray(LinkSpec[]::new)))
            )
        );
        fillBlastFurnace(fixture);
        fillPreheater(fixture, leftId);
        fillPreheater(fixture, rightId);
        fillWideMain(fixture, wideId);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty(), () -> result.setFindings().toString());
        assertEquals(List.of(rootId, leftId, rightId, wideId), result.settled());
        assertEquals(List.of(rootId, leftId, rightId, wideId), new ArrayList<>(result.outcomes().keySet()));
        assertEquals(List.of(rootId, leftId, rightId, wideId), new ArrayList<>(result.claims().keySet()));
        assertTrue(result.rolledBack().isEmpty());
        assertTrue(result.rollbackFailures().isEmpty());

        assertEquals(26, result.claimsOf(rootId).size(), "the three-by-three shell minus its two ports");
        assertEquals(8, result.claimsOf(leftId).size());
        assertEquals(8, result.claimsOf(rightId).size());
        assertEquals(3, result.claimsOf(wideId).size());
        assertEquals(45, fixture.ledger.rows().size());
        assertEquals(45, result.claims().values().stream().mapToInt(List::size).sum());

        Map<NamespacedId, Set<LatticePoint>> cells = claimCells(result);
        for (Map.Entry<NamespacedId, Set<LatticePoint>> left : cells.entrySet()) {
            for (Map.Entry<NamespacedId, Set<LatticePoint>> right : cells.entrySet()) {
                if (left.getKey().compareTo(right.getKey()) < 0) {
                    assertTrue(
                        Collections.disjoint(left.getValue(), right.getValue()),
                        "claims of " + left.getKey() + " and " + right.getKey() + " must be disjoint"
                    );
                }
            }
        }

        Cuboid rootBounds = fixture.plan.rootBounds();
        for (var row : result.claimsOf(rootId)) {
            assertTrue(rootBounds.contains(row.cell().point()), "the furnace owns only its own bounds");
        }
        for (NamespacedId satelliteId : List.of(leftId, rightId, wideId)) {
            StructureSet.Satellite satellite = fixture.plan.satellite(satelliteId);
            assertNotNull(satellite);
            assertFalse(rootBounds.intersects(satellite.worldBounds()), "the satellite sits outside the furnace");
            for (var row : result.claimsOf(satelliteId)) {
                assertFalse(rootBounds.contains(row.cell().point()));
            }
        }

        StructureSet.Satellite left = fixture.plan.satellite(leftId);
        StructureSet.Satellite right = fixture.plan.satellite(rightId);
        StructureSet.Satellite wide = fixture.plan.satellite(wideId);
        assertNotNull(left);
        assertNotNull(right);
        assertNotNull(wide);
        assertEquals(1, manhattan(parent(fixture, furnaceLeftPort), port(left)));
        assertEquals(1, manhattan(parent(fixture, furnaceRightPort), port(right)));
        for (int x = 0; x < 3; x++) {
            LatticePoint parent = parent(fixture, LatticePoint.of(x, 0, 2));
            LatticePoint satelliteCell = world(wide.siting(), LatticePoint.of(x, 0, 0));
            assertEquals(1, manhattan(parent, satelliteCell), "wide pairing " + x);
        }
        // the root settles once; each later structure loses one CAS round and retries once
        assertEquals(7, fixture.ledger.settleCalls);
    }

    @Test
    void oneOptionalPreheaterAbsentLeavesTheRest() {
        StructureSetTestSupport.Fixture fixture = fixture(
            blastFurnaceSet(
                compiledPreheater(leftPreheater(false, leftLink())),
                compiledPreheater(rightPreheater(false, rightLink())),
                compiledWideMain(wideMain(false, wideLinks().toArray(LinkSpec[]::new)))
            )
        );
        fillBlastFurnace(fixture);
        fillPreheater(fixture, leftId);
        fillWideMain(fixture, wideId);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty());
        assertEquals(List.of(rootId, leftId, wideId), result.settled());
        assertFalse(result.claims().containsKey(rightId));
        assertEquals(26 + 8 + 3, fixture.ledger.rows().size());
        assertEquals(26 + 8 + 3, result.claims().values().stream().mapToInt(List::size).sum());
    }

    @Test
    void bothOptionalPreheatersAbsentLeavesTheFurnaceAndItsMain() {
        StructureSetTestSupport.Fixture fixture = fixture(
            blastFurnaceSet(
                compiledPreheater(leftPreheater(false, leftLink())),
                compiledPreheater(rightPreheater(false, rightLink())),
                compiledWideMain(wideMain(false, wideLinks().toArray(LinkSpec[]::new)))
            )
        );
        fillBlastFurnace(fixture);
        fillWideMain(fixture, wideId);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty());
        assertEquals(List.of(rootId, wideId), result.settled());
        assertEquals(26 + 3, fixture.ledger.rows().size());
        assertEquals(2, result.claims().size(), "only root and the wide main wrote rows");
    }

    @Test
    void aRequiredPreheaterAbsenceStopsBeforeLinkAndCheck() {
        StructureSetTestSupport.Fixture fixture = fixture(
            blastFurnaceSet(
                compiledPreheater(leftPreheater(true, leftLink())),
                compiledPreheater(rightPreheater(false, rightLink())),
                compiledWideMain(wideMain(false, wideLinks().toArray(LinkSpec[]::new)))
            )
        );
        fillBlastFurnace(fixture);
        fillPreheater(fixture, rightId);
        fillWideMain(fixture, wideId);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.satelliteAbsent.code()), codes(result));
        assertTrue(result.settled().isEmpty());
        assertTrue(result.outcomes().isEmpty());
        assertTrue(result.claims().isEmpty());
        assertTrue(fixture.instances.isEmpty(), "presence stops the set before any check");
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void aWideMainValidatesEveryOneOfItsThreePairings() {
        SatelliteSpec misplaced = new SatelliteSpec(
            wideId,
            wideId,
            LatticePoint.of(0, 0, 4),
            Orientation.identity,
            false,
            Value.object(),
            null,
            wideLinks(),
            null
        );
        StructureSetTestSupport.Fixture fixture = fixture(blastFurnaceSet(compiledWideMain(misplaced)));
        fillBlastFurnace(fixture);
        fillWideMain(fixture, wideId);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(
            List.of(
                Codes.linkDistanceInvalid.code(),
                Codes.linkDistanceInvalid.code(),
                Codes.linkDistanceInvalid.code()
            ),
            codes(result),
            "each pair of the wide main is validated independently"
        );
        assertTrue(fixture.instances.isEmpty(), "link validation runs before every check");
        assertTrue(result.settled().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    private static StructureSetTestSupport.Fixture fixture(CompiledStructureSet set) {
        return new StructureSetTestSupport.Fixture(set);
    }

    private static LatticePoint parent(StructureSetTestSupport.Fixture fixture, LatticePoint local) {
        return world(fixture.plan.rootSiting(), local);
    }

    private static LatticePoint port(StructureSet.Satellite satellite) {
        return world(satellite.siting(), preheaterPort);
    }

    private static int manhattan(LatticePoint first, LatticePoint second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.y() - second.y()) + Math.abs(first.z() - second.z());
    }
}
