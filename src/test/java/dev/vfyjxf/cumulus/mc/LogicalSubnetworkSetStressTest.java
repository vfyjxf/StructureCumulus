package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.PresenceSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.codes;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.crafting;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.craftingId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.drive;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.driveId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillAt;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillNetwork;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.logicalSatellite;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.networkSet;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.singleCell;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.storage;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.storageCell;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.storageId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.terminal;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.terminalId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.world;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AE2-style logical subnetwork: no spatial links, required/optional presence and presence edge cases. */
class LogicalSubnetworkSetStressTest {

    private static final LatticePoint origin = LatticePoint.origin;
    private static final LatticePoint storageCell = LatticePoint.of(1, 0, 0);
    private static final PresenceSpec storagePresence = PresenceSpec
            .of(CuboidSet.of(Cuboid.of(LatticePoint.of(0, 0, 0)), Cuboid.of(storageCell)));

    @Test
    void aLogicalSubnetworkNeedsNoSpatialLink() {
        StructureSetTestSupport.Fixture fixture = fixture(
            networkSet(
                compiled(logicalSatellite(driveId, driveId, LatticePoint.of(20, 0, 20), true, null)),
                compiled(
                    logicalSatellite(craftingId, craftingId, LatticePoint.of(-20, 0, -20), false, null, LinkSpec.none())
                ),
                compiled(logicalSatellite(terminalId, terminalId, LatticePoint.of(8, 0, 8), false, null)),
                compiled(logicalSatellite(storageId, storageId, LatticePoint.of(30, 0, 30), false, storagePresence))
            )
        );
        fillNetwork(fixture);
        fillAt(fixture, driveId, origin, drive);
        fillAt(fixture, craftingId, origin, crafting);
        fillAt(fixture, terminalId, origin, terminal);
        fillAt(fixture, storageId, storageCell, storage);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty(), () -> result.setFindings().toString());
        assertEquals(
            List.of(StructureSetStressFixtures.networkId, driveId, craftingId, terminalId, storageId),
            result.settled()
        );
        assertEquals(9, result.claimsOf(StructureSetStressFixtures.networkId).size());
        assertEquals(1, result.claimsOf(driveId).size());
        assertEquals(1, result.claimsOf(craftingId).size());
        assertEquals(1, result.claimsOf(terminalId).size());
        assertEquals(1, result.claimsOf(storageId).size());
        assertEquals(13, fixture.ledger.rows().size());

