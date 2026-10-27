package dev.vfyjxf.cumulus.mc;

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
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SectionSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
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

/** Server-thread capture: block states, block entities and loaded entities, no chunk loads. */
@NullMarked
public final class LevelCapture implements WorldCapture {

    private static final int cellSize = 16;
    private static final FindingCode notCaptured = FindingCode.of(FindingCode.Family.mc, 1);
    private static final Comparator<EntityCandidate> entityOrder = Comparator.comparing(EntityCandidate::type)
            .thenComparing(EntityCandidate::anchor).thenComparing(EntityCandidate::footprint)
            .thenComparing(EntityCandidate::id);

    private final ServerLevel level;
    private final World world;
    private final AtomicLong revisions = new AtomicLong();

    public LevelCapture(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
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
        List<EntityCandidate> candidates = entityCandidates(bounds, policy);
        Map<SectionKey, SectionSlot> sections = new TreeMap<>();
        long revision = revisions.incrementAndGet();
        SnapshotId snapshot = new SnapshotId(world, revision);

        int minSectionX = Math.floorDiv(bounds.min().x(), cellSize);
        int maxSectionX = Math.floorDiv(bounds.max().x() - 1, cellSize);
        int minSectionZ = Math.floorDiv(bounds.min().z(), cellSize);
        int maxSectionZ = Math.floorDiv(bounds.max().z() - 1, cellSize);
        int minSectionY = Math.floorDiv(bounds.min().y(), cellSize);
        int maxSectionY = Math.floorDiv(bounds.max().y() - 1, cellSize);

        for (int chunkX = minSectionX; chunkX <= maxSectionX; chunkX++) {
            for (int chunkZ = minSectionZ; chunkZ <= maxSectionZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    revisions.decrementAndGet();
                    return new CaptureResult.Unavailable(ReadStatus.notCaptured, notCaptured);
                }
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    int sectionIndex = chunk.getSectionIndex(sectionY * cellSize);
                    if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(sectionIndex);
                    SectionKey key = new SectionKey(world, 0, chunkX, sectionY, chunkZ);
                    if (section == null || section.hasOnlyAir()) {
                        sections.put(key, new SectionSlot.Air(0));
                        continue;
                    }
                    Map<LatticePoint, OccupantSnapshot> occupants = new TreeMap<>();
                    int minX = Math.max(bounds.min().x(), chunkX * cellSize);
                    int maxX = Math.min(bounds.max().x(), chunkX * cellSize + cellSize);
                    int minZ = Math.max(bounds.min().z(), chunkZ * cellSize);
                    int maxZ = Math.min(bounds.max().z(), chunkZ * cellSize + cellSize);
                    int minY = Math.max(bounds.min().y(), sectionY * cellSize);
                    int maxY = Math.min(bounds.max().y(), sectionY * cellSize + cellSize);
                    for (int x = minX; x < maxX; x++) {
                        for (int y = minY; y < maxY; y++) {
                            for (int z = minZ; z < maxZ; z++) {
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (state.isAir()) {
                                    continue;
                                }
                                LatticePoint cell = LatticePoint.of(x, y, z);
                                BlockEntity entity = state.hasBlockEntity()
                                        ? chunk.getBlockEntity(new BlockPos(x, y, z))
                                        : null;
                                OccupantSnapshot occupant = entity == null
                                        ? OccupantDescriptors.block(snapshot, 0, cell, state)
                                        : OccupantDescriptors.blockEntity(snapshot, 0, cell, state, entity);
                                occupants.put(cell, occupant);
                            }
                        }
                    }
                    if (occupants.isEmpty()) {
                        sections.put(key, new SectionSlot.Air(0));
                    } else {
                        long digest = occupants.values().stream()
                                .mapToLong(occupant -> occupant.ref().hashCode() ^ occupant.descriptor().hashCode())
                                .reduce(0L, (left, right) -> left * 31L + right);
                        sections.put(key, new SectionSlot.Present(SectionSnapshot.of(key, occupants, digest)));
                    }
                }
            }
        }
        List<OccupantSnapshot> entities = new ArrayList<>(candidates.size());
        for (EntityCandidate candidate : candidates) {
            entities.add(
                OccupantDescriptors.entity(
                    snapshot,
                    0,
                    candidate.anchor(),
                    candidate.type(),
                    candidate.id(),
                    candidate.footprint()
                )
            );
        }
        LevelSnapshot view = new LevelSnapshot(snapshot, bounds, sections, entities, policy);
        LevelLease lease = new LevelLease(snapshot, view, () -> true);
        return new CaptureResult.Captured(view, lease);
    }

    @Override
    public long currentRevision() {
        return revisions.get();
    }

    private List<EntityCandidate> entityCandidates(Cuboid bounds, SnapshotPolicy policy) {
        if (!policy.kinds().contains(OccupantKind.entity)) {
            return List.of();
        }
        Map<UUID, EntityCandidate> found = new HashMap<>();
        AABB captured = box(bounds).inflate(EntityWindow.maxEntitySpan);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, captured, EntitySelector.NO_SPECTATORS)) {
            addCandidate(found, entity, bounds);
        }
        for (EntityWindow window : policy.windows()) {
            AABB windowBox = box(window.cells());
            for (Entity entity : level.getEntitiesOfClass(
                Entity.class,
                windowBox,
                candidate -> !candidate.isSpectator() && window.type().equals(entityType(candidate))
            )) {
                addCandidate(found, entity, bounds);
            }
        }
        List<EntityCandidate> candidates = new ArrayList<>(found.values());
        candidates.sort(entityOrder);
        return List.copyOf(candidates);
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
            footprint
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

    private record EntityCandidate(UUID id, NamespacedId type, LatticePoint anchor, Cuboid footprint) {}
}
