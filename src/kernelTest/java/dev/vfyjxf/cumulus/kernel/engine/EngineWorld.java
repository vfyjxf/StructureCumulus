package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.InstanceId;
import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.occupant.NestedLevelSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantKind;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.ReadStatus;
import dev.vfyjxf.cumulus.kernel.occupant.SectionSlot;
import dev.vfyjxf.cumulus.kernel.occupant.WorldView;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** A frozen view with air by default and whole sections that can be refused. */
final class EngineWorld implements WorldView {

    static final NamespacedId DIMENSION = NamespacedId.of("test", "world");
    static final InstanceId INSTANCE = InstanceId.of(DIMENSION, 1L);
    static final int CELL_SIZE = 16;
    static final NamespacedId REFUSED = NamespacedId.of("structurecumulus", "sc-foc-0301");

    private final Map<LatticeKey, OccupantSnapshot> cells;
    private final Set<SectionKey> refused;
    private final List<OccupantSnapshot> elements;

    private EngineWorld(Map<LatticeKey, OccupantSnapshot> cells, Set<SectionKey> refused) {
        this.cells = Map.copyOf(cells);
        this.refused = Set.copyOf(refused);
        this.elements = elements(cells);
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public SectionSlot slot(SectionKey key) {
        Objects.requireNonNull(key, "key");
        return refused.contains(key)
                ? new SectionSlot.Refused(ReadStatus.NOT_CAPTURED, REFUSED)
                : new SectionSlot.Air(0L);
    }

    @Override
    public @Nullable OccupantSnapshot occupant(LatticePoint cell) {
        Objects.requireNonNull(cell, "cell");
        if (refused.contains(SectionKey.of(DIMENSION, cell, CELL_SIZE))) {
            return null;
        }
        OccupantSnapshot stored = cells.get(LatticeKey.of(cell));
        return stored != null ? stored : OccupantSnapshot.air(INSTANCE, cell);
    }

    @Override
    public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
        for (OccupantSnapshot occupant : elements) {
            if (occupant.ref().equals(ref)) {
                return occupant;
            }
        }
        return null;
    }

    @Override
    public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(filter, "filter");
        List<OccupantSnapshot> found = new ArrayList<>();
        for (OccupantSnapshot occupant : elements) {
            if (occupant.footprint().intersects(box) && filter.matches(occupant)) {
                found.add(occupant);
            }
        }
        return List.copyOf(found);
    }

    @Override
    public @Nullable NestedLevelSnapshot nested(LatticePoint cell) {
        return null;
    }

    @Override
    public long contentDigest(SectionKey key) {
        return 0L;
    }

    private static List<OccupantSnapshot> elements(Map<LatticeKey, OccupantSnapshot> cells) {
        Map<OccupantRef, OccupantSnapshot> distinct = new LinkedHashMap<>();
        for (OccupantSnapshot occupant : cells.values()) {
            if (!occupant.isAir()) {
                distinct.putIfAbsent(occupant.ref(), occupant);
            }
        }
        return List.copyOf(distinct.values());
    }

    static final class Builder {

        private final Map<LatticeKey, OccupantSnapshot> cells = new LinkedHashMap<>();
        private final Set<SectionKey> refused = new LinkedHashSet<>();

        private Builder() {
        }

        Builder putBlock(LatticePoint cell, NamespacedId type) {
            OccupantRef ref = new OccupantRef(INSTANCE, type, OccupantKind.BLOCK, cell, Cuboid.of(cell));
            cells.put(LatticeKey.of(cell), new OccupantSnapshot(ref, OccupantKind.BLOCK,
                    OccupantDescriptor.of(type), Cuboid.of(cell), Set.of(), null));
            return this;
        }

        Builder denySection(LatticePoint cell) {
            refused.add(SectionKey.of(DIMENSION, cell, CELL_SIZE));
            return this;
        }

        EngineWorld build() {
            return new EngineWorld(cells, refused);
        }
    }
}
