package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusResult;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adversarial expectation tests: D-39 classes, envelope edges, per-cell folds and thresholds. */
class AdversarialExpectationTest {

    private static final LatticePoint origin = LatticePoint.origin;
    private static final CuboidSet oneCell = CuboidSet.of(Cuboid.of(origin));
    private static final Siting siting = MagicFixtures.identity(origin);
    private static final NamespacedId stone = TestWorld.stone;
    private static final NamespacedId water = NamespacedId.of("minecraft", "water");
    private static final NamespacedId lava = NamespacedId.of("minecraft", "lava");
    private static final NamespacedId stairs = NamespacedId.of("minecraft", "oak_stairs");

    @Test
    void emptyNonFluidAndNotAirAreThreeDifferentLeaves() {
        assertAll(
            () -> assertOutcome(Expectations.empty(), air(), true, Codes.expected),
            () -> assertOutcome(Expectations.nonFluid(), air(), true, Codes.expected),
            () -> assertOutcome(Expectations.not(Expectations.air()), air(), false, Codes.negationMatched),
            () -> assertOutcome(Expectations.anyOccupant(), air(), true, Codes.expected),
            () -> assertOutcome(Expectations.empty(), block(stone), false, Codes.expected),
            () -> assertOutcome(Expectations.nonFluid(), block(stone), true, Codes.expected),
            () -> assertOutcome(Expectations.not(Expectations.air()), block(stone), true, Codes.expected),
            () -> assertOutcome(Expectations.empty(), block(water), true, Codes.expected),
            () -> assertOutcome(Expectations.nonFluid(), block(water), false, Codes.expected),
            () -> assertOutcome(Expectations.not(Expectations.air()), block(water), true, Codes.expected),
            () -> assertOutcome(Expectations.empty(), block(lava), true, Codes.expected),
            () -> assertOutcome(Expectations.nonFluid(), block(lava), false, Codes.expected),
            () -> assertOutcome(Expectations.not(Expectations.air()), block(lava), true, Codes.expected),
            () -> assertOutcome(Expectations.empty(), block(stairs), false, Codes.expected),
            () -> assertOutcome(Expectations.nonFluid(), block(stairs), true, Codes.expected),
            () -> assertOutcome(Expectations.not(Expectations.air()), block(stairs), true, Codes.expected),
            () -> assertOutcome(Expectations.empty(), entity("minecraft:zombie"), false, Codes.expected),
            () -> assertOutcome(Expectations.nonFluid(), entity("minecraft:zombie"), true, Codes.expected),
            () -> assertOutcome(Expectations.not(Expectations.air()), entity("minecraft:zombie"), true, Codes.expected)
        );
    }

    @Test
    void airAndEnvelopeAirCarryDifferentMismatchCodes() {
        assertEquals(Codes.expected.code(), Expectations.air().meta().mismatchCode());
        assertEquals(Codes.envelopeOpen.code(), Expectations.envelopeAir().meta().mismatchCode());
        assertNotEquals(Expectations.air().meta().id(), Expectations.envelopeAir().meta().id());
        assertEquals(
            Codes.envelopeOpen.code(),
            Expectations.eachCell(Expectations.envelopeAir()).meta().mismatchCode()
        );
        assertEquals(Codes.expected.code(), Expectations.eachCell(Expectations.air()).meta().mismatchCode());
        assertEquals(Codes.expected.code(), Expectations.empty().meta().mismatchCode());
        assertEquals(Codes.expected.code(), Expectations.nonFluid().meta().mismatchCode());
    }

    @Test
    void theEnvelopeRejectsForeignOccupantsAtTheirCell() {
        Blueprint blueprint = envelopeOnly(Cuboid.of(0, 0, 0, 1, 1, 1));
        MatchResult stoneResult = MagicFixtures
                .check(blueprint, siting, MagicFixtures.view().put(blockAt(stone, origin)), emptyClaims());
        MatchResult.Failed stoneFailure = assertInstanceOf(MatchResult.Failed.class, stoneResult);
        assertTrue(MagicFixtures.hasCode(stoneFailure, Codes.envelopeOpen));

        MatchResult waterResult = MagicFixtures
                .check(blueprint, siting, MagicFixtures.view().put(blockAt(water, origin)), emptyClaims());
        assertTrue(MagicFixtures.hasCode(assertInstanceOf(MatchResult.Failed.class, waterResult), Codes.envelopeOpen));

        MatchResult entityResult = MagicFixtures.check(
            blueprint,
            siting,
            MagicFixtures.view().put(entityAt("minecraft:zombie", origin)),
            emptyClaims()
        );
        assertTrue(MagicFixtures.hasCode(assertInstanceOf(MatchResult.Failed.class, entityResult), Codes.envelopeOpen));
    }

