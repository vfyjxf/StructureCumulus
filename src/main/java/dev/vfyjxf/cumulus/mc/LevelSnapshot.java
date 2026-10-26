package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** M1 immutable root view over the captured sections. */
@NullMarked
public final class LevelSnapshot implements WorldView {

    private static final int cellSize = 16;

    private final SnapshotId snapshot;
    private final Cuboid bounds;
    private final Map<SectionKey, SectionSlot> sections;

    public LevelSnapshot(SnapshotId snapshot, Cuboid bounds, Map<SectionKey, SectionSlot> sections) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.sections = Map.copyOf(new TreeMap<>(sections));
    }

    @Override
    public SnapshotId snapshotId() {
        return snapshot;
    }

    @Override
    public int frame() {
        return 0;
    }

    @Override
    public int depth() {
        return 0;
    }

    @Override
    public LatticePoint origin() {
        return bounds.min();
    }

    @Override
    public @Nullable WorldView parent() {
        return null;
    }

    @Override
    public int cellSize() {
        return cellSize;
    }

    @Override
    public SectionKey sectionOf(LatticePoint cell) {
        return SectionKey.of(snapshot.world(), frame(), cell, cellSize);
    }

    @Override
    public SectionSlot slot(SectionKey key) {
        if (!key.world().equals(snapshot.world()) || key.frame() != frame()) {
            return refused();
        }
        SectionSlot slot = sections.get(key);
        if (slot != null) {
            return slot;
        }
        LatticePoint min = LatticePoint.of(key.x() * cellSize, key.y() * cellSize, key.z() * cellSize);
        LatticePoint max = LatticePoint.of(min.x() + cellSize, min.y() + cellSize, min.z() + cellSize);
        return intersects(bounds, Cuboid.of(min, max)) ? new SectionSlot.Air(0) : refused();
    }

    @Override
    public @Nullable OccupantSnapshot occupant(LatticePoint cell) {
        if (!bounds.contains(cell)) {
            return null;
        }
        SectionSlot slot = slot(sectionOf(cell));
        if (!slot.readable()) {
            return null;
        }
        if (slot instanceof SectionSlot.Present present) {
            OccupantSnapshot occupant = present.section().occupantAt(cell);
            if (occupant != null) {
                return occupant;
            }
        }
        return OccupantSnapshot.air(snapshot, frame(), cell);
    }

    @Override
    public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
        if (!ref.from(snapshot) || ref.frame() != frame()) {
            return null;
        }
        return occupant(ref.anchor());
    }

    @Override
    public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
        Cuboid query = intersect(box, bounds);
        if (query == null) {
            return List.of();
        }
        List<OccupantSnapshot> result = new ArrayList<>();
        for (SectionSlot slot : sections.values()) {
            if (!(slot instanceof SectionSlot.Present present)) {
                continue;
            }
            result.addAll(present.section().occupants(query, filter));
        }
        result.sort(Comparator.comparing(OccupantSnapshot::ref));
        return List.copyOf(result);
    }

    @Override
    public @Nullable WorldView nested(LatticePoint cell) {
        return null;
    }

    @Override
    public long contentDigest(SectionKey key) {
        SectionSlot slot = slot(key);
        return slot instanceof SectionSlot.Present present ? present.section().contentDigest() : 0L;
    }

    private static @Nullable Cuboid intersect(Cuboid left, Cuboid right) {
        int minX = Math.max(left.min().x(), right.min().x());
        int minY = Math.max(left.min().y(), right.min().y());
        int minZ = Math.max(left.min().z(), right.min().z());
        int maxX = Math.min(left.max().x(), right.max().x());
        int maxY = Math.min(left.max().y(), right.max().y());
        int maxZ = Math.min(left.max().z(), right.max().z());
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return null;
        }
        return Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean intersects(Cuboid left, Cuboid right) {
        return intersect(left, right) != null;
    }

    private static SectionSlot refused() {
        return new SectionSlot.Refused(ReadStatus.OUT_OF_BOUNDS, FindingCode.of(FindingCode.Family.MC, 1));
    }
}
