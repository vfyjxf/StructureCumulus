package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.occupant.NestedLevelSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;

import java.util.Objects;

/** One nested occupant read in its own frame. */
public record NestedView(NestedLevelSnapshot level, BayView bay) {

    public NestedView {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(bay, "bay");
    }

    public OccupantRef parent() {
        return level.parent();
    }
}
