package dev.vfyjxf.cumulus.kernel.engine;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMatrix;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimSet;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
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

import java.util.ArrayList;
import java.util.Comparator;
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
        return block(type, cell, Map.of(), List.of());
    }

    static OccupantSnapshot block(
        NamespacedId type,
        LatticePoint cell,
        Map<String, String> properties,
        List<TraitSlot> traits
    ) {
        OccupantRef ref = new OccupantRef(snapshot, 0, type, OccupantKind.block, cell);
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.block, properties, List.of());
        return new OccupantSnapshot(ref, descriptor, Cuboid.of(cell), traits);
    }

    static OccupantSnapshot airOccupant(LatticePoint cell) {
        return OccupantSnapshot.air(snapshot, 0, cell);
    }

    static final class View implements WorldView {

        private final Map<LatticePoint, OccupantSnapshot> occupants = new LinkedHashMap<>();
        private final Map<LatticePoint, ReadRefusal> refused = new LinkedHashMap<>();
        private final Set<SectionKey> refusedSections = new LinkedHashSet<>();
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
            return SnapshotPolicy.minimal();
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
                if (sectionOf(entry.getKey()).equals(key) && !entry.getValue().isAir()) {
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
            return new CellRead.Found(occupant == null ? airOccupant(cell) : occupant);
        }

        @Override
        public @Nullable OccupantSnapshot occupant(OccupantRef ref) {
            if (!ref.snapshot().equals(snapshot) || ref.frame() != 0) {
                return null;
            }
            OccupantSnapshot candidate = occupants.get(ref.anchor());
            return candidate != null && candidate.ref().equals(ref) ? candidate : null;
        }

        @Override
        public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
            List<OccupantSnapshot> result = new ArrayList<>();
            for (OccupantSnapshot occupant : occupants.values()) {
                if (refused.containsKey(occupant.ref().anchor())) {
                    continue;
                }
                if (occupant.footprint().intersects(box) && filter.matches(occupant)) {
                    result.add(occupant);
                }
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

    static final class Ledger implements ClaimLedger {

        private final List<ClaimRow> rows = new ArrayList<>();
        private long revision;

        @Override
        public World world() {
            return world;
        }

        @Override
        public long revision() {
            return revision;
        }

        @Override
        public ClaimVerdict settle(SettleRequest request) {
            if (request.expectedRevision() != revision) {
                return new ClaimVerdict.RevisionMoved(request.expectedRevision(), revision);
            }
            for (ClaimRow row : request.rows()) {
                for (ClaimRow incumbent : rows) {
                    if (ClaimMatrix.conflicts(row, incumbent)) {
                        return new ClaimVerdict.Conflict(
                            List.of(incumbent),
                            FindingBundle.of(
                                List.of(
                                    Finding.builder(Codes.claimConflict).phase(Phase.settle).message("test conflict")
                                            .label(
                                                Label.primary(
                                                    Locus.world(Cuboid.of(incumbent.cell().point()).asSet()),
                                                    "held"
                                                )
                                            ).build()
                                )
                            )
                        );
                    }
                }
            }
            rows.removeIf(row -> row.owner().equals(request.owner()) && row.siting().equals(request.siting()));
            rows.addAll(request.rows());
            rows.sort(ClaimRow::compareTo);
            revision++;
            return new ClaimVerdict.Settled(revision, List.copyOf(rows));
        }

        @Override
        public ClaimVerdict release(OwnerId owner, Siting siting) {
            boolean removed = rows.removeIf(row -> row.owner().equals(owner) && row.siting().equals(siting));
            if (removed) {
                revision++;
            }
            return new ClaimVerdict.Settled(revision, List.copyOf(rows));
        }

        @Override
        public ClaimView read(Cuboid box) {
            List<ClaimRow> inBox = new ArrayList<>();
            for (ClaimRow row : rows) {
                if (box.contains(row.cell().point())) {
                    inBox.add(row);
                }
            }
            return new ClaimView(inBox, revision, true);
        }

        List<ClaimRow> all() {
            return List.copyOf(rows);
        }

        ClaimSet set() {
            return new ClaimSet(rows, revision);
        }
    }

    static Map<LatticePoint, OccupantSnapshot> stoneShell(CuboidSet cells) {
        Map<LatticePoint, OccupantSnapshot> map = new LinkedHashMap<>();
        for (Cuboid box : cells.boxes()) {
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
