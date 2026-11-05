package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Terra Plate: a three-by-three two-material base with one plate above, ten claimed cells. */
class TerraPlateScenarioTest {

    private static final NamespacedId baseOuter = MagicFixtures.id("terra/opaque_base_outer");
    private static final NamespacedId baseInner = MagicFixtures.id("terra/opaque_base_inner");
    private static final NamespacedId plate = MagicFixtures.id("terra/opaque_plate");
    private static final NamespacedId extra = MagicFixtures.id("terra/opaque_extra");

    private static final LatticePoint center = LatticePoint.of(1, 0, 1);
    private static final LatticePoint plateCell = LatticePoint.of(1, 1, 1);
    private static final CuboidSet baseCells = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 1, 3));
    private static final CuboidSet bayCells = baseCells.union(CuboidSet.of(Cuboid.of(plateCell)));
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 3, 2, 3);

    @Test
    void tenDeclaredCellsFormAndClaimTenRows() {
        Blueprint blueprint = terraBlueprint();
        assertEquals(10L, blueprint.bayCells().volume(), "the declaration has ten cells");
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(
                blueprint,
                MagicFixtures.identity(LatticePoint.origin),
                terraView(MagicFixtures.identity(LatticePoint.origin)),
                ledger
            )
        );
        assertEquals(10, formed.claims().rows().size(), "ten claim rows");
        assertEquals(10, TestWorld.rows(ledger).size(), "the ledger holds ten rows");
        assertEquals(1L, ledger.revision(), "one settlement revision");
        assertTrue(
            MagicFixtures.rowKeys(formed).contains(MagicFixtures.world(identity(), plateCell).key()),
            "the plate cell is claimed"
        );
    }

    @Test
    void fourTurnsKeepTenClaimRowsAndRotateTheCells() {
        LatticePoint origin = LatticePoint.of(8, 64, 8);
        Turn[] turns = {Turn.none, Turn.clockwise, Turn.half, Turn.counterclockwise};
        for (Turn turn : turns) {
            Siting siting = MagicFixtures.turn(origin, turn);
            ClaimLedger ledger = MagicFixtures.ledger();
            MatchResult.Formed formed = assertInstanceOf(
                MatchResult.Formed.class,
                MagicFixtures.settle(terraBlueprint(), siting, terraView(siting), ledger),
                "turn " + turn
            );
            assertEquals(10, formed.claims().rows().size(), "turn " + turn + " claims ten rows");
            Set<LatticeKey> expected = MagicFixtures.worldKeys(siting, bayCells);
            assertEquals(
                expected,
                Set.copyOf(MagicFixtures.rowKeys(formed)),
                "turn " + turn + " claims the rotated cells"
            );
            assertEquals(10, TestWorld.rows(ledger).size(), "turn " + turn + " writes ten rows");
        }
    }

    @Test
    void aMissingDeclaredCellFailsAndWritesNothing() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.View view = terraView(siting);
        MagicFixtures.air(view, siting, center);
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchResult result = MagicFixtures.settle(terraBlueprint(), siting, view, ledger);
        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            result,
            "the missing base cell is a mismatch"
        );
        assertTrue(MagicFixtures.hasCode(failed, Codes.expected), "the mismatch carries SC-EXP-0001");
        assertEquals(0L, ledger.revision(), "a failed check writes no revision");
        assertEquals(0, TestWorld.rows(ledger).size(), "a failed check writes no rows");
    }

    @Test
    void anExtraBlockAboveThePlateIsIgnored() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.View view = terraView(siting);
        MagicFixtures.put(view, siting, LatticePoint.of(1, 2, 1), extra);
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(terraBlueprint(), siting, view, ledger),
            "outside the bounds the extra block is not judged"
        );
        assertEquals(10, formed.claims().rows().size(), "the extra block does not add a claim");
        assertEquals(10, TestWorld.rows(ledger).size(), "the ledger holds ten rows");
        assertTrue(
            !MagicFixtures.rowKeys(formed).contains(MagicFixtures.world(siting, LatticePoint.of(1, 2, 1)).key()),
            "the extra block is not claimed"
        );
    }

    @Test
    void anIncumbentExclusiveOwnerMakesTheCheckFail() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.View view = terraView(siting);
        ClaimLedger ledger = MagicFixtures.ledger();
        assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(terraBlueprint(), siting, view, ledger),
            "the first owner settles"
        );
        ClaimView claims = ledger.read(MagicFixtures.worldBounds(terraBlueprint(), siting));
        MatchResult result = MagicFixtures
                .check(terraBlueprint(), siting, view, claims, MagicFixtures.rival, MagicFixtures.rivalInstance);
        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            result,
            "the exclusive incumbent conflicts"
        );
        assertTrue(MagicFixtures.hasCode(failed, Codes.claimConflict), "the conflict carries SC-CLM-0001");
        assertEquals(1L, ledger.revision(), "the failed check does not move the revision");
        assertEquals(10, TestWorld.rows(ledger).size(), "the failed check does not write rows");
        assertTrue(
            TestWorld.rows(ledger).stream().allMatch(row -> row.instance().equals(MagicFixtures.instance)),
            "the incumbent instance holds the rows"
        );
    }

    private static Siting identity() {
        return MagicFixtures.identity(LatticePoint.origin);
    }

    private static Blueprint terraBlueprint() {
        Bay bay = MagicFixtures
                .bay(MagicFixtures.id("terra/bay"), bayCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id("terra/template")).bounds(bounds);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                LatticePoint cell = LatticePoint.of(x, 0, z);
                NamespacedId type = cell.equals(center) ? baseInner : baseOuter;
                builder.check(
                    MagicFixtures.occupant(
                        MagicFixtures.id("terra/base_" + x + "_" + z),
                        bay,
                        cell,
                        Expectations.block(type)
                    )
                );
            }
        }
        builder.check(
            MagicFixtures.occupant(MagicFixtures.id("terra/plate_check"), bay, plateCell, Expectations.block(plate))
        );
        return builder.build();
    }

    private static TestWorld.View terraView(Siting siting) {
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.fill(view, siting, baseCells, baseOuter);
        MagicFixtures.put(view, siting, center, baseInner);
        MagicFixtures.put(view, siting, plateCell, plate);
        return view;
    }
}
