package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.PresenceSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.bodyCells;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.casing;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.compiled;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.fillHappy;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.fillPreheater;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.fillRoot;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.half;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.id;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.leftId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.leftParent;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.leftSatellite;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.plug;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.port;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.preheaterBlueprint;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.preheaterSatellite;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rightId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rightParent;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rightSatellite;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rootId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.setId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.shell;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.singleBlueprint;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.solarId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.solarSatellite;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.wideBlueprint;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.wideId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.world;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureSetHostTest {

    @Test
    void formsTheRootAndTwoPresentSatellitesWithSeparateClaims() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            )
        );
        fillHappy(fixture);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.setFindings().isEmpty(), () -> result.setFindings().toString());
        assertEquals(List.of(rootId, leftId, rightId), result.settled());
        assertEquals(List.of(rootId, leftId, rightId), new ArrayList<>(result.outcomes().keySet()));
        assertTrue(result.rolledBack().isEmpty());
        assertTrue(result.rollbackFailures().isEmpty());
        assertEquals(List.of(rootId, leftId, rightId), new ArrayList<>(result.claims().keySet()));

        assertEquals(7, result.claimsOf(rootId).size());
        assertEquals(2, result.claimsOf(leftId).size());
        assertEquals(2, result.claimsOf(rightId).size());
        assertEquals(11, fixture.ledger.rows().size());
        assertEquals(11, result.claims().values().stream().mapToInt(List::size).sum());

        Blueprint root = fixture.plan.root();
        for (ClaimRow row : result.claimsOf(rootId)) {
            assertEquals(fixture.instance(0), row.instance());
            assertEquals(fixture.plan.rootSiting(), row.siting());
            assertNotNull(root.bay(row.bay()));
        }
        StructureSet.Satellite left = fixture.plan.satellite(leftId);
        assertNotNull(left);
        for (ClaimRow row : result.claimsOf(leftId)) {
            assertEquals(left.siting(), row.siting());
            assertNotNull(left.blueprint().bay(row.bay()));
        }
        assertEquals(LatticePoint.of(98, 10, 101), left.siting().origin());
        StructureSet.Satellite right = fixture.plan.satellite(rightId);
        assertNotNull(right);
        assertEquals(half, right.siting().placement().orientation());
        // the root settles first; each satellite then loses one CAS round and retries once
        assertEquals(5, fixture.ledger.settleCalls);
    }

    @Test
    void anAbsentRequiredSatelliteIsScSet0001AndStopsBeforeCheck() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(leftSatellite(true, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            )
        );
        fillRoot(fixture);
        fillPreheater(fixture, fixture.plan.satellite(rightId));
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertTrue(result.settled().isEmpty());
        assertTrue(result.outcomes().isEmpty());
        assertTrue(result.claims().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
        assertTrue(fixture.instances.isEmpty(), "no check was attempted");
        assertEquals(List.of(Codes.satelliteAbsent.code()), codes(result.setFindings()));
    }

    @Test
    void requiredPresenceIsCheckedBeforeOptionalPresence() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(true, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            )
        );
        fillRoot(fixture);
        StructureSet.Satellite left = fixture.plan.satellite(leftId);
        assertNotNull(left);
        fixture.view.refuse(world(left.siting(), LatticePoint.origin));
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.satelliteAbsent.code()), codes(result.setFindings()));
    }

    @Test
    void anAbsentOptionalSatelliteIsSkipped() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            )
        );
        fillRoot(fixture);
        fillPreheater(fixture, fixture.plan.satellite(leftId));
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(rootId, leftId), result.settled());
        assertEquals(List.of(rootId, leftId), new ArrayList<>(result.outcomes().keySet()));
        assertFalse(result.claims().containsKey(rightId));
        assertEquals(9, fixture.ledger.rows().size());
        assertEquals(9, result.claims().values().stream().mapToInt(List::size).sum());
    }

    @Test
    void aPresenceOverrideIgnoresTheRestOfTheSatelliteBounds() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(
                    preheaterSatellite(
                        leftId,
                        LatticePoint.of(-2, 0, 1),
                        Orientation.identity,
                        true,
                        PresenceSpec.cell(port),
                        LinkSpec.face(leftParent, port)
                    ),
                    preheaterBlueprint()
                )
            )
        );
        fillRoot(fixture);
        StructureSet.Satellite left = fixture.plan.satellite(leftId);
        assertNotNull(left);
        fixture.view.put(world(left.siting(), LatticePoint.origin), shell);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertTrue(result.settled().isEmpty());
        assertTrue(fixture.instances.isEmpty());
        assertEquals(List.of(Codes.satelliteAbsent.code()), codes(result.setFindings()));
    }

    @Test
    void aPartiallyFilledPresenceRegionIsPresent() {
        PresenceSpec partial = PresenceSpec
                .of(CuboidSet.of(Cuboid.of(LatticePoint.origin), Cuboid.of(LatticePoint.of(1, 0, 1))));
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(
                    preheaterSatellite(
                        leftId,
                        LatticePoint.of(-2, 0, 1),
                        Orientation.identity,
                        false,
                        partial,
                        LinkSpec.face(leftParent, port)
                    ),
                    preheaterBlueprint()
                )
            )
        );
        fillRoot(fixture);
        fillPreheater(fixture, fixture.plan.satellite(leftId));
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertTrue(result.settled().contains(leftId));
        assertTrue(result.setFindings().isEmpty());
    }

    @Test
    void anUnreadablePresenceCellIsScSet0004() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(compiled(leftSatellite(true, LinkSpec.face(leftParent, port)), preheaterBlueprint()))
        );
        fillRoot(fixture);
        StructureSet.Satellite left = fixture.plan.satellite(leftId);
        assertNotNull(left);
        fixture.view.refuse(world(left.siting(), LatticePoint.origin));
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertTrue(result.settled().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
        assertEquals(List.of(Codes.presenceUnreadable.code()), codes(result.setFindings()));
    }

    @Test
    void aPresenceBudgetExhaustionIsUnreadable() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(compiled(leftSatellite(true, LinkSpec.face(leftParent, port)), preheaterBlueprint()))
        );
        fillRoot(fixture);
        StructureSetResult result = fixture.run(Budget.of(1, Integer.MAX_VALUE));

        assertFalse(result.formed());
        assertTrue(result.settled().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
        assertEquals(List.of(Codes.presenceUnreadable.code()), codes(result.setFindings()));
        assertTrue(result.setFindings().get(0).message().contains("budget"), result.setFindings().get(0).message());
    }

    @Test
    void aBlockEntityOccupantProvesPresence() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(compiled(leftSatellite(true, LinkSpec.face(leftParent, port)), preheaterBlueprint()))
        );
        fillRoot(fixture);
        StructureSet.Satellite left = fixture.plan.satellite(leftId);
        assertNotNull(left);
        fixture.view.putBlockEntity(world(left.siting(), LatticePoint.origin), shell);
        fixture.view.put(world(left.siting(), port), plug);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(rootId, leftId), result.settled());
    }

    @Test
    void aMisalignedFaceLinkIsScSet0002() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(
                    preheaterSatellite(
                        leftId,
                        LatticePoint.of(-3, 0, 1),
                        Orientation.identity,
                        true,
                        null,
                        LinkSpec.face(leftParent, port)
                    ),
                    preheaterBlueprint()
                )
            )
        );
        fillRoot(fixture);
        fillPreheater(fixture, fixture.plan.satellite(leftId));
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertTrue(result.settled().isEmpty());
        assertTrue(fixture.instances.isEmpty());
        assertEquals(List.of(Codes.linkDistanceInvalid.code()), codes(result.setFindings()));
    }

    @Test
    void rangeLinksHonorChebyshevManhattanAndEuclidean() {
        assertTrue(
            runSolar(
                LatticePoint.of(4, 0, 3),
                LinkSpec.range(rightParent, LatticePoint.origin, 2, DistanceMetric.chebyshev)
            ).formed()
        );
        assertEquals(
            List.of(Codes.linkDistanceInvalid.code()),
            codes(
                runSolar(
                    LatticePoint.of(4, 0, 3),
                    LinkSpec.range(rightParent, LatticePoint.origin, 2, DistanceMetric.manhattan)
                ).setFindings()
            )
        );
        assertEquals(
            List.of(Codes.linkDistanceInvalid.code()),
            codes(
                runSolar(
                    LatticePoint.of(4, 0, 3),
                    LinkSpec.range(rightParent, LatticePoint.origin, 2, DistanceMetric.euclidean)
                ).setFindings()
            )
        );

        assertTrue(
            runSolar(
                LatticePoint.of(3, 0, 2),
                LinkSpec.range(rightParent, LatticePoint.origin, 1, DistanceMetric.chebyshev)
            ).formed()
        );
        assertEquals(
            List.of(Codes.linkDistanceInvalid.code()),
            codes(
                runSolar(
                    LatticePoint.of(3, 0, 2),
                    LinkSpec.range(rightParent, LatticePoint.origin, 1, DistanceMetric.euclidean)
                ).setFindings()
            )
        );
        assertTrue(
            runSolar(
                LatticePoint.of(2, 0, 3),
                LinkSpec.range(rightParent, LatticePoint.origin, 2, DistanceMetric.euclidean)
            ).formed()
        );
    }

    @Test
    void aNoneLinkCarriesNoDistanceAndStillSettles() {
        StructureSetResult result = runSolar(LatticePoint.of(20, 0, 20), LinkSpec.none());
        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(rootId, id("solar")), result.settled());
    }

    @Test
    void aWideSatelliteValidatesEveryFaceLink() {
        Blueprint wide = wideBlueprint();
        SatelliteSpec spec = new SatelliteSpec(
            id("wide_attach"),
            wideId,
            LatticePoint.of(0, 1, 0),
            Orientation.identity,
            false,
            Value.object(),
            null,
            List.of(
                LinkSpec.face(LatticePoint.of(0, 0, 0), LatticePoint.of(0, 0, 0)),
                LinkSpec.face(LatticePoint.of(1, 0, 0), LatticePoint.of(1, 0, 0)),
                LinkSpec.face(LatticePoint.of(2, 0, 0), LatticePoint.of(2, 0, 0))
            ),
            null
        );
        StructureSetTestSupport.Fixture fixture = fixture(compiled(compiled(spec, wide)));
        fillRoot(fixture);
        StructureSet.Satellite placed = fixture.plan.satellite(id("wide_attach"));
        assertNotNull(placed);
        for (int x = 0; x < 3; x++) {
            fixture.view.put(world(placed.siting(), LatticePoint.of(x, 0, 0)), shell);
        }
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(rootId, id("wide_attach")), result.settled());
        assertEquals(3, result.claimsOf(id("wide_attach")).size());
    }

    @Test
    void severalRangeLinksMayFanInToOneParentCell() {
        Blueprint solar = singleBlueprint(solarId, shell);
        CompiledStructureSet set = compiled(
            compiled(
                solarSatellite(
                    id("solar_a"),
                    LatticePoint.of(4, 0, 1),
                    LinkSpec.range(rightParent, LatticePoint.origin, 8)
                ),
                solar
            ),
            compiled(
                solarSatellite(
                    id("solar_b"),
                    LatticePoint.of(6, 0, 1),
                    LinkSpec.range(rightParent, LatticePoint.origin, 8)
                ),
                solar
            )
        );
        StructureSetTestSupport.Fixture fixture = fixture(set);
        fillRoot(fixture);
        for (NamespacedId satelliteId : List.of(id("solar_a"), id("solar_b"))) {
            StructureSet.Satellite placed = fixture.plan.satellite(satelliteId);
            assertNotNull(placed);
            fixture.view.put(world(placed.siting(), LatticePoint.origin), shell);
        }
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(rootId, id("solar_a"), id("solar_b")), result.settled());
    }

    @Test
    void theHostRechecksLinkCoverageDefensively() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 3, 1, 3);
        Bay bay = Bay.of(
            id("only"),
            CuboidSet.of(Cuboid.of(LatticePoint.origin)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Blueprint root = Blueprint.builder(rootId).bounds(bounds).checks(
            List.of(Check.of(id("only_check"), bay, Focuses.occupant(LatticePoint.origin), Expectations.block(casing)))
        ).build();
        SatelliteSpec spec = preheaterSatellite(
            leftId,
            LatticePoint.of(-2, 0, 1),
            Orientation.identity,
            true,
            null,
            LinkSpec.face(LatticePoint.of(1, 0, 0), port)
        );
        CompiledStructureSet set = new CompiledStructureSet(
            setId,
            root,
            List.of(new CompiledSatellite(spec, preheaterBlueprint()))
        );
        StructureSetTestSupport.Fixture fixture = fixture(set);
        fixture.view.put(world(fixture.plan.rootSiting(), LatticePoint.origin), casing);
        StructureSet.Satellite left = fixture.plan.satellite(leftId);
        assertNotNull(left);
        fixture.view.put(world(left.siting(), LatticePoint.origin), shell);
        fixture.view.put(world(left.siting(), port), plug);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.linkCoverageInvalid.code()), codes(result.setFindings()));
    }

    @Test
    void theHostRechecksTheSatelliteSideLinkCoverage() {
        Blueprint satellite = Blueprint.builder(StructureSetTestSupport.preheaterId).bounds(Cuboid.of(0, 0, 0, 2, 1, 2))
                .checks(
                    List.of(
                        Check.of(
                            id("body_check"),
                            Bay.of(
                                id("body"),
                                CuboidSet.of(Cuboid.of(LatticePoint.origin)),
                                Coverage.everyCell,
                                Footprint.box(),
                                Sharing.exclusive
                            ),
                            Focuses.occupant(LatticePoint.origin),
                            Expectations.block(shell)
                        )
                    )
                ).build();
        SatelliteSpec spec = preheaterSatellite(
            leftId,
            LatticePoint.of(-2, 0, 1),
            Orientation.identity,
            true,
            null,
            LinkSpec.face(leftParent, port)
        );
        CompiledStructureSet set = new CompiledStructureSet(
            setId,
            StructureSetTestSupport.rootBlueprint(),
            List.of(new CompiledSatellite(spec, satellite))
        );
        StructureSetTestSupport.Fixture fixture = fixture(set);
        fillRoot(fixture);
        StructureSet.Satellite left = fixture.plan.satellite(leftId);
        assertNotNull(left);
        fixture.view.put(world(left.siting(), LatticePoint.origin), shell);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.linkCoverageInvalid.code()), codes(result.setFindings()));
    }

    @Test
    void aFailedRootCheckWritesNothing() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            )
        );
        fillHappy(fixture);
        fixture.view.air(world(fixture.plan.rootSiting(), bodyCells().get(0)));
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertTrue(result.settled().isEmpty());
        assertTrue(result.outcomes().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
        assertEquals(1, fixture.instances.size());
        assertTrue(codes(result.setFindings()).contains(Codes.expected.code()), () -> result.setFindings().toString());
    }

    @Test
    void aFailedSatelliteCheckStopsBeforeEverySettle() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            )
        );
        fillHappy(fixture);
        StructureSet.Satellite left = fixture.plan.satellite(leftId);
        assertNotNull(left);
        fixture.view.put(world(left.siting(), LatticePoint.origin), StructureSetTestSupport.wrong);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertTrue(result.settled().isEmpty());
        assertEquals(List.of(rootId), new ArrayList<>(result.outcomes().keySet()));
        assertEquals(0, fixture.ledger.rows().size());
        assertEquals(2, fixture.instances.size());
        assertTrue(codes(result.setFindings()).contains(Codes.expected.code()), () -> result.setFindings().toString());
    }

    @Test
    void aSettleFailureRollsBackTheFormedStructuresInReverseOrder() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            )
        );
        fillHappy(fixture);
        StructureSet.Satellite left = fixture.plan.satellite(leftId);
        StructureSet.Satellite right = fixture.plan.satellite(rightId);
        assertNotNull(left);
        assertNotNull(right);
        fixture.ledger.movingSiting = right.siting();
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(rootId, leftId), result.settled());
        assertEquals(List.of(leftId, rootId), result.rolledBack());
        assertTrue(result.rollbackFailures().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
        assertEquals(List.of(left.siting(), fixture.plan.rootSiting()), fixture.ledger.released);
        assertTrue(
            codes(result.setFindings()).contains(Codes.ledgerMoved.code()),
            () -> result.setFindings().toString()
        );
    }

    @Test
    void aFailedReleaseReportsScSet0007AndKeepsTheOtherRollbacks() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            )
        );
        fillHappy(fixture);
        StructureSet.Satellite right = fixture.plan.satellite(rightId);
        assertNotNull(right);
        fixture.ledger.movingSiting = right.siting();
        fixture.ledger.throwingRelease = fixture.plan.rootSiting();
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(leftId), result.rolledBack());
        assertEquals(1, result.rollbackFailures().size());
        assertEquals(Codes.rollbackFailed.code(), result.rollbackFailures().get(0).code());
        assertEquals(7, fixture.ledger.rows().size(), "the root release failed, so its rows remain");
        assertEquals(List.of(rootId, leftId), result.settled());
        assertEquals(2, fixture.ledger.releaseCalls);
        assertNull(
            fixture.contexts.remove(fixture.instance(0)),
            "the failed-release root context is still invalidated"
        );
    }

    @Test
    void aFailedContextRemovalIsScSet0007AndKeepsTheOtherRemovals() {
        StructureSetTestSupport.RemoveFailingStructures contexts = new StructureSetTestSupport.RemoveFailingStructures();
        StructureSetTestSupport.Fixture fixture = new StructureSetTestSupport.Fixture(
            compiled(
                compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            ),
            StructureSetTestSupport.rootSiting(),
            contexts
        );
        fillHappy(fixture);
        StructureSet.Satellite right = fixture.plan.satellite(rightId);
        assertNotNull(right);
        fixture.ledger.movingSiting = right.siting();
        contexts.allowedRemovals = 1;
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(leftId, rootId), result.rolledBack());
        assertEquals(1, result.rollbackFailures().size());
        assertEquals(Codes.rollbackFailed.code(), result.rollbackFailures().get(0).code());
        assertTrue(
            result.rollbackFailures().get(0).message().contains("context"),
            () -> result.rollbackFailures().get(0).message()
        );
        assertEquals(2, contexts.removed().size(), "both formed attempts are invalidated");
        assertEquals(0, contexts.context(fixture.instance(1)).revision(), "the left context was removed");
        assertTrue(
            contexts.context(fixture.instance(0)).revision() >= 1,
            "the root removal failed, so its context stays for the host"
        );
    }

    @Test
    void rollbackRemovesTheKernelContextOfEveryFormedAttempt() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            )
        );
        fillHappy(fixture);
        StructureSet.Satellite right = fixture.plan.satellite(rightId);
        assertNotNull(right);
        fixture.ledger.movingSiting = right.siting();
        StructureSetResult result = fixture.run();

        assertEquals(List.of(leftId, rootId), result.rolledBack());
        assertNull(fixture.contexts.remove(fixture.instance(0)), "the rolled-back root context is removed");
        assertNull(fixture.contexts.remove(fixture.instance(1)), "the rolled-back left context is removed");
        assertEquals(0, fixture.contexts.context(fixture.instance(0)).revision());
        assertEquals(0, fixture.contexts.context(fixture.instance(1)).revision());
        assertEquals(
            0,
            fixture.contexts.context(fixture.instance(2)).revision(),
            "the failed right settle published none"
        );
    }

    @Test
    void aHandBuiltSatelliteThatSharesTheRootSitingIsRejectedBeforePresence() {
        SatelliteSpec onRoot = preheaterSatellite(leftId, LatticePoint.origin, Orientation.identity, true, null);
        StructureSetTestSupport.Fixture fixture = new StructureSetTestSupport.Fixture(
            StructureSetTestSupport.handBuiltOnRoot(compiled(compiled(onRoot, preheaterBlueprint())))
        );
        StructureSetResult result = fixture.check();

        assertFalse(result.formed());
        assertTrue(result.outcomes().isEmpty());
        assertTrue(result.settled().isEmpty());
        assertTrue(fixture.instances.isEmpty(), "the set is rejected before presence and before any check");
        assertTrue(fixture.ledger.rows().isEmpty());
        assertEquals(List.of(Codes.rootPlacementCollision.code()), codes(result.setFindings()));
    }

    @Test
    void aStaleLeaseChecksAndSettlesNothing() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()))
        );
        fillRoot(fixture);
        fillPreheater(fixture, fixture.plan.satellite(leftId));
        StructureSetTestSupport.TestLease lease = new StructureSetTestSupport.TestLease(fixture.view);
        lease.invalidate();
        StructureSetResult result = fixture.run(lease);

        assertFalse(result.formed());
        assertTrue(result.settled().isEmpty());
        assertTrue(fixture.instances.isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
        assertEquals(List.of(Codes.snapshotStale.code()), codes(result.setFindings()));
    }

    @Test
    void aCheckOnlyRunAdmitsEveryStructureAndWritesNothing() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            )
        );
        fillHappy(fixture);
        StructureSetResult result = fixture.check();

        assertFalse(result.formed());
        assertTrue(result.setFindings().isEmpty(), () -> result.setFindings().toString());
        assertEquals(List.of(rootId, leftId, rightId), new ArrayList<>(result.outcomes().keySet()));
        assertTrue(result.settled().isEmpty());
        assertTrue(result.rolledBack().isEmpty());
        assertTrue(result.rollbackFailures().isEmpty());
        assertTrue(result.claims().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
        assertEquals(0, fixture.ledger.settleCalls);
        assertEquals(3, fixture.instances.size());
    }

    @Test
    void aCheckOnlyRunStopsAtTheFailedMember() {
        StructureSetTestSupport.Fixture fixture = fixture(
            compiled(
                compiled(leftSatellite(false, LinkSpec.face(leftParent, port)), preheaterBlueprint()),
                compiled(rightSatellite(false, LinkSpec.face(rightParent, port)), preheaterBlueprint())
            )
        );
        fillHappy(fixture);
        StructureSet.Satellite left = fixture.plan.satellite(leftId);
        assertNotNull(left);
        fixture.view.put(world(left.siting(), LatticePoint.origin), StructureSetTestSupport.wrong);
        StructureSetResult result = fixture.check();

        assertFalse(result.formed());
        assertTrue(result.settled().isEmpty());
        assertTrue(result.claims().isEmpty());
        assertEquals(List.of(rootId), new ArrayList<>(result.outcomes().keySet()));
        assertEquals(2, fixture.instances.size());
        assertEquals(0, fixture.ledger.rows().size());
        assertEquals(0, fixture.ledger.settleCalls);
        assertTrue(codes(result.setFindings()).contains(Codes.expected.code()), () -> result.setFindings().toString());
    }

    private static StructureSetTestSupport.Fixture fixture(CompiledStructureSet compiled) {
        return new StructureSetTestSupport.Fixture(compiled);
    }

    private static StructureSetResult runSolar(LatticePoint at, LinkSpec link) {
        Blueprint solar = singleBlueprint(solarId, shell);
        SatelliteSpec spec = solarSatellite(id("solar"), at, link);
        StructureSetTestSupport.Fixture fixture = fixture(compiled(compiled(spec, solar)));
        fillRoot(fixture);
        StructureSet.Satellite placed = fixture.plan.satellite(id("solar"));
        assertNotNull(placed);
        fixture.view.put(world(placed.siting(), LatticePoint.origin), shell);
        return fixture.run();
    }

    private static List<FindingCode> codes(List<Finding> findings) {
        return findings.stream().map(Finding::code).toList();
    }
}
