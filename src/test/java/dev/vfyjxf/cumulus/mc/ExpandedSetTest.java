package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.engine.MatchOutcome;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.Fixture;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.TestLease;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.casing;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.id;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.owner;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rival;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.rootOrigin;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.shell;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.singleBlueprint;
import static dev.vfyjxf.cumulus.mc.StructureSetTestSupport.world;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpandedSetTest {

    private static final NamespacedId rootId = id("expanded_root");
    private static final NamespacedId remoteId = id("expanded_remote");

    @Test
    void sixtyFourMembersUseOneInstanceOneContextAndOneClaimRowEach() {
        Shape shape = shape(64);
        Fixture fixture = fixture(shape);
        StructureSetResult result = fixture.run();

        List<NamespacedId> expected = members(shape);
        assertTrue(result.formed(), () -> result.setFindings().toString());
        assertEquals(expected, result.settled());
        assertEquals(expected, new ArrayList<>(result.outcomes().keySet()));
        assertEquals(65, result.claims().values().stream().mapToInt(List::size).sum());
        assertEquals(65, fixture.ledger.rows().size());
        assertEquals(65, fixture.structures.instances().size());
        for (StructureSet.Satellite satellite : fixture.plan.satellites()) {
            StructureInstance instance = fixture.structures.find(owner, satellite.siting());
            assertNotNull(instance, () -> "missing instance for " + satellite.id());
            assertTrue(instance.context().revision() >= 1, () -> "missing context for " + satellite.id());
            assertEquals(satellite.worldBounds().asSet(), instance.bounds());
        }
        assertEquals(1, fixture.structures.findAt(fixture.plan.rootBounds().min()).size());
    }

    @Test
    void sixtyFourMembersRespectTheExactSharedBudgetAndOneShort() {
        Shape shape = shape(64);
        Fixture baseline = fixture(shape);
        StructureSetResult formed = baseline.run();
        assertTrue(formed.formed(), () -> formed.setFindings().toString());
        Spend memberSpend = Spend.zero;
        for (MatchOutcome outcome : formed.outcomes().values()) {
            memberSpend = memberSpend.plus(outcome.spend());
        }
        int presence = shape.satellites().size();
        Budget exact = Budget.of(presence + memberSpend.cells(), memberSpend.sections());

        Fixture exactFixture = fixture(shape);
        assertTrue(exactFixture.run(exact).formed(), "the exact 64-member budget must form");

        Budget oneShort = memberSpend.cells() > 0
                ? Budget.of(presence + memberSpend.cells() - 1, memberSpend.sections())
                : Budget.of(presence, memberSpend.sections() - 1);
        Fixture shortFixture = fixture(shape);
        StructureSetResult shortResult = shortFixture.run(oneShort);
        assertFalse(shortResult.formed());
        assertTrue(shortResult.settled().isEmpty());
        assertTrue(shortResult.claims().isEmpty());
        assertTrue(shortFixture.structures.instances().isEmpty());
        assertEquals(0, shortFixture.ledger.rows().size());
        assertTrue(
            codes(shortResult.setFindings()).contains(Codes.budgetExhausted.code()),
            () -> "expected the shared budget to stop a member: " + shortResult.setFindings()
        );
    }

    @Test
    void rollbackOfThirtyTwoMembersReleasesEveryClaimAndInvalidatesEveryContext() {
        Shape shape = shape(32);
        Fixture fixture = fixture(shape);
        StructureSet.Satellite last = fixture.plan.satellites().get(31);
        fixture.ledger.movingSiting = last.siting();
        StructureSetResult result = fixture.run();

        List<NamespacedId> expected = members(shape);
        assertFalse(result.formed());
        assertEquals(expected.subList(0, 32), result.settled());
        List<NamespacedId> reversed = new ArrayList<>(expected.subList(0, 32));
        Collections.reverse(reversed);
        assertEquals(reversed, result.rolledBack());
        assertEquals(0, fixture.ledger.rows().size());
        assertTrue(fixture.structures.instances().isEmpty());
        for (InstanceId instance : fixture.instances) {
            assertNull(fixture.structures.remove(instance), "a failed 32-member set leaked a context");
        }
        assertTrue(codes(result.setFindings()).contains(Codes.ledgerMoved.code()));
    }

    @Test
    void aReleaseFailureInTheMiddleDoesNotStopTheRemainingRollbacks() {
        Shape shape = shape(16);
        Fixture fixture = fixture(shape);
        fixture.ledger.movingSiting = fixture.plan.satellites().get(15).siting();
        fixture.ledger.throwingRelease = fixture.plan.satellites().get(8).siting();
        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertEquals(16, result.settled().size());
        assertEquals(15, result.rolledBack().size());
        assertEquals(1, result.rollbackFailures().size());
        assertEquals(Codes.rollbackFailed.code(), result.rollbackFailures().get(0).code());
        assertEquals(1, fixture.ledger.rows().size(), "only the refused release keeps its rows");
        assertTrue(fixture.structures.instances().isEmpty());
        for (InstanceId instance : fixture.instances) {
            assertNull(fixture.structures.remove(instance), "a release-failure rollback leaked a context");
        }
    }

    @Test
    void aFailedReSettleOfSixteenMembersReleasesEveryOldRow() {
        Shape shape = shape(16);
        Fixture fixture = fixture(shape);
        StructureSetResult first = fixture.run();
        assertTrue(first.formed(), () -> first.setFindings().toString());
        List<InstanceId> oldInstances = List.copyOf(fixture.instances);
        int oldRows = fixture.ledger.rows().size();
        long oldRevision = fixture.ledger.revision();
        fixture.ledger.movingSiting = fixture.plan.satellites().get(15).siting();

        StructureSetResult second = fixture.run();

        assertFalse(second.formed());
        assertEquals(0, fixture.ledger.rows().size(), "a failed re-settle never restores the old rows (D-79)");
        assertEquals(17, oldInstances.size());
        assertEquals(17, fixture.structures.instances().size(), "the old identities stay as stale entries");
        for (InstanceId old : oldInstances) {
            assertNotNull(fixture.structures.find(old), () -> "a failed re-settle replaced " + old);
        }
        assertTrue(fixture.ledger.revision() > oldRevision);
        assertTrue(oldRows > 0);
    }

    @Test
    void repeatedSuccessfulReSettleReplacesTheSixtyFourMemberSetWithoutGhosts() {
        Shape shape = shape(64);
        Fixture fixture = fixture(shape);
        assertTrue(fixture.run().formed());
        List<InstanceId> oldInstances = List.copyOf(fixture.instances);
        long firstRevision = fixture.ledger.revision();
        int firstRows = fixture.ledger.rows().size();

        StructureSetResult second = fixture.run();

        assertTrue(second.formed(), () -> second.setFindings().toString());
        assertEquals(65, fixture.structures.instances().size());
        assertEquals(firstRows, fixture.ledger.rows().size());
        assertEquals(65, second.claims().values().stream().mapToInt(List::size).sum());
        assertTrue(fixture.ledger.revision() > firstRevision);
        for (InstanceId old : oldInstances) {
            assertNull(fixture.structures.find(old), "a repeated set left a ghost instance");
        }
        for (InstanceId current : fixture.structures.instances().stream().map(StructureInstance::id).toList()) {
            assertNotNull(fixture.structures.find(current));
        }
    }

    @Test
    void aSecondOwnerSetAtTheSameSitingConflictsWithoutTouchingTheFirstSet() {
        Shape shape = shape(16);
        Fixture fixture = fixture(shape);
        assertTrue(fixture.run().formed());
        int firstRows = fixture.ledger.rows().size();
        List<InstanceId> firstInstances = fixture.structures.instances().stream().map(StructureInstance::id).toList();
        List<InstanceId> secondDrafts = new ArrayList<>();

        StructureSetResult second = new StructureSetHost(fixture.kernel, fixture.structures)
                .run(fixture.plan, new TestLease(fixture.view), rival, Budget.unbounded(), spec -> {
                    var attempt = fixture.structures.begin(world, spec);
                    secondDrafts.add(attempt.id());
                    return attempt;
                });

        assertFalse(second.formed());
        assertTrue(second.settled().isEmpty());
        assertTrue(second.claims().isEmpty());
        assertEquals(firstRows, fixture.ledger.rows().size(), "the first set keeps all claim rows");
        assertEquals(firstInstances.size(), fixture.structures.instances().size());
        for (InstanceId first : firstInstances) {
            assertNotNull(fixture.structures.find(first), "the overlapping second set replaced the first");
        }
        for (InstanceId draft : secondDrafts) {
            assertNull(fixture.structures.remove(draft), "the rejected overlapping set leaked a draft");
        }
        assertTrue(
            codes(second.setFindings()).contains(Codes.claimConflict.code()),
            () -> second.setFindings().toString()
        );
    }

    @Test
    void repeatedSetsAtDifferentSitingsCoexistWithDoubleTheClaimVolume() {
        Shape shape = shape(16);
        Fixture fixture = fixture(shape);
        assertTrue(fixture.run().formed());
        int firstRows = fixture.ledger.rows().size();
        int firstInstances = fixture.structures.instances().size();
        Siting secondSiting = Siting.of(
            LatticePoint.of(rootOrigin.x() + 1_000, rootOrigin.y(), rootOrigin.z() + 1_000),
            Orientation.identity
        );
        StructureSet.Placement placement = StructureSet.place(shape.compiled(), secondSiting);
        assertTrue(placement.succeeded(), () -> placement.findings().toString());
        StructureSet secondPlan = Objects.requireNonNull(placement.set());
        fillPlan(fixture.view, secondPlan);

        StructureSetResult second = new StructureSetHost(fixture.kernel, fixture.structures).run(
            secondPlan,
            new TestLease(fixture.view),
            owner,
            Budget.unbounded(),
            spec -> fixture.structures.begin(world, spec)
        );

        assertTrue(second.formed(), () -> second.setFindings().toString());
        assertEquals(firstRows * 2, fixture.ledger.rows().size());
        assertEquals(firstInstances * 2, fixture.structures.instances().size());
        assertNotNull(fixture.structures.find(owner, secondPlan.rootSiting()));
        for (StructureSet.Satellite satellite : secondPlan.satellites()) {
            assertNotNull(fixture.structures.find(owner, satellite.siting()));
        }
    }

    @Test
    void aDuplicatePlacementAmongThirtyTwoMembersIsRejectedBeforeAnyDraft() {
        Shape base = shape(32);
        List<SatelliteSpec> specs = new ArrayList<>(base.satellites());
        SatelliteSpec first = specs.get(0);
        SatelliteSpec duplicate = new SatelliteSpec(
            id("expanded_duplicate"),
            first.blueprint(),
            first.at(),
            first.orientation(),
            false,
            Value.object(),
            null,
            List.of(),
            null
        );
        specs.add(duplicate);
        CompiledStructureSet compiled = compile(base.root(), base.remote(), specs, id("expanded_duplicate_set"));
        StructureSet.Placement placement = StructureSet.place(compiled, Siting.of(rootOrigin, Orientation.identity));
        assertTrue(placement.succeeded(), () -> placement.findings().toString());
        Fixture fixture = new Fixture(Objects.requireNonNull(placement.set()));
        fillPlan(fixture.view, fixture.plan);

        StructureSetResult result = fixture.run();

        assertFalse(result.formed());
        assertTrue(
            codes(result.setFindings()).contains(Codes.duplicatePlacement.code()),
            () -> result.setFindings().toString()
        );
        assertTrue(fixture.instances.isEmpty(), "validation must reject before any draft");
        assertTrue(fixture.structures.instances().isEmpty());
        assertEquals(0, fixture.ledger.rows().size());
    }

    private static Shape shape(int count) {
        Blueprint root = singleBlueprint(rootId, casing);
        Blueprint remote = singleBlueprint(remoteId, shell);
        List<SatelliteSpec> specs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            specs.add(
                new SatelliteSpec(
                    id("expanded_sat_" + index),
                    remoteId,
                    LatticePoint.of(2 + index * 2, 0, 0),
                    Orientation.identity,
                    index % 3 == 0,
                    Value.object(),
                    null,
                    List.of(),
                    null
                )
            );
        }
        CompiledStructureSet compiled = compile(root, remote, specs, id("expanded_set_" + count));
        return new Shape(root, remote, specs, compiled);
    }

    private static Fixture fixture(Shape shape) {
        Fixture fixture = new Fixture(shape.compiled());
        fillPlan(fixture.view, fixture.plan);
        return fixture;
    }

    private static void fillPlan(StructureSetTestSupport.TestView view, StructureSet plan) {
        view.put(world(plan.rootSiting(), LatticePoint.origin), casing);
        for (StructureSet.Satellite satellite : plan.satellites()) {
            view.put(world(satellite.siting(), LatticePoint.origin), shell);
        }
    }

    private static CompiledStructureSet compile(
        Blueprint root,
        Blueprint remote,
        List<SatelliteSpec> specs,
        NamespacedId setId
    ) {
        List<CompiledSatellite> satellites = new ArrayList<>(specs.size());
        for (SatelliteSpec spec : specs) {
            satellites.add(new CompiledSatellite(spec, remote));
        }
        return new CompiledStructureSet(setId, root, satellites);
    }

    private static List<NamespacedId> members(Shape shape) {
        List<NamespacedId> names = new ArrayList<>();
        names.add(shape.root().id());
        for (SatelliteSpec spec : shape.satellites()) {
            names.add(spec.id());
        }
        return names;
    }

    private static List<FindingCode> codes(List<Finding> findings) {
        return findings.stream().map(Finding::code).toList();
    }

    private record Shape(
        Blueprint root,
        Blueprint remote,
        List<SatelliteSpec> satellites,
        CompiledStructureSet compiled
    ) {}
}
