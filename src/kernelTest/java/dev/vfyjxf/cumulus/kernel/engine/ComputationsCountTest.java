package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Computations;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.SectionComputation;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The predicate count: filter, non-air, multi-section sum, body identity and refused slices. */
class ComputationsCountTest {

    private static final Siting atOrigin = MagicFixtures.identity(LatticePoint.origin);
    private static final Cuboid checkCell = Cuboid.of(0, 0, 0, 1, 1, 1);

    @Test
    void countsOnlyNonAirOccupantsThatMatchThePredicate() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 1, 1));
        ContextKey<Long> key = countKey("count/predicate");
        Predicate<OccupantSnapshot> stoneOnly = occupant -> occupant.descriptor().type().equals(TestWorld.stone);
        SectionComputation<Long> count = Computations.count(key, region, stoneOnly);
        Blueprint blueprint = blueprint("count/predicate", region, count, key, value -> value == 1L);
        TestWorld.View view = MagicFixtures.view().put(TestWorld.block(TestWorld.stone, LatticePoint.of(0, 0, 0)))
                .put(TestWorld.block(TestWorld.campfire, LatticePoint.of(1, 0, 0))).air(LatticePoint.of(2, 0, 0));
        MatchResult.Admitted admitted = check(blueprint, view);
        assertEquals(1L, admitted.outcome().value(key).fold(value -> value, () -> -1L));
    }

    @Test
    void multiSectionContributionsAreSummed() {
        CuboidSet region = CuboidSet.of(Cuboid.of(15, 0, 0, 18, 1, 1));
        ContextKey<Long> key = countKey("count/sections");
        Predicate<OccupantSnapshot> stoneOnly = occupant -> occupant.descriptor().type().equals(TestWorld.stone);
        SectionComputation<Long> count = Computations.count(key, region, stoneOnly);
        Blueprint blueprint = blueprint("count/sections", region, count, key, value -> value == 3L);
        TestWorld.View view = MagicFixtures.view().sectionSize(16)
                .put(TestWorld.block(TestWorld.stone, LatticePoint.of(15, 0, 0)))
                .put(TestWorld.block(TestWorld.stone, LatticePoint.of(16, 0, 0)))
                .put(TestWorld.block(TestWorld.stone, LatticePoint.of(17, 0, 0)));
        MatchResult.Admitted admitted = check(blueprint, view);
        assertEquals(3L, admitted.outcome().value(key).fold(value -> value, () -> -1L));
        assertEquals(new Spend(3, 2), admitted.outcome().spend());
    }

    @Test
    void aMultiCellBodyIsCountedOnceAndOnlyByItsAnchor() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 8, 1, 1));
        ContextKey<Long> key = countKey("count/body");
        SectionComputation<Long> count = Computations
                .count(key, region, occupant -> occupant.ref().kind().equals(OccupantKind.entity));
        Blueprint blueprint = blueprint("count/body", region, count, key, value -> value == 1L);
        NamespacedId type = MagicFixtures.mc("multi_cell_type");
        OccupantSnapshot counted = entity(type, LatticePoint.of(1, 0, 0), Cuboid.of(1, 0, 0, 3, 1, 1), "one");
        OccupantSnapshot clipped = entity(type, LatticePoint.of(8, 0, 0), Cuboid.of(7, 0, 0, 9, 1, 1), "two");
        TestWorld.View view = MagicFixtures.view().put(counted).put(clipped)
                .put(TestWorld.block(TestWorld.stone, LatticePoint.of(4, 0, 0)));
        MatchResult.Admitted admitted = check(blueprint, view);
        assertEquals(
            1L,
            admitted.outcome().value(key).fold(value -> value, () -> -1L),
            "a two-cell body is one occupant, and an off-region anchor is not counted"
        );
    }

    @Test
    void aRefusedSliceMakesCountMissingAndNeverZero() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        ContextKey<Long> key = countKey("count/refused");
        SectionComputation<Long> count = Computations
                .count(key, region, occupant -> occupant.descriptor().type().equals(TestWorld.stone));
        Blueprint blueprint = blueprint("count/refused", region, count, key, value -> value == 0L);
        TestWorld.View view = MagicFixtures.view().put(TestWorld.block(TestWorld.stone, LatticePoint.origin))
                .refuse(LatticePoint.of(1, 0, 0));
        MatchRequest request = request(blueprint, view);
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.unreadable, undecided.reason());
        assertTrue(MagicFixtures.hasCode(undecided, Codes.unreadableRead), "the refusal is relayed");
        assertTrue(MagicFixtures.hasCode(undecided, Codes.keyAbsent), "the count key is absent, not zero");
        MatchOutcome outcome = Objects.requireNonNull(undecided.partial());
        assertTrue(outcome.value(key).fold(value -> false, () -> true), "a refused slice is never a short sum");
    }

    @Test
    void aWhollyRefusedSectionNeverRunsTheCount() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        ContextKey<Long> key = countKey("count/refused_section");
        SectionComputation<Long> count = Computations.count(key, region, occupant -> true);
        Blueprint blueprint = blueprint("count/refused_section", region, count, key, value -> value == 0L);
        TestWorld.View view = MagicFixtures.view();
        view.refuseSection(SectionKey.of(TestWorld.world, 0, LatticePoint.origin, 16));
        MatchRequest request = request(blueprint, view);
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertTrue(MagicFixtures.hasCode(undecided, Codes.foldRefused), "a refused section is SC-CTX-0006");
        assertTrue(MagicFixtures.hasCode(undecided, Codes.keyAbsent), "the count key is absent, not zero");
    }

    @Test
    void noMatchOnAReadableSliceIsAZero() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        ContextKey<Long> key = countKey("count/zero");
        SectionComputation<Long> count = Computations
                .count(key, region, occupant -> occupant.descriptor().type().equals(TestWorld.stone));
        Blueprint blueprint = blueprint("count/zero", region, count, key, value -> value == 0L);
        TestWorld.View view = MagicFixtures.view().air(LatticePoint.origin).air(LatticePoint.of(1, 0, 0));
        MatchResult.Admitted admitted = check(blueprint, view);
        assertEquals(0L, admitted.outcome().value(key).fold(value -> value, () -> -1L));
    }

    @Test
    void thePredicateCountNeedsAMergeAndANonEmptyRegion() {
        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        ContextKey<Long> merged = countKey("count/merged");
        ContextKey<Long> unmerged = ContextKeys.of(MagicFixtures.id("count/unmerged"), Long.class, Retention.instance);
        assertThrows(
            IllegalArgumentException.class,
            () -> Computations.count(unmerged, region, occupant -> true),
            "a count output needs a merge"
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> Computations.count(merged, CuboidSet.empty(), occupant -> true),
            "a count region must not be empty"
        );
        assertDoesNotThrow(
            () -> Computations.count(unmerged, region),
            "the two-argument count keeps its historical shape"
        );
    }

    private static NamespacedId id(String path) {
        return MagicFixtures.id(path);
    }

    private static ContextKey<Long> countKey(String path) {
        return ContextKeys.of(id(path), Long.class, Retention.instance, Merges.sum());
    }

    private static OccupantSnapshot entity(NamespacedId type, LatticePoint anchor, Cuboid footprint, String body) {
        OccupantRef ref = new OccupantRef(TestWorld.snapshot, 0, type, OccupantKind.entity, anchor, id(body));
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.entity);
        return new OccupantSnapshot(ref, descriptor, footprint, List.of());
    }

    private static Blueprint blueprint(
        String path,
        CuboidSet region,
        SectionComputation<Long> computation,
        ContextKey<Long> key,
        Predicate<Long> expected
    ) {
        Bay bay = MagicFixtures
                .bay(id(path + "_bay"), CuboidSet.of(checkCell), Coverage.anySubject, Footprint.none(), Sharing.shared);
        Cuboid bounds = Objects.requireNonNull(region.union(checkCell).bounds(), "bounds");
        return Blueprint.builder(id(path)).bounds(bounds).computation(computation)
                .check(Check.of(id(path + "_check"), bay, Focuses.context(key), Expectations.context(key, expected)))
                .build();
    }

    private static MatchRequest request(Blueprint blueprint, TestWorld.View view) {
        return MatchRequest.of(
            blueprint,
            atOrigin,
            new TestWorld.Lease(view),
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.unbounded(),
            new ClaimView(List.of(), 0, true)
        );
    }

    private static MatchResult.Admitted check(Blueprint blueprint, TestWorld.View view) {
        return assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request(blueprint, view)));
    }

}
