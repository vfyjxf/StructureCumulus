package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
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
import dev.vfyjxf.cumulus.kernel.world.WorldCapture;
import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Server-thread capture: frames, block states, block entities and loaded entities, no chunk loads. */
@NullMarked
public final class LevelCapture implements WorldCapture {

    private static final int cellSize = 16;
    private static final FindingCode notCaptured = FindingCode.of(FindingCode.Family.mc, 1);
    private static final Comparator<EntityCandidate> entityOrder = Comparator.comparing(EntityCandidate::type)
            .thenComparing(EntityCandidate::anchor).thenComparing(EntityCandidate::footprint)
            .thenComparing(EntityCandidate::id);

    private final ServerLevel level;
    private final World world;
    private final NestedExtentLookup nestedLookup;
    private final AtomicLong revisions = new AtomicLong();

    public LevelCapture(ServerLevel level) {
        this(level, NestedExtentLookup.capabilities());
    }

    LevelCapture(ServerLevel level, NestedExtentLookup nestedLookup) {
        this.level = Objects.requireNonNull(level, "level");
        this.nestedLookup = Objects.requireNonNull(nestedLookup, "nestedLookup");
        var location = level.dimension().location();
        this.world = World.of(NamespacedId.of(location.getNamespace(), location.getPath()));
    }

    @Override
    public CaptureResult capture(CaptureRequest request) {
        Cuboid bounds = request.bounds();
        SnapshotPolicy policy = request.policy();
        long volume = bounds.volume();
        if (volume > policy.maxElements()) {
            return new CaptureResult.TooLarge(bounds, (int) Math.min(Integer.MAX_VALUE, volume));
        }
        long revision = revisions.incrementAndGet();
        SnapshotId snapshot = new SnapshotId(world, revision);
        CaptureContext context = new CaptureContext(snapshot, policy, volume);
        FrameData root = new FrameCapture(context, 0, 0, bounds, LatticePoint.origin, true).build(List.of());
        if (root == null) {
            revisions.decrementAndGet();
            return new CaptureResult.Unavailable(ReadStatus.notCaptured, notCaptured);
        }
        LevelSnapshot view = new LevelSnapshot(snapshot, policy, root);
        LevelLease lease = new LevelLease(snapshot, view, () -> true);
        return new CaptureResult.Captured(view, lease);
    }

    @Override
    public long currentRevision() {
        return revisions.get();
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
        private final Map<LatticePoint, NestedExtent> blockExtents = new TreeMap<>();

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
            List<EntityCandidate> candidates = entityCandidates();
            List<OccupantSnapshot> entities = entityBodies(candidates);
            Map<LatticePoint, NestedExtent> declarations = nestedDeclarations(candidates);
            List<OccupantSnapshot> nestedBodies = new ArrayList<>();
            Map<LatticePoint, FrameData> children = new TreeMap<>();
            for (Map.Entry<LatticePoint, NestedExtent> entry : declarations.entrySet()) {
                NestedExtent extent = entry.getValue();
                Cuboid footprint = extent.bounds().translate(-origin.x(), -origin.y(), -origin.z());
                if (footprint == null) {
                    StructureCumulus.logger.warn("nested extent {} leaves the parent frame", extent);
                    continue;
                }
                nestedBodies.add(
                    OccupantDescriptors.nested(context.snapshot(), frame, entry.getKey(), extent.type(), footprint)
                );
                FrameData child = child(extent, path);
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
                sections,
                entities,
                nestedBodies,
                children
            );
        }

