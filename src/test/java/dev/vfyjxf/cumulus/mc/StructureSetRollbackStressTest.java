package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.codes;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.compiledSolar;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillReactor;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.fillSolar;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.id;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.reactorId;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.reactorPort;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.reactorSet;
import static dev.vfyjxf.cumulus.mc.StructureSetStressFixtures.solarSpec;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.owner;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rival;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.world;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rollback, claim filtering and context bookkeeping for a checked reactor set. */
class StructureSetRollbackStressTest {

    private static final NamespacedId firstId = id("solar_one");
    private static final NamespacedId secondId = id("solar_two");
    private static final NamespacedId thirdId = id("solar_three");

    @Test
    void aFailedSatelliteCheckStopsBeforeAnySettle() {
        StructureSetTestSupport.Fixture fixture = filledFixture();
        StructureSet.Satellite second = fixture.plan.satellite(secondId);
        assertNotNull(second);
        fixture.view.put(world(second.siting(), LatticePoint.origin), StructureSetTestSupport.wrong);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertTrue(result.settled().isEmpty());
        assertTrue(result.claims().isEmpty());
        assertEquals(0, fixture.ledger.settleCalls, "no settle runs after a failed check");
        assertEquals(0, fixture.ledger.rows().size());
        assertEquals(List.of(reactorId, firstId), new ArrayList<>(result.outcomes().keySet()));
        assertEquals(3, fixture.instances.size(), "the third satellite is never checked");
        assertTrue(codes(result).contains(Codes.expected.code()), () -> result.setFindings().toString());
        for (int index = 0; index < fixture.instances.size(); index++) {
            assertEquals(
                0,
                fixture.contexts.context(fixture.instance(index)).revision(),
                "a check publishes no context"
            );
        }
    }

    @Test
    void aFailedRootCheckWritesNothingAndPublishesNoContext() {
        StructureSetTestSupport.Fixture fixture = filledFixture();
        fixture.view.put(world(fixture.plan.rootSiting(), LatticePoint.of(0, 0, 0)), StructureSetTestSupport.wrong);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertTrue(result.outcomes().isEmpty());
        assertTrue(result.settled().isEmpty());
        assertEquals(0, fixture.ledger.settleCalls);
        assertEquals(0, fixture.ledger.rows().size());
        assertEquals(1, fixture.instances.size());
        assertEquals(0, fixture.contexts.context(fixture.instance(0)).revision());
        assertTrue(codes(result).contains(Codes.expected.code()));
    }

    @Test
    void aSettleFailureReleasesInReverseOrder() {
        StructureSetTestSupport.Fixture fixture = filledFixture();
        StructureSet.Satellite first = fixture.plan.satellite(firstId);
        StructureSet.Satellite second = fixture.plan.satellite(secondId);
        StructureSet.Satellite third = fixture.plan.satellite(thirdId);
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
        fixture.ledger.movingSiting = third.siting();
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(reactorId, firstId, secondId), result.settled());
        assertEquals(List.of(secondId, firstId, reactorId), result.rolledBack());
        assertTrue(result.rollbackFailures().isEmpty());
        assertEquals(0, fixture.ledger.rows().size(), "every settled row is released");
        assertEquals(List.of(second.siting(), first.siting(), fixture.plan.rootSiting()), fixture.ledger.released);

