package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Claim stress: sharing, conflict, release, whole-key replacement and duplicate rows. */
class ClaimStressScenarioTest {

    private static final LatticePoint cell = LatticePoint.origin;
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);
    private static final CuboidSet oneCell = CuboidSet.of(Cuboid.of(cell));
    private static final Siting siting = MagicFixtures.identity(LatticePoint.origin);

    @Test
    void sharedWallCoexistsAcrossOwners() {
        ClaimLedger ledger = MagicFixtures.ledger();
        Blueprint blueprint = wall("claim/shared", Sharing.shared);
        assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(blueprint, siting, airView(), ledger),
            "the first instance settles the shared wall"
        );
        MatchResult.Formed second = settleAs(
            blueprint,
            siting,
            airView(),
            ledger,
            MagicFixtures.rival,
            MagicFixtures.rivalInstance
        );
        assertEquals(2, TestWorld.rows(ledger).size(), "two shared instances coexist");
        assertEquals(1, second.claims().rows().size(), "a settlement returns the rows it wrote");
    }

    @Test
    void anExclusiveWallConflicts() {
        ClaimLedger ledger = MagicFixtures.ledger();
        Blueprint blueprint = wall("claim/exclusive", Sharing.exclusive);
        assertInstanceOf(MatchResult.Formed.class, MagicFixtures.settle(blueprint, siting, airView(), ledger));
        MatchResult result = MagicFixtures.check(
            blueprint,
            siting,
            airView(),
            ledger.read(MagicFixtures.worldBounds(blueprint, siting)),
            MagicFixtures.rival,
            MagicFixtures.rivalInstance
        );
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result);
        assertTrue(MagicFixtures.hasCode(failed, Codes.claimConflict), "the exclusive wall conflicts");
        assertEquals(1, TestWorld.rows(ledger).size(), "the failed check writes nothing");
    }

    @Test
    void releaseRemovesOnlyTheReleasedInstance() {
        ClaimLedger ledger = MagicFixtures.ledger();
        Blueprint blueprint = wall("claim/release", Sharing.shared);
        assertInstanceOf(MatchResult.Formed.class, MagicFixtures.settle(blueprint, siting, airView(), ledger));
        settleAs(blueprint, siting, airView(), ledger, MagicFixtures.rival, MagicFixtures.rivalInstance);
        assertEquals(2, TestWorld.rows(ledger).size(), "both instances hold a row");
        ledger.release(MagicFixtures.rivalInstance);
        assertEquals(1, TestWorld.rows(ledger).size(), "only the released instance's row is removed");
        assertTrue(TestWorld.rows(ledger).stream().allMatch(row -> row.instance().equals(MagicFixtures.instance)));
    }

    @Test
    void aSettleReplacesTheWholeInstanceKey() {
        ClaimLedger ledger = MagicFixtures.ledger();
        Blueprint first = oneCellBlueprint("claim/replace_first", cell, Sharing.shared);
        Blueprint second = oneCellBlueprint("claim/replace_second", LatticePoint.of(1, 0, 0), Sharing.shared);
        assertInstanceOf(MatchResult.Formed.class, MagicFixtures.settle(first, siting, airView(), ledger));
        assertEquals(1, TestWorld.rows(ledger).size(), "the first settlement writes one row");
        long revision = ledger.revision();
        assertInstanceOf(MatchResult.Formed.class, MagicFixtures.settle(second, siting, airView(), ledger));
        assertEquals(1, TestWorld.rows(ledger).size(), "the whole instance is replaced, not merged");
        assertEquals(revision + 1, ledger.revision(), "the replacement bumps the revision once");
        assertEquals(LatticePoint.of(1, 0, 0).key(), TestWorld.rows(ledger).get(0).cell(), "the new cell is the only row");
    }

    @Test
    @DisplayName("known gap: two SHARED bays over one cell write duplicate claim rows")
    void duplicateRowsAreCharacterized() {
        CuboidSet cells = oneCell;
        Bay first = MagicFixtures
                .bay(MagicFixtures.id("claim/duplicate_a"), cells, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Bay second = MagicFixtures
                .bay(MagicFixtures.id("claim/duplicate_b"), cells, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Blueprint blueprint = Blueprint.builder(MagicFixtures.id("claim/duplicate")).bounds(bounds)
                .check(
                    Check.of(
                        MagicFixtures.id("claim/duplicate_a_check"),
                        first,
                        Focuses.occupant(cell),
                        Expectations.anyOccupant()
                    )
                )
                .check(
                    Check.of(
                        MagicFixtures.id("claim/duplicate_b_check"),
                        second,
                        Focuses.occupant(cell),
                        Expectations.anyOccupant()
                    )
                ).build();
        ClaimLedger ledger = MagicFixtures.ledger();
        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MagicFixtures.settle(blueprint, siting, airView(), ledger)
        );
        assertEquals(2, formed.claims().rows().size(), "both shared bays contribute a row");
        assertEquals(2, TestWorld.rows(ledger).size(), "the ledger keeps both duplicate cell rows");
        assertFalse(
            formed.claims().rows().get(0).bay().equals(formed.claims().rows().get(1).bay()),
            "the duplicate rows differ by bay id"
        );
    }

    private static Blueprint wall(String path, Sharing sharing) {
        Bay bay = MagicFixtures
                .bay(MagicFixtures.id(path + "_bay"), oneCell, Coverage.everyCell, Footprint.box(), sharing);
        return Blueprint.builder(MagicFixtures.id(path)).bounds(bounds)
                .check(
                    Check.of(MagicFixtures.id(path + "_check"), bay, Focuses.occupant(cell), Expectations.anyOccupant())
                ).build();
    }

    private static Blueprint oneCellBlueprint(String path, LatticePoint at, Sharing sharing) {
        CuboidSet cells = CuboidSet.of(Cuboid.of(at));
        Bay bay = MagicFixtures
                .bay(MagicFixtures.id(path + "_bay"), cells, Coverage.everyCell, Footprint.box(), sharing);
        return Blueprint.builder(MagicFixtures.id(path)).bounds(bounds)
                .check(
                    Check.of(MagicFixtures.id(path + "_check"), bay, Focuses.occupant(at), Expectations.anyOccupant())
                ).build();
    }

    private static MatchResult.Formed settleAs(
        Blueprint blueprint,
        Siting siting,
        TestWorld.View view,
        ClaimLedger ledger,
        OwnerId owner,
        InstanceId instance
    ) {
        ClaimView claims = ledger.read(MagicFixtures.worldBounds(blueprint, siting));
        MatchRequest request = MagicFixtures.request(blueprint, siting, view, claims, owner, instance);
        MatchResult checked = MatchEngine.check(request);
        return assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(
                request,
                assertInstanceOf(MatchResult.Admitted.class, checked),
                ledger,
                TestAttempt.of(request)
            )
        );
    }

    private static TestWorld.View airView() {
        TestWorld.View view = MagicFixtures.view();
        MagicFixtures.air(view, siting, cell);
        MagicFixtures.air(view, siting, LatticePoint.of(1, 0, 0));
        return view;
    }
}
