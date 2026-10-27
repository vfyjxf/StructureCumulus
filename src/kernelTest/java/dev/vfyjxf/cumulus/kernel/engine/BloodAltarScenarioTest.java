package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Blood Altar: a tier II prefix, a tier III prefix, any-rune slots and a NOTAIR probe. */
class BloodAltarScenarioTest {

    private static final NamespacedId core = MagicFixtures.id("blood/opaque_core");
    private static final NamespacedId runeA = MagicFixtures.id("blood/opaque_rune_a");
    private static final NamespacedId runeB = MagicFixtures.id("blood/opaque_rune_b");
    private static final NamespacedId runeC = MagicFixtures.id("blood/opaque_rune_c");
    private static final NamespacedId water = MagicFixtures.mc("water");

    private static final LatticePoint originCell = LatticePoint.origin;
    private static final LatticePoint west = LatticePoint.of(-1, 0, 0);
    private static final LatticePoint east = LatticePoint.of(1, 0, 0);
    private static final LatticePoint north = LatticePoint.of(0, 0, -1);
    private static final LatticePoint south = LatticePoint.of(0, 0, 1);
    private static final LatticePoint northWest = LatticePoint.of(-1, 0, -1);
    private static final LatticePoint northEast = LatticePoint.of(1, 0, -1);
    private static final LatticePoint southWest = LatticePoint.of(-1, 0, 1);
    private static final LatticePoint southEast = LatticePoint.of(1, 0, 1);
    private static final LatticePoint notAir = LatticePoint.of(0, 1, 0);

    private static final CuboidSet prefixCells = MagicFixtures.cellsAt(originCell, west, east, north, south);
    private static final CuboidSet tierThreeCells = MagicFixtures
            .cellsAt(originCell, west, east, north, south, northWest, northEast, southWest, southEast);
    private static final CuboidSet notAirCells = prefixCells.union(MagicFixtures.cellsAt(notAir));
    private static final Cuboid bounds = Cuboid.of(-1, 0, -1, 2, 2, 2);

