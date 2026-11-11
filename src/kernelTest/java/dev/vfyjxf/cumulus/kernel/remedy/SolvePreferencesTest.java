package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.box;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.context;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.deficitBlueprint;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.finding;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.id;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.keyedFinding;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.oneCell;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.preferred;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.stone;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-86: the record law of `SolvePreferences` and the preference and pin laws of remedy §5.3. */
class SolvePreferencesTest {

    private static final LatticePoint origin = LatticePoint.origin;
    private static final LatticePoint east = LatticePoint.of(1, 0, 0);
    private static final LatticePoint north = LatticePoint.of(0, 0, 1);

    private final StandardRepairProvider repair = new StandardRepairProvider();

    @Test
    @SuppressWarnings("NullAway") // the null tolerance is the contract
    void aNullListOrMapMeansEmpty() {
        List<CuboidSet> noCells = null;
        Map<NamespacedId, CuboidSet> noPins = null;

        SolvePreferences preferences = new SolvePreferences(noCells, noPins);

        assertTrue(preferences.preferredCells().isEmpty());
        assertTrue(preferences.partPins().isEmpty());
    }

    @Test
    void theCopiesAreImmutable() {
        List<CuboidSet> cells = new ArrayList<>(List.of(oneCell(origin)));
        Map<NamespacedId, CuboidSet> pins = new HashMap<>(Map.of(id("bay"), oneCell(origin)));
        SolvePreferences preferences = new SolvePreferences(cells, pins);

        cells.clear();
        pins.clear();

        assertEquals(List.of(oneCell(origin)), preferences.preferredCells());
        assertEquals(Map.of(id("bay"), oneCell(origin)), preferences.partPins());
        assertThrows(UnsupportedOperationException.class, () -> preferences.preferredCells().add(oneCell(east)));
        assertThrows(UnsupportedOperationException.class, () -> preferences.partPins().put(id("other"), oneCell(east)));
    }

    @Test
    @SuppressWarnings("NullAway") // the null tolerance is the contract
    void partPinsAreSorted() {
        NamespacedId later = NamespacedId.of("zzz", "port");

        SolvePreferences preferences = new SolvePreferences(
            null,
            Map.of(later, oneCell(east), id("bay"), oneCell(origin))
        );

        assertEquals(List.of(id("bay"), later), new ArrayList<>(preferences.partPins().keySet()));
    }

    @Test
    void preferencesOnlyReorderTheLegalCandidatesAndKeepTheRung() {
        Blueprint blueprint = deficitBlueprint();
        CuboidSet failure = box(0, 0, 0, 2, 1, 1);
        RemedyFixtures.View view = new RemedyFixtures.View().air(origin).air(east);
        SolvePreferences preferences = new SolvePreferences(List.of(oneCell(east)), Map.of());

        List<Remedy> canonical = repair.solve(finding(Codes.thresholdImpossible, failure), context(blueprint, view));
        List<Remedy> preferredRemedies = repair.solve(
            finding(Codes.thresholdImpossible, failure),
            RemedyFixtures.preferred(blueprint, view, preferences)
        );

        assertEquals(rungs(canonical), rungs(preferredRemedies));
        assertEquals(new Edit.Place(oneCell(origin), stone, Map.of()), firstEdit(canonical));
        assertEquals(new Edit.Place(oneCell(east), stone, Map.of()), firstEdit(preferredRemedies));
        assertTrue(rungs(preferredRemedies).stream().allMatch(rung -> rung == Applicability.machineApplicable));
        assertEquals(3, preferredRemedies.size(), () -> preferredRemedies.toString());
    }

    @Test
    void aPinSelectsOnlyInsideTheLegalCandidates() {
        Blueprint blueprint = deficitBlueprint();
        CuboidSet failure = box(0, 0, 0, 2, 1, 1);
        SolvePreferences preferences = new SolvePreferences(List.of(), Map.of(id("bay"), oneCell(east)));

        List<Remedy> remedies = repair.solve(
            finding(Codes.thresholdImpossible, failure),
            preferred(blueprint, new RemedyFixtures.View().air(origin).air(east), preferences)
        );

        assertEquals(new Edit.Place(oneCell(east), stone, Map.of()), firstEdit(remedies));
    }

    @Test
    void anIllegalPinIsIgnoredAndTheCanonicalOrderStillPicks() {
        Blueprint blueprint = deficitBlueprint();
        CuboidSet failure = box(0, 0, 0, 2, 1, 1);
        SolvePreferences preferences = new SolvePreferences(
            List.of(),
            Map.of(id("bay"), oneCell(LatticePoint.of(3, 0, 0)))
        );

        List<Remedy> remedies = repair.solve(
            finding(Codes.thresholdImpossible, failure),
            preferred(blueprint, new RemedyFixtures.View().air(origin).air(east), preferences)
        );

        assertEquals(new Edit.Place(oneCell(origin), stone, Map.of()), firstEdit(remedies));
    }

    @Test
    void withoutAPreferenceTheCanonicalLatticeOrderPicks() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1), Cuboid.of(0, 0, 1, 1, 1, 2));
        Blueprint blueprint = deficitBlueprint(cells);
        RemedyFixtures.View view = new RemedyFixtures.View().air(origin).air(east).air(north);

        List<Remedy> remedies = repair.solve(
            finding(Codes.thresholdImpossible, CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 2))),
            context(blueprint, view)
        );

        assertEquals(new Edit.Place(oneCell(origin), stone, Map.of()), firstEdit(remedies));
    }

    @Test
    void theSameInputAndPreferencesSolveIdentically() {
        StructureKernel kernel = StructureKernel.create(Catalogs.builtin());
        Blueprint blueprint = deficitBlueprint();
        FindingBundle bundle = FindingBundle.of(
            List.of(
                finding(Codes.thresholdImpossible, box(0, 0, 0, 2, 1, 1)),
                finding(Codes.claimConflict, origin),
                keyedFinding(Codes.cancelled)
            )
        );
        SolvePreferences preferences = new SolvePreferences(List.of(oneCell(east)), Map.of(id("bay"), oneCell(origin)));
        RemedyContext context = preferred(blueprint, new RemedyFixtures.View().air(origin).air(east), preferences);

        SolveResult first = kernel.solve(bundle, context);
        SolveResult second = kernel.solve(bundle, context);

        assertEquals(first.remedies(), second.remedies());
        assertEquals(first.findings().findings(), second.findings().findings());
        assertFalse(first.remedies().isEmpty());
        assertEquals(first.remedies().size(), second.remedies().size());
    }

    private static List<Applicability> rungs(List<Remedy> remedies) {
        List<Applicability> rungs = new ArrayList<>();
        for (Remedy remedy : remedies) {
            rungs.add(assertInstanceOf(Remedy.Repair.class, remedy).applicability());
        }
        return rungs;
    }

    private static Edit firstEdit(List<Remedy> remedies) {
        Remedy.Repair repair = assertInstanceOf(Remedy.Repair.class, remedies.get(0));
        return repair.plan().steps().get(0).edit();
    }
}
