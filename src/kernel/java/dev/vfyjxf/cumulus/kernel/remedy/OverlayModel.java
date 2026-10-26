package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
/** Pure overlay data. Rendering is the host's. */
public record OverlayModel(Cuboid bounds, Map<LatticeKey, CellMark> cells,
                           List<Marker> markers) {

    public OverlayModel {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(markers, "markers");
        cells = Collections.unmodifiableMap(new TreeMap<>(cells));
        markers = List.copyOf(markers);
    }
}
