package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.world.CaptureReport;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.EntityWindow;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SectionSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import dev.vfyjxf.cumulus.kernel.world.WorldCapture;
import dev.vfyjxf.cumulus.mc.LevelWatch.WatchHandle;
import dev.vfyjxf.cumulus.mc.capability.BlockNestedExtentContext;
import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentHolders;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentLookup;
import dev.vfyjxf.cumulus.mc.trait.TraitCaptureContext;
import dev.vfyjxf.cumulus.mc.trait.TraitLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Server-thread capture: frames, block states, block entities and loaded entities, no chunk loads. */
@NullMarked
public final class LevelCapture implements WorldCapture {

    static final int cellSize = 16;
    private static final FindingCode notCaptured = AdapterCodes.notCaptured;
    private static final Comparator<EntityCandidate> entityOrder = Comparator.comparing(EntityCandidate::type)
            .thenComparing(EntityCandidate::anchor).thenComparing(EntityCandidate::footprint)
            .thenComparing(EntityCandidate::id);

    private final ServerLevel level;
    private final World world;
    private final NestedExtentLookup nestedLookup;
    private final TraitLookup traitLookup;
    private final TraitCaptureContext traitCapture;
    private final AtomicLong revisions = new AtomicLong();
    private volatile boolean closed;
    private @Nullable WatchHandle watch;

    /** The level instance this capture was built for; a reloaded dimension builds a new one. */
    ServerLevel level() {
        return level;
    }

    public LevelCapture(ServerLevel level) {
        this(level, NestedExtentLookup.capabilities(), TraitLookup.builtin(), TraitCaptureContext.empty());
    }

    public LevelCapture(ServerLevel level, TraitLookup traitLookup, TraitCaptureContext traitCapture) {
        this(level, NestedExtentLookup.capabilities(), traitLookup, traitCapture);
    }

    public LevelCapture(ServerLevel level, NestedExtentLookup nestedLookup) {
        this(level, nestedLookup, TraitLookup.builtin(), TraitCaptureContext.empty());
    }

    LevelCapture(ServerLevel level, NestedExtentLookup nestedLookup, TraitLookup traitLookup) {
        this(level, nestedLookup, traitLookup, TraitCaptureContext.empty());
    }

    LevelCapture(
        ServerLevel level,
        NestedExtentLookup nestedLookup,
        TraitLookup traitLookup,
        TraitCaptureContext traitCapture
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.nestedLookup = Objects.requireNonNull(nestedLookup, "nestedLookup");
        this.traitLookup = Objects.requireNonNull(traitLookup, "traitLookup");
        this.traitCapture = Objects.requireNonNull(traitCapture, "traitCapture");
        var location = level.dimension().location();
        this.world = World.of(NamespacedId.of(location.getNamespace(), location.getPath()));
    }

    @Override
    public CaptureResult capture(CaptureRequest request) {
        return capture(request, null);
    }

    /**
     * Adapter-only: capture with the siting orientation, whose inverse audits every root block state. A state the
     * inverse cannot express is grouped into an {@code SC-MC-0004} report entry; child frames and unoriented captures
     * are not audited.
     */
    public CaptureResult capture(CaptureRequest request, @Nullable Orientation orientation) {
        if (closed) {
            throw new IllegalStateException("capture is closed");
        }
        Cuboid bounds = request.bounds();
        SnapshotPolicy policy = request.policy();
        long volume = bounds.volume();
        if (volume > policy.maxElements()) {
            int elements = (int) Math.min(Integer.MAX_VALUE, volume);
            CaptureReport report = CaptureReport.of(
                List.of(
                    new CaptureReport.Entry(
                        AdapterCodes.overCap,
                        "the root capture is over the element ceiling",
                        List.of("bounds cells: " + volume, "elements: " + elements, "ceiling: " + policy.maxElements()),
                        bounds.asSet()
                    )
                )
            );
            return new CaptureResult.TooLarge(bounds, elements, report);
        }
        long revision = revisions.incrementAndGet();
        boolean captured = false;
        try {
            SnapshotId snapshot = new SnapshotId(world, revision);
            CaptureContext context = new CaptureContext(snapshot, policy, volume, orientation);
            FrameCapture rootCapture = new FrameCapture(context, 0, 0, bounds, LatticePoint.origin, true);
            FrameData root = rootCapture.build(List.of());
            if (root == null) {
                return new CaptureResult.Unavailable(ReadStatus.notCaptured, notCaptured);
            }
            LevelSnapshot view = new LevelSnapshot(snapshot, policy, root);
            LevelWatch.WatchHandle handle = acquire(rootCapture.watch());
            LevelLease lease = new LevelLease(
                snapshot,
                view,
                () -> !closed && revisions.get() == revision && handle.isCurrent(),
                handle::release
            );
            captured = true;
            return new CaptureResult.Captured(view, lease, context.report());
        } finally {
            if (!captured) {
                revisions.decrementAndGet();
            }
        }
    }