    @Test
    void anUnreadableEnvelopeCellIsUndecidedNotAMismatch() {
        Blueprint blueprint = envelopeOnly(Cuboid.of(0, 0, 0, 1, 1, 1));
        MatchResult result = MagicFixtures.check(blueprint, siting, MagicFixtures.view().refuse(origin), emptyClaims());
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.unreadable, undecided.reason());
        assertTrue(MagicFixtures.hasCode(undecided, Codes.unreadableRead));
        assertFalse(MagicFixtures.hasCode(undecided, Codes.envelopeOpen));
    }

    @Test
    void anEnvelopeFormsWithoutALedgerAndClaimsNothing() {
        Blueprint blueprint = envelopeOnly(Cuboid.of(0, 0, 0, 1, 1, 1));
        TestWorld.View view = MagicFixtures.view().air(origin);
        MatchRequest request = MagicFixtures.request(blueprint, siting, view, emptyClaims());
        MatchResult.Admitted admitted = assertInstanceOf(MatchResult.Admitted.class, MatchEngine.check(request));
        assertFalse(admitted.ledgerAvailable());

        MatchResult.Formed formed = assertInstanceOf(
            MatchResult.Formed.class,
            MatchEngine.settle(request, admitted, null, TestAttempt.of(request))
        );
        assertTrue(formed.claims().rows().isEmpty());
        assertEquals(0, formed.claims().revision());
        assertEquals(0, formed.ledgerRevision());
        assertEquals(1, formed.contextRevision());
    }

    @Test
    void theEnvelopeStopsAtTheBoundsEvenWhenForeignBlocksSitOutside() {
        Blueprint blueprint = envelopeOnly(Cuboid.of(0, 0, 0, 1, 1, 1));
        TestWorld.View view = MagicFixtures.view().air(origin);
        view.put(blockAt(stone, LatticePoint.of(5, 0, 5)));
        MatchResult result = MagicFixtures.check(blueprint, siting, view, emptyClaims());
        assertInstanceOf(MatchResult.Admitted.class, result, "a block outside the bounds is invisible");
    }

    @Test
    void declaredFootprintsAreExcludedFromTheEnvelope() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 3, 1, 1);
        Bay bay = Bay.of(
            id("declared_bay"),
            oneCell,
            Coverage.everyCell,
            Footprint.declared(CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1))),
            Sharing.shared
        );
        Blueprint blueprint = Blueprint.builder(id("envelope_declared")).bounds(bounds)
                .check(Check.of(id("declared_check"), bay, Focuses.occupant(origin), Expectations.anyOccupant()))
                .envelope().build();
        Bay envelope = blueprint.bay(NamespacedId.of("structurecumulus", "envelope"));
        assertEquals(CuboidSet.of(Cuboid.of(2, 0, 0, 3, 1, 1)), Objects.requireNonNull(envelope, "envelope").cells());

        TestWorld.View view = MagicFixtures.view().put(blockAt(stone, origin));
        view.put(blockAt(stone, LatticePoint.of(1, 0, 0)));
        view.air(LatticePoint.of(2, 0, 0));
        MatchResult result = MagicFixtures.check(blueprint, siting, view, emptyClaims());
        assertInstanceOf(MatchResult.Admitted.class, result, "a declared footprint cell is not the envelope");
    }

    @Test
    void eachCellFoldsCellRepliesAndPrefersMismatchOverUnreadable() {
        CuboidSet twoCells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        Bay bay = Bay.of(id("two_cells"), twoCells, Coverage.everyCell, Footprint.none(), Sharing.shared);
        Blueprint blueprint = Blueprint.builder(id("each_cell_precedence")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .check(
                    Check.of(
                        id("each_cell_precedence_check"),
                        bay,
                        Focuses.bay(),
                        Expectations.eachCell(Expectations.block(stone))
                    )
                ).build();

        TestWorld.View mismatchAndRefused = MagicFixtures.view().air(origin);
        mismatchAndRefused.refuse(LatticePoint.of(1, 0, 0));
        assertInstanceOf(
            MatchResult.Failed.class,
            check(blueprint, mismatchAndRefused),
            "a mismatch wins over a refusal"
        );

        TestWorld.View matchAndRefused = MagicFixtures.view().put(blockAt(stone, origin));
        matchAndRefused.refuse(LatticePoint.of(1, 0, 0));
        MatchResult.Undecided unreadable = assertInstanceOf(
            MatchResult.Undecided.class,
            check(blueprint, matchAndRefused)
        );
        assertEquals(UndecidedReason.unreadable, unreadable.reason());

        TestWorld.View bothRefused = MagicFixtures.view().refuse(origin);
        bothRefused.refuse(LatticePoint.of(1, 0, 0));
        assertInstanceOf(MatchResult.Undecided.class, check(blueprint, bothRefused));

        TestWorld.View bothMatch = MagicFixtures.view().put(blockAt(stone, origin));
        bothMatch.put(blockAt(stone, LatticePoint.of(1, 0, 0)));
        assertInstanceOf(MatchResult.Admitted.class, check(blueprint, bothMatch));
    }

    @Test
    void eachCellPropagatesAbstainAndMatchFromTheElement() {
        CuboidSet twoCells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        Bay bay = Bay.of(id("abstain_bay"), twoCells, Coverage.everyCell, Footprint.none(), Sharing.shared);
        Blueprint abstaining = Blueprint.builder(id("each_cell_abstain")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .check(
                    Check.of(
                        id("each_cell_abstain_check"),
                        bay,
                        Focuses.bay(),
                        Expectations.eachCell(customIgnoringSubject(Reply.abstain()))
                    )
                ).build();
        TestWorld.View air = MagicFixtures.view().air(origin);
        air.air(LatticePoint.of(1, 0, 0));
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, check(abstaining, air));
        assertEquals(UndecidedReason.abstained, undecided.reason());

        Blueprint matching = Blueprint.builder(id("each_cell_match")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .check(
                    Check.of(
                        id("each_cell_match_check"),
                        bay,
                        Focuses.bay(),
                        Expectations.eachCell(customIgnoringSubject(Reply.match()))
                    )
                ).build();
        assertInstanceOf(MatchResult.Admitted.class, check(matching, air));
    }

    @Test
    void thresholdBoundariesFollowTheDocumentedCountingRules() {
        assertAll(
            () -> assertAdmitted(atLeast(2, Reply.match(), Reply.match())),
            () -> assertThresholdFailure(atLeast(2, Reply.match(), mismatchReply())),
            () -> assertUnreadable(atLeast(2, Reply.match(), unreadableReply())),
            () -> assertAbstained(atLeast(2, Reply.match(), Reply.abstain())),
            () -> assertThresholdFailure(atLeast(2, mismatchReply(), mismatchReply())),
            () -> assertAdmitted(atLeast(0, mismatchReply())),
            () -> assertAdmitted(atMost(0, mismatchReply())),
            () -> assertThresholdFailure(atMost(0, Reply.match())),
            () -> assertUnreadable(atMost(1, Reply.match(), unreadableReply())),
            () -> assertUnreadable(atMost(1, unreadableReply(), unreadableReply())),
            () -> assertAdmitted(exactly(0, mismatchReply())),
            () -> assertThresholdFailure(exactly(0, Reply.match())),
            () -> assertAdmitted(exactly(1, Reply.match())),
            () -> assertUnreadable(exactly(1, Reply.match(), unreadableReply())),
            () -> assertThresholdFailure(exactly(1, mismatchReply(), mismatchReply())),
            () -> assertThresholdFailure(exactly(2, Reply.match(), Reply.match(), Reply.match()))
        );
    }

    @Test
    void allOfAnyOfSequenceFollowTheFoldTable() {
        for (Reply first : List.of(Reply.match(), mismatchReply(), unreadableReply(), Reply.abstain())) {
            for (Reply second : List.of(Reply.match(), mismatchReply(), unreadableReply(), Reply.abstain())) {
                String pair = first + "+" + second;
                assertAll(
                    () -> assertFold(
                        "all_of(" + pair + ")",
                        Expectations.allOf(customIgnoringSubject(first), customIgnoringSubject(second)),
                        foldAllOf(first, second)
                    ),
                    () -> assertFold(
                        "any_of(" + pair + ")",
                        Expectations.anyOf(customIgnoringSubject(first), customIgnoringSubject(second)),
                        foldAnyOf(first, second)
                    ),
                    () -> assertFold(
                        "sequence(" + pair + ")",
                        Expectations.sequence(customIgnoringSubject(first), customIgnoringSubject(second)),
                        foldAllOf(first, second)
                    )
                );
            }
        }
    }

    @Test
    void aMismatchCarriesRefutationsAndUnknowns() {
        Expectation<OccupantSnapshot> mismatched = Expectations
                .allOf(customIgnoringSubject(mismatchReply()), customIgnoringSubject(unreadableReply()));
        MatchResult.Failed failed = assertInstanceOf(
            MatchResult.Failed.class,
            check(singleCellBlueprint(mismatched), MagicFixtures.view().air(origin))
        );
        assertTrue(MagicFixtures.hasCode(failed, Codes.expected), "the refutation is merged");
        assertTrue(MagicFixtures.hasCode(failed, Codes.unreadableRead), "the unknown is merged");
    }

    @Test
    void sequenceEqualsAllOfIncludingFindings() {
        TestWorld.View view = MagicFixtures.view().air(origin);
        MatchResult sequence = check(
            singleCellBlueprint(
                Expectations.sequence(customIgnoringSubject(mismatchReply()), customIgnoringSubject(unreadableReply()))
            ),
            view
        );
        MatchResult allOf = check(
            singleCellBlueprint(
                Expectations.allOf(customIgnoringSubject(mismatchReply()), customIgnoringSubject(unreadableReply()))
            ),
            view
        );
        assertEquals(allOf.getClass(), sequence.getClass());
        assertEquals(codeTexts(allOf), codeTexts(sequence));
    }

    @Test
    void notKeepsUnreadableAndAbstain() {
        assertFold("not(unreadable)", Expectations.not(customIgnoringSubject(unreadableReply())), "U");
        assertFold("not(abstain)", Expectations.not(customIgnoringSubject(Reply.abstain())), "A");
        assertFold("not(mismatch)", Expectations.not(customIgnoringSubject(mismatchReply())), "T");
    }

    @Test
    @SuppressWarnings("NullAway")
    void combinatorsRejectEmptyAndImpossibleShapes() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class, Expectations::allOf),
            () -> assertThrows(IllegalArgumentException.class, Expectations::anyOf),
            () -> assertThrows(IllegalArgumentException.class, Expectations::sequence),
            () -> assertThrows(IllegalArgumentException.class, () -> Expectations.atLeast(1)),
            () -> assertThrows(IllegalArgumentException.class, () -> Expectations.atMost(1)),
            () -> assertThrows(IllegalArgumentException.class, () -> Expectations.exactly(1)),
            () -> assertThrows(IllegalArgumentException.class, () -> Expectations.atLeast(-1, Expectations.air())),
            () -> assertThrows(IllegalArgumentException.class, () -> Expectations.atMost(-1, Expectations.air())),
            () -> assertThrows(IllegalArgumentException.class, () -> Expectations.exactly(-1, Expectations.air())),
            () -> assertThrows(NullPointerException.class, () -> Expectations.eachCell(null)),
            () -> assertThrows(IllegalArgumentException.class, () -> Reply.mismatch(FindingBundle.empty())),
            () -> assertThrows(IllegalArgumentException.class, () -> Reply.unreadable(FindingBundle.empty())),
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new FocusResult.Unavailable<OccupantSnapshot>(FindingBundle.empty())
            ),
            () -> assertThrows(
                NullPointerException.class,
                () -> new FocusResult.Taken<OccupantSnapshot>(null, List.of(), FindingBundle.empty())
            ),
            () -> assertThrows(IllegalArgumentException.class, AdversarialExpectationTest::buildMismatchedSubjectCheck)
        );
    }

    @Test
    void aRequiredInstanceKeyIsGatedBeforeTheTestRuns() {
        ContextKey<Long> required = ContextKeys.of(id("required_value"), Long.class, Retention.instance);
        AtomicBoolean called = new AtomicBoolean();
        Expectation<OccupantSnapshot> expectation = Expectations.custom(
            OccupantSnapshot.class,
            new ExpectationMeta(id("requires_meta"), 0, Set.of(required), Codes.expected.code()),
            ctx -> {
                called.set(true);
                return Reply.match();
            }
        );
        Blueprint blueprint = singleCellBlueprint(expectation);
        TestWorld.View view = MagicFixtures.view().air(origin);

        MatchResult result = check(blueprint, view);

        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.unreadable, undecided.reason());
        assertTrue(MagicFixtures.hasCode(undecided, Codes.keyAbsent));
        assertFalse(called.get(), "the expectation test is not called when a required key is absent");
    }

    @Test
    void aPerCellCheckStopsAtTheBudget() {
        Blueprint blueprint = envelopeOnly(Cuboid.of(0, 0, 0, 1, 1, 1));
        MatchRequest request = MatchRequest.of(
            blueprint,
            siting,
            new TestWorld.Lease(MagicFixtures.view().air(origin)),
            MagicFixtures.instance,
            MagicFixtures.owner,
            Budget.of(0, Integer.MAX_VALUE),
            emptyClaims()
        );

        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, MatchEngine.check(request));
        assertEquals(UndecidedReason.budgetExhausted, undecided.reason());
        assertTrue(MagicFixtures.hasCode(undecided, Codes.budgetExhausted));
    }

    private static void assertOutcome(
        Expectation<OccupantSnapshot> expectation,
        OccupantSnapshot subject,
        boolean shouldMatch,
        CodeEntry failureCode
    ) {
        Blueprint blueprint = singleCellBlueprint(expectation);
        TestWorld.View view = MagicFixtures.view().put(subject);
        MatchResult result = check(blueprint, view);
        if (shouldMatch) {
            assertInstanceOf(MatchResult.Admitted.class, result);
            return;
        }
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result);
        assertTrue(MagicFixtures.hasCode(failed, failureCode), "the expected failure code is reported");
    }

    private static MatchResult check(Blueprint blueprint, TestWorld.View view) {
        return MagicFixtures.check(blueprint, siting, view, emptyClaims());
    }

    private static void assertAdmitted(Expectation<OccupantSnapshot> expectation) {
        assertInstanceOf(
            MatchResult.Admitted.class,
            check(singleCellBlueprint(expectation), MagicFixtures.view().air(origin))
        );
    }

    private static void assertThresholdFailure(Expectation<OccupantSnapshot> expectation) {
        MatchResult result = check(singleCellBlueprint(expectation), MagicFixtures.view().air(origin));
        MatchResult.Failed failed = assertInstanceOf(MatchResult.Failed.class, result);
        assertTrue(MagicFixtures.hasCode(failed, Codes.thresholdImpossible));
    }

    private static void assertUnreadable(Expectation<OccupantSnapshot> expectation) {
        MatchResult result = check(singleCellBlueprint(expectation), MagicFixtures.view().air(origin));
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.unreadable, undecided.reason());
    }

    private static void assertAbstained(Expectation<OccupantSnapshot> expectation) {
        MatchResult result = check(singleCellBlueprint(expectation), MagicFixtures.view().air(origin));
        MatchResult.Undecided undecided = assertInstanceOf(MatchResult.Undecided.class, result);
        assertEquals(UndecidedReason.abstained, undecided.reason());
    }

    private static Expectation<OccupantSnapshot> atLeast(int count, Reply first, Reply... rest) {
        return Expectations.atLeast(count, children(first, rest));
    }

    private static Expectation<OccupantSnapshot> atMost(int count, Reply first, Reply... rest) {
        return Expectations.atMost(count, children(first, rest));
    }

    private static Expectation<OccupantSnapshot> exactly(int count, Reply first, Reply... rest) {
        return Expectations.exactly(count, children(first, rest));
    }

    private static Expectation<OccupantSnapshot>[] children(Reply first, Reply... rest) {
        @SuppressWarnings("unchecked")
        Expectation<OccupantSnapshot>[] children = new Expectation[1 + rest.length];
        children[0] = customIgnoringSubject(first);
        for (int i = 0; i < rest.length; i++) {
            children[i + 1] = customIgnoringSubject(rest[i]);
        }
        return children;
    }

    private static Reply mismatchReply() {
        return Reply
                .mismatch(Finding.builder(Codes.expected).phase(Phase.check).message("adversarial mismatch").build());
    }

    private static Reply unreadableReply() {
        return Reply.unreadable(
            Finding.builder(Codes.unreadableRead).phase(Phase.check).message("adversarial unreadable").build()
        );
    }

    private static Expectation<OccupantSnapshot> customIgnoringSubject(Reply reply) {
        return Expectations.custom(OccupantSnapshot.class, ExpectationMeta.anonymous(), ctx -> reply);
    }

    private static String foldAllOf(Reply first, Reply second) {
        if (first instanceof Reply.Mismatch || second instanceof Reply.Mismatch) {
            return "M";
        }
        if (first instanceof Reply.Unreadable || second instanceof Reply.Unreadable) {
            return "U";
        }
        if (first instanceof Reply.Abstain || second instanceof Reply.Abstain) {
            return "A";
        }
        return "T";
    }

    private static String foldAnyOf(Reply first, Reply second) {
        if (first instanceof Reply.Match || second instanceof Reply.Match) {
            return "T";
        }
        if (first instanceof Reply.Unreadable || second instanceof Reply.Unreadable) {
            return "U";
        }
        if (first instanceof Reply.Mismatch || second instanceof Reply.Mismatch) {
            return "M";
        }
        return "A";
    }

    private static void assertFold(String caseName, Expectation<OccupantSnapshot> expectation, String expected) {
        MatchResult result = check(singleCellBlueprint(expectation), MagicFixtures.view().air(origin));
        switch (expected) {
            case "T" -> assertInstanceOf(MatchResult.Admitted.class, result, caseName);
            case "M" -> assertInstanceOf(MatchResult.Failed.class, result, caseName);
            case "U" -> assertEquals(
                UndecidedReason.unreadable,
                assertInstanceOf(MatchResult.Undecided.class, result, caseName).reason(),
                caseName
            );
            case "A" -> assertEquals(
                UndecidedReason.abstained,
                assertInstanceOf(MatchResult.Undecided.class, result, caseName).reason(),
                caseName
            );
            default -> throw new AssertionError(caseName + ": unknown fold " + expected);
        }
    }

    private static List<String> codeTexts(MatchResult result) {
        return result.findings().findings().stream().map(finding -> finding.code().text()).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void buildMismatchedSubjectCheck() {
        Bay bay = Bay.of(id("subject_bay"), oneCell, Coverage.everyCell, Footprint.none(), Sharing.shared);
        Check.of(
            id("mismatched_subject"),
            bay,
            (Focus) Focuses.occupant(origin),
            (Expectation) Expectations.eachCell(Expectations.anyOccupant())
        );
    }

    private static Cuboid boundingBox(CuboidSet cells) {
        return Objects.requireNonNull(cells.bounds(), "bounds");
    }

    private static Blueprint singleCellBlueprint(Expectation<OccupantSnapshot> expectation) {
        Bay bay = Bay.of(id("probe_bay"), oneCell, Coverage.everyCell, Footprint.none(), Sharing.shared);
        return Blueprint.builder(id("probe")).bounds(boundingBox(oneCell))
                .check(Check.of(id("probe_check"), bay, Focuses.occupant(origin), expectation)).build();
    }

    private static Blueprint envelopeOnly(Cuboid bounds) {
        return Blueprint.builder(id("envelope_probe")).bounds(bounds).envelope().build();
    }

    private static ClaimView emptyClaims() {
        return new ClaimView(List.of(), 0, true);
    }

    private static NamespacedId id(String path) {
        return MagicFixtures.id(path);
    }

    private static OccupantSnapshot air() {
        return TestWorld.airOccupant(origin);
    }

    private static OccupantSnapshot block(NamespacedId type) {
        return blockAt(type, origin);
    }

    private static OccupantSnapshot blockAt(NamespacedId type, LatticePoint cell) {
        return TestWorld.block(type, cell);
    }

    private static OccupantSnapshot entity(String type) {
        return entityAt(type, origin);
    }

    private static OccupantSnapshot entityAt(String type, LatticePoint cell) {
        NamespacedId id = NamespacedId.of("minecraft", type.substring("minecraft:".length()));
        OccupantRef ref = new OccupantRef(TestWorld.snapshot, 0, id, OccupantKind.entity, cell);
        return new OccupantSnapshot(ref, new OccupantDescriptor(id, OccupantKind.entity), Cuboid.of(cell), List.of());
    }
}
