package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SectionSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable root view over the captured sections and entity bodies. */
@NullMarked
public final class LevelSnapshot implements WorldView {

    private static final int cellSize = 16;

    private final SnapshotId snapshot;
    private final Cuboid bounds;
    private final SnapshotPolicy policy;
    private final Map<SectionKey, SectionSlot> sections;
    private final Map<OccupantRef, OccupantSnapshot> entitiesByRef;
    private final Map<LatticePoint, OccupantSnapshot> entitiesByCell;

    public LevelSnapshot(SnapshotId snapshot, Cuboid bounds, Map<SectionKey, SectionSlot> sections) {
        this(snapshot, bounds, sections, List.of(), SnapshotPolicy.minimal());
    }

    public LevelSnapshot(
        SnapshotId snapshot,
        Cuboid bounds,
        Map<SectionKey, SectionSlot> sections,
        List<OccupantSnapshot> entities
    ) {
        this(snapshot, bounds, sections, entities, SnapshotPolicy.minimal());
    }

    public LevelSnapshot(
        SnapshotId snapshot,
        Cuboid bounds,
        Map<SectionKey, SectionSlot> sections,
        List<OccupantSnapshot> entities,
        SnapshotPolicy policy
    ) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.sections = Map.copyOf(new TreeMap<>(sections));
        this.entitiesByRef = indexEntities(snapshot, bounds, entities);
        this.entitiesByCell = indexEntityCells(bounds, entitiesByRef.values());
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
        return LatticePoint.origin;
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
    public Cuboid bounds() {
        return bounds;
    }

    @Override
    public SnapshotPolicy policy() {
        return policy;
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
    public CellRead read(LatticePoint cell) {
        if (!bounds.contains(cell)) {
            return new CellRead.Unreadable(new ReadRefusal(ReadStatus.outOfBounds, Codes.unreadableRead.code()));
        }
        SectionSlot slot = slot(sectionOf(cell));
        if (slot instanceof SectionSlot.Refused refused) {
            return new CellRead.Unreadable(new ReadRefusal(refused.status(), refused.code()));
        }
        if (slot instanceof SectionSlot.Partial partial) {
            ReadRefusal refusal = partial.unreadable().get(cell);
            if (refusal != null) {
                return new CellRead.Unreadable(refusal);
            }
        }
        OccupantSnapshot base = readableOccupant(cell, slot);
        OccupantSnapshot entity = entitiesByCell.get(cell);
        return new CellRead.Found(entity == null ? base : narrow(base, entity));
    }

    @Override
    public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
        if (!ref.from(snapshot) || ref.frame() != frame()) {
            return null;
        }
        if (ref.kind().equals(OccupantKind.entity)) {
            return entitiesByRef.get(ref);
        }
        if (!bounds.contains(ref.anchor())) {
            return null;
        }
        SectionSlot slot = slot(sectionOf(ref.anchor()));
        if (!slot.readable()) {
            return null;
        }
        if (slot instanceof SectionSlot.Partial partial && partial.unreadable().containsKey(ref.anchor())) {
            return null;
        }
        OccupantSnapshot base = readableOccupant(ref.anchor(), slot);
        return base.ref().equals(ref) ? base : null;
    }

