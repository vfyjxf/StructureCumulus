package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.Map;
import java.util.Objects;

/** A client render description of one expected cell: block id plus the serialized state properties. */
public record GhostCell(LatticePoint cell, NamespacedId block, Map<String, String> properties) {

    public GhostCell {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(block, "block");
        properties = Map.copyOf(properties);
    }
}
