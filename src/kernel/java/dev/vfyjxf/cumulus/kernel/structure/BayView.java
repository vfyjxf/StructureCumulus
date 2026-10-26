package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/** A bay's read face over a snapshot. */
public record BayView(NamespacedId id, CuboidSet cells, WorldView view, Siting siting) {

    public BayView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(siting, "siting");
    }

    public @Nullable OccupantSnapshot occupantAt(LatticePoint local) {
        LatticePoint world = siting.toWorld(local);
        return world == null ? null : view.occupant(world);
    }

    public List<OccupantSnapshot> occupants() {
        return occupants(OccupantFilter.any());
    }

    public List<OccupantSnapshot> occupants(OccupantFilter filter) {
        CuboidSet worldCells = siting.toWorld(cells);
        if (worldCells == null) {
            return List.of();
        }
        Cuboid bounds = worldCells.bounds();
        if (bounds == null) {
            return List.of();
        }
        Map<OccupantRef, OccupantSnapshot> unique = new LinkedHashMap<>();
        for (OccupantSnapshot candidate : view.occupants(bounds, filter)) {
            if (worldCells.intersects(candidate.footprint().asSet())) {
                unique.putIfAbsent(candidate.ref(), candidate);
            }
        }
        List<OccupantSnapshot> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(OccupantSnapshot::ref));
        return List.copyOf(result);
    }
}
