package dev.vfyjxf.cumulus.kernel.examples;

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
import dev.vfyjxf.cumulus.kernel.occupant.SectionSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.WorldView;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * A {@link WorldView} over exactly the cells a scenario declared: mutable while the builder runs,
 * immutable afterwards, split into one {@link SectionSnapshot} per captured section.
 */
public final class TestWorld implements WorldView {

    public static final NamespacedId DEFAULT_DIMENSION = NamespacedId.of("minecraft", "overworld");
    public static final InstanceId DEFAULT_INSTANCE = InstanceId.of(NamespacedId.of("cumulus", "test"), 1);
    public static final NamespacedId FOC_REFUSED_SECTION = NamespacedId.of("structurecumulus", "sc-foc-0301");
    public static final int DEFAULT_CELL_SIZE = 16;

    private final NamespacedId dimension;
    private final InstanceId instance;
    private final int cellSize;
    private final Map<LatticeKey, OccupantSnapshot> cells;
    private final Map<SectionKey, SectionSlot> slots;
    private final List<OccupantSnapshot> elements;

    private TestWorld(NamespacedId dimension, InstanceId instance, int cellSize,
                      Map<LatticeKey, OccupantSnapshot> cells, Map<SectionKey, SectionSlot> slots) {
        this.dimension = dimension;
        this.instance = instance;
        this.cellSize = cellSize;
        this.cells = Map.copyOf(cells);
        this.slots = Map.copyOf(slots);
        this.elements = elements(cells);
    }

    public static Builder builder() {
        return new Builder();
    }

    public NamespacedId dimension() {
        return dimension;
    }

    public InstanceId instance() {
        return instance;
    }

    public int cellSize() {
        return cellSize;
    }

    @Override
    public SectionSlot slot(SectionKey key) {
        Objects.requireNonNull(key, "key");
        SectionSlot declared = slots.get(key);
        return declared != null ? declared : refused(ReadStatus.NOT_CAPTURED);
    }

    @Override
    public @Nullable OccupantSnapshot occupant(LatticePoint cell) {
        Objects.requireNonNull(cell, "cell");
        SectionSlot slot = slots.get(SectionKey.of(dimension, cell, cellSize));
        if (slot == null || slot instanceof SectionSlot.Refused) {
            return null;
        }
        if (slot instanceof SectionSlot.Present present) {
            OccupantSnapshot found = present.section().occupantAt(cell);
            return found != null ? found : OccupantSnapshot.air(instance, cell);
        }
        return OccupantSnapshot.air(instance, cell);
    }

