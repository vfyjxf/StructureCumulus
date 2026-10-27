package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.BayView;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AE2 crafting CPU geometry: a 2x2x2 solid, a missing storage cell, an outside block and the lattice edge. */
class Ae2CpuScenarioTest {

    private static final char cpu = '#';
    private static final TechFixtures.Pattern cpuPattern = TechFixtures.pattern("##/##", "##/##");
    private static final CuboidSet cpuCells = cpuPattern.cells(cpu);
    private static final LatticePoint origin = LatticePoint.origin;

    @Test
    void solidCpuFormsAtAClockwiseSiting() {
        assertEquals(8L, cpuPattern.bounds().volume(), "the CPU is a 2x2x2 solid");
        Siting siting = TechFixtures.turn(origin, Turn.clockwise);
        TestWorld.View view = TechFixtures.view();
        cpuPattern.fill(view, siting, cpu, TechFixtures.cpu);
        TestWorld.Ledger ledger = TechFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(cpuBlueprint("ae2/cpu_clockwise"), siting, view, ledger),
            "the eight-cell CPU forms"
        );
        assertEquals(8, formed.claims().rows().size(), "eight CPU cells are claimed");
        Set<LatticeKey> expected = TechFixtures.worldKeys(siting, cpuCells);
        assertEquals(expected, new LinkedHashSet<>(TechFixtures.rowKeys(formed)), "the claims are the sited CPU cells");
        assertEquals(8, ledger.all().size(), "the ledger holds eight rows");
    }

    @Test
    void aMissingStorageCellFails() {
        Siting siting = TechFixtures.identity(origin);
        TestWorld.View view = TechFixtures.view();
        cpuPattern.fill(view, siting, cpu, TechFixtures.cpu);
        view.air(TechFixtures.world(siting, LatticePoint.of(1, 1, 1)));
        TestWorld.Ledger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures.settle(cpuBlueprint("ae2/cpu_missing_storage"), siting, view, ledger);
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result, "one CPU cell is air");
        assertTrue(TechFixtures.hasCode(failed, Codes.expected), "the missing storage is SC-EXP-0001");
        assertEquals(0, ledger.all().size(), "a failed check writes no rows");
    }

    @Test
    void anExtraBlockJustOutsideTheBayIsAdmitted() {
        Siting siting = TechFixtures.identity(origin);
        TestWorld.View view = TechFixtures.view();
        cpuPattern.fill(view, siting, cpu, TechFixtures.cpu);
        TechFixtures.put(view, siting, LatticePoint.of(2, 0, 0), TechFixtures.storage);
        TestWorld.Ledger ledger = TechFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(cpuBlueprint("ae2/cpu_outside_block"), siting, view, ledger),
            "an extra block outside the bay is not evidence"
        );
        assertEquals(8, formed.claims().rows().size(), "the outside storage is not claimed");
        assertTrue(
            TechFixtures.rowKeys(formed).stream().noneMatch(key -> key.equals(LatticePoint.of(2, 0, 0).key())),
            "the outside cell stays outside the claim"
        );
    }

    @Test
    void cpuBayAtTheLatticeExtentDegradesToUnreadable() {
        Siting siting = TechFixtures.orientation(
            LatticePoint.of(LatticePoint.maxXz, LatticePoint.maxY, LatticePoint.maxXz),
            Orientation.identity
        );
        TestWorld.View view = TechFixtures.view();
        MatchResult result = TechFixtures
                .check(cpuBlueprint("ae2/cpu_lattice_edge"), siting, view, TechFixtures.emptyClaims());
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            result,
            "a partially unmapped CPU bay is not silently admitted"
        );
        assertEquals(UndecidedReason.unreadable, undecided.reason(), "the unmapped cells are unreadable");
        assertTrue(TechFixtures.hasCode(undecided, Codes.unreadableRead), "the refusal is SC-OCC-0004");
        assertNotNull(undecided.partial(), "the partial outcome is kept");
    }

    @Test
    void anExclusiveCpuRejectsAnotherOwner() {
        Siting siting = TechFixtures.identity(origin);
        TestWorld.View view = TechFixtures.view();
        cpuPattern.fill(view, siting, cpu, TechFixtures.cpu);
        TestWorld.Ledger ledger = TechFixtures.ledger();
        assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(cpuBlueprint("ae2/cpu_conflict"), siting, view, ledger),
            "the first owner forms"
        );
        assertEquals(8, ledger.all().size(), "the first owner holds eight rows");
        long revision = ledger.revision();
        MatchResult result = TechFixtures.settle(
            cpuBlueprint("ae2/cpu_conflict"),
            siting,
            view,
            ledger,
            TechFixtures.rival,
            TechFixtures.rivalInstance
        );
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result, "the rival is rejected");
        assertTrue(TechFixtures.hasCode(failed, Codes.claimConflict), "the conflict is SC-CLM-0001");
        assertEquals(8, ledger.all().size(), "the conflict leaves the incumbent rows untouched");
        assertEquals(revision, ledger.revision(), "the conflict does not move the revision");
    }

    private static Blueprint cpuBlueprint(String path) {
        Bay bay = TechFixtures.bay(
            TechFixtures.id(path + "/bay"),
            cpuCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Check<BayView> check = TechFixtures
                .eachCell(TechFixtures.id(path + "/cpu"), bay, Expectations.block(TechFixtures.cpu));
        return Blueprint.builder(TechFixtures.id(path)).bounds(cpuPattern.bounds()).check(check).build();
    }
}
