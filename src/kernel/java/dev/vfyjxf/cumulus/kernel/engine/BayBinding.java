package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import dev.vfyjxf.cumulus.kernel.predicate.Bindings;
import java.util.List;
import java.util.Objects;

/** One bay's matched subjects and bindings: the only input claim planning needs beyond the bay. */
public record BayBinding(NamespacedId bay, List<OccupantRef> subjects, Bindings bindings) {

    public BayBinding(NamespacedId bay, List<OccupantRef> subjects, Bindings bindings) {
        this.bay = Objects.requireNonNull(bay, "bay");
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        this.bindings = Objects.requireNonNull(bindings, "bindings");
    }
}