    @Override
    public long currentRevision() {
        return revisions.get();
    }

    /** Invalidates every lease handed out by this capture; idempotent. */
    void close() {
        closed = true;
        WatchHandle handle = watch;
        if (handle != null) {
            handle.release();
        }
    }

    private WatchHandle acquire(LevelWatch.WatchRegion region) {
        WatchHandle handle = LevelWatch.acquire(level, region);
        WatchHandle previous = watch;
        watch = handle;
        if (previous != null) {
            previous.release();
        }
        return handle;
    }

    @Nullable
    WatchHandle watchHandle() {
        return watch;
    }

    private final class FrameCapture {

        private final CaptureContext context;
        private final int frame;
        private final int depth;
        private final Cuboid bounds;
        private final LatticePoint origin;
        private final boolean root;
        private final boolean nestedEnabled;
        private final Map<SectionKey, SectionSlot> sections = new TreeMap<>();
        private final Map<SectionKey, Cuboid> digestRegions = new TreeMap<>();
        private final Map<Long, PalettedContainer<BlockState>> snapshotStates = new HashMap<>();
        private final Map<Long, FluidState[]> snapshotFluids = new HashMap<>();
        private final Map<BlockPos, BlockEntity> snapshotBlockEntities = new HashMap<>();
        private final Set<Long> copiedSections = new HashSet<>();
        private final Map<LatticePoint, NestedExtent> blockExtents = new TreeMap<>();
        private final LevelWatch.WatchRegion watch = new LevelWatch.WatchRegion();

        private FrameCapture(
            CaptureContext context,
            int frame,
            int depth,
            Cuboid bounds,
            LatticePoint origin,
            boolean root
        ) {
            this.context = context;
            this.frame = frame;
            this.depth = depth;
            this.bounds = bounds;
            this.origin = origin;
            this.root = root;
            this.nestedEnabled = context.policy().kinds().contains(OccupantKind.nested)
                    && depth < context.policy().maxDepth();
        }

        private @Nullable FrameData build(List<NestedKey> path) {
            if (!captureSections()) {
                return null;
            }
            watch.frame(bounds, origin);
            List<EntityCandidate> candidates = entityCandidates();
            for (EntityCandidate candidate : candidates) {
                watch.entity(candidate.id(), candidate.type(), candidate.anchor(), candidate.footprint());
            }
            List<OccupantSnapshot> entities = entityBodies(candidates);
            SnapshotLevel host = snapshot(candidates);
            Map<SectionKey, SectionSlot> digested = digestSections(host);
            Map<LatticePoint, NestedExtent> declarations = nestedDeclarations(candidates);
            List<OccupantSnapshot> nestedBodies = new ArrayList<>();
            Map<LatticePoint, FrameData> children = new TreeMap<>();
            for (Map.Entry<LatticePoint, NestedExtent> entry : declarations.entrySet()) {
                NestedExtent extent = entry.getValue();
                Cuboid footprint = extent.bounds().translate(-origin.x(), -origin.y(), -origin.z());
                if (footprint == null) {
                    StructureCumulus.logger.warn("nested extent {} leaves the parent frame", extent);
                    cut("parent-frame", extent, worldAnchor(entry.getKey()));
                    continue;
                }
                nestedBodies.add(
                    OccupantDescriptors.nested(context.snapshot(), frame, entry.getKey(), extent.type(), footprint)
                );
                FrameData child = child(extent, entry.getKey(), path);
                if (child != null) {
                    children.put(entry.getKey(), child);
                }
            }
            return new FrameData(
                context.snapshot(),
                frame,
                depth,
                origin,
                bounds,
                digested,
                entities,
                nestedBodies,
                children,
                host
            );
        }

