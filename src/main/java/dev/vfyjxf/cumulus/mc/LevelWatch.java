package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The per-level mark registry: refcounted watched chunks and the lazy entity and block-entity
 * fingerprints of each capture. Server thread only.
 */
@NullMarked
final class LevelWatch {

    private static final Map<ResourceKey<Level>, LevelWatch> levels = new HashMap<>();
    private static final AtomicInteger activeHandles = new AtomicInteger();

    private LevelWatch() {}

    static boolean active() {
        return activeHandles.get() > 0;
    }

    static void clear() {
        for (LevelWatch watch : List.copyOf(levels.values())) {
            watch.invalidateAll();
        }
        levels.clear();
    }

    static void blockChanged(LevelAccessor accessor, BlockPos pos) {
        LevelWatch watch = watchOf(accessor);
        if (watch != null) {
            watch.markChunk(ChunkPos.asLong(pos));
        }
    }

    static void chunkUnloaded(LevelAccessor accessor, ChunkPos pos) {
        LevelWatch watch = watchOf(accessor);
        if (watch != null) {
            watch.markChunk(pos.toLong());
        }
    }

    static void entityChanged(LevelAccessor accessor, Entity entity, boolean joined) {
        LevelWatch watch = watchOf(accessor);
        if (watch != null) {
            watch.markEntity(entity, joined);
        }
    }

    static void levelUnloaded(LevelAccessor accessor) {
        if (!(accessor instanceof Level level)) {
            return;
        }
        LevelWatch watch = levels.remove(level.dimension());
        if (watch != null) {
            watch.epoch.incrementAndGet();
        }
    }

    static WatchHandle acquire(ServerLevel level, WatchRegion region) {
        return levels.computeIfAbsent(level.dimension(), ignored -> new LevelWatch()).add(region, level);
    }

    static int refCount(ServerLevel level, long chunk) {
        LevelWatch watch = levels.get(level.dimension());
        if (watch == null) {
            return 0;
        }
        Set<WatchHandle> watchers = watch.chunks.get(chunk);
        return watchers == null ? 0 : watchers.size();
    }

    private static @Nullable LevelWatch watchOf(LevelAccessor accessor) {
        if (!active() || !(accessor instanceof Level level)) {
            return null;
        }
        return levels.get(level.dimension());
    }

    private final Map<Long, Set<WatchHandle>> chunks = new HashMap<>();
    private final Set<WatchHandle> handles = new LinkedHashSet<>();
    private final AtomicLong epoch = new AtomicLong();

