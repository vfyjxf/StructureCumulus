package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.occupant.NestedLevelSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.SectionSlot;
import dev.vfyjxf.cumulus.kernel.occupant.WorldView;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class TestWorld implements WorldView {

    private final Map<LatticePoint, OccupantSnapshot> cells = new HashMap<>();
    private final Set<LatticePoint> unreadable = new HashSet<>();

    static TestWorld create() {
        return new TestWorld();
    }

    TestWorld put(LatticePoint cell, OccupantSnapshot snapshot) {
        cells.put(cell, snapshot);
        return this;
    }

    TestWorld unreadable(LatticePoint cell) {
        unreadable.add(cell);
        return this;
    }

    @Override
    public SectionSlot slot(SectionKey key) {
        return new SectionSlot.Air(0L);
    }

    @Override
    public @Nullable OccupantSnapshot occupant(LatticePoint cell) {
        return unreadable.contains(cell) ? null : cells.get(cell);
    }

    @Override
    public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
        for (OccupantSnapshot snapshot : cells.values()) {
            if (snapshot.ref().equals(ref)) {
                return snapshot;
            }
        }
        return null;
    }

    @Override
    public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
        List<LatticePoint> ordered = new ArrayList<>(cells.keySet());
        ordered.sort(Comparator.naturalOrder());
        Map<OccupantRef, OccupantSnapshot> distinct = new LinkedHashMap<>();
        for (LatticePoint cell : ordered) {
            if (!box.contains(cell) || unreadable.contains(cell)) {
                continue;
            }
            OccupantSnapshot snapshot = Objects.requireNonNull(cells.get(cell), "cell");
            if (filter.matches(snapshot)) {
                distinct.putIfAbsent(snapshot.ref(), snapshot);
            }
        }
        return List.copyOf(distinct.values());
    }

    @Override
    public @Nullable NestedLevelSnapshot nested(LatticePoint cell) {
        return null;
    }

    @Override
    public long contentDigest(SectionKey key) {
        return 0L;
    }
}
