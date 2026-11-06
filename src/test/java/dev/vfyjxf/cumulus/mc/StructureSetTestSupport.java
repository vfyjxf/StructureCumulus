package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimMatrix;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.PresenceSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.Host;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
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
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Fakes and fixtures for the host structure-set tests: no Minecraft, no capture. */
final class StructureSetTestSupport {

    static final World world = World.of(NamespacedId.of("test", "overworld"));
    static final SnapshotId snapshot = new SnapshotId(world, 1);
    static final OwnerId owner = OwnerId.of(NamespacedId.of("test", "owner"));
    static final OwnerId rival = OwnerId.of(NamespacedId.of("test", "rival"));

    static final NamespacedId casing = id("casing");
    static final NamespacedId socket = id("socket");
    static final NamespacedId shell = id("shell");
    static final NamespacedId plug = id("plug");
    static final NamespacedId wrong = id("wrong");

    static final NamespacedId rootId = id("blast_furnace");
    static final NamespacedId preheaterId = id("preheater");
    static final NamespacedId solarId = id("solar");
    static final NamespacedId wideId = id("wide");
    static final NamespacedId setId = id("blast_furnace_set");
    static final NamespacedId leftId = id("left_preheater");
    static final NamespacedId rightId = id("right_preheater");

    static final LatticePoint leftParent = LatticePoint.of(0, 0, 1);
    static final LatticePoint rightParent = LatticePoint.of(2, 0, 1);
    static final LatticePoint port = LatticePoint.of(1, 0, 0);
    static final LatticePoint rootOrigin = LatticePoint.of(100, 10, 100);

    static final Orientation half = Orientation.of(Turn.half, Tilt.level, Flip.none);
    static final Orientation clockwise = Orientation.of(Turn.clockwise, Tilt.level, Flip.none);

    private StructureSetTestSupport() {}

    static NamespacedId id(String path) {
        return NamespacedId.of("settest", path);
    }

    static Siting rootSiting() {
        return Siting.of(rootOrigin, Orientation.identity);
    }

