package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** One node of the visual hierarchy: root, satellite, sub-satellite or substructure. */
public record VisualHierarchyNode(
    InstanceId id,
    @Nullable InstanceId parent,
    int depth,
    String label,
    String groupId,
    LatticePoint origin
) {

    public VisualHierarchyNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(origin, "origin");
        if (depth < 0) {
            throw new IllegalArgumentException("depth >= 0");
        }
    }
}