        assertEquals(List.of(reactorId, firstId, secondId, thirdId), new ArrayList<>(result.outcomes().keySet()));
        assertEquals(List.of(reactorId, firstId, secondId), new ArrayList<>(result.claims().keySet()));
        assertEquals(26, result.claimsOf(reactorId).size());
        assertEquals(1, result.claimsOf(firstId).size());
        assertTrue(codes(result).contains(Codes.ledgerMoved.code()));
        for (int index = 0; index < 3; index++) {
            assertNull(fixture.contexts.remove(fixture.instance(index)), "a rolled-back settle removes its context");
            assertEquals(0, fixture.contexts.context(fixture.instance(index)).revision());
        }
        assertEquals(0, fixture.contexts.context(fixture.instance(3)).revision());
    }

    @Test
    void aFailedReleaseReportsScSet0007AndKeepsTheRest() {
        StructureSetTestSupport.Fixture fixture = filledFixture();
        StructureSet.Satellite third = fixture.plan.satellite(thirdId);
        assertNotNull(third);
        fixture.ledger.movingSiting = third.siting();
        fixture.ledger.throwingRelease = fixture.plan.rootSiting();
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(secondId, firstId), result.rolledBack());
        assertEquals(1, result.rollbackFailures().size());
        assertEquals(Codes.rollbackFailed.code(), result.rollbackFailures().get(0).code());
        assertEquals(26, fixture.ledger.rows().size(), "the root release failed, so its rows remain");
        assertEquals(3, fixture.ledger.releaseCalls, "the remaining releases still run");
        assertEquals(List.of(reactorId, firstId, secondId), result.settled());
        assertFalse(result.formed());
        for (int index = 0; index < 3; index++) {
            assertNull(
                fixture.contexts.remove(fixture.instance(index)),
                "every formed attempt is invalidated, including the failed-release root"
            );
        }
    }

    @Test
    void foreignLedgerRowsAreFilteredFromPerStructureClaims() {
        StructureSetTestSupport.Fixture fixture = filledFixture();
        ClaimRow foreignOwner = new ClaimRow(
            LatticeKey.of(LatticePoint.of(500, 0, 500)),
            Sharing.exclusive,
            rival,
            fixture.plan.rootSiting(),
            id("foreign")
        );
        ClaimRow foreignSiting = new ClaimRow(
            LatticeKey.of(LatticePoint.of(500, 0, 501)),
            Sharing.exclusive,
            owner,
            fixture.plan.rootSiting().withOrigin(LatticePoint.of(700, 10, 700)),
            id("foreign")
        );
        fixture.ledger.seed(foreignOwner, foreignSiting);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(reactorId, firstId, secondId, thirdId), result.settled());
        assertEquals(26 + 3, result.claims().values().stream().mapToInt(List::size).sum());
        assertEquals(26 + 3 + 2, fixture.ledger.rows().size(), "the foreign rows stay in the ledger");
        for (List<ClaimRow> rows : result.claims().values()) {
            for (ClaimRow row : rows) {
                assertFalse(row.cell().point().equals(LatticePoint.of(500, 0, 500)));
                assertFalse(row.cell().point().equals(LatticePoint.of(500, 0, 501)));
                assertFalse(row.bay().equals(id("foreign")));
            }
        }
        assertEquals(26, result.claimsOf(reactorId).size());
        assertEquals(1, result.claimsOf(firstId).size());
    }

    @Test
    void aRequiredSatelliteDeclaredLastStillSettlesInDeclarationOrder() {
        NamespacedId optionalFirst = id("solar_optional");
        NamespacedId requiredLast = id("solar_required");
        StructureSetTestSupport.Fixture fixture = new StructureSetTestSupport.Fixture(
            reactorSet(
                compiledSolar(
                    solarSpec(
                        optionalFirst,
                        LatticePoint.of(1, 1, 6),
                        LinkSpec.range(reactorPort, LatticePoint.origin, 4)
                    )
                ),
                compiledSolar(
                    solarSpec(
                        requiredLast,
                        LatticePoint.of(4, 1, 5),
                        true,
                        LinkSpec.range(reactorPort, LatticePoint.origin, 4)
                    )
                )
            )
        );
        fillReactor(fixture);
        fillSolar(fixture, optionalFirst);
        fillSolar(fixture, requiredLast);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(reactorId, optionalFirst, requiredLast), result.settled());
        assertEquals(26 + 2, fixture.ledger.rows().size());
    }

    private static StructureSetTestSupport.Fixture filledFixture() {
        StructureSetTestSupport.Fixture fixture = new StructureSetTestSupport.Fixture(
            reactorSet(
                compiledSolar(
                    solarSpec(firstId, LatticePoint.of(1, 1, 6), LinkSpec.range(reactorPort, LatticePoint.origin, 4))
                ),
                compiledSolar(
                    solarSpec(secondId, LatticePoint.of(4, 1, 5), LinkSpec.range(reactorPort, LatticePoint.origin, 4))
                ),
                compiledSolar(
                    solarSpec(thirdId, LatticePoint.of(-1, 1, 6), LinkSpec.range(reactorPort, LatticePoint.origin, 4))
                )
            )
        );
        fillReactor(fixture);
        fillSolar(fixture, firstId);
        fillSolar(fixture, secondId);
        fillSolar(fixture, thirdId);
        return fixture;
    }
}
