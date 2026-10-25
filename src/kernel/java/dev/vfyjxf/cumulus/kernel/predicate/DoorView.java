package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;

import java.util.List;
import java.util.Objects;

/** Both halves of one door, already read; a door test never looks the second half up again. */
public record DoorView(OccupantRef first, OccupantRef second, BayView bay) {

    public DoorView {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(bay, "bay");
    }

    public List<OccupantRef> halves() {
        return List.of(first, second);
    }
}
