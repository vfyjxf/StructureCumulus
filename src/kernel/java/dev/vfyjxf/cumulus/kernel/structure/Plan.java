package dev.vfyjxf.cumulus.kernel.structure;

import java.util.List;
import java.util.Objects;
/** The ordered units of one check. */
public record Plan(List<SectionComputation<?>> sections, List<AggregateComputation<?>> aggregates,
                   List<Check<?>> checks) {

    public Plan {
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(aggregates, "aggregates");
        Objects.requireNonNull(checks, "checks");
        sections = List.copyOf(sections);
        aggregates = List.copyOf(aggregates);
        checks = List.copyOf(checks);
    }
}
