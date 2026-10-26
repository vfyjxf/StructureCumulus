package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

/** Converts a locus between the local and world frames of one siting. */
public record LocusMap(Siting siting) {

    public static LocusMap of(Siting siting) {
        return new LocusMap(siting);
    }

    public @Nullable Locus toWorld(Locus local) {
        return switch (local.frame()) {
            case world -> local;
            case local -> {
                CuboidSet world = siting.toWorld(local.cells());
                yield world == null ? null : Locus.world(world);
            }
            case definition, key -> null;
        };
    }

    public @Nullable Locus toLocal(Locus world) {
        return switch (world.frame()) {
            case local -> world;
            case world -> {
                CuboidSet local = siting.toLocal(world.cells());
                yield local == null ? null : Locus.local(local);
            }
            case definition, key -> null;
        };
    }
}
