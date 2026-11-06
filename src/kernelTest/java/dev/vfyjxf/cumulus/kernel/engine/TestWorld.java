package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SectionSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

final class TestWorld {

    static final World world = World.of(NamespacedId.of("test", "overworld"));
    static final SnapshotId snapshot = new SnapshotId(world, 1);
    static final NamespacedId stone = NamespacedId.of("minecraft", "stone_bricks");
    static final NamespacedId campfire = NamespacedId.of("minecraft", "campfire");
    static final NamespacedId door = NamespacedId.of("minecraft", "oak_door");

    private TestWorld() {}

    static OccupantSnapshot block(NamespacedId type, LatticePoint cell) {
        return block(type, cell, List.of());
    }

    static OccupantSnapshot block(NamespacedId type, LatticePoint cell, List<TraitSlot> traits) {
        OccupantRef ref = new OccupantRef(snapshot, 0, type, OccupantKind.block, cell);
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.block);
        return new OccupantSnapshot(ref, descriptor, Cuboid.of(cell), traits);
    }

    static OccupantSnapshot airOccupant(LatticePoint cell) {
        return OccupantSnapshot.air(snapshot, 0, cell);
    }

    static final class View implements WorldView {

        private final Map<LatticePoint, OccupantSnapshot> occupants = new LinkedHashMap<>();
        private final Map<OccupantRef, OccupantSnapshot> bodiesByRef = new LinkedHashMap<>();
        private final Map<LatticePoint, OccupantSnapshot> bodiesByCell = new LinkedHashMap<>();
        private final Map<LatticePoint, ReadRefusal> refused = new LinkedHashMap<>();
        private final Set<SectionKey> refusedSections = new LinkedHashSet<>();
        private SnapshotPolicy policy = new SnapshotPolicy(
            1_000_000,
            SnapshotPolicy.defaultMaxDepth,
            Set.of(OccupantKind.entity, OccupantKind.nested),
            Set.of(),
            List.of()
        );
        private int sectionSize = 16;
        private Cuboid bounds = Cuboid.of(
            LatticePoint.minXz,
            LatticePoint.minY,
            LatticePoint.minXz,
            LatticePoint.maxXz,
            LatticePoint.maxY,
            LatticePoint.maxXz
        );

        View put(OccupantSnapshot occupant) {
            occupants.put(occupant.ref().anchor(), occupant);
            return this;
        }

        View policy(SnapshotPolicy policy) {
            this.policy = policy;
            return this;
        }

        View putBody(OccupantSnapshot body) {
            bodiesByRef.put(body.ref(), body);
            for (int x = body.footprint().min().x(); x < body.footprint().max().x(); x++) {
                for (int y = body.footprint().min().y(); y < body.footprint().max().y(); y++) {
                    for (int z = body.footprint().min().z(); z < body.footprint().max().z(); z++) {
                        LatticePoint cell = LatticePoint.of(x, y, z);
                        OccupantSnapshot current = bodiesByCell.get(cell);
                        if (current == null || bodyOrder(body, current) < 0) {
                            bodiesByCell.put(cell, body);
                        }
                    }
                }
            }
            return this;
        }

        private static int bodyOrder(OccupantSnapshot left, OccupantSnapshot right) {
            int byVolume = Long.compare(left.footprint().volume(), right.footprint().volume());
            return byVolume != 0 ? byVolume : left.ref().compareTo(right.ref());
        }

        View air(LatticePoint cell) {
            occupants.put(cell, airOccupant(cell));
            return this;
        }

        View refuseSection(SectionKey key) {
            refusedSections.add(key);
            return this;
        }

        View sectionSize(int size) {
            this.sectionSize = size;
            return this;
        }

        View refuse(LatticePoint cell) {
            refused.put(cell, new ReadRefusal(ReadStatus.notCaptured, Codes.unreadableRead.code()));
            return this;
        }

        View refuse(LatticePoint cell, ReadRefusal refusal) {
            refused.put(cell, refusal);
            return this;
        }

        View bounds(Cuboid bounds) {
            this.bounds = bounds;
            return this;
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
            return sectionSize;
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
            return SectionKey.of(world, 0, cell, sectionSize);
        }

        @Override
        public SectionSlot slot(SectionKey key) {
            if (!key.world().equals(world) || key.frame() != 0) {
                return new SectionSlot.Refused(ReadStatus.outOfBounds, Codes.unreadableRead.code());
            }
            if (refusedSections.contains(key)) {
                return new SectionSlot.Refused(ReadStatus.notCaptured, Codes.unreadableRead.code());
            }
            Map<LatticePoint, OccupantSnapshot> slice = new LinkedHashMap<>();
            Map<LatticePoint, ReadRefusal> unreadable = new TreeMap<>();
            for (Map.Entry<LatticePoint, OccupantSnapshot> entry : occupants.entrySet()) {
                if (sectionOf(entry.getKey()).equals(key) && !entry.getValue().isAir() && visible(entry.getValue())) {
                    slice.put(entry.getKey(), entry.getValue());
                }
            }
            for (Map.Entry<LatticePoint, ReadRefusal> entry : refused.entrySet()) {
                if (sectionOf(entry.getKey()).equals(key)) {
                    unreadable.put(entry.getKey(), entry.getValue());
                }
            }
            SectionSnapshot section = SectionSnapshot.of(key, slice, contentDigest(slice));
            if (!unreadable.isEmpty()) {
                return new SectionSlot.Partial(section, unreadable);
            }
            if (slice.isEmpty()) {
                return new SectionSlot.Air(0);
            }
            return new SectionSlot.Present(section);
        }

        @Override
        public CellRead read(LatticePoint cell) {
            if (!bounds.contains(cell)) {
                return new CellRead.Unreadable(new ReadRefusal(ReadStatus.outOfBounds, Codes.unreadableRead.code()));
            }
            SectionSlot slot = slot(sectionOf(cell));
            if (slot instanceof SectionSlot.Refused refusedSlot) {
                return new CellRead.Unreadable(new ReadRefusal(refusedSlot.status(), refusedSlot.code()));
            }
            Map<LatticePoint, ReadRefusal> unreadable = slot instanceof SectionSlot.Partial partial
                    ? partial.unreadable()
                    : Map.of();
            ReadRefusal refusal = unreadable.get(cell);
            if (refusal != null) {
                return new CellRead.Unreadable(refusal);
            }
            OccupantSnapshot occupant = switch (slot) {
                case SectionSlot.Present present -> present.section().occupantAt(cell);
                case SectionSlot.Partial partial -> partial.section().occupantAt(cell);
                default -> null;
            };
            OccupantSnapshot body = bodiesByCell.get(cell);
            if (body != null) {
                return new CellRead.Found(body);
            }
            return new CellRead.Found(occupant == null ? airOccupant(cell) : occupant);
        }

        @Override
        public @Nullable OccupantSnapshot cellOccupant(LatticePoint cell) {
            if (!bounds.contains(cell)) {
                return null;
            }
            SectionSlot slot = slot(sectionOf(cell));
            if (!slot.readable()) {
                return null;
            }
            if (slot instanceof SectionSlot.Partial partial && partial.unreadable().containsKey(cell)) {
                return null;
            }
            OccupantSnapshot occupant = switch (slot) {
                case SectionSlot.Present present -> present.section().occupantAt(cell);
                case SectionSlot.Partial partial -> partial.section().occupantAt(cell);
                default -> null;
            };
            return occupant == null ? airOccupant(cell) : occupant;
        }

        @Override
        public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
            if (!ref.snapshot().equals(snapshot) || ref.frame() != 0) {
                return null;
            }
            OccupantSnapshot body = bodiesByRef.get(ref);
            if (body != null) {
                return body;
            }
            OccupantSnapshot candidate = occupants.get(ref.anchor());
            return candidate != null && candidate.ref().equals(ref) && visible(candidate) ? candidate : null;
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
            Map<OccupantRef, OccupantSnapshot> found = new TreeMap<>();
            for (OccupantSnapshot occupant : occupants.values()) {
                if (refused.containsKey(occupant.ref().anchor()) || !visible(occupant)) {
                    continue;
                }
                if (occupant.footprint().intersects(box) && filter.matches(occupant)) {
                    found.putIfAbsent(occupant.ref(), occupant);
                }
            }
            for (OccupantSnapshot body : bodiesByRef.values()) {
                if (body.footprint().intersects(box) && filter.matches(body)) {
                    found.putIfAbsent(body.ref(), body);
                }
            }
            return List.copyOf(found.values());
        }

        @Override
        public @Nullable WorldView nested(LatticePoint cell) {
            return null;
        }

        // blocks are captured unconditionally on the real host; only bodies answer to the policy
        private boolean visible(OccupantSnapshot occupant) {
            OccupantKind kind = occupant.descriptor().kind();
            return kind.equals(OccupantKind.block)
                    || kind.equals(OccupantKind.blockEntity)
                    || policy.kinds().contains(kind);
        }

        @Override
        public long contentDigest(SectionKey key) {
            SectionSlot slot = slot(key);
            return switch (slot) {
                case SectionSlot.Air air -> 0L;
                case SectionSlot.Refused refusedSlot -> 0L;
                case SectionSlot.Present present -> present.section().contentDigest();
                case SectionSlot.Partial partial -> {
                    long refusedDigest = 0L;
                    for (Map.Entry<LatticePoint, ReadRefusal> entry : partial.unreadable().entrySet()) {
                        refusedDigest = refusedDigest * 31L + entry.getKey().hashCode();
                        refusedDigest = refusedDigest * 31L + entry.getValue().status().ordinal();
                    }
                    yield partial.section().contentDigest() * 31L + refusedDigest;
                }
            };
        }

        private static long contentDigest(Map<LatticePoint, OccupantSnapshot> slice) {
            long digest = 17L;
            for (Map.Entry<LatticePoint, OccupantSnapshot> entry : slice.entrySet()) {
                digest = digest * 31L + entry.getKey().hashCode();
                digest = digest * 31L + entry.getValue().ref().hashCode();
            }
            return digest;
        }
    }

    static final class Lease implements SnapshotLease {
        private final View view;
        private boolean current = true;

        Lease(View view) {
            this.view = view;
        }

        void invalidate() {
            current = false;
        }

        @Override
        public SnapshotId id() {
            return snapshot;
        }

        @Override
        public WorldView view() {
            return view;
        }

        @Override
        public boolean isCurrent() {
            return current;
        }

        @Override
        public void close() {
            current = false;
        }
    }

    private static final Cuboid lattice = Cuboid.of(
        LatticePoint.minXz,
        LatticePoint.minY,
        LatticePoint.minXz,
        LatticePoint.maxXz,
        LatticePoint.maxY,
        LatticePoint.maxXz
    );

    static List<ClaimRow> rows(ClaimLedger ledger) {
        return ledger.read(lattice).rows();
    }

    static Map<LatticePoint, OccupantSnapshot> stoneShell(CuboidSet cells) {
        Map<LatticePoint, OccupantSnapshot> map = new LinkedHashMap<>();
        for (Cuboid box : cells.decomposition()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        LatticePoint cell = LatticePoint.of(x, y, z);
                        map.put(cell, block(stone, cell));
                    }
                }
            }
        }
        return map;
    }

    static CuboidSet cells(CuboidSet set) {
        return Objects.requireNonNull(set);
    }
}