    private WatchHandle add(WatchRegion region, ServerLevel level) {
        WatchHandle handle = new WatchHandle(this, level, region, epoch.get());
        for (long chunk : handle.chunks()) {
            chunks.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>()).add(handle);
        }
        handles.add(handle);
        activeHandles.incrementAndGet();
        return handle;
    }

    private void markChunk(long chunk) {
        Set<WatchHandle> watchers = chunks.get(chunk);
        if (watchers == null) {
            return;
        }
        for (WatchHandle handle : watchers) {
            handle.mark();
        }
    }

    private void markEntity(Entity entity, boolean joined) {
        for (WatchHandle handle : List.copyOf(handles)) {
            if (handle.affects(entity, joined)) {
                handle.mark();
            }
        }
    }

    private void release(WatchHandle handle) {
        if (!handle.detach()) {
            return;
        }
        for (long chunk : handle.chunks()) {
            Set<WatchHandle> watchers = chunks.get(chunk);
            if (watchers != null) {
                watchers.remove(handle);
                if (watchers.isEmpty()) {
                    chunks.remove(chunk);
                }
            }
        }
        handles.remove(handle);
        activeHandles.decrementAndGet();
        if (handles.isEmpty()) {
            levels.remove(handle.level().dimension(), this);
        }
    }

    private void invalidateAll() {
        epoch.incrementAndGet();
        for (WatchHandle handle : List.copyOf(handles)) {
            if (handle.detach()) {
                activeHandles.decrementAndGet();
            }
        }
        handles.clear();
        chunks.clear();
    }

    static NamespacedId blockEntityType(BlockEntity entity) {
        var location = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());
        return NamespacedId.of(location.getNamespace(), location.getPath());
    }

    /** One capture's watch: the chunks and bounds it read plus the fingerprints it captured. */
    static final class WatchHandle {

        private final LevelWatch owner;
        private final ServerLevel level;
        private final Set<Long> chunks;
        private final List<Cuboid> bounds;
        private final Map<UUID, EntityFingerprint> entities;
        private final Map<BlockPos, BlockEntityFingerprint> blockEntities;
        private final AtomicLong marks = new AtomicLong();
        private final AtomicBoolean registered = new AtomicBoolean(true);
        private final long epochAtCapture;

        private WatchHandle(LevelWatch owner, ServerLevel level, WatchRegion region, long epochAtCapture) {
            this.owner = owner;
            this.level = level;
            this.chunks = Set.copyOf(region.chunks());
            this.bounds = List.copyOf(region.bounds());
            this.entities = Map.copyOf(region.entities());
            this.blockEntities = Map.copyOf(region.blockEntities());
            this.epochAtCapture = epochAtCapture;
        }

        boolean isCurrent() {
            if (!registered.get() || marks.get() != 0 || owner.epoch.get() != epochAtCapture) {
                return false;
            }
            return entitiesUnchanged() && blockEntitiesUnchanged();
        }

        void mark() {
            if (registered.get()) {
                marks.incrementAndGet();
            }
        }

        @Override
        public String toString() {
            return "WatchHandle[" + chunks.size() + " chunks, " + marks.get() + " marks]";
        }

        long marks() {
            return marks.get();
        }

        Set<Long> chunks() {
            return chunks;
        }

        ServerLevel level() {
            return level;
        }

        boolean detach() {
            return registered.compareAndSet(true, false);
        }

        void release() {
            owner.release(this);
        }

        private boolean affects(Entity entity, boolean joined) {
            if (!registered.get()) {
                return false;
            }
            if (entities.containsKey(entity.getUUID())) {
                return true;
            }
            if (!joined || entity.isSpectator()) {
                return false;
            }
            Cuboid footprint = LevelCapture.footprint(entity.getBoundingBox());
            if (footprint == null) {
                return false;
            }
            for (Cuboid bound : bounds) {
                if (footprint.intersects(bound)) {
                    return true;
                }
            }
            return false;
        }

        private boolean entitiesUnchanged() {
            for (EntityFingerprint fingerprint : entities.values()) {
                Entity live = level.getEntity(fingerprint.id());
                if (live == null || live.isRemoved() || live.isSpectator() || live.level() != level) {
                    return false;
                }
                if (!fingerprint.type().equals(LevelCapture.entityType(live))) {
                    return false;
                }
                BlockPos liveAnchor = live.blockPosition();
                if (!fingerprint.anchor()
                        .equals(LatticePoint.of(liveAnchor.getX(), liveAnchor.getY(), liveAnchor.getZ()))) {
                    return false;
                }
                Cuboid footprint = LevelCapture.footprint(live.getBoundingBox());
                if (footprint == null || !fingerprint.footprint().equals(footprint)) {
                    return false;
                }
            }
            for (Cuboid bound : bounds) {
                for (Entity entity : level.getEntitiesOfClass(
                    Entity.class,
                    LevelCapture.box(bound),
                    candidate -> !candidate.isSpectator()
                )) {
                    Cuboid footprint = LevelCapture.footprint(entity.getBoundingBox());
                    if (footprint == null || !footprint.intersects(bound)) {
                        continue;
                    }
                    if (!entities.containsKey(entity.getUUID())) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean blockEntitiesUnchanged() {
            if (blockEntities.isEmpty()) {
                return true;
            }
            ServerChunkCache chunkSource = level.getChunkSource();
            for (Map.Entry<BlockPos, BlockEntityFingerprint> entry : blockEntities.entrySet()) {
                BlockPos pos = entry.getKey();
                LevelChunk chunk = chunkSource.getChunkNow(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ())
                );
                if (chunk == null) {
                    return false;
                }
                BlockEntity live = chunk.getBlockEntity(pos);
                if (live == null || live.isRemoved()) {
                    return false;
                }
                BlockEntityFingerprint recorded = entry.getValue();
                if (!recorded.type().equals(blockEntityType(live))) {
                    return false;
                }
                if (!recorded.nbt().equals(live.saveWithoutMetadata(level.registryAccess()))) {
                    return false;
                }
            }
            return true;
        }
    }

    /** One capture's collected watch data; frames merge only when the frame is built, never when cut. */
    static final class WatchRegion {

        private final Set<Long> chunks = new LinkedHashSet<>();
        private final List<Cuboid> bounds = new ArrayList<>();
        private final Map<UUID, EntityFingerprint> entities = new LinkedHashMap<>();
        private final Map<BlockPos, BlockEntityFingerprint> blockEntities = new LinkedHashMap<>();

        void frame(Cuboid localBounds, LatticePoint origin) {
            Cuboid world = localBounds.translate(origin.x(), origin.y(), origin.z());
            if (world == null) {
                return;
            }
            bounds.add(world);
            int minX = Math.floorDiv(world.min().x(), LevelCapture.cellSize);
            int maxX = Math.floorDiv(world.max().x() - 1, LevelCapture.cellSize);
            int minZ = Math.floorDiv(world.min().z(), LevelCapture.cellSize);
            int maxZ = Math.floorDiv(world.max().z() - 1, LevelCapture.cellSize);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    chunks.add(ChunkPos.asLong(x, z));
                }
            }
        }

        void entity(UUID id, NamespacedId type, LatticePoint anchor, Cuboid footprint) {
            entities.putIfAbsent(id, new EntityFingerprint(id, type, anchor, footprint));
        }

        void blockEntity(BlockPos pos, BlockEntity entity, HolderLookup.Provider registries) {
            blockEntities.putIfAbsent(
                pos,
                new BlockEntityFingerprint(blockEntityType(entity), entity.saveWithoutMetadata(registries))
            );
        }

        void merge(WatchRegion other) {
            chunks.addAll(other.chunks);
            bounds.addAll(other.bounds);
            other.entities.forEach(entities::putIfAbsent);
            other.blockEntities.forEach(blockEntities::putIfAbsent);
        }

        Set<Long> chunks() {
            return chunks;
        }

        List<Cuboid> bounds() {
            return bounds;
        }

        Map<UUID, EntityFingerprint> entities() {
            return entities;
        }

        Map<BlockPos, BlockEntityFingerprint> blockEntities() {
            return blockEntities;
        }
    }

    record EntityFingerprint(UUID id, NamespacedId type, LatticePoint anchor, Cuboid footprint) {
        EntityFingerprint {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(footprint, "footprint");
        }
    }

    record BlockEntityFingerprint(NamespacedId type, CompoundTag nbt) {
        BlockEntityFingerprint {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(nbt, "nbt");
            nbt = nbt.copy();
        }
    }
}
