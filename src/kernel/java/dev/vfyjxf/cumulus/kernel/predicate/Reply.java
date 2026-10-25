package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.Finding;
import dev.vfyjxf.cumulus.kernel.base.FindingBundle;

import java.util.Objects;

/** The four states one check answers in: a witness, a refutation, an unreadable snapshot, a refusal. */
public sealed interface Reply permits Reply.Match, Reply.Mismatch, Reply.Unreadable, Reply.Abstain {

    record Match(Bindings bindings) implements Reply {

        public Match {
            Objects.requireNonNull(bindings, "bindings");
        }
    }

    record Mismatch(FindingBundle findings) implements Reply {

        public Mismatch {
            Objects.requireNonNull(findings, "findings");
            if (findings.isEmpty()) {
                throw new IllegalArgumentException("a mismatch carries at least one finding");
            }
        }
    }

    record Unreadable(FindingBundle findings) implements Reply {

        public Unreadable {
            Objects.requireNonNull(findings, "findings");
            if (findings.isEmpty()) {
                throw new IllegalArgumentException("an unreadable reply carries at least one finding");
            }
        }
    }

    record Abstain() implements Reply {
    }

    static Reply match() {
        return new Match(Bindings.EMPTY);
    }

    static Reply match(Bindings bindings) {
        return new Match(bindings);
    }

    static Reply mismatch(Finding first, Finding... rest) {
        return new Mismatch(bundle(first, rest));
    }

    static Reply mismatch(FindingBundle findings) {
        return new Mismatch(findings);
    }

    static Reply unreadable(Finding first, Finding... rest) {
        return new Unreadable(bundle(first, rest));
    }

    static Reply unreadable(FindingBundle findings) {
        return new Unreadable(findings);
    }

    static Reply abstain() {
        return new Abstain();
    }

    /**
     * The attempt fold over independent checks: {@code UNREADABLE > MISMATCH > ABSTAIN > MATCH}, findings
     * accumulating left to right and two matches merging their bindings.
     */
    static Reply combine(Reply left, Reply right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left instanceof Match leftMatch && right instanceof Match rightMatch) {
            return leftMatch.bindings().merge(rightMatch.bindings());
        }
        int leftRank = rank(left);
        int rightRank = rank(right);
        if (leftRank > rightRank) {
            return left;
        }
        if (rightRank > leftRank) {
            return right;
        }
        if (left instanceof Abstain) {
            return left;
        }
        FindingBundle findings = findingsOf(left).plus(findingsOf(right));
        return left instanceof Unreadable ? unreadable(findings) : mismatch(findings);
    }

    private static int rank(Reply reply) {
        if (reply instanceof Unreadable) {
            return 3;
        }
        if (reply instanceof Mismatch) {
            return 2;
        }
        return reply instanceof Abstain ? 1 : 0;
    }

    private static FindingBundle findingsOf(Reply reply) {
        if (reply instanceof Mismatch mismatch) {
            return mismatch.findings();
        }
        if (reply instanceof Unreadable unreadable) {
            return unreadable.findings();
        }
        return FindingBundle.empty();
    }

    private static FindingBundle bundle(Finding first, Finding... rest) {
        Objects.requireNonNull(first, "first");
        FindingBundle bundle = FindingBundle.of(first);
        for (Finding finding : rest) {
            bundle = bundle.plus(Objects.requireNonNull(finding, "finding"));
        }
        return bundle;
    }
}
