package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The box one entity type may move in, inflated by {@link #maxEntitySpan}. */
public record EntityWindow(Cuboid cells, NamespacedId type) {

    public static final int maxEntitySpan = 3;

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
        int minX = clamp(declared.min().x() - maxEntitySpan, LatticePoint.minXz, LatticePoint.maxXz);
        int minY = clamp(declared.min().y() - maxEntitySpan, LatticePoint.minY, LatticePoint.maxY);
        int minZ = clamp(declared.min().z() - maxEntitySpan, LatticePoint.minXz, LatticePoint.maxXz);
        int maxX = clamp(declared.max().x() + maxEntitySpan, LatticePoint.minXz, LatticePoint.maxXz);
        int maxY = clamp(declared.max().y() + maxEntitySpan, LatticePoint.minY, LatticePoint.maxY);
        int maxZ = clamp(declared.max().z() + maxEntitySpan, LatticePoint.minXz, LatticePoint.maxXz);
        return new EntityWindow(Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ), type);
    }

    private static int clamp(long value, int min, int max) {
        return (int) Math.max(min, Math.min(max, value));
    }
}
