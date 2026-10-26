package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The box one entity type may move in, inflated by {@link #MAX_ENTITY_SPAN}. */
public record EntityWindow(Cuboid cells, NamespacedId type) {

    public static final int MAX_ENTITY_SPAN = 3;

    public EntityWindow {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(type, "type");
    }

    public static @Nullable EntityWindow of(Cuboid declared, NamespacedId type) {
        Objects.requireNonNull(declared, "declared");
        Objects.requireNonNull(type, "type");
        if (declared.isEmpty()) {
            return null;
        }
        int minX = clamp(declared.min().x() - MAX_ENTITY_SPAN, LatticePoint.MIN_XZ, LatticePoint.MAX_XZ);
        int minY = clamp(declared.min().y() - MAX_ENTITY_SPAN, LatticePoint.MIN_Y, LatticePoint.MAX_Y);
        int minZ = clamp(declared.min().z() - MAX_ENTITY_SPAN, LatticePoint.MIN_XZ, LatticePoint.MAX_XZ);
        int maxX = clamp(declared.max().x() + MAX_ENTITY_SPAN, LatticePoint.MIN_XZ, LatticePoint.MAX_XZ);
        int maxY = clamp(declared.max().y() + MAX_ENTITY_SPAN, LatticePoint.MIN_Y, LatticePoint.MAX_Y);
        int maxZ = clamp(declared.max().z() + MAX_ENTITY_SPAN, LatticePoint.MIN_XZ, LatticePoint.MAX_XZ);
        return new EntityWindow(Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ), type);
    }

    private static int clamp(long value, int min, int max) {
        return (int) Math.max(min, Math.min(max, value));
    }
}