        StructureSet.Satellite driveSatellite = fixture.plan.satellite(driveId);
        StructureSet.Satellite craftingSatellite = fixture.plan.satellite(craftingId);
        assertNotNull(driveSatellite);
        assertNotNull(craftingSatellite);
        assertTrue(driveSatellite.links().isEmpty(), "an omitted links member is no spatial link");
        assertEquals(LinkMode.none, craftingSatellite.links().get(0).mode());
        assertEquals(LatticePoint.of(120, 10, 120), driveSatellite.siting().origin());
        assertEquals(LatticePoint.of(80, 10, 80), craftingSatellite.siting().origin());
        assertEquals(
            fixture.plan.rootSiting().compose(driveSatellite.compiled().relativeSiting()),
            driveSatellite.siting()
        );
    }

    @Test
    void aRequiredSubnetworkMemberCannotBeAbsent() {
        StructureSetTestSupport.Fixture fixture = fixture(
            networkSet(
                compiled(logicalSatellite(driveId, driveId, LatticePoint.of(20, 0, 20), true, null)),
                compiled(logicalSatellite(craftingId, craftingId, LatticePoint.of(-20, 0, -20), false, null))
            )
        );
        fillNetwork(fixture);
        fillAt(fixture, craftingId, origin, crafting);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.satelliteAbsent.code()), codes(result));
        assertTrue(result.settled().isEmpty());
        assertTrue(fixture.instances.isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void anOptionalSubnetworkMemberMayBeAbsent() {
        StructureSetTestSupport.Fixture fixture = fixture(
            networkSet(
                compiled(logicalSatellite(driveId, driveId, LatticePoint.of(20, 0, 20), true, null)),
                compiled(logicalSatellite(craftingId, craftingId, LatticePoint.of(-20, 0, -20), false, null)),
                compiled(logicalSatellite(terminalId, terminalId, LatticePoint.of(8, 0, 8), false, null))
            )
        );
        fillNetwork(fixture);
        fillAt(fixture, driveId, origin, drive);
        fillAt(fixture, terminalId, origin, terminal);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty());
        assertEquals(List.of(StructureSetStressFixtures.networkId, driveId, terminalId), result.settled());
        assertFalse(result.outcomes().containsKey(craftingId));
        assertEquals(9 + 1 + 1, fixture.ledger.rows().size());
    }

    @Test
    void anUnreadableOptionalPresenceIsUnknownNotAbsent() {
        StructureSetTestSupport.Fixture fixture = fixture(
            networkSet(
                compiled(logicalSatellite(driveId, driveId, LatticePoint.of(20, 0, 20), true, null)),
                compiled(logicalSatellite(terminalId, terminalId, LatticePoint.of(8, 0, 8), false, null))
            )
        );
        fillNetwork(fixture);
        fillAt(fixture, driveId, origin, drive);
        StructureSet.Satellite terminalSatellite = fixture.plan.satellite(terminalId);
        assertNotNull(terminalSatellite);
        fixture.view.refuse(world(terminalSatellite.siting(), origin));
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.presenceUnreadable.code()), codes(result));
        assertTrue(result.settled().isEmpty(), "unknown is never treated as absent for an optional member");
        assertTrue(fixture.instances.isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void presenceBudgetExhaustionIsScSet0004() {
        StructureSetTestSupport.Fixture fixture = fixture(
            networkSet(
                compiled(logicalSatellite(driveId, driveId, LatticePoint.of(20, 0, 20), true, null)),
                compiled(logicalSatellite(craftingId, craftingId, LatticePoint.of(-20, 0, -20), false, null))
            )
        );
        fillNetwork(fixture);
        fillAt(fixture, driveId, origin, drive);
        StructureSetResult result = fixture.run(Budget.of(1, Integer.MAX_VALUE));

        assertFalse(result.formed());
        assertEquals(List.of(Codes.presenceUnreadable.code()), codes(result));
        assertTrue(result.setFindings().get(0).message().contains("budget"), result.setFindings().get(0).message());
        assertTrue(result.settled().isEmpty());
        assertTrue(fixture.instances.isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void aPartiallyFilledPresenceRegionProvesPresence() {
        StructureSetTestSupport.Fixture fixture = fixture(
            networkSet(
                compiled(logicalSatellite(storageId, storageId, LatticePoint.of(30, 0, 30), false, storagePresence))
            )
        );
        fillNetwork(fixture);
        fillAt(fixture, storageId, storageCell, storage);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty());
        assertEquals(List.of(StructureSetStressFixtures.networkId, storageId), result.settled());
        assertEquals(1, result.claimsOf(storageId).size());
        assertEquals(9 + 1, fixture.ledger.rows().size());
    }

    @Test
    void anEntityDoesNotProvePresence() {
        StructureSetTestSupport.Fixture fixture = fixture(
            networkSet(compiled(logicalSatellite(driveId, driveId, LatticePoint.of(20, 0, 20), false, null)))
        );
        fillNetwork(fixture);
        StructureSet.Satellite driveSatellite = fixture.plan.satellite(driveId);
        assertNotNull(driveSatellite);
        fixture.view.putEntity(world(driveSatellite.siting(), origin), drive);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty());
        assertEquals(List.of(StructureSetStressFixtures.networkId), result.settled());
        assertFalse(result.outcomes().containsKey(driveId), "an entity is not a block-family presence proof");
        assertEquals(9, fixture.ledger.rows().size());
    }

    @Test
    void presenceAndEveryStructureAttemptShareOneBudgetCeiling() {
        StructureSetTestSupport.Fixture fixture = new StructureSetTestSupport.Fixture(
            new CompiledStructureSet(
                StructureSetTestSupport.setId,
                singleCell(StructureSetStressFixtures.networkId, StructureSetStressFixtures.networkController),
                List.of(compiled(logicalSatellite(driveId, driveId, LatticePoint.of(20, 0, 20), true, null)))
            )
        );
        fixture.view.put(world(fixture.plan.rootSiting(), origin), StructureSetStressFixtures.networkController);
        fillAt(fixture, driveId, origin, drive);
        StructureSetResult result = fixture.run(Budget.of(3, Integer.MAX_VALUE));

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(
            List.of(StructureSetStressFixtures.networkId, driveId),
            result.settled(),
            "presence (1) + root (1) + satellite (1) exactly consume the three-cell set budget"
        );
        assertEquals(1 + 1, fixture.ledger.rows().size());
    }

    @Test
    void aOneCellBudgetCannotCoverPresenceAndAnAttempt() {
        StructureSetTestSupport.Fixture fixture = new StructureSetTestSupport.Fixture(
            new CompiledStructureSet(
                StructureSetTestSupport.setId,
                singleCell(StructureSetStressFixtures.networkId, StructureSetStressFixtures.networkController),
                List.of(compiled(logicalSatellite(driveId, driveId, LatticePoint.of(20, 0, 20), true, null)))
            )
        );
        fixture.view.put(world(fixture.plan.rootSiting(), origin), StructureSetStressFixtures.networkController);
        fillAt(fixture, driveId, origin, drive);
        StructureSetResult result = fixture.run(Budget.of(1, Integer.MAX_VALUE));

        assertFalse(result.formed());
        assertTrue(result.outcomes().isEmpty(), "the presence read leaves nothing for the root attempt");
        assertTrue(result.settled().isEmpty());
        assertEquals(1, fixture.instances.size(), "the root attempt is made and refused");
        assertTrue(codes(result).contains(Codes.budgetExhausted.code()), () -> codes(result).toString());
        assertEquals(0, fixture.ledger.rows().size());
    }

    private static CompiledSatellite compiled(SatelliteSpec spec) {
        NamespacedId blueprint = spec.blueprint();
        if (blueprint.equals(storageId)) {
            return new CompiledSatellite(spec, storageCell());
        }
        if (blueprint.equals(driveId)) {
            return new CompiledSatellite(spec, singleCell(driveId, drive));
        }
        if (blueprint.equals(craftingId)) {
            return new CompiledSatellite(spec, singleCell(craftingId, crafting));
        }
        if (blueprint.equals(terminalId)) {
            return new CompiledSatellite(spec, singleCell(terminalId, terminal));
        }
        throw new IllegalArgumentException("unknown subnetwork blueprint " + blueprint);
    }

    private static StructureSetTestSupport.Fixture fixture(CompiledStructureSet set) {
        return new StructureSetTestSupport.Fixture(set);
    }
}
