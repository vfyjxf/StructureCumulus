package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.FindingBundle;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;

import java.util.List;
import java.util.Objects;

/** The claim planner either produces the rows to settle or refuses the design, never a partial set. */
public sealed interface ClaimPlan permits ClaimPlan.Rows, ClaimPlan.Invalid {

    record Rows(List<ClaimRow> rows) implements ClaimPlan {
        public Rows(List<ClaimRow> rows) {
            this.rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }
    }

    record Invalid(FindingBundle findings) implements ClaimPlan {
        public Invalid(FindingBundle findings) {
            this.findings = Objects.requireNonNull(findings, "findings");
            if (findings.findings().isEmpty()) {
                throw new IllegalArgumentException("an invalid claim plan needs at least one finding");
            }
        }
    }
}
