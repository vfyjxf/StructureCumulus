package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
/** One frozen section's non-air occupants. */
public record SectionSnapshot(SectionKey key, Map<LatticePoint, OccupantSnapshot> occupants,
                              long contentDigest) {

    private static final Comparator<OccupantSnapshot> REF_ORDER =
            Comparator.comparing(OccupantSnapshot::ref);

    public SectionSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(occupants, "occupants");
        TreeMap<LatticePoint, OccupantSnapshot> sorted = new TreeMap<>();
        for (Map.Entry<LatticePoint, OccupantSnapshot> entry : occupants.entrySet()) {
            sorted.put(Objects.requireNonNull(entry.getKey(), "cell"),
                    Objects.requireNonNull(entry.getValue(), "occupant"));
        }
        occupants = Collections.unmodifiableMap(sorted);
    }

    public static SectionSnapshot of(SectionKey key, Map<LatticePoint, OccupantSnapshot> occupants,
                                     long contentDigest) {
        return new SectionSnapshot(key, occupants, contentDigest);
    }

    public @Nullable OccupantSnapshot occupantAt(LatticePoint cell) {
        return occupants.get(cell);
    }

    public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
        List<OccupantSnapshot> result = new ArrayList<>();
        for (OccupantSnapshot occupant : occupants.values()) {
            if (occupant.footprint().intersects(box) && filter.matches(occupant)) {
                result.add(occupant);
            }
        }
        result.sort(REF_ORDER);
        return List.copyOf(result);
    }
}