    static Blueprint rootBlueprint() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 3, 1, 3);
        CuboidSet body = bounds.asSet().subtract(CuboidSet.of(Cuboid.of(leftParent), Cuboid.of(rightParent)));
        Bay bodyBay = Bay.of(id("body"), body, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay leftBay = Bay.of(
            id("left_socket"),
            CuboidSet.of(Cuboid.of(leftParent)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        Bay rightBay = Bay.of(
            id("right_socket"),
            CuboidSet.of(Cuboid.of(rightParent)),
            Coverage.everyCell,
            Footprint.none(),
            Sharing.shared
        );
        return Blueprint.builder(rootId).bounds(bounds).checks(
            List.of(
                Check.of(id("body_blocks"), bodyBay, Focuses.bay(), Expectations.eachCell(Expectations.block(casing))),
                Check.of(id("left_check"), leftBay, Focuses.occupant(leftParent), Expectations.block(socket)),
                Check.of(id("right_check"), rightBay, Focuses.occupant(rightParent), Expectations.block(socket))
            )
        ).build();
    }

    static Blueprint preheaterBlueprint() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 2);
        Bay body = Bay.of(
            id("body"),
            CuboidSet.of(Cuboid.of(LatticePoint.origin)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Bay portBay = Bay
                .of(id("port"), CuboidSet.of(Cuboid.of(port)), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(preheaterId).bounds(bounds).checks(
            List.of(
                Check.of(id("body_check"), body, Focuses.occupant(LatticePoint.origin), Expectations.block(shell)),
                Check.of(id("port_check"), portBay, Focuses.occupant(port), Expectations.block(plug))
            )
        ).build();
    }

    static Blueprint singleBlueprint(NamespacedId id, NamespacedId block) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(id("cell"), bounds.asSet(), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(id).bounds(bounds).checks(
            List.of(Check.of(id("cell_check"), bay, Focuses.occupant(LatticePoint.origin), Expectations.block(block)))
        ).build();
    }

    static Blueprint wideBlueprint() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 3, 1, 1);
        Bay bay = Bay.of(id("body"), bounds.asSet(), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(wideId).bounds(bounds).checks(
            List.of(Check.of(id("body_blocks"), bay, Focuses.bay(), Expectations.eachCell(Expectations.block(shell))))
        ).build();
    }

    static SatelliteSpec preheaterSatellite(
        NamespacedId id,
        LatticePoint at,
        Orientation orientation,
        boolean required,
        @Nullable PresenceSpec presence,
        LinkSpec... links
    ) {
        return new SatelliteSpec(
            id,
            preheaterId,
            at,
            orientation,
            required,
            Value.object(),
            presence,
            List.of(links),
            null
        );
    }

    static SatelliteSpec leftSatellite(boolean required, LinkSpec... links) {
        return preheaterSatellite(leftId, LatticePoint.of(-2, 0, 1), Orientation.identity, required, null, links);
    }

    static SatelliteSpec rightSatellite(boolean required, LinkSpec... links) {
        return preheaterSatellite(rightId, LatticePoint.of(4, 0, 1), half, required, null, links);
    }

    static SatelliteSpec solarSatellite(NamespacedId id, LatticePoint at, LinkSpec link) {
        return new SatelliteSpec(
            id,
            solarId,
            at,
            Orientation.identity,
            false,
            Value.object(),
            null,
            List.of(link),
            null
        );
    }

    static CompiledSatellite compiled(SatelliteSpec spec, Blueprint blueprint) {
        return new CompiledSatellite(spec, blueprint);
    }

    static CompiledStructureSet compiled(CompiledSatellite... satellites) {
        return new CompiledStructureSet(setId, rootBlueprint(), List.of(satellites));
    }

    static StructureSet place(CompiledStructureSet compiled) {
        StructureSet.Placement placement = StructureSet.place(compiled, rootSiting());
        return Objects.requireNonNull(placement.set(), () -> "placement failed: " + placement.findings());
    }

    /**
     * Builds a set whose every satellite sits exactly on the host root siting, bypassing
     * {@link StructureSet#place} so the host's defensive rejection can be exercised.
     */
    static StructureSet handBuiltOnRoot(CompiledStructureSet compiled) {
        Siting root = rootSiting();
        Cuboid rootBounds = Objects.requireNonNull(root.toWorld(compiled.root().bounds()), "root bounds");
        List<StructureSet.Satellite> satellites = new ArrayList<>();
        for (CompiledSatellite candidate : compiled.satellites()) {
            Cuboid worldBounds = Objects
                    .requireNonNull(root.toWorld(candidate.blueprint().bounds()), "satellite bounds");
            CuboidSet presence = Objects
                    .requireNonNull(root.toWorld(candidate.blueprint().bounds().asSet()), "satellite presence");
            satellites.add(new StructureSet.Satellite(candidate.spec().id(), candidate, root, worldBounds, presence));
        }
        return new StructureSet(compiled, root, rootBounds, satellites);
    }

    static List<LatticePoint> bodyCells() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 3, 1, 3);
        CuboidSet body = bounds.asSet().subtract(CuboidSet.of(Cuboid.of(leftParent), Cuboid.of(rightParent)));
        List<LatticePoint> cells = new ArrayList<>();
        for (Cuboid box : body.boxes()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        cells.add(LatticePoint.of(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(cells);
    }

    static void fillHappy(Fixture fixture) {
        fillRoot(fixture);
        fillPreheater(fixture, fixture.plan.satellite(leftId));
        fillPreheater(fixture, fixture.plan.satellite(rightId));
    }

    static void fillRoot(Fixture fixture) {
        Siting root = fixture.plan.rootSiting();
        for (LatticePoint local : bodyCells()) {
            fixture.view.put(world(root, local), casing);
        }
        fixture.view.put(world(root, leftParent), socket);
        fixture.view.put(world(root, rightParent), socket);
    }

    static void fillPreheater(Fixture fixture, StructureSet.@Nullable Satellite satellite) {
        Objects.requireNonNull(satellite, "satellite");
        fixture.view.put(world(satellite.siting(), LatticePoint.origin), shell);
        fixture.view.put(world(satellite.siting(), port), plug);
    }

    static LatticePoint world(Siting siting, LatticePoint local) {
        return Objects.requireNonNull(siting.toWorld(local), () -> "unmapped " + local + " at " + siting);
    }

    static final class Fixture {

        final TestView view = new TestView();
        final FakeLedger ledger = new FakeLedger();
        final StructureManager structures;
        final ContextStore contexts;
        final StructureKernel kernel;
        final StructureSet plan;
        final List<InstanceId> instances = new ArrayList<>();

        Fixture(CompiledStructureSet compiled) {
            this(compiled, rootSiting());
        }

        Fixture(CompiledStructureSet compiled, Siting rootSiting) {
            this(compiled, rootSiting, StructureManager.create());
        }

        Fixture(CompiledStructureSet compiled, Siting rootSiting, StructureManager structures) {
            this.structures = structures;
            this.contexts = structures;
            this.kernel = StructureKernel
                    .create(Catalogs.builtin(), new Host(DocumentSource.empty(), ledger, structures, Map.of()));
            StructureSet.Placement placement = StructureSet.place(compiled, rootSiting);
            this.plan = Objects.requireNonNull(placement.set(), () -> "placement failed: " + placement.findings());
        }

        Fixture(StructureSet plan) {
            this(plan, StructureManager.create());
        }

        Fixture(StructureSet plan, StructureManager structures) {
            this.structures = structures;
            this.contexts = structures;
            this.plan = plan;
            this.kernel = StructureKernel
                    .create(Catalogs.builtin(), new Host(DocumentSource.empty(), ledger, structures, Map.of()));
        }

        StructureSetResult run() {
            return run(Budget.unbounded());
        }

        StructureSetResult run(Budget budget) {
            return run(new TestLease(view), budget);
        }

        StructureSetResult run(SnapshotLease lease) {
            return run(lease, Budget.unbounded());
        }

        StructureSetResult run(SnapshotLease lease, Budget budget) {
            return new StructureSetHost(kernel).run(plan, lease, owner, budget, this::begin);
        }

        StructureSetResult check() {
            return new StructureSetHost(kernel)
                    .check(plan, new TestLease(view), owner, Budget.unbounded(), this::begin);
        }

        InstanceId instance(int index) {
            return instances.get(index);
        }

        private StructureAttempt begin(StructureSpec spec) {
            StructureAttempt attempt = structures.begin(world, spec);
            instances.add(attempt.id());
            return attempt;
        }
    }

    static final class TestView implements WorldView {

        private final Map<LatticePoint, OccupantSnapshot> occupants = new LinkedHashMap<>();
        private final Map<LatticePoint, ReadRefusal> refused = new LinkedHashMap<>();
        private Cuboid bounds = Cuboid.of(-2048, -2048, -2048, 4096, 2047, 4096);
        private int sectionSize = 16;

        TestView put(LatticePoint cell, NamespacedId type) {
            OccupantRef ref = new OccupantRef(snapshot, 0, type, OccupantKind.block, cell);
            OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.block);
            occupants.put(cell, new OccupantSnapshot(ref, descriptor, Cuboid.of(cell), List.of()));
            return this;
        }

        TestView putBlockEntity(LatticePoint cell, NamespacedId type) {
            OccupantRef ref = new OccupantRef(snapshot, 0, type, OccupantKind.blockEntity, cell);
            OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.blockEntity);
            occupants.put(cell, new OccupantSnapshot(ref, descriptor, Cuboid.of(cell), List.of()));
            return this;
        }

        TestView putEntity(LatticePoint cell, NamespacedId type) {
            OccupantRef ref = new OccupantRef(snapshot, 0, type, OccupantKind.entity, cell);
            OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.entity);
            occupants.put(cell, new OccupantSnapshot(ref, descriptor, Cuboid.of(cell), List.of()));
            return this;
        }

        TestView air(LatticePoint cell) {
            occupants.put(cell, OccupantSnapshot.air(snapshot, 0, cell));
            return this;
        }

        TestView refuse(LatticePoint cell) {
            refused.put(cell, new ReadRefusal(ReadStatus.notCaptured, Codes.unreadableRead.code()));
            return this;
        }

        TestView bounds(Cuboid bounds) {
            this.bounds = bounds;
            return this;
        }

        TestView sectionSize(int size) {
            this.sectionSize = size;
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
            Map<LatticePoint, OccupantSnapshot> slice = new TreeMap<>();
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
            SectionSnapshot section = SectionSnapshot.of(key, slice, digest(slice));
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
            return new CellRead.Found(occupant == null ? OccupantSnapshot.air(snapshot, 0, cell) : occupant);
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
            List<OccupantSnapshot> selected = new ArrayList<>();
            for (OccupantSnapshot occupant : occupants.values()) {
                if (occupant.footprint().intersects(box) && filter.matches(occupant)) {
                    selected.add(occupant);
                }
            }
            selected.sort((left, right) -> left.ref().compareTo(right.ref()));
            return List.copyOf(selected);
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
                case SectionSlot.Partial partial -> partial.section().contentDigest();
            };
        }

        private static long digest(Map<LatticePoint, OccupantSnapshot> slice) {
            long digest = 17L;
            for (Map.Entry<LatticePoint, OccupantSnapshot> entry : slice.entrySet()) {
                digest = digest * 31L + entry.getKey().hashCode();
                digest = digest * 31L + entry.getValue().ref().hashCode();
            }
            return digest;
        }
    }

    static final class TestLease implements SnapshotLease {

        private final TestView view;
        private boolean current = true;

        TestLease(TestView view) {
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

    static final class FakeLedger implements ClaimLedger {

        private final List<ClaimRow> rows = new ArrayList<>();
        private long revision;

        @Nullable
        Siting movingSiting;
        @Nullable
        Siting throwingRelease;
        int settleCalls;
        int releaseCalls;
        final List<Siting> released = new ArrayList<>();
        private final Map<InstanceId, Siting> sitings = new LinkedHashMap<>();

        void seed(ClaimRow... seeded) {
            for (ClaimRow row : seeded) {
                rows.add(row);
            }
            rows.sort(ClaimRow::compareTo);
        }

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
            settleCalls++;
            Siting requestSiting = request.rows().isEmpty() ? null : request.rows().get(0).siting();
            if (requestSiting != null
                    && requestSiting.equals(movingSiting)
                    && !sitings.containsKey(request.instance())) {
                return new ClaimVerdict.RevisionMoved(request.expectedRevision(), revision + 1);
            }
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
            rows.removeIf(row -> row.instance().equals(request.instance()));
            rows.addAll(request.rows());
            rows.sort(ClaimRow::compareTo);
            if (requestSiting != null) {
                sitings.put(request.instance(), requestSiting);
            }
            revision++;
            return new ClaimVerdict.Settled(revision, List.copyOf(request.rows()));
        }

        @Override
        public ClaimVerdict release(InstanceId instance) {
            releaseCalls++;
            Siting siting = sitings.get(instance);
            released.add(siting);
            if (siting != null && siting.equals(throwingRelease)) {
                throw new IllegalStateException("release refused by the test ledger");
            }
            rows.removeIf(row -> row.instance().equals(instance));
            revision++;
            return new ClaimVerdict.Settled(revision, List.of());
        }

        @Override
        public ClaimView read(Cuboid box) {
            List<ClaimRow> found = new ArrayList<>();
            for (ClaimRow row : rows) {
                if (box.contains(row.cell().point())) {
                    found.add(row);
                }
            }
            return new ClaimView(found, revision, true);
        }

        @Override
        public void retire() {
            rows.clear();
            revision = 0;
        }

        List<ClaimRow> rows() {
            return List.copyOf(rows);
        }
    }

    static final class RemoveFailingStructures implements StructureManager {

        private final StructureManager delegate = StructureManager.create();
        private final List<InstanceId> removed = new ArrayList<>();
        int allowedRemovals = Integer.MAX_VALUE;

        @Override
        public StructureAttempt begin(World world, StructureSpec spec) {
            return delegate.begin(world, spec);
        }

        @Override
        public @Nullable StructureInstance find(InstanceId id) {
            return delegate.find(id);
        }

        @Override
        public @Nullable StructureInstance find(World world, OwnerId owner, Siting siting) {
            return delegate.find(world, owner, siting);
        }

        @Override
        public @Nullable StructureInstance find(OwnerId owner, Siting siting) {
            return delegate.find(owner, siting);
        }

        @Override
        public List<StructureInstance> findAt(LatticePoint worldCell) {
            return delegate.findAt(worldCell);
        }

        @Override
        public List<StructureInstance> instances() {
            return delegate.instances();
        }

        @Override
        public StructureContext context(InstanceId instance) {
            return delegate.context(instance);
        }

        @Override
        public StructureContext publish(
            InstanceId instance,
            Map<ContextKey<?>, Object> values,
            FindingBundle findings,
            Spend spend
        ) {
            return delegate.publish(instance, values, findings, spend);
        }

        @Override
        public @Nullable StructureContext remove(InstanceId instance) {
            removed.add(instance);
            if (removed.size() > allowedRemovals) {
                throw new IllegalStateException("context removal refused by the test store");
            }
            return delegate.remove(instance);
        }

        @Override
        public void onEvent(StructureEvent event) {
            if (event instanceof StructureEvent.Broken broken) {
                removed.add(broken.id());
                if (removed.size() > allowedRemovals) {
                    throw new IllegalStateException("context removal refused by the test store");
                }
            }
            delegate.onEvent(event);
        }

        List<InstanceId> removed() {
            return List.copyOf(removed);
        }
    }
}
