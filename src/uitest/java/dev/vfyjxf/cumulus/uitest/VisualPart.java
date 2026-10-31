package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** One named part of a staged structure: role, description, color and the bounds it colors. */
public record VisualPart(
    InstanceId instance,
    String name,
    VisualPartRole role,
    int color,
    String description,
    CuboidSet bounds
) {

    public VisualPart {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(bounds, "bounds");
        if (name.isBlank() || description.isBlank()) {
            throw new IllegalArgumentException("part name and description must not be blank");
        }
    }

    static @Nullable VisualPart of(InstanceId instance, String name, VisualPartRole role, CuboidSet bounds) {
        if (instance == null || name == null || role == null || bounds == null) {
            return null;
        }
        return new VisualPart(instance, name, role, role.color(), role.description(), bounds);
    }
}
