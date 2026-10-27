package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Astral Altar: a fifty-four cell tier II and a seventy-two cell tier III that grows in place. */
class AstralAltarScenarioTest {

    private static final NamespacedId base = MagicFixtures.id("astral/opaque_base");
    private static final NamespacedId relay = MagicFixtures.id("astral/opaque_relay");
    private static final NamespacedId slab = MagicFixtures.id("astral/opaque_slab");

    private static final LatticePoint relayCell = LatticePoint.of(1, 3, 1);
    private static final LatticePoint slabCell = LatticePoint.of(1, 0, 1);
    private static final Cuboid tierTwoBounds = Cuboid.of(0, 0, 0, 3, 6, 3);
    private static final Cuboid tierThreeBounds = Cuboid.of(0, 0, 0, 3, 8, 3);

    @Test
    void tierTwoFiftyFourCellsForm() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        Blueprint tierTwo = blueprint("astral/tier_two", tierTwoBounds);
        assertEquals(54L, tierTwo.bayCells().volume(), "the tier II altar has fifty-four cells");
        TestWorld.Ledger ledger = MagicFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(tierTwo, siting, view(tierTwoBounds, true, "top"), ledger)
        );
        assertEquals(54, formed.claims().rows().size(), "fifty-four claim rows");
        assertEquals(54, ledger.all().size(), "the ledger holds fifty-four rows");
    }

    @Test
    void aMissingRelayFails() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.Ledger ledger = MagicFixtures.ledger();
        MatchResult result = MagicFixtures.settle(
            blueprint("astral/tier_two_missing_relay", tierTwoBounds),
            siting,
            view(tierTwoBounds, false, "top"),
            ledger
        );
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result, "the relay cell is air");
        assertTrue(MagicFixtures.hasCode(failed, Codes.expected), "the missing relay is SC-EXP-0001");
        assertEquals(0, ledger.all().size(), "a failed check writes no rows");
    }

    @Test
    void aWrongSlabPropertyFails() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.Ledger ledger = MagicFixtures.ledger();
        MatchResult result = MagicFixtures.settle(
            blueprint("astral/tier_two_wrong_slab", tierTwoBounds),
            siting,
            view(tierTwoBounds, true, "bottom"),
            ledger
        );
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result, "the slab type is wrong");
        assertTrue(MagicFixtures.hasCode(failed, Codes.expected), "the wrong slab is SC-EXP-0001");
        assertEquals(0, ledger.all().size(), "a failed check writes no rows");
    }

    @Test
    void tierThreeGrowsTheClaimInPlace() {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.Ledger ledger = MagicFixtures.ledger();
        Blueprint tierTwo = blueprint("astral/tier_two_growth", tierTwoBounds);
        assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(tierTwo, siting, view(tierTwoBounds, true, "top"), ledger),
            "the tier II claim settles first"
        );
        assertEquals(54, ledger.all().size(), "the first tier holds fifty-four rows");
        assertEquals(1L, ledger.revision(), "one settlement revision");

        Blueprint tierThree = blueprint("astral/tier_three_growth", tierThreeBounds);
        TestWorld.View tierThreeView = view(tierThreeBounds, true, "top");
        ClaimView claims = ledger.read(MagicFixtures.worldBounds(tierThree, siting));
        MatchRequest request = MagicFixtures.request(tierThree, siting, tierThreeView, claims);
        MatchResult checked = MatchEngine.check(request);
        MatchResult.Formed grown = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                request,
                assertInstanceOf(MatchResult.Admitted.class, checked),
                ledger,
                ContextStore.inMemory()
            ),
            "the tier III check replaces the tier II claim"
        );
        assertEquals(72, grown.claims().rows().size(), "the grown claim has seventy-two rows");
        assertEquals(72, ledger.all().size(), "the ledger grows in place to seventy-two rows");
        assertTrue(
            ledger.all().stream().allMatch(row -> row.owner().equals(MagicFixtures.owner)),
            "the grown rows belong to the same owner"
        );
        assertEquals(2L, ledger.revision(), "the in-place replacement bumps the revision once");
    }

    private static Blueprint blueprint(String path, Cuboid bounds) {
        Bay bay = MagicFixtures.bay(
            MagicFixtures.id(path + "_bay"),
            CuboidSet.of(bounds),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        BlueprintBuilder builder = Blueprint.builder(MagicFixtures.id(path)).bounds(bounds);
        builder.check(MagicFixtures.eachCell(MagicFixtures.id(path + "_all"), bay, Expectations.anyOccupant()));
        builder.check(
            MagicFixtures.occupant(MagicFixtures.id(path + "_relay"), bay, relayCell, Expectations.block(relay))
        );
        builder.check(
            MagicFixtures.occupant(
                MagicFixtures.id(path + "_slab"),
                bay,
                slabCell,
                Expectations.allOf(Expectations.block(slab), Expectations.property("type", "top"))
            )
        );
        return builder.build();
    }

    private static TestWorld.View view(Cuboid bounds, boolean relayPresent, String slabType) {
        Siting siting = MagicFixtures.identity(LatticePoint.origin);
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.fill(view, siting, CuboidSet.of(bounds), base);
        if (relayPresent) {
            MagicFixtures.put(view, siting, relayCell, relay);
        }
        MagicFixtures.put(view, siting, slabCell, slab, Map.of("type", slabType));
        return view;
    }
}
