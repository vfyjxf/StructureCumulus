package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;

import java.util.List;
import java.util.Objects;

/** A bay and the subjects its checks bound. */
public record BayBinding(Bay bay, List<OccupantSnapshot> subjects) {

    public BayBinding {
        Objects.requireNonNull(bay, "bay");
        Objects.requireNonNull(subjects, "subjects");
        subjects = List.copyOf(subjects);
    }
}
