package dev.vfyjxf.cumulus.kernel.claim;

import dev.vfyjxf.cumulus.kernel.base.FindingBundle;

import java.util.List;
import java.util.Objects;

public sealed interface ClaimVerdict
        permits ClaimVerdict.Settled, ClaimVerdict.ClaimConflict, ClaimVerdict.RevisionMoved {

    record Settled(long revision, List<ClaimRow> rows) implements ClaimVerdict {

        public Settled {
            rows = ClaimRow.ordered(rows);
            if (revision < 0L) {
                throw new IllegalArgumentException("negative revision: " + revision);
            }
        }
    }

    record ClaimConflict(List<ClaimRow> rows, FindingBundle findings) implements ClaimVerdict {

        public ClaimConflict {
            rows = ClaimRow.ordered(rows);
            Objects.requireNonNull(findings, "findings");
        }
    }

    record RevisionMoved(long expected, long actual) implements ClaimVerdict {
    }
}
