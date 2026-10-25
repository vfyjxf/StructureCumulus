package dev.vfyjxf.cumulus.kernel.blueprint;

import dev.vfyjxf.cumulus.kernel.context.Computation;
import dev.vfyjxf.cumulus.kernel.predicate.Check;

import java.util.List;
import java.util.Objects;

/** The units a bay declares; the stage plan is their expansion, not their declaration. */
public record Requirement(List<Check<?>> checks, List<Computation<?>> computations) {

    public Requirement {
        checks = List.copyOf(checks);
        computations = List.copyOf(computations);
        for (Check<?> check : checks) {
            Objects.requireNonNull(check, "check");
        }
        for (Computation<?> computation : computations) {
            Objects.requireNonNull(computation, "computation");
        }
        if (checks.isEmpty() && computations.isEmpty()) {
            throw new IllegalArgumentException("SC-CHK-0107: a bay that asserts nothing is a seam, not a bay");
        }
    }
}
