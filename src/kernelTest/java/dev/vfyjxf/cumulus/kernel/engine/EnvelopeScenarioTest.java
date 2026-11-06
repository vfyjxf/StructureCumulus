package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.LocusFrame;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The derived envelope on realistic shapes: fusion void, AE2 CPU, PNC port, GT EBF ring. */
class EnvelopeScenarioTest {

    private static final NamespacedId envelope = NamespacedId.of("structurecumulus", "envelope");

    private static NamespacedId id(String path) {
        return TechFixtures.id(path);
    }

    @Test
    void fusionTorusEnvelopeIsCleanAtAHalfTurn() {
        Siting siting = TechFixtures.turn(LatticePoint.origin, Turn.half);
        TestWorld.View view = TechFixtures.view();
        fusion().fill(view, siting, Map.of('#', TechFixtures.fusionTorus));
        ClaimLedger ledger = TechFixtures.ledger();
        Blueprint blueprint = fusionBlueprint("envelope/fusion_clean");

        Bay derived = blueprint.bay(envelope);
        assertNotNull(derived, "the void is the envelope");
        assertEquals(93L, derived.cells().volume(), "the torus void inside a 5x5x5 closed world");
        assertEquals(125L, blueprint.reads().cells().volume(), "the read scope is the torus plus the envelope");

        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(blueprint, siting, view, ledger),
            "the torus and its envelope form"
        );
        assertEquals(32, formed.claims().rows().size(), "only the bound torus is claimed");
        assertEquals(32, TestWorld.rows(ledger).size(), "the envelope owns no row");
    }

    @Test
    void aCrystalInTheVoidFailsAtItsCell() {
        Siting siting = TechFixtures.turn(LatticePoint.origin, Turn.half);
        TestWorld.View view = TechFixtures.view();
        fusion().fill(view, siting, Map.of('#', TechFixtures.fusionTorus));
        LatticePoint crystal = LatticePoint.of(2, 0, 2);
        TechFixtures.put(view, siting, crystal, TechFixtures.crystal);
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures.settle(fusionBlueprint("envelope/fusion_crystal"), siting, view, ledger);

        assertEnvelopeFailureAt(result, TechFixtures.world(siting, crystal));
        assertEquals(0, TestWorld.rows(ledger).size(), "a failed check writes no row");
    }

    @Test
    void ae2CpuEnvelopeIsEmptyAndSilent() {
        Blueprint plain = cpuBlueprint("envelope/ae2", false);
        Blueprint enclosed = cpuBlueprint("envelope/ae2", true);
        assertEquals(plain, enclosed, "an empty envelope adds nothing");
        assertEquals(plain.plan(), enclosed.plan());
        assertEquals(plain.reads(), enclosed.reads());
        assertEquals(1, enclosed.bays().size());
        assertEquals(1, enclosed.checks().size());
        assertNull(enclosed.bay(envelope));

        Siting siting = TechFixtures.turn(LatticePoint.origin, Turn.clockwise);
        TestWorld.View view = TechFixtures.view();
        cpu().fill(view, siting, Map.of('#', TechFixtures.cpu));
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(enclosed, siting, view, ledger),
            "the CPU still forms"
        );
        assertEquals(8, formed.claims().rows().size());
    }

    @Test
    void pncDeclaredPortAcceptsABlock() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        refinery().fill(view, siting, refineryLegend());
        TechFixtures.put(view, siting, LatticePoint.of(1, 3, 0), TechFixtures.hatch);
        ClaimLedger ledger = TechFixtures.ledger();
        Blueprint blueprint = portBlueprint("envelope/pnc_port");
        assertEquals(2L, Objects.requireNonNull(blueprint.bay(envelope), "envelope").cells().volume());

        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(blueprint, siting, view, ledger),
            "a declared port cell is not the envelope"
        );
        assertEquals(13, formed.claims().rows().size(), "the port owns nothing");
    }

    @Test
    void pncUndeclaredTopBlockFails() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        refinery().fill(view, siting, refineryLegend());
        TechFixtures.put(view, siting, LatticePoint.of(0, 3, 1), TechFixtures.hatch);
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures.settle(portBlueprint("envelope/pnc_undeclared"), siting, view, ledger);

        assertEnvelopeFailureAt(result, TechFixtures.world(siting, LatticePoint.of(0, 3, 1)));
        assertEquals(0, TestWorld.rows(ledger).size(), "a failed check writes no row");
    }

    @Test
    void gtEbfWidenedBoundsAcceptADeclaredPortRing() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        ebf().fill(view, siting, ebfLegend());
        TechFixtures.put(view, siting, LatticePoint.of(-1, 0, -1), TechFixtures.hatch);
        ClaimLedger ledger = TechFixtures.ledger();
        Blueprint blueprint = widenedEbfBlueprint("envelope/gt_ring");
        assertEquals(48L, Objects.requireNonNull(blueprint.bay(envelope), "envelope").cells().volume());

        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(blueprint, siting, view, ledger),
            "the declared ring accepts the hatch"
        );
        assertEquals(36, formed.claims().rows().size(), "the ring owns nothing");
    }

    @Test
    void gtEbfHatchOutsideTheRingFails() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        ebf().fill(view, siting, ebfLegend());
        TechFixtures.put(view, siting, LatticePoint.of(-1, 1, -1), TechFixtures.hatch);
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures.settle(widenedEbfBlueprint("envelope/gt_outside_ring"), siting, view, ledger);

        assertEnvelopeFailureAt(result, TechFixtures.world(siting, LatticePoint.of(-1, 1, -1)));
        assertEquals(0, TestWorld.rows(ledger).size(), "a failed check writes no row");
    }

    @Test
    void envelopeOnlyBlueprintIsAdmittedWithNoRows() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        ClaimLedger ledger = TechFixtures.ledger();
        TestWorld.View view = TechFixtures.view();
        view.put(TestWorld.block(TestWorld.stone, LatticePoint.of(2, 0, 0)));
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(envelopeOnly("envelope/only_clean"), siting, view, ledger),
            "an empty snapshot closes the envelope and a block outside the bounds is invisible"
        );
        assertTrue(formed.claims().rows().isEmpty(), "the envelope owns nothing");
        assertEquals(0, TestWorld.rows(ledger).size());
        assertEquals(0L, ledger.revision(), "an empty plan releases an absent key, which does not bump");
        assertEquals(0L, formed.ledgerRevision(), "Formed carries the unchanged revision");
    }

    @Test
    void oneBlockInAnEnvelopeOnlyBlueprintFails() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        view.put(TestWorld.block(TestWorld.stone, LatticePoint.origin));
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures.settle(envelopeOnly("envelope/only_blocked"), siting, view, ledger);

        assertEnvelopeFailureAt(result, LatticePoint.origin);
        assertEquals(0, TestWorld.rows(ledger).size());
    }

    @Test
    void anEntityOverAnAirCellIsTransparent() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        view.putBody(TechFixtures.entity(TechFixtures.cow, LatticePoint.origin, Cuboid.of(LatticePoint.origin)));
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(envelopeOnly("envelope/only_entity"), siting, view, ledger),
            "a cow standing on an air cell does not open the envelope"
        );
        assertTrue(formed.claims().rows().isEmpty(), "the envelope owns nothing");
    }

    @Test
    void anEnvelopeStillJudgesTheBlockUnderAnEntity() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        view.put(TestWorld.block(TestWorld.stone, LatticePoint.origin));
        view.putBody(TechFixtures.entity(TechFixtures.cow, LatticePoint.origin, Cuboid.of(LatticePoint.origin)));
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures
                .settle(envelopeOnly("envelope/only_block_under_entity"), siting, view, ledger);

        assertEnvelopeFailureAt(result, LatticePoint.origin);
        assertEquals(0, TestWorld.rows(ledger).size());
    }

    @Test
    void envelopeEntitiesRejectsAnEntity() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        view.putBody(TechFixtures.entity(TechFixtures.cow, LatticePoint.origin, Cuboid.of(LatticePoint.origin)));
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures
                .settle(envelopeEntitiesOnly("envelope/only_entity_rejecting"), siting, view, ledger);

        assertEnvelopeFailureAt(result, LatticePoint.origin);
        assertEquals(0, TestWorld.rows(ledger).size());
    }

    @Test
    void envelopeEntitiesAddsTheEntityKindToReads() {
        Blueprint transparent = envelopeOnly("envelope/reads_off");
        Blueprint rejecting = envelopeEntitiesOnly("envelope/reads_on");

        assertFalse(
            transparent.reads().kinds().contains(OccupantKind.entity),
            "the default envelope captures no entity"
        );
        assertTrue(rejecting.reads().kinds().contains(OccupantKind.entity), "the switch adds the entity kind");
    }

    @Test
    void unreadableEnvelopeCellIsUndecided() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        view.refuse(LatticePoint.origin);
        MatchResult result = TechFixtures
                .check(envelopeOnly("envelope/unreadable"), siting, view, TechFixtures.emptyClaims());

        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.unreadable, undecided.reason());
        assertTrue(TechFixtures.hasCode(undecided, Codes.unreadableRead), "a refusal stays SC-OCC-0004");
        assertFalse(TechFixtures.hasCode(undecided, Codes.envelopeOpen), "a refusal is not a mismatch");
    }

    @Test
    void budgetExhaustionStopsTheEnvelopeCheck() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        MatchRequest request = MatchRequest.of(
            envelopeOnly("envelope/budget"),
            siting,
            new TestWorld.Lease(TechFixtures.view()),
            TechFixtures.instance,
            TechFixtures.owner,
            Budget.of(0, 0),
            TechFixtures.emptyClaims()
        );
        MatchResult result = MatchEngine.check(request);

        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.budgetExhausted, undecided.reason());
        assertTrue(TechFixtures.hasCode(undecided, Codes.budgetExhausted), "the budget gate is SC-ENG-0001");
    }

    private static void assertEnvelopeFailureAt(MatchResult result, LatticePoint cell) {
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result);
        assertTrue(TechFixtures.hasCode(failed, Codes.envelopeOpen), "the envelope is not closed");
        List<Finding> findings = failed.findings().ofCode(Codes.envelopeOpen.code());
        assertEquals(1, findings.size(), "one foreign occupant, one finding");
        Label primary = Objects.requireNonNull(findings.get(0).primaryLabel(), "primary label");
        assertEquals(LocusFrame.world, primary.locus().frame());
        assertTrue(primary.locus().cells().contains(cell), "the finding points at " + cell);
    }

    private static Blueprint envelopeOnly(String path) {
        return Blueprint.builder(id(path)).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).envelope().build();
    }

    private static Blueprint envelopeEntitiesOnly(String path) {
        return Blueprint.builder(id(path)).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).envelope().envelopeEntities().build();
    }

    private static Blueprint fusionBlueprint(String path) {
        Bay torus = TechFixtures.bay(
            id(path + "/torus_bay"),
            fusion().cells('#'),
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        return Blueprint.builder(id(path)).bounds(Cuboid.of(0, 0, 0, 5, 5, 5))
                .check(TechFixtures.eachCell(id(path + "/torus"), torus, Expectations.block(TechFixtures.fusionTorus)))
                .envelope().build();
    }

    private static Blueprint cpuBlueprint(String path, boolean envelope) {
        Bay bay = TechFixtures
                .bay(id(path + "/bay"), cpu().cells('#'), Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        BlueprintBuilder builder = Blueprint.builder(id(path)).bounds(cpu().bounds())
                .check(TechFixtures.eachCell(id(path + "/cpu"), bay, Expectations.block(TechFixtures.cpu)));
        if (envelope) {
            builder.envelope();
        }
        return builder.build();
    }

    private static Blueprint portBlueprint(String path) {
        Bay body = TechFixtures.bay(
            id(path + "/body_bay"),
            refinery().cells('#'),
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay outputs = TechFixtures.bay(
            id(path + "/output_bay"),
            refinery().cells('o'),
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay controller = TechFixtures.bay(
            id(path + "/controller_bay"),
            refinery().cells('c'),
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay port = TechFixtures.bay(
            id(path + "/port_bay"),
            CuboidSet.of(Cuboid.of(1, 3, 0, 2, 4, 1)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        return Blueprint.builder(id(path)).bounds(refinery().bounds())
                .check(TechFixtures.eachCell(id(path + "/body"), body, Expectations.block(TechFixtures.refineryCasing)))
                .check(
                    TechFixtures
                            .eachCell(id(path + "/outputs"), outputs, Expectations.block(TechFixtures.refineryOutput))
                )
                .check(
                    TechFixtures
                            .eachCell(id(path + "/controller"), controller, Expectations.block(TechFixtures.controller))
                ).check(TechFixtures.eachCell(id(path + "/port"), port, Expectations.anyOccupant())).envelope().build();
    }

    private static Blueprint widenedEbfBlueprint(String path) {
        Bay casingBay = TechFixtures.bay(
            id(path + "/casing_bay"),
            ebf().cells('#'),
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay coilBay = TechFixtures.bay(
            id(path + "/coil_bay"),
            ebf().cells('a', 'b'),
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay ring = TechFixtures
                .bay(id(path + "/port_ring"), ring(), Coverage.everyCell, Footprint.none(), Sharing.shared);
        return Blueprint.builder(id(path)).bounds(widened())
                .check(TechFixtures.eachCell(id(path + "/casing"), casingBay, Expectations.block(TechFixtures.casing)))
                .check(TechFixtures.eachCell(id(path + "/coils"), coilBay, Expectations.anyOccupant()))
                .check(TechFixtures.eachCell(id(path + "/ring"), ring, Expectations.anyOccupant())).envelope().build();
    }

    private static CuboidSet ring() {
        return CuboidSet.of(
            Cuboid.of(-1, 0, -1, 0, 1, 4),
            Cuboid.of(3, 0, -1, 4, 1, 4),
            Cuboid.of(-1, 0, -1, 4, 1, 0),
            Cuboid.of(-1, 0, 3, 4, 1, 4)
        );
    }

    private static Cuboid widened() {
        return Cuboid.of(-1, 0, -1, 4, 4, 4);
    }

    private static TechFixtures.Pattern fusion() {
        return TechFixtures.pattern("#####/#...#/#...#/#...#/#####", "#####/#...#/#...#/#...#/#####");
    }

    private static TechFixtures.Pattern cpu() {
        return TechFixtures.pattern("##/##", "##/##");
    }

    private static TechFixtures.Pattern refinery() {
        return TechFixtures.pattern("##/##", "##/##", "oo/oo", "c./..");
    }

    private static TechFixtures.Pattern ebf() {
        return TechFixtures.pattern("aab/aba/baa", "###/###/###", "###/###/###", "###/###/###");
    }

    private static Map<Character, NamespacedId> refineryLegend() {
        return Map.of('#', TechFixtures.refineryCasing, 'o', TechFixtures.refineryOutput, 'c', TechFixtures.controller);
    }

    private static Map<Character, NamespacedId> ebfLegend() {
        return Map.of('#', TechFixtures.casing, 'a', TechFixtures.hotCoil, 'b', TechFixtures.coldCoil);
    }
}
