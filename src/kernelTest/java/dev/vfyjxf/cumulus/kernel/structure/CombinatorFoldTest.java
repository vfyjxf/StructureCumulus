package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The §4.3 fold table over the four reply states, findings included. */
class CombinatorFoldTest {

    @Test
    void allOfAnyOfSequenceFollowTheFoldTable() {
        List<Reply> states = List.of(Reply.match(), mismatch(), unreadable(), Reply.abstain());
        for (Reply first : states) {
            for (Reply second : states) {
                String pair = name(first) + "+" + name(second);
                assertAll(
                    () -> assertFold(
                        "all_of(" + pair + ")",
                        Expectations.allOf(leaf(first), leaf(second)),
                        allOfFold(first, second)
                    ),
                    () -> assertFold(
                        "any_of(" + pair + ")",
                        Expectations.anyOf(leaf(first), leaf(second)),
                        anyOfFold(first, second)
                    ),
                    () -> assertFold(
                        "sequence(" + pair + ")",
                        Expectations.sequence(leaf(first), leaf(second)),
                        allOfFold(first, second)
                    )
                );
            }
        }
    }

    @Test
    void aMismatchCarriesRefutationsAndUnknowns() {
        Reply.Mismatch failed = assertInstanceOf(
            Reply.Mismatch.class,
            Expectations.allOf(leaf(mismatch()), leaf(unreadable())).test(context())
        );
        assertEquals(
            Set.of(Codes.expected.code(), Codes.unreadableRead.code()),
            Set.copyOf(failed.findings().findings().stream().map(Finding::code).toList())
        );
    }

    @Test
    void sequenceEqualsAllOfIncludingFindings() {
        Reply sequence = Expectations.sequence(leaf(mismatch()), leaf(unreadable())).test(context());
        Reply allOf = Expectations.allOf(leaf(mismatch()), leaf(unreadable())).test(context());
        assertEquals(allOf.getClass(), sequence.getClass());
        assertEquals(codes(allOf), codes(sequence));
    }

    @Test
    void notKeepsUnreadableAndAbstain() {
        Reply unreadable = unreadable();
        assertSame(unreadable, Expectations.not(leaf(unreadable)).test(context()));
        assertInstanceOf(Reply.Abstain.class, Expectations.not(leaf(Reply.abstain())).test(context()));
        assertInstanceOf(Reply.Match.class, Expectations.not(leaf(mismatch())).test(context()));
        Reply.Mismatch negated = assertInstanceOf(
            Reply.Mismatch.class,
            Expectations.not(leaf(Reply.match())).test(context())
        );
        assertEquals(List.of(Codes.negationMatched.code()), codes(negated));
    }

    @Test
    void thresholdAbstainBranches() {
        assertInstanceOf(Reply.Abstain.class, Expectations.atMost(0, leaf(Reply.abstain())).test(context()));
        assertInstanceOf(
            Reply.Abstain.class,
            Expectations.exactly(1, leaf(Reply.match()), leaf(Reply.abstain())).test(context())
        );
        assertInstanceOf(
            Reply.Abstain.class,
            Expectations.atLeast(2, leaf(Reply.match()), leaf(Reply.abstain())).test(context())
        );
    }

    @Test
    void aFailingThresholdAddsExp0003() {
        Reply.Mismatch failed = assertInstanceOf(
            Reply.Mismatch.class,
            Expectations.atLeast(2, leaf(Reply.match()), leaf(mismatch())).test(context())
        );
        assertTrue(codes(failed).contains(Codes.thresholdImpossible.code()), () -> codes(failed).toString());
        assertTrue(codes(failed).contains(Codes.expected.code()), () -> codes(failed).toString());
    }

    private static Expectation<String> leaf(Reply reply) {
        return Expectations.custom(String.class, ExpectationMeta.anonymous(), ctx -> reply);
    }

    private static Reply mismatch() {
        return Reply.mismatch(FindingBundle.of(List.of(finding(Codes.expected, "leaf refutation"))));
    }

    private static Reply unreadable() {
        return Reply.unreadable(FindingBundle.of(List.of(finding(Codes.unreadableRead, "leaf unknown"))));
    }

    private static Finding finding(CodeEntry entry, String message) {
        return Finding.builder(entry).phase(Phase.check).message(message).build();
    }

    private static String allOfFold(Reply first, Reply second) {
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

    private static String anyOfFold(Reply first, Reply second) {
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

    private static String name(Reply reply) {
        return switch (reply) {
            case Reply.Match ignored -> "T";
            case Reply.Mismatch ignored -> "M";
            case Reply.Unreadable ignored -> "U";
            case Reply.Abstain ignored -> "A";
        };
    }

    private static void assertFold(String caseName, Expectation<String> expectation, String expected) {
        Reply reply = expectation.test(context());
        switch (expected) {
            case "T" -> assertInstanceOf(Reply.Match.class, reply, caseName);
            case "M" -> assertInstanceOf(Reply.Mismatch.class, reply, caseName);
            case "U" -> assertInstanceOf(Reply.Unreadable.class, reply, caseName);
            case "A" -> assertInstanceOf(Reply.Abstain.class, reply, caseName);
            default -> throw new AssertionError(caseName + ": unknown fold " + expected);
        }
    }

    private static List<FindingCode> codes(Reply reply) {
        FindingBundle bundle = switch (reply) {
            case Reply.Mismatch mismatch -> mismatch.findings();
            case Reply.Unreadable unreadable -> unreadable.findings();
            default -> FindingBundle.empty();
        };
        return bundle.findings().stream().map(Finding::code).toList();
    }

    private static ExpectationContext<String> context() {
        return ExpectationContext.of(new FoldContext(), "subject", List.of());
    }

    private record FoldContext() implements FocusContext {

        @Override
        public Phase phase() {
            return Phase.check;
        }

        @Override
        public Locus locus() {
            return Locus.local(CuboidSet.of(Cuboid.of(LatticePoint.origin)));
        }

        @Override
        public BayView bay() {
            throw new AssertionError("the fold does not read the bay");
        }

        @Override
        public WorldView view() {
            throw new AssertionError("the fold does not read the view");
        }

        @Override
        public Siting siting() {
            throw new AssertionError("the fold does not read the siting");
        }

        @Override
        public InstanceId instance() {
            throw new AssertionError("the fold does not read the instance");
        }

        @Override
        public CuboidSet cells() {
            throw new AssertionError("the fold does not read cells");
        }

        @Override
        public ContextView context() {
            throw new AssertionError("the fold does not read the context");
        }

        @Override
        public Budget remaining() {
            throw new AssertionError("the fold does not read the budget");
        }

        @Override
        public CancelToken cancel() {
            throw new AssertionError("the fold does not read the cancel token");
        }

        @Override
        public CellRead read(LatticePoint cell) {
            throw new AssertionError("the fold does not read cells");
        }

        @Override
        public List<ClaimRow> claimsAt(LatticePoint cell) {
            throw new AssertionError("the fold does not read claims");
        }
    }
}
