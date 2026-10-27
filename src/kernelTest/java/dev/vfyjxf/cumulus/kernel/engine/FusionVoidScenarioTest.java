package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
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
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mekanism-style fusion torus: a hollow ring, its air void and the blocks hidden in that void. */
class FusionVoidScenarioTest {

    private static final char torus = '#';
    private static final char voidKind = '.';

    private static final TechFixtures.Pattern fusion = TechFixtures
            .pattern("#####/#...#/#...#/#...#/#####", "#####/#...#/#...#/#...#/#####");
    private static final CuboidSet torusCells = fusion.cells(torus);
    private static final CuboidSet voidCells = fusion.cells(voidKind);
    private static final LatticePoint voidCenter = LatticePoint.of(2, 0, 2);
    private static final Map<Character, NamespacedId> legend = Map.of(torus, TechFixtures.fusionTorus);

    @Test
    void torusAndVoidFormTogetherAtAHalfTurn() {
        assertEquals(32L, torusCells.volume(), "the torus has thirty-two cells");
        assertEquals(18L, voidCells.volume(), "the void has eighteen air cells");
        assertEquals(50L, fusion.bounds().volume(), "the fusion box has fifty cells");
        Siting siting = TechFixtures.turn(LatticePoint.origin, Turn.half);
        TestWorld.View view = TechFixtures.view();
        fusion.fill(view, siting, legend);
        TestWorld.Ledger ledger = TechFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(combinedBlueprint("fusion/torus_and_void"), siting, view, ledger),
            "the torus and its air void both form"
        );
        assertEquals(50, formed.claims().rows().size(), "the torus and the void are claimed");
        Set<LatticeKey> expected = TechFixtures.worldKeys(siting, fusion.cells(torus, voidKind));
        assertEquals(
            expected,
            new LinkedHashSet<>(TechFixtures.rowKeys(formed)),
            "the claims are the sited torus and void"
        );
        assertEquals(50, ledger.all().size(), "the ledger holds the torus and the void");
    }

    @Test
    void anExtraBlockInAVoidHolePassesATorusOnlyBlueprint() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        fusion.fill(view, siting, legend);
        TechFixtures.put(view, siting, voidCenter, TechFixtures.crystal);
        TestWorld.Ledger ledger = TechFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            TechFixtures.settle(torusOnlyBlueprint("fusion/torus_only_hole"), siting, view, ledger),
            "a torus-only bay never reads the void hole"
        );
        assertEquals(32, formed.claims().rows().size(), "the hidden crystal is not claimed");
        assertEquals(
            32L,
            torusOnlyBlueprint("fusion/torus_only_hole_read").reads().cells().volume(),
            "the read scope is torus-only"
        );
        assertTrue(
            TechFixtures.rowKeys(formed).stream().noneMatch(key -> key.equals(voidCenter.key())),
            "the void hole stays outside the claim"
        );
    }

    @Test
    void theVoidAirBayFailsOnAnExtraBlock() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        fusion.fill(view, siting, legend);
        TechFixtures.put(view, siting, voidCenter, TechFixtures.crystal);
        TestWorld.Ledger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures.settle(combinedBlueprint("fusion/torus_void_occupied"), siting, view, ledger);
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result, "the void bay sees the crystal");
        assertTrue(TechFixtures.hasCode(failed, Codes.expected), "the occupied void is SC-EXP-0001");
        assertEquals(0, ledger.all().size(), "a failed check writes no rows");
    }

    @Test
    void aMissingTorusBlockFails() {
        Siting siting = TechFixtures.identity(LatticePoint.origin);
        TestWorld.View view = TechFixtures.view();
        fusion.fill(view, siting, legend);
        view.air(TechFixtures.world(siting, LatticePoint.of(0, 0, 0)));
        TestWorld.Ledger ledger = TechFixtures.ledger();
        MatchResult result = TechFixtures.settle(torusOnlyBlueprint("fusion/torus_missing"), siting, view, ledger);
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result, "one torus cell is air");
        assertTrue(TechFixtures.hasCode(failed, Codes.expected), "the missing torus cell is SC-EXP-0001");
        assertEquals(0, ledger.all().size(), "a failed check writes no rows");
    }

    private static Blueprint torusOnlyBlueprint(String path) {
        Bay torusBay = TechFixtures.bay(
            TechFixtures.id(path + "/torus_bay"),
            torusCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Check<BayView> torusCheck = TechFixtures
                .eachCell(TechFixtures.id(path + "/torus"), torusBay, Expectations.block(TechFixtures.fusionTorus));
        return Blueprint.builder(TechFixtures.id(path)).bounds(fusion.bounds()).check(torusCheck).build();
    }

    private static Blueprint combinedBlueprint(String path) {
        Bay torusBay = TechFixtures.bay(
            TechFixtures.id(path + "/torus_bay"),
            torusCells,
            Coverage.everyCell,
            Footprint.bound(),
            Sharing.exclusive
        );
        Bay voidBay = TechFixtures.bay(
            TechFixtures.id(path + "/void_bay"),
            voidCells,
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(TechFixtures.id(path)).bounds(fusion.bounds()).check(
            TechFixtures
                    .eachCell(TechFixtures.id(path + "/torus"), torusBay, Expectations.block(TechFixtures.fusionTorus))
        ).check(TechFixtures.eachCell(TechFixtures.id(path + "/void"), voidBay, Expectations.air())).build();
    }
}
