package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cell-level solve preferences (D-86). They order or pick among equally legal candidates; they never
 * change an applicability rung and never fabricate an edit. All cell sets are world-frame at the
 * solve siting. The list and the map tolerate a null argument, which means empty; the accessors
 * never return null.
 */
public record SolvePreferences(List<CuboidSet> preferredCells, Map<NamespacedId, CuboidSet> partPins) {

    @SuppressWarnings("NullAway") // the null tolerance is the contract: a null list or map means empty
    public SolvePreferences {
        preferredCells = preferredCells == null ? List.of() : List.copyOf(preferredCells);
        partPins = partPins == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(partPins));
    }
}