        private @Nullable FrameData child(NestedExtent extent, List<NestedKey> path) {
            if (depth + 1 > context.policy().maxDepth()) {
                return null;
            }
            NestedKey key = new NestedKey(extent.type(), extent.origin());
            if (path.contains(key)) {
                return null;
            }
            Cuboid childBounds = extent.bounds()
                    .translate(-extent.origin().x(), -extent.origin().y(), -extent.origin().z());
            if (childBounds == null) {
                StructureCumulus.logger.warn("nested extent {} leaves the lattice", extent);
                return null;
            }
            long childVolume = childBounds.volume();
            if (childVolume > context.policy().maxElements() - context.spent()) {
                return null;
            }
            if (!chunksLoaded(childBounds, extent.origin())) {
                return null;
            }
            long spentBefore = context.spent();
            int frameBefore = context.nextFrame();
            context.spend(childVolume);
            int childFrame = context.reserveFrame();
            List<NestedKey> childPath = new ArrayList<>(path);
            childPath.add(key);
            FrameData result = new FrameCapture(context, childFrame, depth + 1, childBounds, extent.origin(), false)
                    .build(childPath);
            if (result == null) {
                context.restore(spentBefore, frameBefore);
                return null;
            }
            return result;
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
                        if (chunkSection.hasOnlyAir()) {
                            continue;
                        }
                        BlockState state = chunkSection.getBlockState(worldX & 15, worldY & 15, worldZ & 15);
                        if (state.isAir()) {
                            continue;
                        }
                        LatticePoint cell = LatticePoint.of(x, y, z);
                        BlockPos worldPos = new BlockPos(worldX, worldY, worldZ);
                        BlockEntity blockEntity = state.hasBlockEntity() ? chunk.getBlockEntity(worldPos) : null;
                        OccupantSnapshot occupant = blockEntity == null
                                ? OccupantDescriptors.block(context.snapshot(), frame, cell, state)
                                : OccupantDescriptors.blockEntity(context.snapshot(), frame, cell, state, blockEntity);
                        occupants.put(cell, occupant);
                        if (nestedEnabled && (!state.hasBlockEntity() || blockEntity != null)) {
                            NestedExtent extent = blockExtent(worldPos, state, blockEntity);
                            if (extent != null) {
                                blockExtents.put(cell, extent);
                            }
                        }
                    }
                }
            }
            if (!unreadable.isEmpty()) {
                return new SectionSlot.Partial(SectionSnapshot.of(key, occupants, digest(occupants)), unreadable);
            }
            if (occupants.isEmpty()) {
                return new SectionSlot.Air(0);
            }
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
                    OccupantDescriptors
                            .entity(context.snapshot(), frame, anchor, candidate.type(), candidate.id(), footprint)
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

        private @Nullable Cuboid local(Cuboid world) {
            return world.translate(-origin.x(), -origin.y(), -origin.z());
        }
    }

    private @Nullable NestedExtent blockExtent(BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        try {
            return nestedLookup.block(level, pos, state, blockEntity);
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("nested block provider failed at {}", pos, failure);
            return null;
        }
    }

    private @Nullable NestedExtent entityExtent(EntityCandidate candidate) {
        try {
            return nestedLookup.entity(candidate.entity());
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("nested entity provider failed for {}", candidate.id(), failure);
            return null;
        }
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

    private static NamespacedId entityType(Entity entity) {
        var location = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return NamespacedId.of(location.getNamespace(), location.getPath());
    }

    private static AABB box(Cuboid cuboid) {
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

    private static @Nullable Cuboid footprint(AABB body) {
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

    private static long digest(Map<LatticePoint, OccupantSnapshot> occupants) {
        return occupants.values().stream()
                .mapToLong(occupant -> occupant.ref().hashCode() ^ occupant.descriptor().hashCode())
                .reduce(0L, (left, right) -> left * 31L + right);
    }

    private record EntityCandidate(UUID id, NamespacedId type, LatticePoint anchor, Cuboid footprint, Entity entity) {}

    private record NestedKey(NamespacedId type, LatticePoint origin) {}

    private static final class CaptureContext {

        private final SnapshotId snapshot;
        private final SnapshotPolicy policy;
        private long spent;
        private int nextFrame = 1;

        private CaptureContext(SnapshotId snapshot, SnapshotPolicy policy, long spent) {
            this.snapshot = snapshot;
            this.policy = policy;
            this.spent = spent;
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
