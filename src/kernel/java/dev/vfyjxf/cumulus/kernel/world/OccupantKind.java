package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;

import java.util.Objects;

/** An open occupant family with four reserved constants. */
public record OccupantKind(NamespacedId id) implements Comparable<OccupantKind> {

    public static final OccupantKind block = new OccupantKind(NamespacedId.of("block"));
    public static final OccupantKind blockEntity = new OccupantKind(NamespacedId.of("block_entity"));
    public static final OccupantKind entity = new OccupantKind(NamespacedId.of("entity"));
    public static final OccupantKind nested = new OccupantKind(NamespacedId.of("nested"));

    public OccupantKind {
        Objects.requireNonNull(id, "id");
    }

    public static OccupantKind of(NamespacedId id) {
        return new OccupantKind(id);
    }

    @Override
    public int compareTo(OccupantKind other) {
        return id.compareTo(other.id);
    }
}
