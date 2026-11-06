package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.PresenceSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.Fixture;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.TestLease;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.casing;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.fillRoot;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.half;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.id;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.owner;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.place;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.preheaterBlueprint;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rootBlueprint;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rootOrigin;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rootSiting;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.setId;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.shell;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.singleBlueprint;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.wideBlueprint;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.world;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureSetAdversarialTest {

    private static final NamespacedId rootBlock = casing;
    private static final NamespacedId remoteBlock = shell;
    private static final NamespacedId remoteBlueprintId = id("remote_blueprint");

    @Test
    void anEmptySetRunsTheRootOnly() {
        Blueprint root = singleBlueprint(id("root"), rootBlock);
        Fixture fixture = new Fixture(set(root));
        fixture.view.put(world(fixture.plan.rootSiting(), LatticePoint.origin), rootBlock);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(root.id()), result.settled());
        assertEquals(List.of(root.id()), new ArrayList<>(result.outcomes().keySet()));
        assertEquals(1, result.claimsOf(root.id()).size());
        assertEquals(1, fixture.ledger.rows().size());
        assertEquals(1, fixture.structures.instances().size());
        assertEquals(fixture.plan.rootBounds().asSet(), fixture.structures.instances().get(0).bounds());
    }

    @Test
    void aSingleRemoteSatelliteFormsWithoutALink() {
        NamespacedId satelliteId = id("remote_one");
        Fixture fixture = busyFixture(List.of(satSpec(satelliteId, LatticePoint.of(2, 0, 0), false)));
        fillBusy(fixture);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(id("root"), satelliteId), result.settled());
        assertEquals(2, fixture.structures.instances().size());
        assertEquals(2, fixture.ledger.rows().size());
        StructureSet.Satellite placed = fixture.plan.satellite(satelliteId);
        assertNotNull(placed);
        StructureInstance instance = fixture.structures.find(owner, placed.siting());
        assertNotNull(instance);
        assertEquals(placed.worldBounds().asSet(), instance.bounds());
        assertEquals(1, fixture.structures.findAt(placed.worldBounds().min()).size());
    }

    @Test
    void sixteenSatellitesSettleInDeclarationOrderWithOneInstanceEach() {
        List<SatelliteSpec> specs = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            specs.add(satSpec(id("remote_" + index), LatticePoint.of(2 + index * 2, 0, 0), index % 3 == 0));
        }
        Fixture fixture = busyFixture(specs);
        fillBusy(fixture);
        StructureSetResult result = fixture.run();

        List<NamespacedId> expected = new ArrayList<>();
        expected.add(id("root"));
        for (SatelliteSpec spec : specs) {
            expected.add(spec.id());
        }
        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(expected, result.settled());
        assertEquals(expected, new ArrayList<>(result.outcomes().keySet()));
        assertEquals(17, result.claims().values().stream().mapToInt(List::size).sum());
        assertEquals(17, fixture.ledger.rows().size());
        assertEquals(17, fixture.structures.instances().size());
        for (StructureSet.Satellite satellite : fixture.plan.satellites()) {
            StructureInstance instance = fixture.structures.find(owner, satellite.siting());
            assertNotNull(instance, () -> "no instance at " + satellite.id());
            assertEquals(satellite.worldBounds().asSet(), instance.bounds());
            assertTrue(instance.context().revision() >= 1, () -> "no context for " + satellite.id());
        }
    }

    @Test
    void requiredPresenceIsConsideredBeforeAnUnreadableOptionalPresence() {
        NamespacedId optionalId = id("optional_unreadable");
        NamespacedId requiredId = id("required_absent");
        List<SatelliteSpec> specs = List.of(
            satSpec(optionalId, LatticePoint.of(2, 0, 0), false),
            satSpec(requiredId, LatticePoint.of(4, 0, 0), true)
        );
        Fixture fixture = busyFixture(specs);
        StructureSet.Satellite optional = fixture.plan.satellite(optionalId);
        assertNotNull(optional);
        fixture.view.refuse(optional.presenceCells().decomposition().get(0).min());
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.satelliteAbsent.code()), codes(result.setFindings()));
        assertTrue(result.outcomes().isEmpty());
        assertTrue(fixture.instances.isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void theSameSitingIsRejectedAtPlacementAndBeforeAnyHandBuiltDraft() {
        CompiledStructureSet compiled = busyCompiled(
            List.of(satSpec(id("same_siting"), LatticePoint.origin, Orientation.identity, false))
        );
        StructureSet.Placement placement = StructureSet.place(compiled, rootSiting());
        assertFalse(placement.succeeded());
        assertTrue(codes(placement.findings()).contains(Codes.rootPlacementCollision.code()));

        Fixture fixture = new Fixture(StructureSetTestSupport.handBuiltOnRoot(compiled));
        StructureSetResult result = fixture.check();
        assertFalse(result.formed());
        assertEquals(List.of(Codes.rootPlacementCollision.code()), codes(result.setFindings()));
        assertTrue(result.outcomes().isEmpty());
        assertTrue(fixture.instances.isEmpty(), "no draft may be begun for a colliding set");
        assertTrue(fixture.structures.instances().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void aNonIdentitySatelliteAtTheRootOriginIsPlacedUnderARotatedRoot() {
        NamespacedId satelliteId = id("origin_satellite");
        SatelliteSpec spec = satSpec(satelliteId, LatticePoint.origin, half, false);
        CompiledStructureSet compiled = busyCompiled(List.of(spec));
        Siting rotatedRoot = Siting.of(rootOrigin, half);
        StructureSet.Placement placement = StructureSet.place(compiled, rotatedRoot);
        assertTrue(placement.succeeded(), () -> placement.findings().toString());
        StructureSet plan = placement.set();
        assertNotNull(plan);
        StructureSet.Satellite placed = plan.satellite(satelliteId);
        assertNotNull(placed);
        assertEquals(
            rotatedRoot.compose(new CompiledSatellite(spec, remoteBlueprint()).relativeSiting()),
            placed.siting()
        );
        assertEquals(placed.worldBounds(), placed.siting().toWorld(remoteBlueprint().bounds()));

        assertFalse(placed.siting().equals(plan.rootSiting()), "a non-identity orientation changes the siting");
        assertEquals(placed.worldBounds(), placed.siting().toWorld(remoteBlueprint().bounds()));
    }

    @Test
    void faceLinksRejectSameCellAndDiagonal() {
        Fixture same = preheaterFixture(
            StructureSetTestSupport.preheaterSatellite(
                id("same_cell"),
                LatticePoint.of(-1, 0, 1),
                Orientation.identity,
                false,
                null,
                LinkSpec.face(LatticePoint.of(0, 0, 1), LatticePoint.of(1, 0, 0))
            )
        );
        fillRoot(same);
        StructureSetResult sameResult = same.run();
        assertEquals(List.of(Codes.linkDistanceInvalid.code()), codes(sameResult.setFindings()));

        Fixture diagonal = preheaterFixture(
            StructureSetTestSupport.preheaterSatellite(
                id("diagonal"),
                LatticePoint.of(0, 0, 2),
                Orientation.identity,
                false,
                null,
                LinkSpec.face(LatticePoint.of(0, 0, 1), LatticePoint.of(1, 0, 0))
            )
        );
        fillRoot(diagonal);
        StructureSetResult diagonalResult = diagonal.run();
        assertEquals(List.of(Codes.linkDistanceInvalid.code()), codes(diagonalResult.setFindings()));
    }

    @Test
    void rangeLinksAcceptExactlyTheMaximumAndRejectOneMore() {
        Fixture exact = singleCellFixture(
            id("range_exact"),
            LatticePoint.of(3, 0, 0),
            LinkSpec.range(LatticePoint.origin, LatticePoint.origin, 3, DistanceMetric.manhattan)
        );
        StructureSetResult exactResult = exact.run();
        assertTrue(exactResult.formed(), () -> exactResult.setFindings().toString());

        Fixture over = singleCellFixture(
            id("range_over"),
            LatticePoint.of(4, 0, 0),
            LinkSpec.range(LatticePoint.origin, LatticePoint.origin, 3, DistanceMetric.manhattan)
        );
        StructureSetResult overResult = over.run();
        assertFalse(overResult.formed());
        assertEquals(List.of(Codes.linkDistanceInvalid.code()), codes(overResult.setFindings()));
    }

    @Test
    void aLinkCellOutsideEveryCheckReadScopeIsRejectedBeforeDrafting() {
        Cuboid rootBounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        Bay rootBay = Bay.of(
            id("root_checked"),
            CuboidSet.of(Cuboid.of(LatticePoint.origin)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Blueprint root = Blueprint.builder(id("root_uncovered")).bounds(rootBounds)
                .check(
                    Check.of(
                        id("root_checked_check"),
                        rootBay,
                        Focuses.occupant(LatticePoint.origin),
                        Expectations.block(rootBlock)
                    )
                ).build();
        SatelliteSpec spec = new SatelliteSpec(
            id("uncovered_parent"),
            remoteBlueprintId,
            LatticePoint.of(2, 0, 0),
            Orientation.identity,
            false,
            Value.object(),
            null,
            List.of(LinkSpec.face(LatticePoint.of(1, 0, 0), LatticePoint.origin)),
            null
        );
        Fixture fixture = new Fixture(new CompiledStructureSet(setId, root, List.of(remoteCompiled(spec))));
        fixture.view.put(world(fixture.plan.rootSiting(), LatticePoint.origin), rootBlock);
        StructureSet.Satellite satellite = fixture.plan.satellite(spec.id());
        assertNotNull(satellite);
        fixture.view.put(world(satellite.siting(), LatticePoint.origin), remoteBlock);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.linkCoverageInvalid.code()), codes(result.setFindings()));
        assertTrue(fixture.instances.isEmpty());
        assertTrue(fixture.structures.instances().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void anOverlappingSatellitePresenceIsProvedByARootBlockButItsOwnCheckStillFails() {
        SatelliteSpec spec = new SatelliteSpec(
            id("overlap"),
            preheaterBlueprint().id(),
            LatticePoint.of(2, 0, 0),
            Orientation.identity,
            true,
            Value.object(),
            new PresenceSpec(CuboidSet.of(Cuboid.of(LatticePoint.origin)), null),
            List.of(),
            null
        );
        Fixture fixture = new Fixture(
            new CompiledStructureSet(setId, rootBlueprint(), List.of(new CompiledSatellite(spec, preheaterBlueprint())))
        );
        fillRoot(fixture);
        StructureSet.Satellite satellite = fixture.plan.satellite(spec.id());
        assertNotNull(satellite);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertTrue(codes(result.setFindings()).contains(Codes.expected.code()));
        assertFalse(codes(result.setFindings()).contains(Codes.satelliteAbsent.code()));
        assertFalse(codes(result.setFindings()).contains(Codes.presenceUnreadable.code()));
        assertTrue(result.claims().isEmpty());
        assertEquals(0, fixture.structures.instances().size());
    }

    @Test
    void zeroBudgetStopsAtPresenceWithNoDraft() {
        Fixture fixture = busyFixture(List.of(satSpec(id("budget_zero"), LatticePoint.of(2, 0, 0), false)));
        fillBusy(fixture);
        StructureSetResult result = fixture.run(new Budget(0, 0));

        assertFalse(result.formed());
        assertEquals(List.of(Codes.presenceUnreadable.code()), codes(result.setFindings()));
        assertTrue(result.outcomes().isEmpty());
        assertTrue(fixture.instances.isEmpty());
        assertTrue(fixture.structures.instances().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void oneCellBudgetIsConsumedByPresenceAndStopsTheRootCheck() {
        Fixture fixture = busyFixture(List.of(satSpec(id("budget_one"), LatticePoint.of(2, 0, 0), false)));
        fillBusy(fixture);
        StructureSetResult result = fixture.run(new Budget(1, 0));

        assertFalse(result.formed());
        assertTrue(codes(result.setFindings()).contains(Codes.budgetExhausted.code()));
        assertTrue(result.outcomes().isEmpty());
        assertTrue(result.settled().isEmpty());
        assertTrue(result.claims().isEmpty());
        assertTrue(fixture.structures.instances().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void theExactSharedBudgetFormsAndOneShortLeavesNoSettleOrRow() {
        CompiledStructureSet compiled = busyCompiled(
            List.of(
                satSpec(id("budget_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("budget_b"), LatticePoint.of(4, 0, 0), false)
            )
        );
        Fixture baseline = new Fixture(compiled);
        fillBusy(baseline);
        StructureSetResult formed = baseline.run();
        assertTrue(formed.formed(), () -> formed.setFindings().toString());
        Spend memberSpend = Spend.zero;
        for (MatchOutcome outcome : formed.outcomes().values()) {
            memberSpend = memberSpend.plus(outcome.spend());
        }
        int presenceCells = compiled.satellites().size();
        Budget exact = Budget.of(presenceCells + memberSpend.cells(), memberSpend.sections());

        Fixture exactFixture = new Fixture(compiled);
        fillBusy(exactFixture);
        assertTrue(exactFixture.run(exact).formed(), "the exact shared budget must form");

        Budget shortBudget = memberSpend.cells() > 0
                ? Budget.of(presenceCells + memberSpend.cells() - 1, memberSpend.sections())
                : Budget.of(presenceCells, memberSpend.sections() - 1);
        Fixture shortFixture = new Fixture(compiled);
        fillBusy(shortFixture);
        StructureSetResult shortResult = shortFixture.run(shortBudget);
        assertFalse(shortResult.formed());
        assertTrue(shortResult.settled().isEmpty());
        assertTrue(shortResult.claims().isEmpty());
        assertTrue(shortResult.rolledBack().isEmpty());
        assertTrue(shortFixture.structures.instances().isEmpty());
        assertEquals(0, shortFixture.ledger.rows().size());
    }

    @Test
    void aSettleFailureAtEachMemberRollsBackOnlyTheEarlierMembersAndInvalidatesTheirContexts() {
        CompiledStructureSet compiled = busyCompiled(
            List.of(
                satSpec(id("rollback_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("rollback_b"), LatticePoint.of(4, 0, 0), false)
            )
        );
        List<Siting> sitings = List.of(rootSiting(), compiledSatSiting(compiled, 0), compiledSatSiting(compiled, 1));
        for (int failed = 0; failed < sitings.size(); failed++) {
            int failedIndex = failed;
            Fixture fixture = new Fixture(compiled);
            fillBusy(fixture);
            fixture.ledger.movingSiting = sitings.get(failedIndex);
            StructureSetResult result = fixture.run();

            List<NamespacedId> all = List.of(id("root"), id("rollback_a"), id("rollback_b"));
            assertFalse(result.formed(), () -> "member " + failedIndex + " should fail");
            assertEquals(all.subList(0, failedIndex), result.settled(), "settled prefix at failure " + failedIndex);
            List<NamespacedId> reverse = new ArrayList<>(all.subList(0, failedIndex));
            Collections.reverse(reverse);
            assertEquals(reverse, result.rolledBack(), "reverse rollback at failure " + failedIndex);
            assertEquals(new LinkedHashSet<>(all.subList(0, failedIndex)), result.claims().keySet());
            assertEquals(0, fixture.ledger.rows().size());
            assertTrue(fixture.structures.instances().isEmpty(), "formed members were invalidated");
            for (InstanceId id : fixture.instances) {
                assertNull(fixture.structures.remove(id), "no draft or formed instance may leak: " + id);
            }
        }
    }

    @Test
    void aFailedReleaseAtEachFormedMemberStillInvalidatesEveryContext() {
        CompiledStructureSet compiled = busyCompiled(
            List.of(
                satSpec(id("release_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("release_b"), LatticePoint.of(4, 0, 0), false)
            )
        );
        List<Siting> sitings = List.of(rootSiting(), compiledSatSiting(compiled, 0), compiledSatSiting(compiled, 1));
        for (int failedRelease = 0; failedRelease < 2; failedRelease++) {
            Fixture fixture = new Fixture(compiled);
            fillBusy(fixture);
            fixture.ledger.movingSiting = sitings.get(2);
            fixture.ledger.throwingRelease = sitings.get(failedRelease);
            StructureSetResult result = fixture.run();

            assertFalse(result.formed());
            List<NamespacedId> expectedRolledBack = new ArrayList<>(List.of(id("release_a"), id("root")));
            expectedRolledBack.remove(List.of(id("root"), id("release_a"), id("release_b")).get(failedRelease));
            assertEquals(expectedRolledBack, result.rolledBack());
            assertEquals(1, result.rollbackFailures().size());
            assertEquals(Codes.rollbackFailed.code(), result.rollbackFailures().get(0).code());
            assertTrue(fixture.structures.instances().isEmpty(), "all contexts are invalidated");
        }
    }

    @Test
    void aSecondSettleReplacesTheInstancesAndClaimRowsAtTheSameSiting() {
        CompiledStructureSet compiled = busyCompiled(
            List.of(
                satSpec(id("replace_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("replace_b"), LatticePoint.of(4, 0, 0), true)
            )
        );
        Fixture fixture = new Fixture(compiled);
        fillBusy(fixture);
        StructureSetResult first = fixture.run();
        assertTrue(first.formed(), () -> first.setFindings().toString());
        List<InstanceId> firstInstances = List.copyOf(fixture.instances);
        long firstRevision = fixture.ledger.revision();
        int firstRows = fixture.ledger.rows().size();
        assertEquals(3, firstRows);

        StructureSetResult second = fixture.run();
        assertTrue(second.formed(), () -> second.setFindings().toString());
        assertEquals(3, fixture.structures.instances().size(), "the manager replaces, never duplicates");
        assertEquals(firstRows, fixture.ledger.rows().size(), "the ledger replaces the same owner/siting rows");
        assertEquals(firstRows, second.claims().values().stream().mapToInt(List::size).sum());
        assertTrue(fixture.ledger.revision() > firstRevision);
        for (InstanceId old : firstInstances) {
            assertNull(fixture.structures.find(old));
            assertNull(fixture.structures.remove(old));
        }
        for (StructureSet.Satellite satellite : fixture.plan.satellites()) {
            assertNotNull(fixture.structures.find(owner, satellite.siting()));
        }
    }

    @Test
    void checkOnlyAndAFailedMemberLeaveNoDraftOrInstanceBehind() {
        CompiledStructureSet compiled = busyCompiled(
            List.of(
                satSpec(id("draft_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("draft_b"), LatticePoint.of(4, 0, 0), false)
            )
        );
        Fixture checked = new Fixture(compiled);
        fillBusy(checked);
        StructureSetResult checkedResult = checked.check();
        assertTrue(checkedResult.setFindings().isEmpty(), () -> checkedResult.setFindings().toString());
        assertTrue(checked.structures.instances().isEmpty());
        for (InstanceId id : checked.instances) {
            assertNull(checked.structures.remove(id), "check-only draft leaked: " + id);
        }
        assertEquals(0, checked.ledger.rows().size());

        Fixture failed = new Fixture(compiled);
        fillBusy(failed);
        StructureSet.Satellite first = failed.plan.satellite(id("draft_a"));
        assertNotNull(first);
        failed.view.put(world(first.siting(), LatticePoint.origin), StructureSetTestSupport.wrong);
        StructureSetResult failedResult = failed.run();
        assertFalse(failedResult.formed());
        assertTrue(failed.structures.instances().isEmpty());
        for (InstanceId id : failed.instances) {
            assertNull(failed.structures.remove(id), "failed draft leaked: " + id);
        }
        assertEquals(0, failed.ledger.rows().size());
    }

    @Test
    void aLargeSetFormsWithOrderingContextBudgetAndClaimVolume() {
        List<SatelliteSpec> specs = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            specs.add(satSpec(id("large_" + index), LatticePoint.of(2 + index * 2, 0, 0), index % 4 == 0));
        }
        CompiledStructureSet compiled = busyCompiled(specs);
        Fixture fixture = new Fixture(compiled);
        fillBusy(fixture);
        StructureSetResult result = fixture.run();
        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(25, result.settled().size());
        assertEquals(id("root"), result.settled().get(0));
        assertEquals(25, fixture.structures.instances().size());
        assertEquals(25, fixture.ledger.rows().size());
        int claims = result.claims().values().stream().mapToInt(List::size).sum();
        assertEquals(25, claims);
        for (int index = 0; index < 24; index++) {
            assertEquals(id("large_" + index), result.settled().get(index + 1));
        }

        Spend memberSpend = Spend.zero;
        for (MatchOutcome outcome : result.outcomes().values()) {
            memberSpend = memberSpend.plus(outcome.spend());
        }
        Budget exact = Budget.of(24 + memberSpend.cells(), memberSpend.sections());
        Fixture exactFixture = new Fixture(compiled);
        fillBusy(exactFixture);
        StructureSetResult exactResult = exactFixture.run(exact);
        assertTrue(exactResult.formed(), () -> exactResult.setFindings().toString());
        assertEquals(25, exactFixture.structures.instances().size());
    }

    @Test
    void anOptionalAbsentSatelliteIsSkippedWithoutAnError() {
        NamespacedId satelliteId = id("optional_absent");
        Fixture fixture = busyFixture(List.of(satSpec(satelliteId, LatticePoint.of(2, 0, 0), false)));
        fixture.view.put(world(fixture.plan.rootSiting(), LatticePoint.origin), rootBlock);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(id("root")), result.settled());
        assertEquals(1, fixture.structures.instances().size());
        assertEquals(1, fixture.ledger.rows().size());
        assertTrue(result.setFindings().isEmpty());
    }

    @Test
    void aNoneLinkCarriesNoSpatialRequirement() {
        NamespacedId satelliteId = id("none_link");
        Fixture fixture = busyFixture(
            List.of(
                new SatelliteSpec(
                    satelliteId,
                    remoteBlueprintId,
                    LatticePoint.of(10, 0, 0),
                    Orientation.identity,
                    false,
                    Value.object(),
                    null,
                    List.of(LinkSpec.none()),
                    null
                )
            )
        );
        fillBusy(fixture);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(List.of(id("root"), satelliteId), result.settled());
        assertEquals(2, fixture.ledger.rows().size());
    }

    @Test
    void aSatelliteSideLinkCellOutsideCoverageIsRejected() {
        Blueprint root = singleBlueprint(id("root"), rootBlock);
        Blueprint wideSatellite = Blueprint.builder(remoteBlueprintId).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .check(
                    Check.of(
                        id("wide_check"),
                        Bay.of(
                            id("wide_bay"),
                            CuboidSet.of(Cuboid.of(LatticePoint.origin)),
                            Coverage.everyCell,
                            Footprint.box(),
                            Sharing.exclusive
                        ),
                        Focuses.occupant(LatticePoint.origin),
                        Expectations.block(remoteBlock)
                    )
                ).build();
        SatelliteSpec spec = new SatelliteSpec(
            id("uncovered_port"),
            remoteBlueprintId,
            LatticePoint.of(2, 0, 0),
            Orientation.identity,
            false,
            Value.object(),
            null,
            List.of(LinkSpec.face(LatticePoint.origin, LatticePoint.of(1, 0, 0))),
            null
        );
        Fixture fixture = new Fixture(
            new CompiledStructureSet(setId, root, List.of(new CompiledSatellite(spec, wideSatellite)))
        );
        fixture.view.put(world(fixture.plan.rootSiting(), LatticePoint.origin), rootBlock);
        StructureSet.Satellite placed = fixture.plan.satellite(spec.id());
        assertNotNull(placed);
        fixture.view.put(world(placed.siting(), LatticePoint.origin), remoteBlock);
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(Codes.linkCoverageInvalid.code()), codes(result.setFindings()));
        assertTrue(fixture.instances.isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void aNonIdentitySatelliteFormsUnderARotatedRoot() {
        SatelliteSpec spec = StructureSetTestSupport
                .preheaterSatellite(id("turned_satellite"), LatticePoint.of(6, 0, 0), half, false, null);
        CompiledStructureSet compiled = new CompiledStructureSet(
            setId,
            rootBlueprint(),
            List.of(new CompiledSatellite(spec, preheaterBlueprint()))
        );
        Siting rotatedRoot = Siting.of(rootOrigin, half);
        StructureSet.Placement placement = StructureSet.place(compiled, rotatedRoot);
        assertTrue(placement.succeeded(), () -> placement.findings().toString());
        StructureSet plan = placement.set();
        assertNotNull(plan);
        StructureSet.Satellite placed = plan.satellite(spec.id());
        assertNotNull(placed);
        Fixture fixture = new Fixture(plan);
        fillRoot(fixture);
        fixture.view.put(world(placed.siting(), LatticePoint.origin), remoteBlock);
        fixture.view.put(world(placed.siting(), LatticePoint.of(1, 0, 0)), StructureSetTestSupport.plug);
        StructureSetResult result = fixture.run();

        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(2, fixture.structures.instances().size());
        assertEquals(
            Objects.requireNonNull(rotatedRoot.compose(compiled.satellites().get(0).relativeSiting())),
            placed.siting()
        );
    }

    @Test
    void aFailedSetLeavesNoRowsOfThePreviousInstance() {
        Fixture fixture = new Fixture(set(wideBlueprint()));
        for (int x = 0; x < 3; x++) {
            fixture.view.put(world(fixture.plan.rootSiting(), LatticePoint.of(x, 0, 0)), shell);
        }
        StructureSetResult first = fixture.run();
        assertTrue(first.formed(), () -> first.setFindings().toString());
        InstanceId previousRoot = fixture.instance(0);
        assertEquals(3, fixture.ledger.rows().size());

        StructureSet narrower = place(busyCompiled(List.of(satSpec(id("fail_sat"), LatticePoint.of(10, 0, 0), false))));
        StructureSet.Satellite failing = narrower.satellite(id("fail_sat"));
        assertNotNull(failing);
        fixture.view.put(world(rootSiting(), LatticePoint.origin), rootBlock);
        fixture.view.put(world(failing.siting(), LatticePoint.origin), remoteBlock);
        fixture.ledger.movingSiting = failing.siting();
        StructureSetResult second = new StructureSetHost(fixture.kernel).run(
            narrower,
            new TestLease(fixture.view),
            owner,
            Budget.unbounded(),
            spec -> fixture.structures.begin(world, spec)
        );

        assertFalse(second.formed());
        assertEquals(
            0,
            fixture.ledger.rows().size(),
            "every row of the previous instance is released, including the cells outside the new bounds"
        );
        assertNotNull(
            fixture.structures.find(previousRoot),
            "the previous identity stays as a stale entry until the host settles again (D-79)"
        );
    }

    @Test
    void theHostCanSettleAgainAfterAFailedSet() {
        CompiledStructureSet compiled = busyCompiled(
            List.of(
                satSpec(id("retry_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("retry_b"), LatticePoint.of(4, 0, 0), false)
            )
        );
        Fixture fixture = new Fixture(compiled);
        fillBusy(fixture);
        assertTrue(fixture.run().formed(), () -> "the first set forms the previous instances");
        List<InstanceId> stale = List.copyOf(fixture.instances);
        StructureSet.Satellite failing = fixture.plan.satellite(id("retry_a"));
        assertNotNull(failing);
        fixture.ledger.movingSiting = failing.siting();
        StructureSetResult failed = fixture.run();
        assertFalse(failed.formed());
        assertEquals(0, fixture.ledger.rows().size(), "the failed set leaves no previous row behind");

        fixture.ledger.movingSiting = null;
        StructureSetResult again = fixture.run();

        assertTrue(again.formed(), () -> again.setFindings().toString());
        assertEquals(3, fixture.ledger.rows().size(), "the retry settles every member again");
        assertEquals(3, fixture.structures.instances().size());
        for (InstanceId old : stale) {
            assertNull(fixture.structures.find(old), "the retry replaces the stale instance " + old);
        }
        for (StructureSet.Satellite satellite : fixture.plan.satellites()) {
            assertNotNull(fixture.structures.find(owner, satellite.siting()));
        }
    }

    @Test
    void aFailedSetReportsNoRestoreAttempt() {
        CompiledStructureSet compiled = busyCompiled(
            List.of(
                satSpec(id("no_restore_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("no_restore_b"), LatticePoint.of(4, 0, 0), false)
            )
        );
        Fixture fixture = new Fixture(compiled);
        fillBusy(fixture);
        assertTrue(fixture.run().formed(), () -> "the first set forms the previous instances");
        StructureSet.Satellite failing = fixture.plan.satellite(id("no_restore_a"));
        assertNotNull(failing);
        fixture.ledger.movingSiting = failing.siting();
        StructureSetResult second = fixture.run();

        assertFalse(second.formed());
        assertEquals(List.of(id("root")), second.rolledBack());
        assertTrue(second.rollbackFailures().isEmpty(), () -> second.rollbackFailures().toString());
        for (Finding finding : second.setFindings()) {
            assertFalse(finding.message().contains("restor"), () -> "no restore attempt is reported: " + finding);
        }
    }

    @Test
    void aThrowingCommitLeavesNoSettledClaimOrDraft() {
        Fixture fixture = busyFixture(
            List.of(
                satSpec(id("throw_commit_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("throw_commit_b"), LatticePoint.of(4, 0, 0), false)
            )
        );
        fillBusy(fixture);
        List<StructureAttempt> attempts = new ArrayList<>();
        StructureSetHost host = new StructureSetHost(fixture.kernel);
        assertThrows(
            IllegalStateException.class,
            () -> host.run(fixture.plan, new TestLease(fixture.view), owner, Budget.unbounded(), spec -> {
                StructureAttempt delegate = fixture.structures.begin(world, spec);
                attempts.add(delegate);
                boolean root = spec.structure().equals(id("root"));
                return new ThrowingAttempt(delegate, root, false);
            })
        );

        assertTrue(
            fixture.ledger.rows().isEmpty(),
            () -> "a throwing commit must not leave settled claims: rows=" + fixture.ledger.rows()
        );
        for (StructureAttempt attempt : attempts) {
            assertNull(fixture.structures.remove(attempt.id()), "a throwing commit must not leak a draft");
        }
        assertTrue(fixture.structures.instances().isEmpty());
    }

    @Test
    void aThrowingCommitAfterTheRootRollsBackTheCommittedPrefix() {
        Fixture fixture = busyFixture(
            List.of(
                satSpec(id("throw_late_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("throw_late_b"), LatticePoint.of(4, 0, 0), false)
            )
        );
        fillBusy(fixture);
        List<StructureAttempt> attempts = new ArrayList<>();
        StructureSetHost host = new StructureSetHost(fixture.kernel);
        assertThrows(
            IllegalStateException.class,
            () -> host.run(fixture.plan, new TestLease(fixture.view), owner, Budget.unbounded(), spec -> {
                StructureAttempt delegate = fixture.structures.begin(world, spec);
                attempts.add(delegate);
                return new ThrowingAttempt(delegate, spec.structure().equals(remoteBlueprintId), false);
            })
        );

        assertTrue(fixture.ledger.rows().isEmpty(), () -> "rows=" + fixture.ledger.rows());
        assertTrue(fixture.structures.instances().isEmpty(), "the committed prefix was rolled back");
        for (StructureAttempt attempt : attempts) {
            assertNull(fixture.structures.remove(attempt.id()), "an attempt leaked after commit cleanup");
        }
    }

    @Test
    void aThrowingPublishDoesNotLeavePartialClaimsOrDrafts() {
        Fixture fixture = busyFixture(
            List.of(
                satSpec(id("throw_publish_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("throw_publish_b"), LatticePoint.of(4, 0, 0), false)
            )
        );
        fillBusy(fixture);
        List<StructureAttempt> attempts = new ArrayList<>();
        StructureSetHost host = new StructureSetHost(fixture.kernel);
        assertThrows(
            IllegalStateException.class,
            () -> host.run(fixture.plan, new TestLease(fixture.view), owner, Budget.unbounded(), spec -> {
                StructureAttempt delegate = fixture.structures.begin(world, spec);
                attempts.add(delegate);
                return new ThrowingAttempt(delegate, false, spec.structure().equals(remoteBlueprintId));
            })
        );

        assertTrue(
            fixture.ledger.rows().isEmpty(),
            () -> "a throwing publish must not leave partial claims: rows=" + fixture.ledger.rows()
        );
        for (StructureAttempt attempt : attempts) {
            assertNull(fixture.structures.remove(attempt.id()), "a throwing publish must not leak a draft");
        }
        assertTrue(fixture.structures.instances().isEmpty());
    }

    @Test
    void aThrowingDraftsFunctionAbortsAlreadyCheckedDrafts() {
        Fixture fixture = busyFixture(
            List.of(
                satSpec(id("drafts_throw_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("drafts_throw_b"), LatticePoint.of(4, 0, 0), false)
            )
        );
        fillBusy(fixture);
        List<StructureAttempt> begun = new ArrayList<>();
        StructureSetHost host = new StructureSetHost(fixture.kernel);
        int[] calls = {0};
        assertThrows(
            IllegalStateException.class,
            () -> host.run(fixture.plan, new TestLease(fixture.view), owner, Budget.unbounded(), spec -> {
                if (calls[0]++ == 1) {
                    throw new IllegalStateException("drafts function refused the second member");
                }
                StructureAttempt delegate = fixture.structures.begin(world, spec);
                begun.add(delegate);
                return delegate;
            })
        );

        assertTrue(fixture.ledger.rows().isEmpty());
        for (StructureAttempt attempt : begun) {
            assertNull(
                fixture.structures.remove(attempt.id()),
                () -> "an aborted check sequence must abort its drafts: " + fixture.structures.context(attempt.id())
            );
        }
        assertTrue(fixture.structures.instances().isEmpty());
    }

    @Test
    void anExplicitManagerBindsRollbackToTheSameManager() {
        StructureManager kernelManager = StructureManager.create();
        Fixture fixture = new Fixture(
            busyCompiled(
                List.of(
                    satSpec(id("mismatch_a"), LatticePoint.of(2, 0, 0), false),
                    satSpec(id("mismatch_b"), LatticePoint.of(4, 0, 0), false)
                )
            ),
            rootSiting(),
            kernelManager
        );
        fillBusy(fixture);
        StructureManager otherManager = StructureManager.create();
        List<InstanceId> drafts = new ArrayList<>();
        StructureSet.Satellite failed = fixture.plan.satellite(id("mismatch_b"));
        assertNotNull(failed);
        fixture.ledger.movingSiting = failed.siting();
        StructureSetResult result = new StructureSetHost(fixture.kernel, otherManager)
                .run(fixture.plan, new TestLease(fixture.view), owner, Budget.unbounded(), otherManager, spec -> {
                    StructureAttempt attempt = otherManager.begin(world, spec);
                    drafts.add(attempt.id());
                    return attempt;
                });

        assertFalse(result.formed());
        assertTrue(otherManager.instances().isEmpty(), "the formed manager must not retain instances");
        assertTrue(kernelManager.instances().isEmpty(), "the kernel manager never saw the drafts");
        assertTrue(fixture.ledger.rows().isEmpty());
        for (InstanceId draft : drafts) {
            assertEquals(0, otherManager.context(draft).revision(), "the explicit manager removed the draft");
        }
    }

    @Test
    void aHandBuiltDuplicatePlacementIsRejected() {
        CompiledStructureSet compiled = busyCompiled(
            List.of(
                satSpec(id("hand_dup_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("hand_dup_b"), LatticePoint.of(2, 0, 0), false)
            )
        );
        StructureSet.Placement placement = StructureSet.place(compiled, rootSiting());
        assertTrue(placement.succeeded(), () -> placement.findings().toString());
        StructureSet plan = placement.set();
        assertNotNull(plan);
        Fixture fixture = new Fixture(plan);
        fillBusy(fixture);
        StructureSetResult result;
        try {
            result = fixture.run();
        } catch (RuntimeException failure) {
            throw new AssertionError(
                "the host must reject a hand-built duplicate placement, not let the manager throw: " + failure,
                failure
            );
        }

        assertFalse(result.formed(), "a hand-built duplicate placement must be rejected before settle");
        assertTrue(
            codes(result.setFindings()).contains(Codes.duplicatePlacement.code()),
            () -> "expected SC-SET-0005 duplicate placement, observed " + result.setFindings()
        );
    }

    @Test
    void aHandBuiltDuplicateParentFaceIsRejected() {
        SatelliteSpec duplicated = new SatelliteSpec(
            id("duplicate_face"),
            preheaterBlueprint().id(),
            LatticePoint.of(-2, 0, 1),
            Orientation.identity,
            false,
            Value.object(),
            null,
            List.of(
                LinkSpec.face(LatticePoint.of(0, 0, 1), LatticePoint.of(1, 0, 0)),
                LinkSpec.face(LatticePoint.of(0, 0, 1), LatticePoint.of(1, 0, 0))
            ),
            null
        );
        StructureSet.Placement placement = StructureSet.place(
            new CompiledStructureSet(
                setId,
                rootBlueprint(),
                List.of(new CompiledSatellite(duplicated, preheaterBlueprint()))
            ),
            rootSiting()
        );
        assertTrue(placement.succeeded(), () -> placement.findings().toString());
        StructureSet plan = placement.set();
        assertNotNull(plan);
        Fixture fixture = new Fixture(plan);
        fillRoot(fixture);
        StructureSetTestSupport.fillPreheater(fixture, plan.satellites().get(0));
        StructureSetResult result = fixture.run();

        assertFalse(
            result.formed(),
            () -> "a hand-built duplicate parent face must be rejected before settle: settled=" + result.settled()
                    + " claims=" + result.claims()
        );
        assertTrue(
            codes(result.setFindings()).contains(Codes.duplicateFaceLink.code()),
            () -> "expected SC-SET-0006 duplicate face link, observed " + result.setFindings()
        );
    }

    @Test
    void aHandBuiltPresenceOverrideOutsideTheSatelliteBoundsIsRejected() {
        Fixture base = busyFixture(List.of(satSpec(id("override"), LatticePoint.of(2, 0, 0), false)));
        StructureSet.Satellite original = base.plan.satellites().get(0);
        StructureSet.Satellite modified = new StructureSet.Satellite(
            original.id(),
            original.compiled(),
            original.siting(),
            original.worldBounds(),
            CuboidSet.of(Cuboid.of(world(base.plan.rootSiting(), LatticePoint.of(10, 0, 10))))
        );
        StructureSet plan = new StructureSet(
            base.plan.compiled(),
            base.plan.rootSiting(),
            base.plan.rootBounds(),
            List.of(modified)
        );
        Fixture fixture = new Fixture(plan);
        fixture.view.put(world(plan.rootSiting(), LatticePoint.origin), rootBlock);
        fixture.view.put(world(modified.siting(), LatticePoint.origin), remoteBlock);
        fixture.view.put(world(plan.rootSiting(), LatticePoint.of(10, 0, 10)), remoteBlock);
        StructureSetResult result = fixture.run();

        assertFalse(
            result.formed(),
            () -> "a hand-built presence override outside bounds must be rejected: settled=" + result.settled()
                    + " claims=" + result.claims()
        );
        assertTrue(
            codes(result.setFindings()).contains(Codes.documentField.code()),
            () -> "expected a presence/document-field rejection, observed " + result.setFindings()
        );
        assertTrue(fixture.instances.isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    @Test
    void settledKeepsEveryRolledBackMemberAndFormedIsFalse() {
        Fixture fixture = busyFixture(
            List.of(
                satSpec(id("exit_a"), LatticePoint.of(2, 0, 0), false),
                satSpec(id("exit_b"), LatticePoint.of(4, 0, 0), false)
            )
        );
        fillBusy(fixture);
        StructureSet.Satellite last = fixture.plan.satellite(id("exit_b"));
        assertNotNull(last);
        fixture.ledger.movingSiting = last.siting();
        StructureSetResult result = fixture.run();

        assertFalse(result.formed(), "a rollback set is not formed");
        assertEquals(List.of(id("exit_a"), id("root")), result.rolledBack());
        assertTrue(result.settled().containsAll(result.rolledBack()), "settled keeps the settle order before rollback");
        assertNotNull(result.firstFailure());
        assertTrue(
            codes(result.setFindings()).contains(Codes.ledgerMoved.code()),
            () -> result.setFindings().toString()
        );
    }

    @Test
    void aContextRemovalFailureIsReportedAndKeepsTheOtherRollbacks() {
        StructureSetTestSupport.RemoveFailingStructures manager = new StructureSetTestSupport.RemoveFailingStructures();
        Fixture fixture = new Fixture(
            busyCompiled(
                List.of(
                    satSpec(id("context_a"), LatticePoint.of(2, 0, 0), false),
                    satSpec(id("context_b"), LatticePoint.of(4, 0, 0), false)
                )
            ),
            rootSiting(),
            manager
        );
        fillBusy(fixture);
        StructureSet.Satellite last = fixture.plan.satellite(id("context_b"));
        assertNotNull(last);
        fixture.ledger.movingSiting = last.siting();
        manager.allowedRemovals = 1;
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(List.of(id("context_a"), id("root")), result.rolledBack());
        assertEquals(1, result.rollbackFailures().size());
        assertEquals(Codes.rollbackFailed.code(), result.rollbackFailures().get(0).code());
        assertEquals(2, manager.removed().size(), "both rolled-back drafts are invalidated");
        assertEquals(0, manager.context(fixture.instance(1)).revision(), "the left draft context was removed");
        assertTrue(
            manager.context(fixture.instance(0)).revision() >= 1,
            "the refused root draft context is kept for the host"
        );
        assertEquals(0, fixture.ledger.rows().size(), "claims are released even when context removal fails");
    }

    private static Fixture busyFixture(List<SatelliteSpec> specs) {
        return new Fixture(busyCompiled(specs));
    }

    private static Fixture preheaterFixture(SatelliteSpec spec) {
        return new Fixture(
            new CompiledStructureSet(
                setId,
                StructureSetTestSupport.rootBlueprint(),
                List.of(new CompiledSatellite(spec, StructureSetTestSupport.preheaterBlueprint()))
            )
        );
    }

    private static Fixture singleCellFixture(NamespacedId satelliteId, LatticePoint at, LinkSpec link) {
        Blueprint root = singleBlueprint(id("root"), rootBlock);
        SatelliteSpec spec = new SatelliteSpec(
            satelliteId,
            remoteBlueprintId,
            at,
            Orientation.identity,
            false,
            Value.object(),
            null,
            List.of(link),
            null
        );
        Fixture fixture = new Fixture(set(root, remoteCompiled(spec)));
        fixture.view.put(world(fixture.plan.rootSiting(), LatticePoint.origin), rootBlock);
        StructureSet.Satellite satellite = fixture.plan.satellite(satelliteId);
        assertNotNull(satellite);
        fixture.view.put(world(satellite.siting(), LatticePoint.origin), remoteBlock);
        return fixture;
    }

    private static CompiledStructureSet busyCompiled(List<SatelliteSpec> specs) {
        List<CompiledSatellite> compiled = new ArrayList<>();
        for (SatelliteSpec spec : specs) {
            compiled.add(remoteCompiled(spec));
        }
        return set(singleBlueprint(id("root"), rootBlock), compiled);
    }

    private static CompiledStructureSet set(Blueprint root, CompiledSatellite... satellites) {
        return set(root, List.of(satellites));
    }

    private static CompiledStructureSet set(Blueprint root, List<CompiledSatellite> satellites) {
        return new CompiledStructureSet(setId, root, List.copyOf(satellites));
    }

    private static CompiledSatellite remoteCompiled(SatelliteSpec spec) {
        return new CompiledSatellite(spec, remoteBlueprint());
    }

    private static Blueprint remoteBlueprint() {
        return singleBlueprint(remoteBlueprintId, remoteBlock);
    }

    private static SatelliteSpec satSpec(
        NamespacedId satelliteId,
        LatticePoint at,
        boolean required,
        LinkSpec... links
    ) {
        return satSpec(satelliteId, at, Orientation.identity, required, links);
    }

    private static SatelliteSpec satSpec(
        NamespacedId satelliteId,
        LatticePoint at,
        Orientation orientation,
        boolean required,
        LinkSpec... links
    ) {
        return new SatelliteSpec(
            satelliteId,
            remoteBlueprintId,
            at,
            orientation,
            required,
            Value.object(),
            null,
            List.of(links),
            null
        );
    }

    private static void fillBusy(Fixture fixture) {
        fixture.view.put(world(fixture.plan.rootSiting(), LatticePoint.origin), rootBlock);
        for (StructureSet.Satellite satellite : fixture.plan.satellites()) {
            fixture.view.put(world(satellite.siting(), LatticePoint.origin), remoteBlock);
        }
    }

    private static Siting compiledSatSiting(CompiledStructureSet compiled, int index) {
        return Objects.requireNonNull(rootSiting().compose(compiled.satellites().get(index).relativeSiting()));
    }

    private static List<FindingCode> codes(List<Finding> findings) {
        return findings.stream().map(Finding::code).toList();
    }

    private static final class ThrowingAttempt implements StructureAttempt {

        private final StructureAttempt delegate;
        private final boolean throwCommit;
        private final boolean throwPublish;

        private ThrowingAttempt(StructureAttempt delegate, boolean throwCommit, boolean throwPublish) {
            this.delegate = delegate;
            this.throwCommit = throwCommit;
            this.throwPublish = throwPublish;
        }

        @Override
        public InstanceId id() {
            return delegate.id();
        }

        @Override
        public StructureSpec spec() {
            return delegate.spec();
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public StructureInstance commit(CuboidSet bounds) {
            if (throwCommit) {
                throw new IllegalStateException("test commit refused");
            }
            return delegate.commit(bounds);
        }

        @Override
        public void abort() {
            delegate.abort();
        }

        @Override
        public StructureContext context(InstanceId instance) {
            return delegate.context(instance);
        }

        @Override
        public @Nullable StructureContext remove(InstanceId instance) {
            return delegate.remove(instance);
        }

        @Override
        public StructureContext publish(
            InstanceId instance,
            Map<ContextKey<?>, Object> values,
            FindingBundle findings,
            Spend spend
        ) {
            if (throwPublish) {
                throw new IllegalStateException("test publish refused");
            }
            return delegate.publish(instance, values, findings, spend);
        }
    }
}
