package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
/** The four-state answer of one expectation. */
public sealed interface Reply {
    record Match() implements Reply {
    }

    record Mismatch(FindingBundle findings) implements Reply {
        public Mismatch {
            Objects.requireNonNull(findings, "findings");
            if (findings.isEmpty()) {
                throw new IllegalArgumentException("a mismatch needs at least one finding");
            }
        }
    }

    record Unreadable(FindingBundle findings) implements Reply {
        public Unreadable {
            Objects.requireNonNull(findings, "findings");
            if (findings.isEmpty()) {
                throw new IllegalArgumentException("an unreadable reply needs at least one finding");
            }
        }
    }

    record Abstain() implements Reply {
    }

    static Reply match() {
        return new Match();
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

    private static FindingBundle bundle(Finding first, Finding... rest) {
        List<Finding> findings = new ArrayList<>(1 + rest.length);
        findings.add(Objects.requireNonNull(first, "first"));
        for (Finding finding : rest) {
            findings.add(Objects.requireNonNull(finding, "finding"));
        }
        return FindingBundle.of(findings);
    }
}
