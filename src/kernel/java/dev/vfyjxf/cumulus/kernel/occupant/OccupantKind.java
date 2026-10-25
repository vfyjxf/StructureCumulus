package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

import java.util.Objects;

/**
 * The family an occupant belongs to; a host family is any other id.
 */
public record OccupantKind(NamespacedId id) {

    public static final OccupantKind BLOCK = new OccupantKind(NamespacedId.of("structurecumulus", "block"));
    public static final OccupantKind BLOCK_ENTITY =
            new OccupantKind(NamespacedId.of("structurecumulus", "block_entity"));
    public static final OccupantKind ENTITY = new OccupantKind(NamespacedId.of("structurecumulus", "entity"));
    public static final OccupantKind NESTED = new OccupantKind(NamespacedId.of("structurecumulus", "nested"));

    public OccupantKind {
        Objects.requireNonNull(id, "id");
    }

    public static OccupantKind of(NamespacedId id) {
        return new OccupantKind(id);
    }
}
