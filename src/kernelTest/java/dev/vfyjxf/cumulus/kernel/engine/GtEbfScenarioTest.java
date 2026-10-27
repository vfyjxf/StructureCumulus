package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.BayView;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GregTech Modern EBF geometry: a 3x3x4 shell with nine coils, four yaw turns and host-side coil counting. */
class GtEbfScenarioTest {

    private static final char casing = '#';
    private static final char hot = 'a';
    private static final char cold = 'b';

    private static final TechFixtures.Pattern ebf = TechFixtures
            .pattern("aab/aba/baa", "###/###/###", "###/###/###", "###/###/###");
    private static final Cuboid bounds = ebf.bounds();
    private static final CuboidSet coilCells = ebf.cells(hot, cold);
    private static final CuboidSet casingCells = ebf.cells(casing);
    private static final Map<Character, NamespacedId> legend = Map
            .of(casing, TechFixtures.casing, hot, TechFixtures.hotCoil, cold, TechFixtures.coldCoil);

    @Test
    void theElectricBlastFurnaceIsThirtySixCellsAcrossFourTurns() {
        assertEquals(36L, bounds.volume(), "the EBF is a 3x3x4 shell");
        assertEquals(9, coilCells.volume(), "one coil layer");
        assertEquals(27, casingCells.volume(), "three casing layers");
        Blueprint blueprint = mixedCoilBlueprint("gt/ebf_four_turns");
        for (Turn turn : Turn.values()) {
            Siting siting = TechFixtures.turn(LatticePoint.origin, turn);
            TestWorld.View view = TechFixtures.view();
            ebf.fill(view, siting, legend);
            TestWorld.Ledger ledger = TechFixtures.ledger();
            MatchResult outcome = TechFixtures.settle(blueprint, siting, view, ledger);
            MatchResult.Formed formed = assertInstanceOf(
                MatchResult.Formed.class,
                outcome,
                "the EBF forms at " + turn + ": " + outcome.findings().findings()
            );
            assertEquals(36, formed.claims().rows().size(), "thirty-six cells at " + turn);
            Set<LatticeKey> expected = TechFixtures.worldKeys(siting, ebf.cells(casing, hot, cold));
            assertEquals(expected, new LinkedHashSet<>(TechFixtures.rowKeys(formed)), "sited claim cells at " + turn);
            assertEquals(36, ledger.all().size(), "the ledger holds the same cells at " + turn);
        }
    }

    @Test
    void aMissingCasingFailsWithoutClaims() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        ebf.fill(view, siting, legend);
        LatticePoint missing = LatticePoint.of(0, 1, 0);
        view.air(TechFixtures.world(siting, missing));
        TestWorld.Ledger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures.settle(mixedCoilBlueprint("gt/ebf_missing_casing"), siting, view, ledger);
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result, "the missing casing is air");
        assertTrue(TechFixtures.hasCode(failed, Codes.expected), "the missing casing is SC-EXP-0001");
        assertEquals(0, ledger.all().size(), "a failed check writes no rows");
        assertEquals(0L, ledger.revision(), "a failed check does not move the ledger");
    }

    @Test
    void anExtraHatchOutsideTheBayIsNotEvidence() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        ebf.fill(view, siting, legend);
        TechFixtures.put(view, siting, LatticePoint.of(-1, 0, 0), TechFixtures.hatch);
        TestWorld.Ledger ledger = TechFixtures.ledger();
        MatchResult outcome = TechFixtures.settle(mixedCoilBlueprint("gt/ebf_extra_hatch"), siting, view, ledger);
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            outcome,
            "an extra hatch outside the bay stays unread: " + outcome.findings().findings()
        );
        assertEquals(36, formed.claims().rows().size(), "only the declared bay is claimed");
        assertTrue(
            TechFixtures.rowKeys(formed).stream().noneMatch(key -> key.equals(LatticePoint.of(-1, 0, 0).key())),
            "the hatch cell is not in the claim"
        );
    }

    @Test
    void mixedCoilCountNeedsAHostAggregate() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        ebf.fill(view, siting, legend);
        MatchResult hostResult = TechFixtures
                .settle(mixedCoilBlueprint("gt/ebf_mixed_host"), siting, view, TechFixtures.ledger());
        MatchResult.Formed host = assertInstanceOf(
            MatchResult.Formed.class,
            hostResult,
            "a host BayView count accepts six hot and three cold coils: " + hostResult.findings().findings()
        );
        assertEquals(36, host.claims().rows().size(), "the mixed coil layer still claims nine cells");

        MatchResult nativeResult = TechFixtures
                .settle(allHotBlueprint("gt/ebf_mixed_native"), siting, view, TechFixtures.ledger());
        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            nativeResult,
            "per-cell block checks cannot express a mixed coil tier"
        );
        assertTrue(TechFixtures.hasCode(failed, Codes.expected), "the native coil mismatch is SC-EXP-0001");
    }

    private static Blueprint mixedCoilBlueprint(String path) {
        Bay casingBay = TechFixtures.bay(
            TechFixtures.id(path + "/casing_bay"),
            casingCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay coilBay = TechFixtures.bay(
            TechFixtures.id(path + "/coil_bay"),
            coilCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Check<BayView> casingCheck = TechFixtures
                .eachCell(TechFixtures.id(path + "/casing"), casingBay, Expectations.block(TechFixtures.casing));
        Check<BayView> coilCheck = Check
                .of(TechFixtures.id(path + "/coil_mix"), coilBay, Focuses.bay(), mixedCoilExpectation());
        return Blueprint.builder(TechFixtures.id(path)).bounds(bounds).check(casingCheck).check(coilCheck).build();
    }

    private static Blueprint allHotBlueprint(String path) {
        Bay coilBay = TechFixtures.bay(
            TechFixtures.id(path + "/coil_bay"),
            coilCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay casingBay = TechFixtures.bay(
            TechFixtures.id(path + "/casing_bay"),
            casingCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        return Blueprint.builder(TechFixtures.id(path)).bounds(bounds).check(
            TechFixtures.eachCell(TechFixtures.id(path + "/casing"), casingBay, Expectations.block(TechFixtures.casing))
        ).check(
            TechFixtures.eachCell(TechFixtures.id(path + "/coil"), coilBay, Expectations.block(TechFixtures.hotCoil))
        ).build();
    }

    private static Expectation<BayView> mixedCoilExpectation() {
        return Expectations.custom(BayView.class, ExpectationMeta.anonymous(), ctx -> {
            BayView subject = ctx.subject();
            int hotCount = subject.occupants(OccupantFilter.ofType(TechFixtures.hotCoil)).size();
            int coldCount = subject.occupants(OccupantFilter.ofType(TechFixtures.coldCoil)).size();
            if (hotCount == 6 && coldCount == 3) {
                return Reply.match();
            }
            Finding finding = Finding.builder(Codes.expected).phase(ctx.phase())
                    .message("expected six hot coils and three cold coils, found " + hotCount + " and " + coldCount)
                    .label(Label.primary(ctx.locus(), "coil mix")).build();
            return Reply.mismatch(finding);
        });
    }
}
