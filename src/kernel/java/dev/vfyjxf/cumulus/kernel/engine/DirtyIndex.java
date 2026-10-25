package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;
import dev.vfyjxf.cumulus.kernel.base.ReadScope;
import dev.vfyjxf.cumulus.kernel.occupant.EntityKey;
import dev.vfyjxf.cumulus.kernel.occupant.EntityWindow;
import dev.vfyjxf.cumulus.kernel.space.ChunkGrid;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The host's mark table: every mark takes one monotone stamp, and a unit's stamp is the newest mark
 * that intersects what it read.
 */
public final class DirtyIndex {

    private final NamespacedId dimension;
    private final int cellSize;
    private final Map<LatticeKey, Long> cells = new HashMap<>();
    private final Map<SectionKey, Long> sections = new HashMap<>();
    private final Map<EntityKey, EntityMark> entities = new HashMap<>();
    private long epoch;

    public DirtyIndex(NamespacedId dimension) {
        this(dimension, ChunkGrid.ADAPTER_CELL_SIZE);
    }

    public DirtyIndex(NamespacedId dimension, int cellSize) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive: " + cellSize);
        }
        this.cellSize = cellSize;
    }

    public synchronized long stamp() {
        return epoch;
    }

    public synchronized long markCell(LatticeKey cell) {
        Objects.requireNonNull(cell, "cell");
        return record(cells, cell);
    }

    public synchronized long markCell(LatticePoint cell) {
        Objects.requireNonNull(cell, "cell");
        return markCell(LatticeKey.of(cell));
    }

    public synchronized long markSection(SectionKey section) {
        Objects.requireNonNull(section, "section");
        return record(sections, section);
    }

    public synchronized long markEntity(EntityKey entity, Cuboid footprint) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(footprint, "footprint");
        epoch++;
        entities.put(entity, new EntityMark(epoch, footprint));
        return epoch;
    }

    /** Zero when nothing the scope read has been marked; otherwise the newest such mark. */
    public synchronized long cellStamp(ReadScope reads) {
        Objects.requireNonNull(reads, "reads");
        long stamp = 0L;
        for (Map.Entry<LatticeKey, Long> entry : cells.entrySet()) {
            if (reads.cells().contains(entry.getKey().toPoint())) {
                stamp = Math.max(stamp, entry.getValue());
            }
        }
        for (Map.Entry<SectionKey, Long> entry : sections.entrySet()) {
            Cuboid bounds = boundsOf(entry.getKey());
            if (bounds != null && reads.cells().intersects(CuboidSet.of(bounds))) {
                stamp = Math.max(stamp, entry.getValue());
            }
        }
        for (EntityMark mark : entities.values()) {
            if (reads.cells().intersects(CuboidSet.of(mark.footprint()))
                    || intersectsWindow(mark.footprint(), reads)) {
                stamp = Math.max(stamp, mark.stamp());
            }
        }
        return stamp;
    }

    private static boolean intersectsWindow(Cuboid footprint, ReadScope reads) {
        for (EntityWindow window : reads.entities()) {
            if (window.cells().intersects(footprint)) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Cuboid boundsOf(SectionKey key) {
        if (!key.dimension().equals(dimension)) {
            return null;
        }
        long lowX = (long) key.x() * cellSize;
        long lowY = (long) key.y() * cellSize;
        long lowZ = (long) key.z() * cellSize;
        if (lowX < LatticePoint.MIN_XZ || lowX + cellSize > (long) LatticePoint.MAX_XZ + 1
                || lowZ < LatticePoint.MIN_XZ || lowZ + cellSize > (long) LatticePoint.MAX_XZ + 1
                || lowY < LatticePoint.MIN_Y || lowY + cellSize > (long) LatticePoint.MAX_Y + 1) {
            return null;
        }
        return Cuboid.of((int) lowX, (int) lowY, (int) lowZ,
                (int) (lowX + cellSize), (int) (lowY + cellSize), (int) (lowZ + cellSize));
    }

    private <K> long record(Map<K, Long> marks, K key) {
        epoch++;
        marks.put(key, epoch);
        return epoch;
    }

    private record EntityMark(long stamp, Cuboid footprint) {
    }
}
