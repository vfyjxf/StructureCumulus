package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.DoorView;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusContext;
import dev.vfyjxf.cumulus.kernel.structure.FocusResult;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.mc.pref.SitingCandidateScorer.Candidate;
import dev.vfyjxf.cumulus.mc.pref.SitingCandidateScorer.Scored;
import dev.vfyjxf.cumulus.mc.pref.SitingCandidateScorer.View;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SitingCandidateScorerTest {

    private static final LatticePoint origin = LatticePoint.of(10, 64, 10);
    private static final LatticePoint player = LatticePoint.of(10, 64, 16);
    private static final Siting identity = Siting.of(origin, Orientation.identity);
    private static final Siting east = Siting.of(origin, Orientation.of(Turn.clockwise, Tilt.level, Flip.none));
    private static final Siting far = Siting.of(LatticePoint.of(10, 64, 16 + 1_500), Orientation.identity);
    private static final SitingCandidateScorer scorer = new SitingCandidateScorer();

    @Test
    void theFacingWeightPrefersThePoseWhoseFrontMatchesThePlayer() {
        View view = view(Heading.north, Heading.north);
        assertEquals(100, scorer.score(candidate(0, identity), view, List.of(PlacementWeight.facingPlayer)));
        assertEquals(0, scorer.score(candidate(0, east), view, List.of(PlacementWeight.facingPlayer)));
        List<Scored> ranked = scorer
                .rank(List.of(candidate(1, east), candidate(0, identity)), view, List.of(PlacementWeight.facingPlayer));
        assertEquals(identity, ranked.get(0).siting());
    }

    @Test
    void theRemovalWeightCostsTenPerCellToClear() {
        View view = view(Heading.north, Heading.north);
        assertEquals(0, scorer.score(facts(identity, 0, 0), view, List.of(PlacementWeight.fewestRemovals)));
        assertEquals(-20, scorer.score(facts(identity, 0, 2), view, List.of(PlacementWeight.fewestRemovals)));
    }

    @Test
    void theProgressWeightRewardsTenPerSatisfiedCell() {
        View view = view(Heading.north, Heading.north);
        assertEquals(30, scorer.score(facts(identity, 3, 0), view, List.of(PlacementWeight.mostProgress)));
    }

    @Test
    void theDistanceWeightCostsTenPerBlockAndStopsAtItsCeiling() {
        View view = view(Heading.north, Heading.north);
        assertEquals(-60, scorer.score(candidate(0, identity), view, List.of(PlacementWeight.nearestToPlayer)));
        assertEquals(
            -SitingCandidateScorer.maxDistance * 10,
            scorer.score(candidate(0, far), view, List.of(PlacementWeight.nearestToPlayer))
        );
    }

    @Test
    void aRememberedPoseLeadsOutright() {
        ChoiceHistory memory = new ChoiceHistory(List.of(east, east, east));
        View view = new View(player, Heading.north, Heading.north, memory, false);
        int remembered = scorer
                .score(facts(east, 0, 0), view, List.of(PlacementWeight.mostProgress, PlacementWeight.remembered));
        int progress = scorer
                .score(facts(identity, 50, 0), view, List.of(PlacementWeight.mostProgress, PlacementWeight.remembered));
        assertTrue(remembered > progress, "the remembered pose outranks fifty satisfied cells");
        List<Scored> ranked = scorer.rank(
            List.of(factsCandidate(1, identity, 50, 0), factsCandidate(0, east, 0, 0)),
            view,
            List.of(PlacementWeight.mostProgress, PlacementWeight.remembered)
        );
        assertEquals(east, ranked.get(0).siting());
    }

    @Test
    void aHangingBlueprintScoresTheTopSupportAndALevelOneTheUnderside() {
        Candidate hangingCandidate = candidate(0, identity, 0, 0, 0, 100);
        Candidate levelCandidate = candidate(0, identity, 0, 0, 100, 0);
        assertEquals(
            100,
            scorer.score(
                hangingCandidate,
                new View(player, Heading.north, Heading.north, null, true),
                List.of(PlacementWeight.support)
            )
        );
        assertEquals(
            0,
            scorer.score(
                levelCandidate,
                new View(player, Heading.north, Heading.north, null, true),
                List.of(PlacementWeight.support)
            )
        );
        assertEquals(
            100,
            scorer.score(
                levelCandidate,
                new View(player, Heading.north, Heading.north, null, false),
                List.of(PlacementWeight.support)
            )
        );
    }

    @Test
    void alignIsDeclaredButContributesNothing() {
        View view = view(Heading.north, Heading.north);
        assertEquals(0, scorer.score(facts(identity, 5, 5), view, List.of(PlacementWeight.align)));
    }

    @Test
    void theCombinedScoreIsTheSumOfItsTerms() {
        View view = view(Heading.north, Heading.north);
        Candidate candidate = facts(identity, 2, 1);
        int facing = scorer.score(candidate, view, List.of(PlacementWeight.facingPlayer));
        int progress = scorer.score(candidate, view, List.of(PlacementWeight.mostProgress));
        int removals = scorer.score(candidate, view, List.of(PlacementWeight.fewestRemovals));
        assertEquals(
            facing + progress + removals,
            scorer.score(
                candidate,
                view,
                List.of(PlacementWeight.facingPlayer, PlacementWeight.mostProgress, PlacementWeight.fewestRemovals)
            )
        );
    }

    @Test
    void aTieKeepsTheCanonicalOrderWhateverTheInputOrder() {
        View view = view(Heading.north, Heading.north);
        List<Candidate> candidates = List.of(
            candidate(3, Siting.of(LatticePoint.of(10, 64, 10), Orientation.identity)),
            candidate(1, Siting.of(LatticePoint.of(10, 64, 10), Orientation.of(Turn.clockwise, Tilt.level, Flip.none)))
        );
        List<Scored> ranked = scorer.rank(candidates, view, List.of());
        assertEquals(List.of(1, 3), ranked.stream().map(Scored::order).toList());
    }

    @Test
    void aScrambledInputRanksIdentically() {
        View view = view(Heading.north, Heading.north);
        List<PlacementWeight> weights = List.of(
            PlacementWeight.facingPlayer,
            PlacementWeight.mostProgress,
            PlacementWeight.fewestRemovals,
            PlacementWeight.nearestToPlayer,
            PlacementWeight.support
        );
        List<Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            candidates.add(candidate(index, siting(index), index % 3, index % 2, index * 10, 100 - index * 10));
        }
        List<Scored> forward = scorer.rank(candidates, view, weights);
        List<Candidate> reversed = new ArrayList<>(candidates);
        Collections.reverse(reversed);
        assertEquals(forward, scorer.rank(reversed, view, weights));
    }

    @Test
    void anEmptyOrNullWeightListFallsBackToTheCanonicalOrder() {
        View view = view(Heading.north, Heading.north);
        List<Candidate> candidates = List.of(candidate(1, east), candidate(0, identity));
        assertEquals(identity, scorer.rank(candidates, view, null).get(0).siting());
        assertEquals(identity, scorer.rank(candidates, view, List.of()).get(0).siting());
    }

    @Test
    void theRankingRejectsAmbiguousInputs() {
        View view = view(Heading.north, Heading.north);
        assertThrows(
            IllegalArgumentException.class,
            () -> scorer.rank(List.of(candidate(0, identity), candidate(0, east)), view, List.of())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> scorer.rank(List.of(candidate(0, identity), candidate(1, identity)), view, List.of())
        );
    }

    @Test
    void aCandidateRejectsAnImpossibleMeasurement() {
        assertThrows(IllegalArgumentException.class, () -> new Candidate(identity, 0, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Candidate(identity, 0, 0, 0, 101, 0));
        assertThrows(IllegalArgumentException.class, () -> new Candidate(identity, -1, 0, 0, 0, 0));
    }

    @Test
    void theFrontIsTheSideTheDoorCellsSitOn() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 5, 3, 5);
        assertEquals(
            Heading.south,
            SitingCandidateScorer.frontOf(List.of(LatticePoint.of(2, 0, 4), LatticePoint.of(2, 1, 4)), bounds)
        );
        assertEquals(
            Heading.east,
            SitingCandidateScorer.frontOf(List.of(LatticePoint.of(4, 0, 2), LatticePoint.of(4, 1, 2)), bounds)
        );
        Cuboid even = Cuboid.of(0, 0, 0, 4, 2, 4);
        assertNull(SitingCandidateScorer.frontOf(List.of(LatticePoint.of(2, 0, 2)), even));
        assertNull(SitingCandidateScorer.frontOf(List.of(), even));
    }

    @Test
    void aBlueprintWithoutADoorKeepsTheIdentityFront() {
        assertEquals(Heading.north, SitingCandidateScorer.frontOf(blueprint(null)));
    }

    @Test
    void aBlueprintWithADoorCheckPutsTheFrontOnTheDoorsSide() {
        assertEquals(Heading.west, SitingCandidateScorer.frontOf(blueprint(Heading.west)));
        assertEquals(Heading.south, SitingCandidateScorer.frontOf(blueprint(Heading.south)));
    }

    private static Blueprint blueprint(@Nullable Heading side) {
        BlueprintBuilder builder = Blueprint.builder(NamespacedId.of("structurecumulus", "front_probe"))
                .bounds(Cuboid.of(0, 0, 0, 5, 3, 5));
        Bay body = Bay.of(
            NamespacedId.of("structurecumulus", "body_bay"),
            Cuboid.of(0, 0, 0, 5, 3, 5),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.shared
        );
        builder.check(
            Check.of(
                NamespacedId.of("structurecumulus", "body_check"),
                body,
                Focuses.bay(),
                Expectations.eachCell(Expectations.block(NamespacedId.of("minecraft", "stone")))
            )
        );
        if (side == null) {
            return builder.build();
        }
        List<LatticePoint> cells = switch (side) {
            case west -> List.of(LatticePoint.of(0, 0, 2), LatticePoint.of(0, 1, 2));
            case east -> List.of(LatticePoint.of(4, 0, 2), LatticePoint.of(4, 1, 2));
            case south -> List.of(LatticePoint.of(2, 0, 4), LatticePoint.of(2, 1, 4));
            case north -> List.of(LatticePoint.of(2, 0, 0), LatticePoint.of(2, 1, 0));
            default -> List.of();
        };
        Bay bay = Bay.of(
            NamespacedId.of("structurecumulus", "door_bay"),
            Cuboid.of(0, 0, 0, 5, 3, 5),
            Coverage.oneSubject,
            Footprint.box(),
            Sharing.shared
        );
        return builder.check(
            Check.of(
                NamespacedId.of("structurecumulus", "door_check"),
                bay,
                new DoorCells(cells),
                new DoorExpectation()
            )
        ).build();
    }

    private static Candidate candidate(int order, Siting siting) {
        return candidate(order, siting, 0, 0, 0, 0);
    }

    private static Candidate facts(Siting siting, int satisfied, int removals) {
        return candidate(0, siting, satisfied, removals, 0, 0);
    }

    private static Candidate factsCandidate(int order, Siting siting, int satisfied, int removals) {
        return candidate(order, siting, satisfied, removals, 0, 0);
    }

    private static Candidate candidate(int order, Siting siting, int satisfied, int removals, int below, int above) {
        return new Candidate(siting, order, satisfied, removals, below, above);
    }

    private static Siting siting(int index) {
        return Siting.of(
            LatticePoint.of(10 + index, 64, 10),
            Orientation.of(Turn.values()[index % 4], Tilt.level, index % 2 == 0 ? Flip.none : Flip.frontBack)
        );
    }

    private static View view(Heading facing, Heading front) {
        return new View(player, facing, front, null, false);
    }

    /** A focus that reports two declaration-frame cells and never reads the world. */
    private record DoorCells(List<LatticePoint> cells) implements Focus<DoorView> {

        @Override
        public FocusResult<DoorView> take(FocusContext ctx) {
            throw new UnsupportedOperationException("the front derivation never takes a door");
        }

        @Override
        public Class<DoorView> subject() {
            return DoorView.class;
        }

        @Override
        public boolean bindsClaimableSubject() {
            return true;
        }

        @Override
        public CuboidSet staticCells(CuboidSet bayCells) {
            List<Cuboid> boxes = new ArrayList<>();
            for (LatticePoint cell : cells) {
                boxes.add(Cuboid.of(cell));
            }
            return CuboidSet.of(boxes);
        }
    }

    /** An expectation that only names its subject type, which {@link Check} cross-checks. */
    private record DoorExpectation() implements Expectation<DoorView> {

        @Override
        public Reply test(ExpectationContext<DoorView> ctx) {
            throw new UnsupportedOperationException("the front derivation never tests a door");
        }

        @Override
        public Class<DoorView> subject() {
            return DoorView.class;
        }
    }
}