    @Override
    public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
        Objects.requireNonNull(ref, "ref");
        for (OccupantSnapshot occupant : elements) {
            if (occupant.ref().equals(ref)) {
                return occupant;
            }
        }
        OccupantSnapshot covering = occupant(ref.anchor());
        return covering != null && covering.ref().equals(ref) ? covering : null;
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
        OccupantSnapshot occupant = occupant(cell);
        return occupant == null ? null : occupant.nested();
    }

    @Override
    public long contentDigest(SectionKey key) {
        Objects.requireNonNull(key, "key");
        SectionSlot slot = slots.get(key);
        return slot instanceof SectionSlot.Present present ? present.section().contentDigest() : 0L;
    }

    /** Every cell of a half-open box, in x, then y, then z order. */
    public static List<LatticePoint> cellsOf(Cuboid box) {
        Objects.requireNonNull(box, "box");
        List<LatticePoint> cells = new ArrayList<>((int) Math.min(Integer.MAX_VALUE, box.volume()));
        for (int x = box.min().x(); x < box.max().x(); x++) {
            for (int y = box.min().y(); y < box.max().y(); y++) {
                for (int z = box.min().z(); z < box.max().z(); z++) {
                    cells.add(LatticePoint.of(x, y, z));
                }
            }
        }
        return List.copyOf(cells);
    }

    public static Cuboid sectionBounds(SectionKey key, int cellSize) {
        Objects.requireNonNull(key, "key");
        return Cuboid.of(key.x() * cellSize, key.y() * cellSize, key.z() * cellSize,
                (key.x() + 1) * cellSize, (key.y() + 1) * cellSize, (key.z() + 1) * cellSize);
    }

    private static SectionSlot refused(ReadStatus status) {
        return new SectionSlot.Refused(status, FOC_REFUSED_SECTION);
    }

    private static List<OccupantSnapshot> elements(Map<LatticeKey, OccupantSnapshot> cells) {
        Map<OccupantRef, OccupantSnapshot> distinct = new LinkedHashMap<>();
        for (OccupantSnapshot occupant : cells.values()) {
            if (!occupant.isAir()) {
                distinct.putIfAbsent(occupant.ref(), occupant);
            }
        }
        List<OccupantSnapshot> ordered = new ArrayList<>(distinct.values());
        ordered.sort(Comparator.<OccupantSnapshot, LatticePoint>comparing(occupant -> occupant.ref().anchor())
                .thenComparing(occupant -> occupant.ref().type()));
        return List.copyOf(ordered);
    }

    public static final class Builder {

        private NamespacedId dimension = DEFAULT_DIMENSION;
        private InstanceId instance = DEFAULT_INSTANCE;
        private int cellSize = DEFAULT_CELL_SIZE;
        private final Map<LatticeKey, CellSpec> specs = new LinkedHashMap<>();
        private final List<LatticePoint> refusedCells = new ArrayList<>();
        private final Map<SectionKey, ReadStatus> refusedSections = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder dimension(NamespacedId dimension) {
            this.dimension = Objects.requireNonNull(dimension, "dimension");
            return this;
        }

        public Builder instance(InstanceId instance) {
            this.instance = Objects.requireNonNull(instance, "instance");
            return this;
        }

        public Builder cellSize(int cellSize) {
            if (cellSize <= 0) {
                throw new IllegalArgumentException("cellSize must be positive: " + cellSize);
            }
            this.cellSize = cellSize;
            return this;
        }

        public Builder putBlock(LatticePoint cell, NamespacedId type) {
            Objects.requireNonNull(cell, "cell");
            return putBlock(cell, type, Set.of());
        }

        public Builder putBlock(LatticePoint cell, NamespacedId type, Set<NamespacedId> traits) {
            Objects.requireNonNull(cell, "cell");
            Cuboid footprint = Cuboid.of(cell);
            return place(cell, type, OccupantKind.BLOCK, footprint, traits);
        }

        public Builder putBlock(LatticePoint anchor, NamespacedId type, Cuboid footprint) {
            Objects.requireNonNull(anchor, "anchor");
            return place(anchor, type, OccupantKind.BLOCK, footprint, Set.of());
        }

        public Builder putEntity(LatticePoint anchor, NamespacedId type, Cuboid footprint) {
            Objects.requireNonNull(anchor, "anchor");
            return place(anchor, type, OccupantKind.ENTITY, footprint, Set.of());
        }

        public Builder putAir(LatticePoint cell) {
            Objects.requireNonNull(cell, "cell");
            specs.put(LatticeKey.of(cell), new CellSpec(OccupantSnapshot.AIR, OccupantKind.BLOCK, cell,
                    Cuboid.of(cell), Set.of()));
            return this;
        }

        public Builder fill(Cuboid box, NamespacedId type) {
            for (LatticePoint cell : cellsOf(box)) {
                putBlock(cell, type);
            }
            return this;
        }

        public Builder fillAir(Cuboid box) {
            for (LatticePoint cell : cellsOf(box)) {
                putAir(cell);
            }
            return this;
        }

        public Builder refuseSection(SectionKey key) {
            return refuseSection(key, ReadStatus.NOT_CAPTURED);
        }

        public Builder refuseSection(SectionKey key, ReadStatus status) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(status, "status");
            if (status.readable()) {
                throw new IllegalArgumentException("a refused section cannot be readable: " + status);
            }
            refusedSections.put(key, status);
            return this;
        }

        public Builder refuseSection(LatticePoint cell) {
            Objects.requireNonNull(cell, "cell");
            refusedCells.add(cell);
            return this;
        }

        public TestWorld build() {
            Map<LatticeKey, OccupantSnapshot> cells = new LinkedHashMap<>();
            Map<SectionKey, Map<LatticePoint, OccupantSnapshot>> bySection = new TreeMap<>();
            for (Map.Entry<LatticeKey, CellSpec> entry : specs.entrySet()) {
                OccupantSnapshot occupant = entry.getValue().resolve(instance);
                cells.put(entry.getKey(), occupant);
                for (LatticePoint cell : cellsOf(occupant.footprint())) {
                    SectionKey section = SectionKey.of(dimension, cell, cellSize);
                    bySection.computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(cell, occupant);
                }
            }
            Map<SectionKey, SectionSlot> slots = new LinkedHashMap<>();
            for (Map.Entry<SectionKey, Map<LatticePoint, OccupantSnapshot>> entry : bySection.entrySet()) {
                SectionSnapshot section = SectionSnapshot.of(entry.getKey(), entry.getValue());
                slots.put(entry.getKey(), new SectionSlot.Present(section));
            }
            for (LatticePoint cell : refusedCells) {
                refusedSections.put(SectionKey.of(dimension, cell, cellSize), ReadStatus.NOT_CAPTURED);
            }
            for (Map.Entry<SectionKey, ReadStatus> entry : refusedSections.entrySet()) {
                slots.put(entry.getKey(), refused(entry.getValue()));
            }
            return new TestWorld(dimension, instance, cellSize, cells, slots);
        }

        private Builder place(LatticePoint anchor, NamespacedId type, OccupantKind kind, Cuboid footprint,
                              Set<NamespacedId> traits) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(footprint, "footprint");
            CellSpec spec = new CellSpec(type, kind, anchor, footprint, traits);
            for (LatticePoint cell : cellsOf(footprint)) {
                specs.put(LatticeKey.of(cell), spec);
            }
            return this;
        }
    }

    private record CellSpec(NamespacedId type, OccupantKind kind, LatticePoint anchor, Cuboid footprint,
                            Set<NamespacedId> traits) {

        OccupantSnapshot resolve(InstanceId instance) {
            if (type.equals(OccupantSnapshot.AIR)) {
                return OccupantSnapshot.air(instance, anchor);
            }
            OccupantRef ref = new OccupantRef(instance, type, kind, anchor, footprint);
            return new OccupantSnapshot(ref, kind, new OccupantDescriptor(type, traits), footprint, traits, null);
        }
    }
}
