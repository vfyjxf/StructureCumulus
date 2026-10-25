package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;

import java.util.Objects;

/**
 * The declared box plus one entity type; a type wider than {@link #MAX_ENTITY_SPAN} is refused elsewhere.
 */
public record EntityWindow(Cuboid cells, NamespacedId type) {

    public static final int MAX_ENTITY_SPAN = 3;

    public EntityWindow {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(type, "type");
    }
}