    @Test
    void tierTwoPrefixFormsFiveClaimRows() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.Ledger ledger = MagicFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(prefixBlueprint("blood/tier_two", Footprint.box()), siting, prefixView(siting), ledger)
        );
        assertEquals(5, formed.claims().rows().size(), "the tier II prefix claims five cells");
        assertEquals(5, ledger.all().size(), "the ledger holds five rows");
    }

    @Test
    void tierThreePrefixRequiresTheTierThreeExtension() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.Ledger ledger = MagicFixtures.ledger();
        MatchResult missing = MagicFixtures
                .settle(tierThreeBlueprint("blood/tier_three_missing"), siting, prefixView(siting), ledger);
        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            missing,
            "the tier III extension is missing"
        );
        assertTrue(MagicFixtures.hasCode(failed, Codes.expected), "the missing extension is SC-EXP-0001");
        assertEquals(0, ledger.all().size(), "the failed tier III check writes nothing");

        TestWorld.Ledger fullLedger = MagicFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(tierThreeBlueprint("blood/tier_three_full"), siting, tierThreeView(siting), fullLedger)
        );
        assertEquals(9, formed.claims().rows().size(), "the tier III altar claims nine cells");
        assertEquals(9, fullLedger.all().size(), "the ledger holds nine rows");
    }

    @Test
    void theAnyRuneSetAcceptsDifferentRuneTypes() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.put(view, siting, originCell, core);
        MagicFixtures.put(view, siting, west, runeA);
        MagicFixtures.put(view, siting, east, runeB);
        MagicFixtures.put(view, siting, north, runeC);
        MagicFixtures.put(view, siting, south, runeA);
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(
                prefixBlueprint("blood/tier_two_mixed", Footprint.box()),
                siting,
                view,
                MagicFixtures.ledger()
            )
        );
        assertEquals(5, formed.claims().rows().size(), "mixed rune types satisfy the set");
    }

    @Test
    void declaredAndBoxFootprintsOwnDifferentCells() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        ViewPair box = new ViewPair(prefixView(siting), MagicFixtures.ledger());
        MatchResult.Formed boxed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(footprintBlueprint("blood/box_bay", Footprint.box()), siting, box.view, box.ledger)
        );
        assertEquals(9, boxed.claims().rows().size(), "Box owns the nine bay cells, including the air corners");

        ViewPair declared = new ViewPair(prefixView(siting), MagicFixtures.ledger());
        MatchResult.Formed declaredFormed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(
                footprintBlueprint("blood/declared_bay", Footprint.declared(prefixCells)),
                siting,
                declared.view,
                declared.ledger
            )
        );
        assertEquals(5, declaredFormed.claims().rows().size(), "Declared owns only the rune and core cells");
    }

    @Test
    @DisplayName("known false positive: anyOccupant admits water in a NOTAIR slot")
    void waterInANotAirSlotIsAdmittedAsAKnownFalsePositive() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.View view = prefixView(siting);
        MagicFixtures.put(view, siting, notAir, water);
        MatchResult checked = MagicFixtures
                .check(notAirBlueprint("blood/not_air_water"), siting, view, new ClaimView(List.of(), 0, true));
        assertInstanceOf(
            MatchResult.Admitted.class,
            checked,
            "known gap: anyOccupant has no fluid predicate, so water satisfies a NOTAIR slot"
        );
    }

    private record ViewPair(TestWorld.View view, TestWorld.Ledger ledger) {}

    private static Blueprint footprintBlueprint(String path, Footprint footprint) {
        CuboidSet cells = CuboidSet.of(Cuboid.of(-1, 0, -1, 2, 1, 2));
        Bay bay = MagicFixtures
                .bay(MagicFixtures.id(path + "_bay"), cells, Coverage.everyCell, footprint, Sharing.exclusive);
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id(path)).bounds(bounds);
        builder.check(
            MagicFixtures.occupant(MagicFixtures.id(path + "_core"), bay, originCell, Expectations.block(core))
        );
        for (LatticePoint cell : List.of(west, east, north, south)) {
            builder.check(
                MagicFixtures.occupant(
                    MagicFixtures.id(path + "_rune_" + cell.x() + "_" + cell.z()),
                    bay,
                    cell,
                    Expectations.anyOf(Expectations.block(runeA), Expectations.block(runeB), Expectations.block(runeC))
                )
            );
        }
        for (LatticePoint cell : List.of(northWest, northEast, southWest, southEast)) {
            builder.check(
                MagicFixtures.occupant(
                    MagicFixtures.id(path + "_air_" + cell.x() + "_" + cell.z()),
                    bay,
                    cell,
                    Expectations.air()
                )
            );
        }
        return builder.build();
    }

    private static Blueprint prefixBlueprint(String path, Footprint footprint) {
        Bay bay = MagicFixtures
                .bay(MagicFixtures.id(path + "_bay"), prefixCells, Coverage.everyCell, footprint, Sharing.exclusive);
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id(path)).bounds(bounds);
        builder.check(
            MagicFixtures.occupant(MagicFixtures.id(path + "_core"), bay, originCell, Expectations.block(core))
        );
        for (LatticePoint cell : List.of(west, east, north, south)) {
            builder.check(
                MagicFixtures.occupant(
                    MagicFixtures.id(path + "_rune_" + cell.x() + "_" + cell.z()),
                    bay,
                    cell,
                    Expectations.anyOf(Expectations.block(runeA), Expectations.block(runeB), Expectations.block(runeC))
                )
            );
        }
        return builder.build();
    }

    private static Blueprint tierThreeBlueprint(String path) {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id(path + "_bay"),
            tierThreeCells,
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id(path)).bounds(bounds);
        builder.check(
            MagicFixtures.occupant(MagicFixtures.id(path + "_core"), bay, originCell, Expectations.block(core))
        );
        for (LatticePoint cell : List.of(west, east, north, south, northWest, northEast, southWest, southEast)) {
            builder.check(
                MagicFixtures.occupant(
                    MagicFixtures.id(path + "_rune_" + cell.x() + "_" + cell.z()),
                    bay,
                    cell,
                    Expectations.anyOf(Expectations.block(runeA), Expectations.block(runeB), Expectations.block(runeC))
                )
            );
        }
        return builder.build();
    }

    private static Blueprint notAirBlueprint(String path) {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id(path + "_bay"),
            notAirCells,
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id(path)).bounds(bounds);
        builder.check(
            MagicFixtures.occupant(MagicFixtures.id(path + "_core"), bay, originCell, Expectations.block(core))
        );
        for (LatticePoint cell : List.of(west, east, north, south)) {
            builder.check(
                MagicFixtures.occupant(
                    MagicFixtures.id(path + "_rune_" + cell.x() + "_" + cell.z()),
                    bay,
                    cell,
                    Expectations.anyOf(Expectations.block(runeA), Expectations.block(runeB), Expectations.block(runeC))
                )
            );
        }
        builder.check(
            Check.of(MagicFixtures.id(path + "_not_air"), bay, Focuses.occupant(notAir), Expectations.anyOccupant())
        );
        return builder.build();
    }

    private static TestWorld.View prefixView(Siting siting) {
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.put(view, siting, originCell, core);
        MagicFixtures.put(view, siting, west, runeA);
        MagicFixtures.put(view, siting, east, runeB);
        MagicFixtures.put(view, siting, north, runeC);
        MagicFixtures.put(view, siting, south, runeA);
        return view;
    }

    private static TestWorld.View tierThreeView(Siting siting) {
        TestWorld.View view = prefixView(siting);
        MagicFixtures.put(view, siting, northWest, runeA);
        MagicFixtures.put(view, siting, northEast, runeB);
        MagicFixtures.put(view, siting, southWest, runeC);
        MagicFixtures.put(view, siting, southEast, runeA);
        return view;
    }
}