    @Override
    public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
        Cuboid query = intersect(box, bounds);
        if (query == null) {
            return List.of();
        }
        TreeMap<OccupantRef, OccupantSnapshot> found = new TreeMap<>();
        for (SectionSlot slot : sections.values()) {
            @Nullable
            SectionSnapshot section = switch (slot) {
                case SectionSlot.Present present -> present.section();
                case SectionSlot.Partial partial -> partial.section();
                default -> null;
            };
            if (section == null) {
                continue;
            }
            for (OccupantSnapshot occupant : section.occupants(query, filter)) {
                found.putIfAbsent(occupant.ref(), occupant);
            }
        }
        for (OccupantSnapshot entity : entitiesByRef.values()) {
            if (entity.footprint().intersects(query) && filter.matches(entity)) {
                found.putIfAbsent(entity.ref(), entity);
            }
        }
        return List.copyOf(found.values());
    }

    @Override
    public @Nullable WorldView nested(LatticePoint cell) {
        return null;
    }

    @Override
    public long contentDigest(SectionKey key) {
        SectionSlot slot = slot(key);
        if (slot instanceof SectionSlot.Present present) {
            return present.section().contentDigest();
        }
        if (slot instanceof SectionSlot.Partial partial) {
            return partialDigest(partial);
        }
        return 0L;
    }

    private OccupantSnapshot readableOccupant(LatticePoint cell, SectionSlot slot) {
        @Nullable
        OccupantSnapshot occupant = switch (slot) {
            case SectionSlot.Present present -> present.section().occupantAt(cell);
            case SectionSlot.Partial partial -> partial.section().occupantAt(cell);
            default -> null;
        };
        return occupant == null ? OccupantSnapshot.air(snapshot, frame(), cell) : occupant;
    }

    private static long partialDigest(SectionSlot.Partial partial) {
        long digest = partial.section().contentDigest();
        for (Map.Entry<LatticePoint, ReadRefusal> entry : partial.unreadable().entrySet()) {
            digest = digest * 31L + entry.getKey().hashCode();
            digest = digest * 31L + entry.getValue().status().ordinal();
        }
        return digest;
    }

    private static Map<OccupantRef, OccupantSnapshot> indexEntities(
        SnapshotId snapshot,
        Cuboid bounds,
        List<OccupantSnapshot> entities
    ) {
        TreeMap<OccupantRef, OccupantSnapshot> index = new TreeMap<>();
        for (OccupantSnapshot entity : entities) {
            Objects.requireNonNull(entity, "entity");
            if (!entity.ref().from(snapshot) || entity.ref().frame() != 0) {
                throw new IllegalArgumentException("entity occupant from another snapshot or frame");
            }
            if (!entity.descriptor().kind().equals(OccupantKind.entity)) {
                throw new IllegalArgumentException("not an entity occupant: " + entity.descriptor().type());
            }
            if (entity.footprint().intersects(bounds)) {
                index.putIfAbsent(entity.ref(), entity);
            }
        }
        return Collections.unmodifiableMap(index);
    }

    private static Map<LatticePoint, OccupantSnapshot> indexEntityCells(
        Cuboid bounds,
        Iterable<OccupantSnapshot> entities
    ) {
        TreeMap<LatticePoint, OccupantSnapshot> index = new TreeMap<>();
        for (OccupantSnapshot entity : entities) {
            Cuboid covered = intersect(entity.footprint(), bounds);
            if (covered == null) {
                continue;
            }
            for (int x = covered.min().x(); x < covered.max().x(); x++) {
                for (int y = covered.min().y(); y < covered.max().y(); y++) {
                    for (int z = covered.min().z(); z < covered.max().z(); z++) {
                        LatticePoint cell = LatticePoint.of(x, y, z);
                        OccupantSnapshot current = index.get(cell);
                        if (current == null || bodyOrder(entity, current) < 0) {
                            index.put(cell, entity);
                        }
                    }
                }
            }
        }
        return Collections.unmodifiableMap(index);
    }

    private static int bodyOrder(OccupantSnapshot left, OccupantSnapshot right) {
        int byVolume = Long.compare(left.footprint().volume(), right.footprint().volume());
        return byVolume != 0 ? byVolume : left.ref().compareTo(right.ref());
    }

    private static OccupantSnapshot narrow(OccupantSnapshot left, OccupantSnapshot right) {
        if (left.isAir() != right.isAir()) {
            return left.isAir() ? right : left;
        }
        int byVolume = Long.compare(left.footprint().volume(), right.footprint().volume());
        if (byVolume != 0) {
            return byVolume < 0 ? left : right;
        }
        return left.ref().compareTo(right.ref()) <= 0 ? left : right;
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
        return new SectionSlot.Refused(ReadStatus.outOfBounds, Codes.unreadableRead.code());
    }
}
