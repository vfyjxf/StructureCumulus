package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.WorldView;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One bay's rectangle read against one frozen view; a cell outside the bay answers like an unread one. */
public record BayView(NamespacedId id, Cuboid cells, WorldView view) {

    public BayView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(view, "view");
    }

    public @Nullable OccupantSnapshot occupantAt(LatticePoint cell) {
        Objects.requireNonNull(cell, "cell");
        return cells.contains(cell) ? view.occupant(cell) : null;
    }

    public List<OccupantSnapshot> occupants() {
        return view.occupants(cells, OccupantFilter.any());
    }

    public List<LatticePoint> points() {
        List<LatticePoint> points = new ArrayList<>();
        for (int x = cells.min().x(); x < cells.max().x(); x++) {
            for (int y = cells.min().y(); y < cells.max().y(); y++) {
                for (int z = cells.min().z(); z < cells.max().z(); z++) {
                    points.add(LatticePoint.of(x, y, z));
                }
            }
        }
        return List.copyOf(points);
    }
}
