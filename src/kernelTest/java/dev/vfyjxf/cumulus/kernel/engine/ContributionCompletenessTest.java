package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.Merge;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Computation;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.SectionComputation;
import dev.vfyjxf.cumulus.kernel.structure.SectionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A contributed key is complete or absent: wrong types and broken merges never publish a short sum. */
class ContributionCompletenessTest {

    private static final Siting atOrigin = MagicFixtures.identity(LatticePoint.origin);
    private static final Cuboid checkCell = Cuboid.of(0, 0, 0, 1, 1, 1);
    private static final CuboidSet fourSections = CuboidSet.of(Cuboid.of(0, 0, 0, 4, 1, 1));

    @Test
    void aWrongTypedContributionLeavesTheKeyAbsent() {
        ContextKey<Long> key = sumKey("complete/wrong_type");
        Bay bay = originBay("complete/wrong_type");
        Blueprint blueprint = blueprint(
            "complete/wrong_type",
            List.of(new LyingSections(key, fourSections)),
            bay,
            contextCheck("complete/wrong_type", bay, key, value -> true)
        );
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(blueprint, fourSectionView()))
        );
        assertEquals(
            UndecidedReason.unreadable,
            undecided.reason(),
            "a check that requires the key answers Unreadable"
        );
        assertTrue(MagicFixtures.hasCode(undecided, Codes.designDefect), "the wrong type is SC-ENG-0004");
        assertTrue(MagicFixtures.hasCode(undecided, Codes.keyAbsent), "the required key is reported absent");
        MatchOutcome outcome = Objects.requireNonNull(undecided.partial());
        assertTrue(outcome.value(key).fold(value -> false, () -> true), "the context holds no value for the key");
    }

    @Test
    void aShortSumIsNeverPublished() {
        ContextKey<Long> key = sumKey("complete/short_sum");
        Bay bay = originBay("complete/short_sum");
        Blueprint blueprint = blueprint(
            "complete/short_sum",
            List.of(new LyingSections(key, fourSections)),
            bay,
            contextCheck("complete/short_sum", bay, key, value -> value == 3L)
        );
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(blueprint, fourSectionView()))
        );
        MatchOutcome outcome = Objects.requireNonNull(undecided.partial());
        assertTrue(
            outcome.value(key).fold(value -> false, () -> true),
            "the three honest sections never become a short sum the check could accept"
        );
    }

    @Test
    void aThrowingMergeBecomesAFindingAndAbsence() {
        ContextKey<Long> key = ContextKeys
                .of(id("complete/throwing_merge"), Long.class, Retention.instance, throwingMerge());
        Bay bay = originBay("complete/throwing_merge");
        Blueprint blueprint = blueprint(
            "complete/throwing_merge",
            List.of(new HonestSections(key, fourSections)),
            bay,
            contextCheck("complete/throwing_merge", bay, key, value -> true)
        );
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(blueprint, fourSectionView()))
        );
        assertTrue(MagicFixtures.hasCode(undecided, Codes.extensionFailed), "the throwing merge is SC-SPI-0001");
        assertTrue(MagicFixtures.hasCode(undecided, Codes.keyAbsent), "the folded key is absent");
        MatchOutcome outcome = Objects.requireNonNull(undecided.partial());
        assertTrue(outcome.value(key).fold(value -> false, () -> true), "the key is absent, not a partial sum");
    }

    @Test
    void aMergedValueOfTheWrongTypeIsAbsent() {
        ContextKey<Long> key = ContextKeys
                .of(id("complete/textual_merge"), Long.class, Retention.instance, textualMerge());
        Bay bay = originBay("complete/textual_merge");
        Blueprint blueprint = blueprint(
            "complete/textual_merge",
            List.of(new HonestSections(key, fourSections)),
            bay,
            contextCheck("complete/textual_merge", bay, key, value -> true)
        );
        MatchResult.Undecided undecided = assertInstanceOf(
            MatchResult.Undecided.class,
            MatchEngine.check(request(blueprint, fourSectionView()))
        );
        assertTrue(MagicFixtures.hasCode(undecided, Codes.designDefect), "the merged value is SC-ENG-0004");
        assertTrue(
            undecided.findings().findings().stream().anyMatch(
                finding -> finding.code().equals(Codes.designDefect.code()) && finding.message().startsWith("merging ")
            ),
            "the fold itself is named as the defect"
        );
        MatchOutcome outcome = Objects.requireNonNull(undecided.partial());
        assertTrue(outcome.value(key).fold(value -> false, () -> true), "the key is absent");
    }

    @Test
    void anUnusedBrokenKeyStillForms() {
        ContextKey<Long> broken = sumKey("complete/unused_broken");
        ContextKey<Long> used = sumKey("complete/used_healthy");
        Bay bay = originBay("complete/unused_broken");
        Blueprint blueprint = blueprint(
            "complete/unused_broken",
            List.of(new LyingSections(broken, fourSections), new HonestSections(used, fourSections)),
            bay,
            contextCheck("complete/unused_broken", bay, used, value -> value == 4L)
        );
        MatchResult formed = MagicFixtures.settle(blueprint, atOrigin, fourSectionView(), MagicFixtures.ledger());
        MatchResult.Formed formedResult = assertInstanceOf(MatchResult.Formed.class, formed);
        assertTrue(MagicFixtures.hasCode(formedResult, Codes.designDefect), "the broken computation still reports");
        assertEquals(
            4L,
            formedResult.outcome().value(used).fold(value -> value, () -> -1L),
            "the healthy fold publishes"
        );
        assertTrue(
            formedResult.outcome().value(broken).fold(value -> false, () -> true),
            "an attempt that never reads the broken key still forms"
        );
    }

    private static NamespacedId id(String path) {
        return MagicFixtures.id(path);
    }

    private static ContextKey<Long> sumKey(String path) {
        return ContextKeys.of(id(path), Long.class, Retention.instance, Merges.sum());
    }

    private static Merge<Long> throwingMerge() {
        return Merges.of((left, right) -> {
            throw new IllegalStateException("the merge is broken");
        });
    }

    @SuppressWarnings("unchecked")
    private static Merge<Long> textualMerge() {
        Merge<Object> textual = (left, right) -> "not a number";
        return (Merge<Long>) (Merge<?>) textual;
    }

    private static TestWorld.View fourSectionView() {
        return MagicFixtures.view().sectionSize(1);
    }

    private static Bay originBay(String path) {
        return MagicFixtures
                .bay(id(path + "_bay"), CuboidSet.of(checkCell), Coverage.anySubject, Footprint.none(), Sharing.shared);
    }

    private static Check<Long> contextCheck(String path, Bay bay, ContextKey<Long> key, Predicate<Long> expected) {
        return Check.of(id(path + "_check"), bay, Focuses.context(key), Expectations.context(key, expected));
    }

    private static Blueprint blueprint(
        String path,
        List<? extends Computation<?>> computations,
        Bay bay,
        Check<?> check
    ) {
        CuboidSet covered = CuboidSet.of(checkCell);
        for (Computation<?> computation : computations) {
            covered = covered.union(computation.region());
        }
        Cuboid bounds = Objects.requireNonNull(covered.bounds(), "bounds");
        return Blueprint.builder(id(path)).bounds(bounds).computations(computations).check(check).build();
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

    /** Three sections answer 1L; the fourth answers a String that is not a Long. */
    private record LyingSections(ContextKey<Long> output, CuboidSet region) implements SectionComputation<Long> {

        @Override
        public Set<ContextKey<?>> inputs() {
            return Set.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Outcome<Long> compute(SectionContext ctx) {
            if (ctx.section().x() == 3) {
                return (Outcome<Long>) (Outcome<?>) Outcome.found("not a number");
            }
            return Outcome.found(1L);
        }
    }

    private record HonestSections(ContextKey<Long> output, CuboidSet region) implements SectionComputation<Long> {

        @Override
        public Set<ContextKey<?>> inputs() {
            return Set.of();
        }

        @Override
        public Outcome<Long> compute(SectionContext ctx) {
            return Outcome.found(1L);
        }
    }
}
