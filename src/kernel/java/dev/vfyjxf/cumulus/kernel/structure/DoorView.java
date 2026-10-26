package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;

import java.util.Objects;
/** The two halves of a door and the bay they sit in. */
public record DoorView(OccupantSnapshot first, OccupantSnapshot second, BayView bay) {

    public DoorView {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(bay, "bay");
    }
}
