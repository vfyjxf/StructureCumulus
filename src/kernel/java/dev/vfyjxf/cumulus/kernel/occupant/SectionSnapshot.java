package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One frozen 16³ section: every enumerated cell answers, air included, and a box query is element-bounded.
 */
public final class SectionSnapshot {

    private final SectionKey key;
    private final CellIndex cells;

    private SectionSnapshot(SectionKey key, CellIndex cells) {
        this.key = key;
        this.cells = cells;
    }

    public static SectionSnapshot of(SectionKey key, Map<LatticePoint, OccupantSnapshot> occupants) {
        return new SectionSnapshot(Objects.requireNonNull(key, "key"), new CellIndex(occupants));
    }

    public static Builder builder(SectionKey key) {
        return new Builder(key);
    }

    public SectionKey key() {
        return key;
    }

    public long contentDigest() {
        return cells.digest();
    }

    /**
     * {@code null} only when the point is outside the cells this section enumerated.
     */
    public @Nullable OccupantSnapshot occupantAt(LatticePoint cell) {
        return cells.at(cell);
    }

    public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
        return cells.occupants(box, filter);
    }

    public static final class Builder {

        private final SectionKey key;
        private final Map<LatticePoint, OccupantSnapshot> cells = new HashMap<>();

        private Builder(SectionKey key) {
            this.key = Objects.requireNonNull(key, "key");
        }

        public Builder put(LatticePoint cell, OccupantSnapshot occupant) {
            cells.put(Objects.requireNonNull(cell, "cell"), Objects.requireNonNull(occupant, "occupant"));
            return this;
        }

        public Builder putAll(Map<LatticePoint, OccupantSnapshot> occupants) {
            occupants.forEach(this::put);
            return this;
        }

        public SectionSnapshot build() {
            return of(key, cells);
        }
    }

    static final class CellIndex {

        private static final Comparator<OccupantSnapshot> ORDER =
                Comparator.<OccupantSnapshot, LatticePoint>comparing(occupant -> occupant.ref().anchor())
                        .thenComparing(occupant -> occupant.ref().type().namespace())
                        .thenComparing(occupant -> occupant.ref().type().path())
                        .thenComparing(occupant -> occupant.kind().id().namespace())
                        .thenComparing(occupant -> occupant.kind().id().path());

        private final Map<LatticeKey, OccupantSnapshot> cells;
        private final List<OccupantSnapshot> elements;
        private final long digest;

        CellIndex(Map<LatticePoint, OccupantSnapshot> cells) {
            Objects.requireNonNull(cells, "cells");
            Map<LatticeKey, OccupantSnapshot> indexed = new HashMap<>(cells.size() * 2);
            for (Map.Entry<LatticePoint, OccupantSnapshot> entry : cells.entrySet()) {
                LatticePoint cell = Objects.requireNonNull(entry.getKey(), "cell");
                OccupantSnapshot occupant = Objects.requireNonNull(entry.getValue(), "occupant");
                indexed.put(LatticeKey.of(cell), occupant);
            }
            this.cells = Map.copyOf(indexed);
            this.elements = elements(indexed);
            this.digest = digest(indexed);
        }

        long digest() {
            return digest;
        }

        @Nullable OccupantSnapshot at(LatticePoint cell) {
            Objects.requireNonNull(cell, "cell");
            return cells.get(LatticeKey.of(cell));
        }

        List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
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

        private static List<OccupantSnapshot> elements(Map<LatticeKey, OccupantSnapshot> cells) {
            Map<OccupantRef, OccupantSnapshot> distinct = new LinkedHashMap<>();
            for (OccupantSnapshot occupant : cells.values()) {
                if (!occupant.isAir()) {
                    distinct.putIfAbsent(occupant.ref(), occupant);
                }
            }
            List<OccupantSnapshot> ordered = new ArrayList<>(distinct.values());
            ordered.sort(ORDER);
            return List.copyOf(ordered);
        }

        private static long digest(Map<LatticeKey, OccupantSnapshot> cells) {
            List<Map.Entry<LatticeKey, OccupantSnapshot>> ordered = new ArrayList<>(cells.entrySet());
            ordered.sort(Comparator.comparingLong(entry -> entry.getKey().packed()));
            long hash = 1L;
            for (Map.Entry<LatticeKey, OccupantSnapshot> entry : ordered) {
                hash = 31L * hash + entry.getKey().packed();
                hash = 31L * hash + entry.getValue().descriptor().type().hashCode();
            }
            return hash;
        }
    }
}