        private SnapshotLevel snapshot(List<EntityCandidate> candidates) {
            List<SnapshotLevel.CapturedEntity> capturedEntities = new ArrayList<>();
            for (EntityCandidate candidate : candidates) {
                LatticePoint anchor = local(candidate.anchor());
                Cuboid footprint = local(candidate.footprint());
                if (anchor == null || footprint == null) {
                    continue;
                }
                capturedEntities.add(
                    new SnapshotLevel.CapturedEntity(candidate.id(), candidate.type(), footprint, candidate.entity().getType())
                );
            }
            return new SnapshotLevel(
                origin,
                bounds,
                level.getMinBuildHeight(),
                level.getHeight(),
                snapshotStates,
                snapshotFluids,
                snapshotBlockEntities,
                capturedEntities
            );
        }

        private Map<SectionKey, SectionSlot> digestSections(SnapshotLevel host) {
            Map<SectionKey, SectionSlot> digested = new TreeMap<>();
            for (Map.Entry<SectionKey, SectionSlot> entry : sections.entrySet()) {
                SectionKey key = entry.getKey();
                SectionSlot slot = entry.getValue();
                Cuboid region = digestRegions.get(key);
                if (region == null) {
                    digested.put(key, slot);
                    continue;
                }
                switch (slot) {
                    case SectionSlot.Present present ->
                        digested.put(key, new SectionSlot.Present(withHostDigest(present.section(), host, region)));
                    case SectionSlot.Partial partial -> digested.put(
                        key,
                        new SectionSlot.Partial(withHostDigest(partial.section(), host, region), partial.unreadable())
                    );
                    default -> digested.put(key, slot);
                }
            }
            return digested;
        }

        private static SectionSnapshot withHostDigest(SectionSnapshot section, SnapshotLevel host, Cuboid region) {
            return SectionSnapshot
                    .of(section.key(), section.occupants(), contentDigest(section.contentDigest(), host, region));
        }

        private @Nullable FrameData child(NestedExtent extent, LatticePoint localAnchor, List<NestedKey> path) {
            LatticePoint anchor = worldAnchor(localAnchor);
            if (depth + 1 > context.policy().maxDepth()) {
                cut("depth", extent, anchor);
                return null;
            }
            NestedKey key = new NestedKey(extent.type(), extent.origin());
            if (path.contains(key)) {
                cut("cycle", extent, anchor);
                return null;
            }
            Cuboid childBounds = extent.bounds()
                    .translate(-extent.origin().x(), -extent.origin().y(), -extent.origin().z());
            if (childBounds == null) {
                StructureCumulus.logger.warn("nested extent {} leaves the lattice", extent);
                cut("parent-frame", extent, anchor);
                return null;
            }
            long childVolume = childBounds.volume();
            if (childVolume > context.policy().maxElements() - context.spent()) {
                cut("budget", extent, anchor);
                return null;
            }
            if (!chunksLoaded(childBounds, extent.origin())) {
                cut("unloaded", extent, anchor);
                return null;
            }
            long spentBefore = context.spent();
            int frameBefore = context.nextFrame();
            context.spend(childVolume);
            int childFrame = context.reserveFrame();
            List<NestedKey> childPath = new ArrayList<>(path);
            childPath.add(key);
            FrameCapture childCapture = new FrameCapture(
                context,
                childFrame,
                depth + 1,
                childBounds,
                extent.origin(),
                false
            );
            FrameData result = childCapture.build(childPath);
            if (result == null) {
                context.restore(spentBefore, frameBefore);
                cut("unloaded", extent, anchor);
                return null;
            }
            watch.merge(childCapture.watch);
            return result;
        }

