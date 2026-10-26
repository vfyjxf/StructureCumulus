package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.finding.Applicability;

import java.util.Objects;

/** A repair or a preview. */
public sealed interface Remedy {
    record Repair(EditPlan plan, Applicability applicability, String rationale) implements Remedy {
        public Repair {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(applicability, "applicability");
            Objects.requireNonNull(rationale, "rationale");
        }
    }

    record Preview(OverlayModel overlay, String rationale) implements Remedy {
        public Preview {
            Objects.requireNonNull(overlay, "overlay");
            Objects.requireNonNull(rationale, "rationale");
        }
    }
}
