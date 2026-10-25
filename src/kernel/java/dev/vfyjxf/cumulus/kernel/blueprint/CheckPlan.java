package dev.vfyjxf.cumulus.kernel.blueprint;

import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.context.Computation;
import dev.vfyjxf.cumulus.kernel.predicate.Check;

import java.util.List;
import java.util.Objects;

/**
 * One stage's expansion: section computations, the implicit barrier, aggregate computations and
 * non-{@code DESIGN} derivations in dependency order, then checks. A check never runs before the
 * barrier of its stage.
 */
public record CheckPlan(Stage stage, List<Computation<?>> computations, List<DerivationSpec> derivations,
                        List<Check<?>> checks) {

    public CheckPlan {
        Objects.requireNonNull(stage, "stage");
        computations = List.copyOf(computations);
        derivations = List.copyOf(derivations);
        checks = List.copyOf(checks);
    }
}
