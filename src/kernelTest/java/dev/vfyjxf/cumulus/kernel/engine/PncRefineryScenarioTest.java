package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
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
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PneumaticCraft refinery geometry: two body layers, four outputs, a controller on top and its edge cases. */
class PncRefineryScenarioTest {

    private static final char body = '#';
    private static final char output = 'o';
    private static final char controller = 'c';

    private static final TechFixtures.Pattern refinery = TechFixtures.pattern("##/##", "##/##", "oo/oo", "c./..");
    private static final CuboidSet bodyCells = refinery.cells(body);
    private static final CuboidSet outputCells = refinery.cells(output);
    private static final CuboidSet controllerCell = refinery.cells(controller);
    private static final CuboidSet allCells = refinery.cells(body, output, controller);
    private static final Map<Character, NamespacedId> legend = Map.of(
        body,
        TechFixtures.refineryCasing,
        output,
        TechFixtures.refineryOutput,
        controller,
        TechFixtures.controller
    );

    @Test
    void fourOutputsWithAControllerOnTopFormAtAHalfTurn() {
        assertEquals(12L, bodyCells.volume() + outputCells.volume(), "two body layers plus four outputs");
        assertEquals(4, outputCells.volume(), "the top layer has four outputs");
        Siting siting = TechFixtures.turn(LatticePoint.origin, Turn.half);
        TestWorld.View view = TechFixtures.view();
        refinery.fill(view, siting, legend);
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(standardBlueprint("pnc/refinery_half"), siting, view, ledger),
            "the refinery forms at a half turn"
        );
        assertEquals(13, formed.claims().rows().size(), "twelve body cells plus the controller");
        Set<LatticeKey> expected = TechFixtures.worldKeys(siting, allCells);
        assertEquals(expected, new LinkedHashSet<>(TechFixtures.rowKeys(formed)), "the claims are the sited refinery");
        assertEquals(13, TestWorld.rows(ledger).size(), "the ledger holds the refinery");
    }

    @Test
    void aMissingOutputFails() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        refinery.fill(view, siting, legend);
        LatticePoint missing = LatticePoint.of(1, 2, 1);
        view.air(TechFixtures.world(siting, missing));
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures
                .settle(standardBlueprint("pnc/refinery_missing_output"), siting, view, ledger);
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result, "one output cell is air");
        assertTrue(TechFixtures.hasCode(failed, Codes.expected), "the missing output is SC-EXP-0001");
        assertEquals(0, TestWorld.rows(ledger).size(), "a failed check writes no rows");
    }

    @Test
    void twoOutputsAndAirPaddingUseBoxVersusBound() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = paddedView(siting);
        ClaimLedger boxLedger = TechFixtures.ledger();
        MatchResult.Formed boxed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(paddedBlueprint("pnc/refinery_box", Footprint.box()), siting, view, boxLedger),
            "the air padding is inside a Box footprint"
        );
        assertEquals(12, boxed.claims().rows().size(), "Box owns eight body cells and four padded outputs");
        assertEquals(12, TestWorld.rows(boxLedger).size(), "the Box ledger holds twelve rows");

        ClaimLedger boundLedger = TechFixtures.ledger();
        MatchResult.Formed bounded = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(paddedBlueprint("pnc/refinery_bound", Footprint.bound()), siting, view, boundLedger),
            "a Bound footprint skips the air padding"
        );
        assertEquals(10, bounded.claims().rows().size(), "Bound owns eight body cells and two real outputs");
        assertEquals(10, TestWorld.rows(boundLedger).size(), "the Bound ledger holds ten rows");
    }

    @Test
    void aFifthOutputOutsideTheBayIsAdmitted() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        refinery.fill(view, siting, legend);
        TechFixtures.put(view, siting, LatticePoint.of(2, 2, 0), TechFixtures.refineryOutput);
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(fifthOutputBlueprint("pnc/refinery_fifth"), siting, view, ledger),
            "the fifth output sits outside every bay"
        );
        assertEquals(13, formed.claims().rows().size(), "the fifth output is not claimed");
        assertTrue(
            TechFixtures.rowKeys(formed).stream().noneMatch(key -> key.equals(LatticePoint.of(2, 2, 0).key())),
            "the outside output stays outside the claim"
        );
    }

    @Test
    void twoControllersAreAdmittedByPerCellChecks() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = ambiguousControllerView(siting);
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(ambiguousControllerBlueprint("pnc/refinery_two_controllers"), siting, view, ledger),
            "per-cell checks cannot reject two controllers"
        );
        assertEquals(14, formed.claims().rows().size(), "both controllers are admitted and claimed");
    }

    @Test
    void aHostCountRejectsTwoControllers() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = ambiguousControllerView(siting);
        ClaimLedger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures
                .settle(countedControllerBlueprint("pnc/refinery_counted_controller"), siting, view, ledger);
        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            result,
            "a host-side uniqueness test sees the ambiguity"
        );
        assertTrue(TechFixtures.hasCode(failed, Codes.expected), "the ambiguity is SC-EXP-0001");
        assertEquals(0, TestWorld.rows(ledger).size(), "an ambiguous controller writes no claim");
    }

    @Test
    void anExclusiveRefineryRejectsAnotherOwner() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        refinery.fill(view, siting, legend);
        ClaimLedger ledger = TechFixtures.ledger();
        assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(standardBlueprint("pnc/refinery_conflict"), siting, view, ledger),
            "the first owner forms"
        );
        long revision = ledger.revision();
        MatchResult result = TechFixtures.settle(
            standardBlueprint("pnc/refinery_conflict"),
            siting,
            view,
            ledger,
            TechFixtures.rival,
            TechFixtures.rivalInstance
        );
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result, "the rival is rejected");
        assertTrue(TechFixtures.hasCode(failed, Codes.claimConflict), "the conflict is SC-CLM-0001");
        assertEquals(13, TestWorld.rows(ledger).size(), "the conflict leaves the incumbent rows untouched");
        assertEquals(revision, ledger.revision(), "the conflict does not move the revision");
    }

    private static Blueprint standardBlueprint(String path) {
        Bay bodyBay = TechFixtures.bay(
            TechFixtures.id(path + "/body_bay"),
            bodyCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay outputBay = TechFixtures.bay(
            TechFixtures.id(path + "/output_bay"),
            outputCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay controllerBay = TechFixtures.bay(
            TechFixtures.id(path + "/controller_bay"),
            controllerCell,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        return Blueprint.builder(TechFixtures.id(path)).bounds(refinery.bounds()).check(
            TechFixtures
                    .eachCell(TechFixtures.id(path + "/body"), bodyBay, Expectations.block(TechFixtures.refineryCasing))
        ).check(
            TechFixtures.eachCell(
                TechFixtures.id(path + "/outputs"),
                outputBay,
                Expectations.block(TechFixtures.refineryOutput)
            )
        ).check(
            TechFixtures.eachCell(
                TechFixtures.id(path + "/controller"),
                controllerBay,
                Expectations.block(TechFixtures.controller)
            )
        ).build();
    }

    private static Blueprint paddedBlueprint(String path, Footprint outputFootprint) {
        Bay bodyBay = TechFixtures.bay(
            TechFixtures.id(path + "/body_bay"),
            bodyCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay outputBay = TechFixtures.bay(
            TechFixtures.id(path + "/output_bay"),
            outputCells,
            Coverage.everyCell,
            outputFootprint,
            Sharing.exclusive
        );
        List<Check<?>> outputChecks = List.of(
            TechFixtures.occupant(
                TechFixtures.id(path + "/output_0"),
                outputBay,
                LatticePoint.of(0, 2, 0),
                Expectations.block(TechFixtures.refineryOutput)
            ),
            TechFixtures.occupant(
                TechFixtures.id(path + "/output_1"),
                outputBay,
                LatticePoint.of(1, 2, 0),
                Expectations.block(TechFixtures.refineryOutput)
            ),
            TechFixtures.occupant(
                TechFixtures.id(path + "/padding_0"),
                outputBay,
                LatticePoint.of(0, 2, 1),
                Expectations.air()
            ),
            TechFixtures.occupant(
                TechFixtures.id(path + "/padding_1"),
                outputBay,
                LatticePoint.of(1, 2, 1),
                Expectations.air()
            )
        );
        return Blueprint.builder(TechFixtures.id(path)).bounds(refinery.bounds()).check(
            TechFixtures
                    .eachCell(TechFixtures.id(path + "/body"), bodyBay, Expectations.block(TechFixtures.refineryCasing))
        ).checks(outputChecks).build();
    }

    private static Blueprint fifthOutputBlueprint(String path) {
        return Blueprint.builder(TechFixtures.id(path)).bounds(TechFixtures.box(0, 0, 0, 3, 4, 2))
                .check(
                    TechFixtures.eachCell(
                        TechFixtures.id(path + "/body"),
                        TechFixtures.bay(
                            TechFixtures.id(path + "/body_bay"),
                            bodyCells,
                            Coverage.everyCell,
                            Footprint.bound(),
                            Sharing.exclusive
                        ),
                        Expectations.block(TechFixtures.refineryCasing)
                    )
                )
                .check(
                    TechFixtures.eachCell(
                        TechFixtures.id(path + "/outputs"),
                        TechFixtures.bay(
                            TechFixtures.id(path + "/output_bay"),
                            outputCells,
                            Coverage.everyCell,
                            Footprint.bound(),
                            Sharing.exclusive
                        ),
                        Expectations.block(TechFixtures.refineryOutput)
                    )
                )
                .check(
                    TechFixtures.eachCell(
                        TechFixtures.id(path + "/controller"),
                        TechFixtures.bay(
                            TechFixtures.id(path + "/controller_bay"),
                            controllerCell,
                            Coverage.everyCell,
                            Footprint.bound(),
                            Sharing.exclusive
                        ),
                        Expectations.block(TechFixtures.controller)
                    )
                ).build();
    }

    private static Blueprint ambiguousControllerBlueprint(String path) {
        CuboidSet candidates = TechFixtures
                .cells(TechFixtures.cell(LatticePoint.of(0, 3, 0)), TechFixtures.cell(LatticePoint.of(1, 3, 0)));
        Bay controllerBay = TechFixtures.bay(
            TechFixtures.id(path + "/controller_bay"),
            candidates,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay bodyBay = TechFixtures.bay(
            TechFixtures.id(path + "/body_bay"),
            bodyCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay outputBay = TechFixtures.bay(
            TechFixtures.id(path + "/output_bay"),
            outputCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        return Blueprint.builder(TechFixtures.id(path)).bounds(refinery.bounds()).check(
            TechFixtures
                    .eachCell(TechFixtures.id(path + "/body"), bodyBay, Expectations.block(TechFixtures.refineryCasing))
        ).check(
            TechFixtures.eachCell(
                TechFixtures.id(path + "/outputs"),
                outputBay,
                Expectations.block(TechFixtures.refineryOutput)
            )
        ).check(
            TechFixtures.eachCell(
                TechFixtures.id(path + "/controllers"),
                controllerBay,
                Expectations.block(TechFixtures.controller)
            )
        ).build();
    }

    private static Blueprint countedControllerBlueprint(String path) {
        CuboidSet candidates = TechFixtures
                .cells(TechFixtures.cell(LatticePoint.of(0, 3, 0)), TechFixtures.cell(LatticePoint.of(1, 3, 0)));
        Bay controllerBay = TechFixtures.bay(
            TechFixtures.id(path + "/controller_bay"),
            candidates,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay bodyBay = TechFixtures.bay(
            TechFixtures.id(path + "/body_bay"),
            bodyCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay outputBay = TechFixtures.bay(
            TechFixtures.id(path + "/output_bay"),
            outputCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Check<BayView> controllerCheck = Check.of(
            TechFixtures.id(path + "/one_controller"),
            controllerBay,
            Focuses.bay(),
            Expectations.custom(BayView.class, ExpectationMeta.anonymous(), ctx -> {
                int count = ctx.subject().occupants(OccupantFilter.ofType(TechFixtures.controller)).size();
                if (count == 1) {
                    return Reply.match();
                }
                Finding finding = Finding.builder(Codes.expected).phase(ctx.phase())
                        .message("expected exactly one controller, found " + count)
                        .label(Label.primary(ctx.locus(), "ambiguous controller")).build();
                return Reply.mismatch(finding);
            })
        );
        return Blueprint.builder(TechFixtures.id(path)).bounds(refinery.bounds()).check(
            TechFixtures
                    .eachCell(TechFixtures.id(path + "/body"), bodyBay, Expectations.block(TechFixtures.refineryCasing))
        ).check(
            TechFixtures.eachCell(
                TechFixtures.id(path + "/outputs"),
                outputBay,
                Expectations.block(TechFixtures.refineryOutput)
            )
        ).check(controllerCheck).build();
    }

    private static TestWorld.View paddedView(Siting siting) {
        TestWorld.View view = TechFixtures.view();
        TechFixtures.fill(view, siting, bodyCells, TechFixtures.refineryCasing);
        TechFixtures.put(view, siting, LatticePoint.of(0, 2, 0), TechFixtures.refineryOutput);
        TechFixtures.put(view, siting, LatticePoint.of(1, 2, 0), TechFixtures.refineryOutput);
        return view;
    }

    private static TestWorld.View ambiguousControllerView(Siting siting) {
        TestWorld.View view = TechFixtures.view();
        refinery.fill(view, siting, legend);
        TechFixtures.put(view, siting, LatticePoint.of(1, 3, 0), TechFixtures.controller);
        return view;
    }
}
