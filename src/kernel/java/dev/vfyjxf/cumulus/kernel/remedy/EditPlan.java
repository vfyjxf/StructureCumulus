package dev.vfyjxf.cumulus.kernel.remedy;

import java.util.List;
import java.util.Objects;

/** An ordered, host-applicable plan. */
public record EditPlan(List<EditStep> steps, String rationale) {

    public EditPlan {
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(rationale, "rationale");
        steps = List.copyOf(steps);
    }
}