        private void cut(String reason, NestedExtent extent, LatticePoint anchor) {
            context.cut(reason, extent, anchor, frame, root);
        }

        private LatticePoint worldAnchor(LatticePoint localAnchor) {
            LatticePoint world = origin.offset(localAnchor.x(), localAnchor.y(), localAnchor.z());
            return world == null ? localAnchor : world;
        }

        private boolean captureSections() {
            int minSectionX = Math.floorDiv(bounds.min().x(), cellSize);
            int maxSectionX = Math.floorDiv(bounds.max().x() - 1, cellSize);
            int minSectionZ = Math.floorDiv(bounds.min().z(), cellSize);
            int maxSectionZ = Math.floorDiv(bounds.max().z() - 1, cellSize);
            int minSectionY = Math.floorDiv(bounds.min().y(), cellSize);
            int maxSectionY = Math.floorDiv(bounds.max().y() - 1, cellSize);
            for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                        SectionKey key = new SectionKey(
                            context.snapshot().world(),
                            frame,
                            sectionX,
                            sectionY,
                            sectionZ
                        );
                        SectionSlot slot = section(key, sectionX, sectionY, sectionZ);
                        if (slot == null) {
                            return false;
                        }
                        sections.put(key, slot);
                    }
                }
            }
            return true;
        }

        private @Nullable SectionSlot section(SectionKey key, int sectionX, int sectionY, int sectionZ) {
            int sectionMinX = sectionX * cellSize;
            int sectionMinY = sectionY * cellSize;
            int sectionMinZ = sectionZ * cellSize;
            int minX = Math.max(sectionMinX, bounds.min().x());
            int minY = Math.max(sectionMinY, bounds.min().y());
            int minZ = Math.max(sectionMinZ, bounds.min().z());
            int maxX = Math.min(sectionMinX + cellSize, bounds.max().x());
            int maxY = Math.min(sectionMinY + cellSize, bounds.max().y());
            int maxZ = Math.min(sectionMinZ + cellSize, bounds.max().z());
            Map<LatticePoint, ReadRefusal> unreadable = new TreeMap<>();
            int readMinY = minY;
            int readMaxY = maxY;
            if (!root) {
                int buildMinY = level.getMinBuildHeight() - origin.y();
                int buildMaxY = level.getMaxBuildHeight() - origin.y();
                readMinY = Math.max(minY, buildMinY);
                readMaxY = Math.min(maxY, buildMaxY);
                if (readMinY >= readMaxY) {
                    return new SectionSlot.Refused(ReadStatus.outOfBounds, notCaptured);
                }
                if (sectionMinY < buildMinY || sectionMinY + cellSize > buildMaxY) {
                    ReadRefusal refusal = new ReadRefusal(ReadStatus.outOfBounds, notCaptured);
                    for (int x = sectionMinX; x < sectionMinX + cellSize; x++) {
                        for (int y = sectionMinY; y < sectionMinY + cellSize; y++) {
                            if (y >= buildMinY && y < buildMaxY) {
                                continue;
                            }
                            for (int z = sectionMinZ; z < sectionMinZ + cellSize; z++) {
                                unreadable.put(LatticePoint.of(x, y, z), refusal);
                            }
                        }
                    }
                }
            }
            Map<Long, LevelChunk> chunks = new HashMap<>();
            int worldMinX = minX + origin.x();
            int worldMinZ = minZ + origin.z();
            int worldMaxX = maxX - 1 + origin.x();
            int worldMaxZ = maxZ - 1 + origin.z();
            for (int chunkX = Math.floorDiv(worldMinX, cellSize); chunkX
                    <= Math.floorDiv(worldMaxX, cellSize); chunkX++) {
                for (int chunkZ = Math.floorDiv(worldMinZ, cellSize); chunkZ
                        <= Math.floorDiv(worldMaxZ, cellSize); chunkZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        return null;
                    }
                    chunks.put(ChunkPos.asLong(chunkX, chunkZ), chunk);
                }
            }
            Map<LatticePoint, OccupantSnapshot> occupants = new TreeMap<>();
            ReadRefusal missingSection = new ReadRefusal(ReadStatus.outOfBounds, notCaptured);
            for (int x = minX; x < maxX; x++) {
                for (int y = readMinY; y < readMaxY; y++) {
                    for (int z = minZ; z < maxZ; z++) {
                        int worldX = x + origin.x();
                        int worldY = y + origin.y();
                        int worldZ = z + origin.z();
                        LevelChunk chunk = chunks
                                .get(ChunkPos.asLong(Math.floorDiv(worldX, cellSize), Math.floorDiv(worldZ, cellSize)));
                        if (chunk == null) {
                            if (root) {
                                return null;
                            }
                            unreadable.put(LatticePoint.of(x, y, z), missingSection);
                            continue;
                        }
                        int sectionIndex = chunk.getSectionIndex(worldY);
                        if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
                            if (!root) {
                                unreadable.put(LatticePoint.of(x, y, z), missingSection);
                            }
                            continue;
                        }
                        LevelChunkSection chunkSection = chunk.getSection(sectionIndex);
                        long worldSection = SnapshotLevel.worldSectionKey(worldX, worldY, worldZ);
                        if (copiedSections.add(worldSection) && !chunkSection.hasOnlyAir()) {
                            PalettedContainer<BlockState> states = chunkSection.getStates().copy();
                            snapshotStates.put(worldSection, states);
                            snapshotFluids.put(worldSection, copyFluids(states));
                        }
                        PalettedContainer<BlockState> sectionStates = snapshotStates.get(worldSection);
                        BlockState state = sectionStates == null
                                ? Blocks.AIR.defaultBlockState()
                                : sectionStates.get(worldX & 15, worldY & 15, worldZ & 15);
                        if (state.isAir()) {
                            continue;
                        }
                        LatticePoint cell = LatticePoint.of(x, y, z);
                        if (root) {
                            context.audit(state, cell);
                        }
                        BlockPos worldPos = new BlockPos(worldX, worldY, worldZ);
                        BlockEntity blockEntity = state.hasBlockEntity() ? chunk.getBlockEntity(worldPos) : null;
                        List<TraitSlot> traits = OccupantTraits.block(
                            context.policy(),
                            traitLookup,
                            traitCapture,
                            level,
                            worldPos,
                            state,
                            blockEntity
                        );
                        OccupantSnapshot occupant = blockEntity == null
                                ? OccupantDescriptors.block(context.snapshot(), frame, cell, state, traits)
                                : OccupantDescriptors.blockEntity(context.snapshot(), frame, cell, blockEntity, traits);
                        occupants.put(cell, occupant);
                        if (blockEntity != null) {
                            BlockEntity copied = copyBlockEntity(blockEntity, new BlockPos(x, y, z), state);
                            if (copied != null) {
                                snapshotBlockEntities.put(new BlockPos(x, y, z), copied);
                            }
                            watch.blockEntity(worldPos, blockEntity, level.registryAccess());
                        }
                        if (nestedEnabled && (!state.hasBlockEntity() || blockEntity != null)) {
                            NestedExtent extent = blockExtent(worldPos, state, blockEntity);
                            if (extent != null) {
                                blockExtents.put(cell, extent);
                            }
                        }
                    }
                }
            }
            Cuboid digestRegion = Cuboid.of(minX, readMinY, minZ, maxX, readMaxY, maxZ);
            if (!unreadable.isEmpty()) {
                digestRegions.put(key, digestRegion);
                return new SectionSlot.Partial(SectionSnapshot.of(key, occupants, digest(occupants)), unreadable);
            }
            if (occupants.isEmpty()) {
                return new SectionSlot.Air(0);
            }
            digestRegions.put(key, digestRegion);
            return new SectionSlot.Present(SectionSnapshot.of(key, occupants, digest(occupants)));
        }

        private List<OccupantSnapshot> entityBodies(List<EntityCandidate> candidates) {
            if (!context.policy().kinds().contains(OccupantKind.entity)) {
                return List.of();
            }
            List<OccupantSnapshot> bodies = new ArrayList<>();
            for (EntityCandidate candidate : candidates) {
                LatticePoint anchor = local(candidate.anchor());
                Cuboid footprint = local(candidate.footprint());
                if (anchor == null || footprint == null) {
                    continue;
                }
                bodies.add(
                    OccupantDescriptors.entity(
                        context.snapshot(),
                        frame,
                        anchor,
                        candidate.type(),
                        candidate.id(),
                        footprint,
                        OccupantTraits.entity(context.policy(), traitLookup, traitCapture, candidate.entity())
                    )
                );
            }
            return bodies;
        }

        private Map<LatticePoint, NestedExtent> nestedDeclarations(List<EntityCandidate> candidates) {
            if (!nestedEnabled) {
                return Map.of();
            }
            Map<LatticePoint, NestedExtent> entityExtents = new TreeMap<>();
            for (EntityCandidate candidate : candidates) {
                LatticePoint anchor = local(candidate.anchor());
                if (anchor == null || blockExtents.containsKey(anchor) || entityExtents.containsKey(anchor)) {
                    continue;
                }
                NestedExtent extent = entityExtent(candidate);
                if (extent != null) {
                    entityExtents.put(anchor, extent);
                }
            }
            Map<LatticePoint, NestedExtent> declarations = new TreeMap<>(entityExtents);
            declarations.putAll(blockExtents);
            return declarations;
        }

        private List<EntityCandidate> entityCandidates() {
            SnapshotPolicy policy = context.policy();
            boolean wantsEntities = policy.kinds().contains(OccupantKind.entity);
            if (!wantsEntities && !nestedEnabled) {
                return List.of();
            }
            Cuboid worldBounds = worldBounds(bounds, origin);
            Map<UUID, EntityCandidate> found = new HashMap<>();
            AABB captured = box(worldBounds).inflate(EntityWindow.maxEntitySpan);
            for (Entity entity : level.getEntitiesOfClass(Entity.class, captured, EntitySelector.NO_SPECTATORS)) {
                addCandidate(found, entity, worldBounds);
            }
            if (root) {
                for (EntityWindow window : policy.windows()) {
                    AABB windowBox = box(window.cells());
                    for (Entity entity : level.getEntitiesOfClass(
                        Entity.class,
                        windowBox,
                        candidate -> !candidate.isSpectator() && window.type().equals(entityType(candidate))
                    )) {
                        addCandidate(found, entity, worldBounds);
                    }
                }
            }
            List<EntityCandidate> candidates = new ArrayList<>(found.values());
            candidates.sort(entityOrder);
            return List.copyOf(candidates);
        }

        private @Nullable LatticePoint local(LatticePoint world) {
            return world.offset(-origin.x(), -origin.y(), -origin.z());
        }

        private LevelWatch.WatchRegion watch() {
            return watch;
        }

        private @Nullable Cuboid local(Cuboid world) {
            return world.translate(-origin.x(), -origin.y(), -origin.z());
        }
    }

    private @Nullable NestedExtent blockExtent(BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        try {
            return nestedLookup.get(
                NestedExtentHolders.block,
                new BlockNestedExtentContext(level, pos, state, blockEntity),
                traitCapture.capabilityContext()
            );
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("nested block provider failed at {}", pos, failure);
            return null;
        }
    }

    private @Nullable NestedExtent entityExtent(EntityCandidate candidate) {
        try {
            return nestedLookup.get(NestedExtentHolders.entity, candidate.entity(), traitCapture.capabilityContext());
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("nested entity provider failed for {}", candidate.id(), failure);
            return null;
        }
    }

    private @Nullable BlockEntity copyBlockEntity(BlockEntity source, BlockPos localPos, BlockState state) {
        try {
            return BlockEntity
                    .loadStatic(localPos, state, source.saveWithId(level.registryAccess()), level.registryAccess());
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("block entity copy failed at {}", localPos, failure);
            return null;
        }
    }

    private static FluidState[] copyFluids(PalettedContainer<BlockState> states) {
        FluidState[] fluids = new FluidState[cellSize * cellSize * cellSize];
        int index = 0;
        for (int y = 0; y < cellSize; y++) {
            for (int z = 0; z < cellSize; z++) {
                for (int x = 0; x < cellSize; x++) {
                    fluids[index++] = states.get(x, y, z).getFluidState();
                }
            }
        }
        return fluids;
    }

    private boolean chunksLoaded(Cuboid localBounds, LatticePoint origin) {
        int minY = Math.max(localBounds.min().y() + origin.y(), level.getMinBuildHeight());
        int maxY = Math.min(localBounds.max().y() + origin.y(), level.getMaxBuildHeight());
        if (minY >= maxY) {
            return true;
        }
        int minX = localBounds.min().x() + origin.x();
        int maxX = localBounds.max().x() - 1 + origin.x();
        int minZ = localBounds.min().z() + origin.z();
        int maxZ = localBounds.max().z() - 1 + origin.z();
        for (int chunkX = Math.floorDiv(minX, cellSize); chunkX <= Math.floorDiv(maxX, cellSize); chunkX++) {
            for (int chunkZ = Math.floorDiv(minZ, cellSize); chunkZ <= Math.floorDiv(maxZ, cellSize); chunkZ++) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void addCandidate(Map<UUID, EntityCandidate> found, Entity entity, Cuboid bounds) {
        Cuboid footprint = footprint(entity.getBoundingBox());
        if (footprint == null || !footprint.intersects(bounds)) {
            return;
        }
        BlockPos position = entity.blockPosition();
        if (!LatticePoint.inLattice(position.getX(), position.getY(), position.getZ())) {
            return;
        }
        EntityCandidate candidate = new EntityCandidate(
            entity.getUUID(),
            entityType(entity),
            LatticePoint.of(position.getX(), position.getY(), position.getZ()),
            footprint,
            entity
        );
        found.putIfAbsent(candidate.id(), candidate);
    }

    private static NamespacedId blockType(BlockState state) {
        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return NamespacedId.of(key.getNamespace(), key.getPath());
    }

    static NamespacedId entityType(Entity entity) {
        var location = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return NamespacedId.of(location.getNamespace(), location.getPath());
    }

    static AABB box(Cuboid cuboid) {
        return new AABB(
            cuboid.min().x(),
            cuboid.min().y(),
            cuboid.min().z(),
            cuboid.max().x(),
            cuboid.max().y(),
            cuboid.max().z()
        );
    }

    private static Cuboid worldBounds(Cuboid local, LatticePoint origin) {
        Cuboid translated = local.translate(origin.x(), origin.y(), origin.z());
        return Objects.requireNonNull(translated, "world bounds");
    }

    static @Nullable Cuboid footprint(AABB body) {
        int minX = (int) Math.max(LatticePoint.minXz, Math.min(LatticePoint.maxXz, Math.floor(body.minX)));
        int minY = (int) Math.max(LatticePoint.minY, Math.min(LatticePoint.maxY, Math.floor(body.minY)));
        int minZ = (int) Math.max(LatticePoint.minXz, Math.min(LatticePoint.maxXz, Math.floor(body.minZ)));
        int maxX = (int) Math.max(LatticePoint.minXz, Math.min(LatticePoint.maxXz, Math.ceil(body.maxX)));
        int maxY = (int) Math.max(LatticePoint.minY, Math.min(LatticePoint.maxY, Math.ceil(body.maxY)));
        int maxZ = (int) Math.max(LatticePoint.minXz, Math.min(LatticePoint.maxXz, Math.ceil(body.maxZ)));
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return null;
        }
        return Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    static long contentDigest(long occupantDigest, SnapshotLevel host, Cuboid region) {
        return occupantDigest * 31L + host.contentDigest(region);
    }

    private static long digest(Map<LatticePoint, OccupantSnapshot> occupants) {
        return occupants.values().stream()
                .mapToLong(occupant -> occupant.ref().hashCode() ^ occupant.descriptor().hashCode())
                .reduce(0L, (left, right) -> left * 31L + right);
    }

    private record EntityCandidate(UUID id, NamespacedId type, LatticePoint anchor, Cuboid footprint, Entity entity) {}

    private record NestedKey(NamespacedId type, LatticePoint origin) {}

    private record AuditKey(NamespacedId block, String property, String value) implements Comparable<AuditKey> {

        @Override
        public int compareTo(AuditKey other) {
            int byBlock = block.compareTo(other.block);
            if (byBlock != 0) {
                return byBlock;
            }
            int byProperty = property.compareTo(other.property);
            return byProperty != 0 ? byProperty : value.compareTo(other.value);
        }
    }

    private record Cut(String reason, NestedExtent extent, LatticePoint anchor, int frame, boolean root) {

        private CaptureReport.Entry entry() {
            return new CaptureReport.Entry(
                AdapterCodes.cut,
                "a nested extent was cut: " + reason,
                List.of("reason: " + reason, "frame: " + frame, "anchor: " + anchor, "extent: " + extent.type().text()),
                root ? extent.bounds().asSet() : null
            );
        }
    }

    private static final class CaptureContext {

        private final SnapshotId snapshot;
        private final SnapshotPolicy policy;
        private final @Nullable Orientation orientation;
        private final @Nullable Orientation auditOrientation;
        private final List<Cut> cuts = new ArrayList<>();
        private final Map<AuditKey, CuboidSet> audits = new TreeMap<>();
        private long spent;
        private int nextFrame = 1;

        private CaptureContext(
            SnapshotId snapshot,
            SnapshotPolicy policy,
            long spent,
            @Nullable Orientation orientation
        ) {
            this.snapshot = snapshot;
            this.policy = policy;
            this.spent = spent;
            this.orientation = orientation;
            this.auditOrientation = orientation == null ? null : orientation.inverse();
        }

        /** Records a root-frame state the inverted siting cannot express. */
        private void audit(BlockState state, LatticePoint cell) {
            Orientation projection = auditOrientation;
            if (projection == null) {
                return;
            }
            OrientationMapping.MappingResult result = OrientationMapping.transform(projection, state);
            if (result instanceof OrientationMapping.MappingResult.Mapped) {
                return;
            }
            var unrepresentable = (OrientationMapping.MappingResult.Unrepresentable) result;
            AuditKey key = new AuditKey(blockType(state), unrepresentable.property(), unrepresentable.value());
            audits.merge(key, CuboidSet.of(Cuboid.of(cell)), CuboidSet::union);
        }

        private void cut(String reason, NestedExtent extent, LatticePoint anchor, int frame, boolean root) {
            cuts.add(new Cut(reason, extent, anchor, frame, root));
        }

        private CaptureReport report() {
            List<CaptureReport.Entry> entries = new ArrayList<>(audits.size() + cuts.size());
            Orientation orientation = this.orientation;
            for (Map.Entry<AuditKey, CuboidSet> group : audits.entrySet()) {
                AuditKey key = group.getKey();
                CuboidSet cells = group.getValue();
                entries.add(
                    new CaptureReport.Entry(
                        AdapterCodes.orientation,
                        "a captured block state cannot express the siting orientation",
                        List.of(
                            "block: " + key.block().text(),
                            "property: " + key.property(),
                            "value: " + key.value(),
                            "cells: " + cells.volume(),
                            "orientation: " + (orientation == null ? 0 : orientation.ordinal())
                        ),
                        cells
                    )
                );
            }
            for (Cut cut : cuts) {
                entries.add(cut.entry());
            }
            return CaptureReport.of(entries);
        }

        private SnapshotId snapshot() {
            return snapshot;
        }

        private SnapshotPolicy policy() {
            return policy;
        }

        private long spent() {
            return spent;
        }

        private void spend(long volume) {
            spent += volume;
        }

        private void restore(long previousSpent, int previousNextFrame) {
            spent = previousSpent;
            nextFrame = previousNextFrame;
        }

        private int nextFrame() {
            return nextFrame;
        }

        private int reserveFrame() {
            return nextFrame++;
        }
    }
}
