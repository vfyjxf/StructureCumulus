package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
/** The ledger's answer to a settle or release. */
public sealed interface ClaimVerdict {
    record Settled(long revision, List<ClaimRow> rows) implements ClaimVerdict {
        public Settled {
            Objects.requireNonNull(rows, "rows");
            if (rows.isEmpty() ? revision < 0 : revision < 1) {
                throw new IllegalArgumentException("a writing settlement needs revision >= 1");
            }
            List<ClaimRow> sorted = new ArrayList<>(rows);
            sorted.sort(ClaimRow::compareTo);
            rows = List.copyOf(sorted);
        }
    }

    record Conflict(List<ClaimRow> incumbents, FindingBundle findings) implements ClaimVerdict {
        public Conflict {
            Objects.requireNonNull(incumbents, "incumbents");
            Objects.requireNonNull(findings, "findings");
            if (incumbents.isEmpty()) {
                throw new IllegalArgumentException("a conflict has at least one incumbent");
            }
            if (findings.isEmpty()) {
                throw new IllegalArgumentException("a conflict explains itself");
            }
            List<ClaimRow> sorted = new ArrayList<>(incumbents);
            sorted.sort(ClaimRow::compareTo);
            incumbents = List.copyOf(sorted);
        }
    }

    record RevisionMoved(long expected, long actual) implements ClaimVerdict {
        public RevisionMoved {
            if (expected < 0 || actual < 0) {
                throw new IllegalArgumentException("revisions >= 0");
            }
        }
    }
}
