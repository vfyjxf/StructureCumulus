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
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SectionSnapshot;
import dev.vfyjxf.cumulus.kernel.world.WorldCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/** M1 server-thread capture: block states and block entities, no chunk loads. */
@NullMarked
public final class LevelCapture implements WorldCapture {

    private static final int cellSize = 16;
    private static final FindingCode notCaptured = FindingCode.of(FindingCode.Family.mc, 1);

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
        long volume = bounds.volume();
        if (volume > request.policy().maxElements()) {
            return new CaptureResult.TooLarge(bounds, (int) Math.min(Integer.MAX_VALUE, volume));
        }
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
        LevelSnapshot view = new LevelSnapshot(snapshot, bounds, sections);
        LevelLease lease = new LevelLease(snapshot, view, () -> true);
        return new CaptureResult.Captured(view, lease);
    }

    @Override
    public long currentRevision() {
        return revisions.get();
    }
}
