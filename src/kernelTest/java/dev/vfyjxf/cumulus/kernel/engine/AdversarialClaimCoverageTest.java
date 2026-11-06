package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMatrix;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusContext;
import dev.vfyjxf.cumulus.kernel.structure.FocusResult;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adversarial claim matrix, ledger, footprint, coverage and claim-planner tests. */
class AdversarialClaimCoverageTest {

    private static final LatticePoint origin = LatticePoint.origin;
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 3, 1, 3);
    private static final CuboidSet oneCell = CuboidSet.of(Cuboid.of(origin));
    private static final OwnerId owner = MagicFixtures.owner;
    private static final OwnerId rival = MagicFixtures.rival;
    private static final InstanceId instance = MagicFixtures.instance;
    private static final InstanceId rivalInstance = MagicFixtures.rivalInstance;
    private static final Siting identity = MagicFixtures.identity(origin);

    @Test
    void claimMatrixIsSymmetricAndFollowsTheConflictRuleExactly() {
        List<Sharing> sharings = List.of(Sharing.shared, Sharing.exclusive);
        List<InstanceId> instances = List.of(instance, rivalInstance);
        List<Siting> sitings = List.of(identity, MagicFixtures.identity(LatticePoint.of(1, 0, 0)));
        List<LatticePoint> cells = List.of(origin, LatticePoint.of(0, 1, 0));
        for (Sharing leftSharing : sharings) {
            for (Sharing rightSharing : sharings) {
                for (InstanceId leftInstance : instances) {
                    for (InstanceId rightInstance : instances) {
                        for (Siting leftSiting : sitings) {
                            for (Siting rightSiting : sitings) {
                                for (LatticePoint leftCell : cells) {
                                    for (LatticePoint rightCell : cells) {
                                        ClaimRow left = row(leftCell, leftSharing, leftInstance, leftSiting, "left");
                                        ClaimRow right = row(
                                            rightCell,
                                            rightSharing,
                                            rightInstance,
                                            rightSiting,
                                            "right"
                                        );
                                        boolean expected = leftCell.equals(rightCell)
                                                && !leftInstance.equals(rightInstance)
                                                && (leftSharing.blocks() || rightSharing.blocks());
                                        assertEquals(expected, ClaimMatrix.conflicts(left, right));
                                        assertEquals(
                                            ClaimMatrix.conflicts(right, left),
                                            ClaimMatrix.conflicts(left, right),
                                            "the matrix is symmetric"
                                        );
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void claimRowOrderIncludesSitingAndKeepsSitingDistinctRows() {
        ClaimRow first = row(origin, Sharing.shared, instance, identity, "bay");
        ClaimRow second = row(
            origin,
            Sharing.shared,
            instance,
            MagicFixtures.identity(LatticePoint.of(5, 0, 5)),
            "bay"
        );

        assertTrue(first.compareTo(second) < 0, "siting is part of the total order");
        assertNotEquals(first, second, "different sitings are different rows");

        ClaimSet set = new ClaimSet(List.of(second, first), 1);
        assertEquals(2, set.rows().size(), "a sorted set keeps both rows of one tie");
        assertEquals(first, set.rows().get(0));
        assertEquals(second, set.rows().get(1));

        List<ClaimRow> source = new ArrayList<>();
        source.add(first);
        ClaimSet copied = new ClaimSet(source, 1);
        source.add(second);
        assertEquals(1, copied.rows().size(), "ClaimSet copies its input");
        assertThrows(UnsupportedOperationException.class, () -> copied.rows().add(first));
    }

    @Test
    void claimSetViewAndSettleRequestCanonicalizeAndGuardTheirShapes() {
        ClaimRow laterCell = row(LatticePoint.of(1, 0, 0), Sharing.shared, instance, identity, "b");
        ClaimRow earlierCell = row(origin, Sharing.shared, instance, identity, "a");
        ClaimSet set = new ClaimSet(List.of(laterCell, earlierCell), 2);
        assertEquals(List.of(earlierCell, laterCell), set.rows(), "rows sort by cell first");
        assertThrows(IllegalArgumentException.class, () -> new ClaimSet(List.of(), -1));

        ClaimView view = new ClaimView(List.of(laterCell, earlierCell), 2, true);
        assertEquals(List.of(earlierCell, laterCell), view.rows());
        assertEquals(List.of(earlierCell), view.rowsIn(Cuboid.of(0, 0, 0, 1, 1, 1)), "the max edge is exclusive");
        assertEquals(List.of(earlierCell, laterCell), view.rowsIn(Cuboid.of(0, 0, 0, 2, 1, 1)));
        assertTrue(view.rowsIn(Cuboid.of(2, 0, 0, 3, 1, 1)).isEmpty());
        assertTrue(ClaimView.unavailable().rowsIn(Cuboid.of(0, 0, 0, 1, 1, 1)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ClaimView(List.of(earlierCell), 0, false));
        assertThrows(IllegalArgumentException.class, () -> new ClaimView(List.of(), -1, true));

        ClaimRow duplicate = row(origin, Sharing.shared, instance, identity, "dupe");
        SettleRequest request = new SettleRequest(instance, List.of(duplicate, duplicate), 0);
        assertEquals(2, request.rows().size(), "duplicate rows are a representation detail, not a constructor error");
        assertThrows(
            IllegalArgumentException.class,
            () -> new SettleRequest(instance, List.of(row(origin, Sharing.shared, rivalInstance, identity, "x")), 0)
        );
        assertThrows(IllegalArgumentException.class, () -> new SettleRequest(instance, List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new SettleRequest(instance, List.of(earlierCell), -1));
    }

    @Test
    void claimVerdictsRejectImpossibleShapes() {
        ClaimRow row = row(origin, Sharing.shared, instance, identity, "bay");
        ClaimVerdict.Settled empty = new ClaimVerdict.Settled(0, List.of());
        assertTrue(empty.rows().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ClaimVerdict.Settled(0, List.of(row)));
        assertThrows(IllegalArgumentException.class, () -> new ClaimVerdict.Settled(-1, List.of()));

        FindingBundle findings = findingBundle("conflict");
        assertThrows(IllegalArgumentException.class, () -> new ClaimVerdict.Conflict(List.of(), findings));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ClaimVerdict.Conflict(List.of(row), FindingBundle.empty())
        );

        assertEquals(new ClaimVerdict.RevisionMoved(0, 1), new ClaimVerdict.RevisionMoved(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ClaimVerdict.RevisionMoved(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClaimVerdict.RevisionMoved(0, -1));
    }

    @Test
    void theLedgerSettleIsACompareAndSetOverTheWholeInstanceKey() {
        ClaimLedger ledger = MagicFixtures.ledger();
        ClaimRow shared = row(origin, Sharing.shared, instance, identity, "shared");
        ClaimRow exclusive = row(
            origin,
            Sharing.exclusive,
            rivalInstance,
            MagicFixtures.turn(origin, Turn.clockwise),
            "rival"
        );

        ClaimVerdict.Settled first = assertInstanceOf(
            ClaimVerdict.Settled.class,
            ledger.settle(new SettleRequest(instance, List.of(shared), 0))
        );
        assertEquals(1, first.revision());
        assertEquals(List.of(shared), TestWorld.rows(ledger));

        ClaimVerdict.RevisionMoved stale = assertInstanceOf(
            ClaimVerdict.RevisionMoved.class,
            ledger.settle(new SettleRequest(instance, List.of(shared), 0))
        );
        assertEquals(new ClaimVerdict.RevisionMoved(0, 1), stale);
        assertEquals(1, ledger.revision(), "a stale CAS writes nothing");

        ClaimVerdict.Conflict conflict = assertInstanceOf(
            ClaimVerdict.Conflict.class,
            ledger.settle(new SettleRequest(rivalInstance, List.of(exclusive), 1))
        );
        assertEquals(List.of(shared), conflict.incumbents(), "the incumbent row is carried");
        assertEquals(1, ledger.revision(), "a conflict writes nothing");
        assertEquals(List.of(shared), TestWorld.rows(ledger), "the ledger bytes are untouched");

        assertInstanceOf(ClaimVerdict.Settled.class, ledger.release(rivalInstance));
        assertEquals(List.of(shared), TestWorld.rows(ledger), "the release leaves the other instance's row");
        assertEquals(1, ledger.revision(), "an absent release does not bump the revision");
    }

    @Test
    void aDuplicateRowKeyKeepsEveryRowAndReleaseDropsTheWholeInstance() {
        ClaimLedger ledger = MagicFixtures.ledger();
        ClaimRow first = row(origin, Sharing.shared, instance, identity, "bay_a");
        ClaimRow second = row(origin, Sharing.shared, instance, identity, "bay_b");
        ClaimVerdict.Settled settled = assertInstanceOf(
            ClaimVerdict.Settled.class,
            ledger.settle(new SettleRequest(instance, List.of(first, second), 0))
        );
        assertEquals(2, settled.rows().size());
        assertEquals(2, TestWorld.rows(ledger).size(), "one instance can hold two rows of two different bays");

        ClaimVerdict.Settled released = assertInstanceOf(ClaimVerdict.Settled.class, ledger.release(instance));
        assertEquals(2, released.revision());
        assertTrue(TestWorld.rows(ledger).isEmpty(), "release removes the whole instance, both duplicate rows");
        ClaimVerdict.Settled again = assertInstanceOf(ClaimVerdict.Settled.class, ledger.release(instance));
        assertEquals(2, again.revision(), "a second release is a no-op");
    }

    @Test
    void plannerBoxAndDeclaredTranslateTheirCellsWithTheSiting() {
        CuboidSet boxCells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        CuboidSet declaredCells = CuboidSet.of(Cuboid.of(2, 0, 0, 3, 1, 1));
        Bay boxBay = MagicFixtures.bay(id("box_bay"), boxCells, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Bay declaredBay = MagicFixtures.bay(
            id("declared_bay"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.everyCell,
            Footprint.declared(declaredCells),
            Sharing.shared
        );
        Blueprint blueprint = blueprint(
            "box_declared",
            Cuboid.of(0, 0, 0, 4, 1, 1),
            everyCellCheck("box_check", boxBay),
            everyCellCheck("declared_check", declaredBay)
        );
        Siting siting = MagicFixtures.turn(origin, Turn.clockwise);

        ClaimPlanner.Derived derived = ClaimPlanner.derive(
            blueprint,
            siting,
            instance,
            List.of(new BayBinding(boxBay, List.of()), new BayBinding(declaredBay, List.of()))
        );

        Set<LatticeKey> expected = new LinkedHashSet<>();
        addKeys(expected, Objects.requireNonNull(siting.toWorld(boxCells), "box world cells"));
        addKeys(expected, Objects.requireNonNull(siting.toWorld(declaredCells), "declared world cells"));
        assertEquals(expected, keys(derived.rows()));
        assertEquals(expected.size(), derived.rows().size(), "one row per cell, no duplicate row");
        assertEquals(
            Objects.requireNonNull(siting.toWorld(boxCells), "box")
                    .union(Objects.requireNonNull(siting.toWorld(declaredCells), "declared")),
            derived.footprint()
        );
        assertSorted(derived.rows());
    }

    @Test
    void plannerBoundUnionsNonAirSubjectsAndRejectsNestedOrNonRootSubjects() {
        Bay boundBay = MagicFixtures
                .bay(id("bound_bay"), oneCell, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        Blueprint blueprint = blueprint("bound", boundingBox(oneCell), everyCellCheck("bound_check", boundBay));
        OccupantSnapshot block = TestWorld.block(TestWorld.stone, origin);
        OccupantSnapshot second = TestWorld.block(TestWorld.stone, LatticePoint.of(0, 1, 0));

        ClaimPlanner.Derived derived = ClaimPlanner.derive(
            blueprint,
            identity,
            instance,
            List.of(new BayBinding(boundBay, List.of(block, second, block)))
        );
        assertEquals(2, derived.rows().size(), "two distinct footprints yield two rows and duplicates do not add rows");

        OccupantSnapshot nested = nestedOccupant(origin, 0);
        IllegalArgumentException nestedFailure = assertThrows(
            IllegalArgumentException.class,
            () -> ClaimPlanner.derive(blueprint, identity, instance, List.of(new BayBinding(boundBay, List.of(nested))))
        );
        assertTrue(Objects.requireNonNull(nestedFailure.getMessage(), "message").contains("SC-CLM-0004"));

        OccupantSnapshot childFrame = block(origin, 1);
        IllegalArgumentException frameFailure = assertThrows(
            IllegalArgumentException.class,
            () -> ClaimPlanner
                    .derive(blueprint, identity, instance, List.of(new BayBinding(boundBay, List.of(childFrame))))
        );
        assertTrue(Objects.requireNonNull(frameFailure.getMessage(), "message").contains("SC-CLM-0004"));
    }

    @Test
    void airSubjectsMustNotContributeBoundFootprints() {
        Bay boundBay = MagicFixtures.bay(
            id("air_bound_bay"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 2)),
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Blueprint blueprint = blueprint(
            "air_bound",
            Cuboid.of(0, 0, 0, 1, 1, 2),
            everyCellCheck("air_bound_check", boundBay)
        );
        OccupantSnapshot block = TestWorld.block(TestWorld.stone, origin);
        OccupantSnapshot air = TestWorld.airOccupant(LatticePoint.of(0, 0, 1));

        ClaimPlanner.Derived derived = ClaimPlanner
                .derive(blueprint, identity, instance, List.of(new BayBinding(boundBay, List.of(block, air))));
        assertAll(
            () -> assertEquals(1, derived.rows().size(), "an air subject contributes no claimed cell"),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> ClaimPlanner
                        .derive(blueprint, identity, instance, List.of(new BayBinding(boundBay, List.of(air)))),
                "an exclusive Bound footprint with only air subjects must be SC-CLM-0005"
            )
        );
    }

    @Test
    void anEmptyExclusiveBoundFootprintIsRejected() {
        Bay boundBay = MagicFixtures
                .bay(id("empty_bound"), oneCell, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        Blueprint blueprint = blueprint(
            "empty_bound",
            boundingBox(oneCell),
            everyCellCheck("empty_bound_check", boundBay)
        );

        IllegalArgumentException failure = assertThrows(
            IllegalArgumentException.class,
            () -> ClaimPlanner.derive(blueprint, identity, instance, List.of(new BayBinding(boundBay, List.of())))
        );
        assertTrue(Objects.requireNonNull(failure.getMessage(), "message").contains("SC-CLM-0005"));
    }

    @Test
    void anySubjectBoundRequiresAClaimableFocus() {
        Bay boundBay = MagicFixtures
                .bay(id("any_bound"), oneCell, Coverage.anySubject, Footprint.bound(), Sharing.exclusive);
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () -> {
            Blueprint.builder(id("any_bound_non_claimable")).bounds(boundingBox(oneCell))
                    .check(
                        Check.of(id("any_check"), boundBay, occupantFocus(origin, false), Expectations.anyOccupant())
                    ).build();
        });
        assertTrue(Objects.requireNonNull(rejected.getMessage(), "message").contains("SC-BPT-0005"));

        Blueprint claimable = blueprint(
            "any_bound_claimable",
            boundingBox(oneCell),
            Check.of(id("any_check"), boundBay, occupantFocus(origin, true), Expectations.anyOccupant())
        );
        TestWorld.View view = MagicFixtures.view().put(TestWorld.block(TestWorld.stone, origin));
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(claimable, identity, view, ledger)
        );
        assertEquals(1, formed.claims().rows().size());
        assertEquals(origin.key(), formed.claims().rows().get(0).cell());
        assertEquals(Sharing.exclusive, formed.claims().rows().get(0).sharing());
    }

    @Test
    void aClaimingFocusThatBindsANestedBodyFailsTheCheck() {
        Bay bay = MagicFixtures.bay(id("lying_bay"), oneCell, Coverage.anySubject, Footprint.box(), Sharing.exclusive);
        Blueprint blueprint = blueprint(
            "lying_focus",
            boundingBox(oneCell),
            Check.of(id("lying_check"), bay, nestedLyingFocus(origin), Expectations.anyOccupant())
        );
        TestWorld.View view = MagicFixtures.view().put(TestWorld.block(TestWorld.stone, origin));

        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            MagicFixtures.check(blueprint, identity, view, new ClaimView(List.of(), 0, true))
        );
        assertTrue(MagicFixtures.hasCode(failed, Codes.bindingInvalid));
    }

    @Test
    void oneSubjectBoxOwnsTheWholeBayOnlyWhenTheSubjectFootprintMatches() {
        CuboidSet bayCells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        Bay boxBay = MagicFixtures
                .bay(id("one_box"), bayCells, Coverage.oneSubject, Footprint.box(), Sharing.exclusive);
        Blueprint matching = blueprint(
            "one_box_matching",
            boundingBox(bayCells),
            Check.of(
                id("one_box_matching_check"),
                boxBay,
                footprintFocus(origin, Cuboid.of(0, 0, 0, 2, 1, 1)),
                Expectations.anyOccupant()
            )
        );
        TestWorld.View view = MagicFixtures.view().put(TestWorld.block(TestWorld.stone, origin));
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(matching, identity, view, ledger)
        );
        assertEquals(2, formed.claims().rows().size(), "a Box footprint owns every bay cell");
        assertEquals(keys(bayCells), keys(formed.claims().rows()));

        Blueprint partial = blueprint(
            "one_box_partial",
            boundingBox(bayCells),
            Check.of(id("one_box_partial_check"), boxBay, occupantFocus(origin, true), Expectations.anyOccupant())
        );
        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            MagicFixtures.check(partial, identity, view, new ClaimView(List.of(), 0, true))
        );
        assertTrue(MagicFixtures.hasCode(failed, Codes.bindingInvalid), "a partial oneSubject bound is rejected");
    }

    @Test
    void everyCellBoxClaimsEveryBayCell() {
        CuboidSet bayCells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        Bay bay = MagicFixtures.bay(id("every_box"), bayCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Blueprint blueprint = blueprint(
            "every_box",
            boundingBox(bayCells),
            Check.of(id("every_box_check"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()))
        );
        TestWorld.View view = MagicFixtures.view().put(TestWorld.block(TestWorld.stone, origin));
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(blueprint, identity, view, ledger)
        );
        assertEquals(2, formed.claims().rows().size());
        assertEquals(keys(bayCells), keys(formed.claims().rows()));
    }

    @Test
    void footprintNoneClaimsNothingAndExclusiveNoneFailsBuild() {
        Bay sharedNone = MagicFixtures
                .bay(id("none_shared"), oneCell, Coverage.everyCell, Footprint.none(), Sharing.shared);
        Blueprint none = blueprint(
            "none_shared",
            boundingBox(oneCell),
            Check.of(id("none_check"), sharedNone, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()))
        );
        TestWorld.View view = MagicFixtures.view().air(origin);
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(none, identity, view, ledger)
        );
        assertTrue(formed.claims().rows().isEmpty(), "Footprint.None claims nothing");
        assertTrue(TestWorld.rows(ledger).isEmpty(), "a None footprint writes no ledger row");
        assertEquals(0, ledger.revision(), "an empty plan releases the key; an absent key does not bump");
        assertEquals(0, formed.ledgerRevision(), "Formed carries the unchanged revision");

        Bay exclusiveNone = MagicFixtures
                .bay(id("none_exclusive"), oneCell, Coverage.everyCell, Footprint.none(), Sharing.exclusive);
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () -> {
            Blueprint.builder(id("none_exclusive")).bounds(boundingBox(oneCell))
                    .check(
                        Check.of(
                            id("none_exclusive_check"),
                            exclusiveNone,
                            Focuses.bay(),
                            Expectations.eachCell(Expectations.anyOccupant())
                        )
                    ).build();
        });
        assertTrue(Objects.requireNonNull(rejected.getMessage(), "message").contains("SC-BPT-0005"));
    }

    @Test
    void sitingRotationTranslatesBoxRows() {
        CuboidSet bayCells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        Bay bay = MagicFixtures.bay(id("rot_box"), bayCells, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Blueprint blueprint = blueprint(
            "rot_box",
            boundingBox(bayCells),
            Check.of(id("rot_box_check"), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()))
        );
        Siting siting = MagicFixtures.turn(LatticePoint.of(10, 0, 10), Turn.clockwise);

        ClaimPlanner.Derived derived = ClaimPlanner
                .derive(blueprint, siting, instance, List.of(new BayBinding(bay, List.of())));
        CuboidSet expected = Objects.requireNonNull(siting.toWorld(bayCells), "rotated bay");
        assertEquals(keys(expected), keys(derived.rows()));
        assertEquals(expected, derived.footprint());
        for (ClaimRow row : derived.rows()) {
            assertEquals(siting, row.siting());
            assertEquals(instance, row.instance());
            assertEquals(bay.id(), row.bay());
        }
    }

    @Test
    void aBoxThatLeavesTheLatticeIsRejected() {
        CuboidSet local = CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1));
        Bay bay = MagicFixtures.bay(id("edge_box"), local, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Blueprint blueprint = blueprint(
            "edge_box",
            Cuboid.of(0, 0, 0, 2, 1, 1),
            Check.of(id("edge_box_check"), bay, Focuses.occupant(LatticePoint.of(1, 0, 0)), Expectations.anyOccupant())
        );
        Siting edge = MagicFixtures.identity(LatticePoint.of(LatticePoint.maxXz - 1, 0, 0));

        IllegalArgumentException failure = assertThrows(
            IllegalArgumentException.class,
            () -> ClaimPlanner.derive(blueprint, edge, instance, List.of(new BayBinding(bay, List.of())))
        );
        assertTrue(Objects.requireNonNull(failure.getMessage(), "message").contains("SC-SPC-0001"));
    }

    @Test
    void sharedOverlapKeepsDuplicateRowsWhileExclusiveOverlapFailsBuild() {
        CuboidSet cell = oneCell;
        Bay first = MagicFixtures.bay(id("overlap_a"), cell, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Bay second = MagicFixtures.bay(id("overlap_b"), cell, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Blueprint duplicate = blueprint(
            "overlap_shared",
            boundingBox(cell),
            Check.of(id("overlap_a_check"), first, Focuses.occupant(origin), Expectations.anyOccupant()),
            Check.of(id("overlap_b_check"), second, Focuses.occupant(origin), Expectations.anyOccupant())
        );
        TestWorld.View view = MagicFixtures.view().put(TestWorld.block(TestWorld.stone, origin));
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(duplicate, identity, view, ledger)
        );
        assertEquals(2, formed.claims().rows().size(), "two shared bays write two rows for one cell");
        assertEquals(2, TestWorld.rows(ledger).size());
        assertEquals(1, ledger.revision());

        Bay exclusive = MagicFixtures
                .bay(id("overlap_exclusive"), cell, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, () -> {
            Blueprint.builder(id("overlap_exclusive")).bounds(boundingBox(cell)).check(
                Check.of(id("overlap_exclusive_check"), exclusive, Focuses.occupant(origin), Expectations.anyOccupant())
            ).check(Check.of(id("overlap_shared_check"), second, Focuses.occupant(origin), Expectations.anyOccupant()))
                    .build();
        });
        assertTrue(Objects.requireNonNull(rejected.getMessage(), "message").contains("SC-BPT-0002"));
    }

    @Test
    void aDeclaredFootprintOverAnotherBayFailsBuild() {
        Bay box = MagicFixtures.bay(id("p1_box"), oneCell, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay declared = MagicFixtures.bay(
            id("p1_declared"),
            CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1)),
            Coverage.everyCell,
            Footprint.declared(oneCell),
            Sharing.shared
        );
        BlueprintBuilder builder = Blueprint.builder(id("p1")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .check(Check.of(id("p1_box_check"), box, Focuses.occupant(origin), Expectations.anyOccupant())).check(
                    Check.of(
                        id("p1_declared_check"),
                        declared,
                        Focuses.occupant(LatticePoint.of(1, 0, 0)),
                        Expectations.anyOccupant()
                    )
                );
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(Objects.requireNonNull(rejected.getMessage(), "message").contains("SC-BPT-0002"));
    }

    @Test
    void twoDeclaredFootprintsOverOneCellFailBuild() {
        CuboidSet leftOwned = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(2, 0, 0, 3, 1, 1));
        CuboidSet rightOwned = CuboidSet.of(Cuboid.of(1, 0, 0, 3, 1, 1));
        for (Sharing leftSharing : List.of(Sharing.exclusive, Sharing.shared)) {
            for (Sharing rightSharing : List.of(Sharing.exclusive, Sharing.shared)) {
                String where = leftSharing + "/" + rightSharing;
                Bay left = MagicFixtures.bay(
                    id("two_declared_left"),
                    oneCell,
                    Coverage.everyCell,
                    Footprint.declared(leftOwned),
                    leftSharing
                );
                Bay right = MagicFixtures.bay(
                    id("two_declared_right"),
                    CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1)),
                    Coverage.everyCell,
                    Footprint.declared(rightOwned),
                    rightSharing
                );
                BlueprintBuilder builder = Blueprint.builder(id("two_declared_" + where.replace('/', '_'))).bounds(
                    Cuboid.of(0, 0, 0, 3, 1, 1)
                ).check(
                    Check.of(id("two_declared_left_check"), left, Focuses.occupant(origin), Expectations.anyOccupant())
                ).check(
                    Check.of(
                        id("two_declared_right_check"),
                        right,
                        Focuses.occupant(LatticePoint.of(1, 0, 0)),
                        Expectations.anyOccupant()
                    )
                );
                if (leftSharing == Sharing.exclusive || rightSharing == Sharing.exclusive) {
                    IllegalArgumentException rejected = assertThrows(
                        IllegalArgumentException.class,
                        builder::build,
                        where + " owns cell 2 on both sides"
                    );
                    assertTrue(Objects.requireNonNull(rejected.getMessage(), "message").contains("SC-BPT-0002"));
                } else {
                    assertNotNull(builder.build(), where + " shared/shared overlaps without an exclusive side");
                }
            }
        }
    }

    @Test
    void cellsMayOverlapWhenOwnedSetsDoNot() {
        CuboidSet inner = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        CuboidSet span = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 1, 1));
        Bay box = MagicFixtures.bay(id("relax_box"), inner, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Bay declared = MagicFixtures.bay(
            id("relax_declared"),
            span,
            Coverage.everyCell,
            Footprint.declared(span.subtract(inner)),
            Sharing.exclusive
        );
        Blueprint declaredBlueprint = blueprint(
            "relax_declared_cells",
            Cuboid.of(0, 0, 0, 3, 1, 1),
            Check.of(id("relax_box_0"), box, Focuses.occupant(origin), Expectations.anyOccupant()),
            Check.of(id("relax_box_1"), box, Focuses.occupant(LatticePoint.of(1, 0, 0)), Expectations.anyOccupant()),
            Check.of(id("relax_declared_0"), declared, Focuses.occupant(origin), Expectations.anyOccupant()),
            Check.of(
                id("relax_declared_1"),
                declared,
                Focuses.occupant(LatticePoint.of(1, 0, 0)),
                Expectations.anyOccupant()
            ),
            Check.of(
                id("relax_declared_2"),
                declared,
                Focuses.occupant(LatticePoint.of(2, 0, 0)),
                Expectations.anyOccupant()
            )
        );
        assertNotNull(declaredBlueprint, "bay cells may overlap when the owned sets do not");

        Bay bound = MagicFixtures.bay(
            id("relax_bound"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1)),
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        assertNotNull(
            blueprint(
                "relax_bound_cells",
                Cuboid.of(0, 0, 0, 2, 1, 1),
                Check.of(id("relax_bound_0"), bound, Focuses.occupant(origin), Expectations.anyOccupant()),
                Check.of(
                    id("relax_bound_1"),
                    bound,
                    Focuses.occupant(LatticePoint.of(1, 0, 0)),
                    Expectations.anyOccupant()
                )
            ),
            "a Bound bay's cells are not its owned set at build"
        );
    }

    @Test
    void anExclusiveEmptyDeclaredFootprintFailsBuild() {
        Bay declared = MagicFixtures.bay(
            id("empty_declared"),
            oneCell,
            Coverage.everyCell,
            Footprint.declared(CuboidSet.empty()),
            Sharing.exclusive
        );
        IllegalArgumentException rejected = assertThrows(
            IllegalArgumentException.class,
            () -> blueprint(
                "empty_declared",
                boundingBox(oneCell),
                Check.of(id("empty_declared_check"), declared, Focuses.occupant(origin), Expectations.anyOccupant())
            )
        );
        assertTrue(Objects.requireNonNull(rejected.getMessage(), "message").contains("SC-BPT-0005"));
    }

    @Test
    void twoBoundBaysThatOverlapAtSettleAreRejected() {
        Bay left = MagicFixtures
                .bay(id("settled_left"), oneCell, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        Bay right = MagicFixtures
                .bay(id("settled_right"), oneCell, Coverage.everyCell, Footprint.bound(), Sharing.shared);
        Blueprint blueprint = blueprint(
            "settled_overlap",
            boundingBox(oneCell),
            Check.of(id("settled_left_check"), left, Focuses.occupant(origin), Expectations.anyOccupant()),
            Check.of(id("settled_right_check"), right, Focuses.occupant(origin), Expectations.anyOccupant())
        );
        OccupantSnapshot block = TestWorld.block(TestWorld.stone, origin);
        IllegalArgumentException rejected = assertThrows(
            IllegalArgumentException.class,
            () -> ClaimPlanner.derive(
                blueprint,
                identity,
                instance,
                List.of(new BayBinding(left, List.of(block)), new BayBinding(right, List.of(block)))
            )
        );
        assertTrue(Objects.requireNonNull(rejected.getMessage(), "message").contains("SC-CLM-0001"));

        TestWorld.View view = MagicFixtures.view().put(block);
        MatchResult.Failed checked = assertInstanceOf(
            MatchResult.Failed.class,
            MagicFixtures.check(blueprint, identity, view, new ClaimView(List.of(), 0, true))
        );
        assertTrue(MagicFixtures.hasCode(checked, Codes.claimConflict), "check answers SC-CLM-0001");

        ClaimLedger ledger = MagicFixtures.ledger();
        MatchResult.Failed settled = assertInstanceOf(
            MatchResult.Failed.class,
            MagicFixtures.settle(blueprint, identity, view, ledger)
        );
        assertTrue(MagicFixtures.hasCode(settled, Codes.claimConflict), "settle answers SC-CLM-0001");
        assertTrue(TestWorld.rows(ledger).isEmpty(), "nothing is written");
    }

    @Test
    void aBoundBayMayCoverAnotherBaysCellsWhenItOwnsNothingThere() {
        CuboidSet inner = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        CuboidSet span = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 1, 1));
        Bay box = MagicFixtures.bay(id("cover_box"), inner, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Bay bound = MagicFixtures
                .bay(id("cover_bound"), span, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        Blueprint blueprint = blueprint(
            "bound_cover",
            Cuboid.of(0, 0, 0, 3, 1, 1),
            Check.of(id("cover_box_0"), box, Focuses.occupant(origin), Expectations.anyOccupant()),
            Check.of(id("cover_box_1"), box, Focuses.occupant(LatticePoint.of(1, 0, 0)), Expectations.anyOccupant()),
            Check.of(id("cover_bound_0"), bound, Focuses.occupant(origin), Expectations.anyOccupant()),
            Check.of(
                id("cover_bound_1"),
                bound,
                Focuses.occupant(LatticePoint.of(1, 0, 0)),
                Expectations.anyOccupant()
            ),
            Check.of(id("cover_bound_2"), bound, Focuses.occupant(LatticePoint.of(2, 0, 0)), Expectations.anyOccupant())
        );
        TestWorld.View view = MagicFixtures.view().air(origin).air(LatticePoint.of(1, 0, 0))
                .put(TestWorld.block(TestWorld.stone, LatticePoint.of(2, 0, 0)));
        ClaimLedger ledger = MagicFixtures.ledger();

        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(blueprint, identity, view, ledger)
        );
        assertEquals(3, formed.claims().rows().size(), "the box owns 0 and 1, the bound bay owns only 2");
        assertEquals(3, TestWorld.rows(ledger).size());
    }

    private static NamespacedId id(String path) {
        return MagicFixtures.id(path);
    }

    private static ClaimRow row(LatticePoint cell, Sharing sharing, InstanceId instance, Siting siting, String bay) {
        return new ClaimRow(LatticeKey.of(cell), sharing, siting, id(bay), instance);
    }

    private static Cuboid boundingBox(CuboidSet cells) {
        return Objects.requireNonNull(cells.bounds(), "bounds");
    }

    private static Blueprint blueprint(String path, Cuboid bounds, Check<?>... checks) {
        BlueprintBuilder builder = Blueprint.builder(id(path)).bounds(bounds);
        for (Check<?> check : checks) {
            builder.check(check);
        }
        return builder.build();
    }

    private static Check<?> everyCellCheck(String path, Bay bay) {
        return Check.of(id(path), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()));
    }

    private static Set<LatticeKey> keys(List<ClaimRow> rows) {
        Set<LatticeKey> result = new LinkedHashSet<>();
        for (ClaimRow row : rows) {
            result.add(row.cell());
        }
        return result;
    }

    private static Set<LatticeKey> keys(CuboidSet cells) {
        Set<LatticeKey> result = new LinkedHashSet<>();
        addKeys(result, cells);
        return result;
    }

    private static void addKeys(Set<LatticeKey> target, CuboidSet cells) {
        for (Cuboid box : cells.decomposition()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        target.add(LatticeKey.of(LatticePoint.of(x, y, z)));
                    }
                }
            }
        }
    }

    private static void assertSorted(List<ClaimRow> rows) {
        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i - 1).compareTo(rows.get(i)) <= 0, "rows are canonical");
        }
    }

    private static OccupantSnapshot nestedOccupant(LatticePoint cell, int frame) {
        NamespacedId type = id("nested_body");
        OccupantRef ref = new OccupantRef(TestWorld.snapshot, frame, type, OccupantKind.nested, cell);
        return new OccupantSnapshot(ref, new OccupantDescriptor(type, OccupantKind.nested), Cuboid.of(cell), List.of());
    }

    private static OccupantSnapshot block(LatticePoint cell, int frame) {
        OccupantRef ref = new OccupantRef(TestWorld.snapshot, frame, TestWorld.stone, OccupantKind.block, cell);
        return new OccupantSnapshot(
            ref,
            new OccupantDescriptor(TestWorld.stone, OccupantKind.block),
            Cuboid.of(cell),
            List.of()
        );
    }

    private static Focus<OccupantSnapshot> occupantFocus(LatticePoint cell, boolean claimable) {
        return new Focus<>() {
            @Override
            public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
                OccupantSnapshot occupant = ctx.occupantAt(cell);
                if (occupant == null || occupant.isAir()) {
                    return new FocusResult.Unavailable<>(findingBundle("missing subject"));
                }
                return new FocusResult.Taken<>(occupant, List.of(occupant), FindingBundle.empty());
            }

            @Override
            public Class<OccupantSnapshot> subject() {
                return OccupantSnapshot.class;
            }

            @Override
            public CuboidSet staticCells(CuboidSet bayCells) {
                return CuboidSet.empty();
            }

            @Override
            public boolean bindsClaimableSubject() {
                return claimable;
            }
        };
    }

    private static Focus<OccupantSnapshot> footprintFocus(LatticePoint at, Cuboid footprint) {
        return new Focus<>() {
            @Override
            public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
                OccupantSnapshot occupant = ctx.occupantAt(at);
                if (occupant == null || occupant.isAir()) {
                    return new FocusResult.Unavailable<>(findingBundle("missing subject"));
                }
                OccupantSnapshot widened = new OccupantSnapshot(
                    occupant.ref(),
                    occupant.descriptor(),
                    footprint,
                    occupant.traits()
                );
                return new FocusResult.Taken<>(widened, List.of(widened), FindingBundle.empty());
            }

            @Override
            public Class<OccupantSnapshot> subject() {
                return OccupantSnapshot.class;
            }

            @Override
            public CuboidSet staticCells(CuboidSet bayCells) {
                return CuboidSet.empty();
            }

            @Override
            public boolean bindsClaimableSubject() {
                return true;
            }
        };
    }

    private static Focus<OccupantSnapshot> nestedLyingFocus(LatticePoint cell) {
        return new Focus<>() {
            @Override
            public FocusResult<OccupantSnapshot> take(FocusContext ctx) {
                OccupantSnapshot nested = nestedOccupant(cell, 0);
                return new FocusResult.Taken<>(nested, List.of(nested), FindingBundle.empty());
            }

            @Override
            public Class<OccupantSnapshot> subject() {
                return OccupantSnapshot.class;
            }

            @Override
            public CuboidSet staticCells(CuboidSet bayCells) {
                return CuboidSet.empty();
            }

            @Override
            public boolean bindsClaimableSubject() {
                return true;
            }
        };
    }

    private static FindingBundle findingBundle(String message) {
        return FindingBundle.of(List.of(Finding.builder(Codes.expected).phase(Phase.check).message(message).build()));
    }
}
