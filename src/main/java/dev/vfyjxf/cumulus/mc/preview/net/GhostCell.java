package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.Map;
import java.util.Objects;

/**
 * A client render description of one expected cell: block id, the serialized state properties and
 * the stage the cell belongs to.
 */
public record GhostCell(LatticePoint cell, NamespacedId block, Map<String, String> properties, int stageIndex) {

    public GhostCell {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(block, "block");
        properties = Map.copyOf(properties);
        if (stageIndex < 0) {
            throw new IllegalArgumentException("stageIndex must name a stage: " + stageIndex);
        }
    }
}
