package dev.vfyjxf.cumulus.kernel.world;

import java.util.Objects;

/** The total answer of one cell read. */
public sealed interface CellRead {

    record Found(OccupantSnapshot occupant) implements CellRead {
        public Found {
            Objects.requireNonNull(occupant, "occupant");
        }
    }

    record Unreadable(ReadRefusal refusal) implements CellRead {
        public Unreadable {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
