package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.Trait;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.bay;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.bayBlueprint;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.box;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.context;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.deficitBlueprint;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.dirt;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.finding;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.id;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.occupantBlueprint;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.oneCell;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.rotation;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.stone;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.withoutCapture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The finding → edit mapping of remedy §5.1, one row per family. */
class StandardRepairProviderTest {

    private static final LatticePoint origin = LatticePoint.origin;
    private static final LatticePoint east = LatticePoint.of(1, 0, 0);

    private final StandardRepairProvider provider = new StandardRepairProvider();

    @Test
    void theProviderIsTheBuiltinCellRepair() {
        assertEquals(NamespacedId.of("structurecumulus", "cell_repair"), provider.id());
    }

    @Test
    void anAbsentExpectedOccupantIsPlacedMachineApplicably() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));

        Remedy.Repair repair = onlyRepair(
            provider.solve(finding(Codes.expected, origin), context(blueprint, new RemedyFixtures.View().air(origin)))
        );

        assertEquals(Applicability.machineApplicable, repair.applicability());
        assertEquals(new Edit.Place(oneCell(origin), stone, Map.of()), repair.plan().steps().get(0).edit());
        assertEquals(Applicability.machineApplicable, repair.plan().steps().get(0).applicability());
        assertEquals(Locus.world(oneCell(origin)), repair.plan().steps().get(0).locus());
    }

    @Test
    void aDifferentOccupantIsReplacedMachineApplicably() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));

        Remedy.Repair repair = onlyRepair(
            provider.solve(
                finding(Codes.expected, origin),
                context(blueprint, new RemedyFixtures.View().block(dirt, origin))
            )
        );

        assertEquals(Applicability.machineApplicable, repair.applicability());
        assertEquals(new Edit.Replace(oneCell(origin), stone, Map.of()), repair.plan().steps().get(0).edit());
    }

    @Test
    void aForeignOccupantOnAnAirLeafIsRemovedMachineApplicably() {
        Blueprint blueprint = occupantBlueprint(Expectations.air());

        Remedy.Repair repair = onlyRepair(
            provider.solve(
                finding(Codes.expected, origin),
                context(blueprint, new RemedyFixtures.View().block(stone, origin))
            )
        );

        assertEquals(Applicability.machineApplicable, repair.applicability());
        assertEquals(new Edit.Remove(oneCell(origin)), repair.plan().steps().get(0).edit());
    }

    @Test
    void aMatchedNegationIsRemovedMachineApplicably() {
        Blueprint blueprint = occupantBlueprint(Expectations.not(Expectations.air()));

        Remedy.Repair repair = onlyRepair(
            provider.solve(
                finding(Codes.negationMatched, origin),
                context(blueprint, new RemedyFixtures.View().air(origin))
            )
        );

        assertEquals(Applicability.machineApplicable, repair.applicability());
        assertEquals(new Edit.Remove(oneCell(origin)), repair.plan().steps().get(0).edit());
    }

    @Test
    void anOpenEnvelopeIsClosedByRemovingTheForeignOccupant() {
        Blueprint blueprint = occupantBlueprint(Expectations.anyOccupant());

        Remedy.Repair repair = onlyRepair(
            provider.solve(
                finding(Codes.envelopeOpen, origin),
                context(blueprint, new RemedyFixtures.View().block(stone, origin))
            )
        );

        assertEquals(Applicability.machineApplicable, repair.applicability());
        assertEquals(new Edit.Remove(oneCell(origin)), repair.plan().steps().get(0).edit());
    }

    @Test
    void aThresholdDeficitPlacesAtTheCanonicalCellAndOffersTheUnchosenCandidates() {
        Blueprint blueprint = deficitBlueprint();
        CuboidSet failure = box(0, 0, 0, 2, 1, 1);

        List<Remedy> remedies = provider.solve(
            finding(Codes.thresholdImpossible, failure),
            context(blueprint, new RemedyFixtures.View().air(origin).air(east))
        );

        Remedy.Repair chosen = assertInstanceOf(Remedy.Repair.class, remedies.get(0));
        assertEquals(Applicability.machineApplicable, chosen.applicability());
        assertEquals(new Edit.Place(oneCell(origin), stone, Map.of()), chosen.plan().steps().get(0).edit());
        assertOffered(remedies, new Edit.Place(oneCell(east), stone, Map.of()));
    }

    @Test
    void aThresholdDeficitChosenByPreferenceAlsoOffersTheDisplacedCandidate() {
        Blueprint blueprint = deficitBlueprint();
        SolvePreferences preferences = new SolvePreferences(List.of(oneCell(east)), Map.of());

        List<Remedy> remedies = provider.solve(
            finding(Codes.thresholdImpossible, box(0, 0, 0, 2, 1, 1)),
            RemedyFixtures.preferred(blueprint, new RemedyFixtures.View().air(origin).air(east), preferences)
        );

        Remedy.Repair chosen = assertInstanceOf(Remedy.Repair.class, remedies.get(0));
        assertEquals(Applicability.machineApplicable, chosen.applicability());
        assertEquals(new Edit.Place(oneCell(east), stone, Map.of()), chosen.plan().steps().get(0).edit());
        assertOffered(remedies, new Edit.Place(oneCell(origin), stone, Map.of()));
    }

    @Test
    void aPinPutsThePinnedCandidateFirstAndOffersTheDisplacedOne() {
        Blueprint blueprint = deficitBlueprint();
        SolvePreferences preferences = new SolvePreferences(List.of(oneCell(origin)), Map.of(id("bay"), oneCell(east)));

        List<Remedy> remedies = provider.solve(
            finding(Codes.thresholdImpossible, box(0, 0, 0, 2, 1, 1)),
            RemedyFixtures.preferred(blueprint, new RemedyFixtures.View().air(origin).air(east), preferences)
        );

        Remedy.Repair chosen = assertInstanceOf(Remedy.Repair.class, remedies.get(0));
        assertEquals(new Edit.Place(oneCell(east), stone, Map.of()), chosen.plan().steps().get(0).edit());
        assertOffered(remedies, new Edit.Place(oneCell(origin), stone, Map.of()));
    }

    @Test
    void aDeficitAcrossTwoSitesRepairsEverySite() {
        Blueprint blueprint = RemedyFixtures.twoThresholdBlueprint();
        RemedyFixtures.View view = new RemedyFixtures.View().air(origin).air(east);

        List<Remedy> remedies = provider
                .solve(finding(Codes.thresholdImpossible, box(0, 0, 0, 2, 1, 1)), context(blueprint, view));

        assertOffered(remedies, new Edit.Place(oneCell(origin), stone, Map.of()));
        assertOffered(remedies, new Edit.Place(oneCell(east), stone, Map.of()));

        List<Remedy> capped = provider
                .solve(finding(Codes.thresholdImpossible, box(0, 0, 0, 2, 1, 1)), context(blueprint, 2, view, null));
        assertEquals(2, capped.size(), () -> capped.toString());
    }

    @Test
    void aThresholdExcessIsAdvisoryWithNoMachineEdit() {
        Blueprint blueprint = excessBlueprint(
            Expectations.atMost(0, Expectations.block(stone), Expectations.block(dirt))
        );

        Remedy.Repair repair = onlyRepair(
            provider.solve(
                finding(Codes.thresholdImpossible, box(0, 0, 0, 2, 1, 1)),
                context(blueprint, new RemedyFixtures.View().block(stone, origin).block(dirt, east))
            )
        );

        assertEquals(Applicability.hasPlaceholders, repair.applicability());
        assertTrue(repair.plan().steps().isEmpty());
    }

    @Test
    void anExactlyExcessIsAdvisoryWithNoMachineEdit() {
        Blueprint blueprint = excessBlueprint(
            Expectations.exactly(1, Expectations.block(stone), Expectations.block(dirt))
        );

        Remedy.Repair repair = onlyRepair(
            provider.solve(
                finding(Codes.thresholdImpossible, box(0, 0, 0, 2, 1, 1)),
                context(blueprint, new RemedyFixtures.View().block(stone, origin).block(dirt, east))
            )
        );

        assertEquals(Applicability.hasPlaceholders, repair.applicability());
        assertTrue(repair.plan().steps().isEmpty());
    }

    @Test
    void aFailedTraitAbsentExpectationIsATraitRemovalAtMostMaybeIncorrect() {
        Blueprint blueprint = occupantBlueprint(Expectations.traitAbsent(rotation));

        Remedy.Repair repair = onlyRepair(
            provider.solve(
                finding(Codes.expected, origin),
                context(blueprint, new RemedyFixtures.View().block(stone, origin))
            )
        );

        assertEquals(Applicability.maybeIncorrect, repair.applicability());
        assertTrue(repair.applicability().rank() <= Applicability.maybeIncorrect.rank());
        Edit.SetTrait edit = assertInstanceOf(Edit.SetTrait.class, repair.plan().steps().get(0).edit());
        assertEquals(oneCell(origin), edit.cells());
        assertEquals(Trait.absent(rotation), edit.trait());
        assertEquals(Applicability.maybeIncorrect, repair.plan().steps().get(0).applicability());
    }

    @Test
    void aFailedTraitPresenceExpectationIsAdvisoryWithNoEdit() {
        Blueprint blueprint = occupantBlueprint(Expectations.trait(rotation));

        Remedy.Repair repair = onlyRepair(
            provider.solve(
                finding(Codes.expected, origin),
                context(blueprint, new RemedyFixtures.View().block(stone, origin))
            )
        );

        assertEquals(Applicability.hasPlaceholders, repair.applicability());
        assertTrue(repair.plan().steps().isEmpty());
    }

    @Test
    void aClaimConflictIsAdvisoryWithNoMachineEdit() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));

        Remedy.Repair repair = onlyRepair(
            provider.solve(
                finding(Codes.claimConflict, origin),
                context(blueprint, new RemedyFixtures.View().block(stone, origin))
            )
        );

        assertEquals(Applicability.hasPlaceholders, repair.applicability());
        assertTrue(repair.plan().steps().isEmpty());
        assertTrue(repair.rationale().contains("claim"));
    }

    @Test
    void anUndescribableDeclarationIsAdvisory() {
        Expectation<OccupantSnapshot> custom = Expectations
                .custom(OccupantSnapshot.class, ExpectationMeta.anonymous(), ctx -> Reply.match());
        Blueprint blueprint = occupantBlueprint(custom);

        Remedy.Repair repair = onlyRepair(
            provider.solve(finding(Codes.expected, origin), context(blueprint, new RemedyFixtures.View().air(origin)))
        );

        assertEquals(Applicability.hasPlaceholders, repair.applicability());
        assertTrue(repair.plan().steps().isEmpty());
    }

    @Test
    void withoutACaptureNoRepairIsMachineApplicable() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));

        List<Remedy> remedies = provider.solve(finding(Codes.expected, origin), withoutCapture(blueprint));

        assertFalse(remedies.stream().anyMatch(StandardRepairProviderTest::isMachineApplicable));
        Remedy.Repair repair = onlyRepair(remedies);
        assertEquals(Applicability.hasPlaceholders, repair.applicability());
        assertTrue(repair.plan().steps().isEmpty());
    }

    @Test
    void aFindingWithoutADeclarationAtItsCellsYieldsNoRepair() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));

        List<Remedy> remedies = provider.solve(
            finding(Codes.expected, LatticePoint.of(5, 0, 0)),
            context(blueprint, new RemedyFixtures.View().air(LatticePoint.of(5, 0, 0)))
        );

        assertTrue(remedies.isEmpty());
    }

    @Test
    void anOrientationAnchorBudgetOrCancellationFindingHasNoEdit() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));
        RemedyContext context = context(blueprint, new RemedyFixtures.View().block(stone, origin));

        assertTrue(provider.solve(finding(FindingCode.of(FindingCode.Family.mc, 4), origin), context).isEmpty());
        assertTrue(provider.solve(finding(Codes.anchorInvalid, origin), context).isEmpty());
        assertTrue(provider.solve(finding(Codes.budgetExhausted, origin), context).isEmpty());
        assertTrue(provider.solve(finding(Codes.cancelled, origin), context).isEmpty());
    }

    @Test
    void aCodeOutsideTheMappingYieldsNoRepair() {
        Blueprint blueprint = occupantBlueprint(Expectations.block(stone));

        assertTrue(
            provider.solve(
                finding(Codes.unreadableCell, origin),
                context(blueprint, new RemedyFixtures.View().unreadable(origin))
            ).isEmpty()
        );
    }

    private static Blueprint excessBlueprint(Expectation<OccupantSnapshot> threshold) {
        return bayBlueprint(bay("bay", box(0, 0, 0, 2, 1, 1)), Expectations.eachCell(threshold));
    }

    private static Remedy.Repair onlyRepair(List<Remedy> remedies) {
        assertEquals(1, remedies.size(), () -> remedies.toString());
        return assertInstanceOf(Remedy.Repair.class, remedies.get(0));
    }

    private static boolean isMachineApplicable(Remedy remedy) {
        return remedy instanceof Remedy.Repair repair && repair.applicability() == Applicability.machineApplicable;
    }

    private static void assertOffered(List<Remedy> remedies, Edit expected) {
        boolean offered = remedies.stream().anyMatch(
            remedy -> remedy instanceof Remedy.Repair repair
                    && repair.plan().steps().size() == 1
                    && repair.plan().steps().get(0).edit().equals(expected)
        );
        assertTrue(offered, () -> "the unchosen candidate is not offered: " + remedies);
    }
}
