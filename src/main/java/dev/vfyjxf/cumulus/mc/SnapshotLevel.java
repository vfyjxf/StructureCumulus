package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.HostType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One frame's immutable host snapshot, addressed by local coordinates. */
@NullMarked
final class SnapshotLevel implements BlockGetter {

    static final int sectionSize = 16;
    private static final Comparator<CapturedEntity> entityOrder = Comparator
            .comparingLong((CapturedEntity entity) -> entity.footprint().volume()).thenComparing(CapturedEntity::id);

    private final LatticePoint origin;
    private final Cuboid bounds;
    private final int minBuildHeight;
    private final int height;
    private final Map<Long, PalettedContainer<BlockState>> sections;
    private final Map<Long, FluidState[]> fluids;
    private final Map<BlockPos, BlockEntity> blockEntities;
    private final List<CapturedEntity> entities;

    SnapshotLevel(
        LatticePoint origin,
        Cuboid bounds,
        int minBuildHeight,
        int height,
        Map<Long, PalettedContainer<BlockState>> sections,
        Map<Long, FluidState[]> fluids,
        Map<BlockPos, BlockEntity> blockEntities,
        List<CapturedEntity> entities
    ) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.minBuildHeight = minBuildHeight;
        this.height = height;
        this.sections = Map.copyOf(Objects.requireNonNull(sections, "sections"));
        this.fluids = Map.copyOf(Objects.requireNonNull(fluids, "fluids"));
        this.blockEntities = Map.copyOf(Objects.requireNonNull(blockEntities, "blockEntities"));
        ArrayList<CapturedEntity> sortedEntities = new ArrayList<>(entities.size());
        for (CapturedEntity entity : entities) {
            sortedEntities.add(Objects.requireNonNull(entity, "entity"));
        }
        sortedEntities.sort(entityOrder);
        this.entities = List.copyOf(sortedEntities);
    }

    LatticePoint origin() {
        return origin;
    }

    Cuboid bounds() {
        return bounds;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinBuildHeight() {
        return minBuildHeight;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        if (!contains(pos.getX(), pos.getY(), pos.getZ())) {
            return null;
        }
        return blockEntities.get(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return blockState(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return fluidState(pos.getX(), pos.getY(), pos.getZ());
    }

    @SuppressWarnings("unchecked")
    <H> @Nullable H get(LatticePoint pos, HostType<H> type) {
        if (type.equals(HostTypes.blockState)) {
            return (H) blockState(pos.x(), pos.y(), pos.z());
        }
        if (type.equals(HostTypes.fluidState)) {
            return (H) fluidState(pos.x(), pos.y(), pos.z());
        }
        if (type.equals(HostTypes.entityType)) {
            return (H) entityTypeAt(pos);
        }
        if (type.equals(HostTypes.blockEntity)) {
            return (H) blockEntityAt(pos);
        }
        return null;
    }

    long contentDigest(Cuboid cells) {
        Cuboid region = bounds.intersect(cells);
        if (region == null) {
            return 0L;
        }
        Map<BlockState, Long> stateDigests = new HashMap<>();
        Map<FluidState, Long> fluidDigests = new HashMap<>();
        long digest = 0x9E3779B97F4A7C15L;
        for (int x = region.min().x(); x < region.max().x(); x++) {
            for (int y = region.min().y(); y < region.max().y(); y++) {
                for (int z = region.min().z(); z < region.max().z(); z++) {
                    digest = digest * 31L
                            + stateDigests.computeIfAbsent(blockState(x, y, z), SnapshotLevel::stateDigest);
                    digest = digest * 31L
                            + fluidDigests.computeIfAbsent(fluidState(x, y, z), SnapshotLevel::fluidDigest);
                }
            }
        }
        for (CapturedEntity entity : entities) {
            if (entity.footprint().intersects(region)) {
                digest = digest * 31L + entity.id().hashCode();
                digest = digest * 31L + entity.type().hashCode();
                digest = digest * 31L + entity.footprint().hashCode();
            }
        }
        return digest;
    }

    private BlockState blockState(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return Blocks.AIR.defaultBlockState();
        }
        long key = sectionAt(x, y, z);
        PalettedContainer<BlockState> section = sections.get(key);
        if (section == null) {
            return Blocks.AIR.defaultBlockState();
        }
        return section.get((x + origin.x()) & 15, (y + origin.y()) & 15, (z + origin.z()) & 15);
    }

    private FluidState fluidState(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return Fluids.EMPTY.defaultFluidState();
        }
        FluidState[] section = fluids.get(sectionAt(x, y, z));
        if (section == null) {
            return Fluids.EMPTY.defaultFluidState();
        }
        return section[fluidIndex(x + origin.x(), y + origin.y(), z + origin.z())];
    }

    private @Nullable BlockEntity blockEntityAt(LatticePoint pos) {
        if (!contains(pos.x(), pos.y(), pos.z())) {
            return null;
        }
        return blockEntities.get(new BlockPos(pos.x(), pos.y(), pos.z()));
    }

    private @Nullable EntityType<?> entityTypeAt(LatticePoint pos) {
        if (!contains(pos.x(), pos.y(), pos.z())) {
            return null;
        }
        for (CapturedEntity entity : entities) {
            if (entity.footprint().contains(pos)) {
                return entity.entityType();
            }
        }
        return null;
    }

    private long sectionAt(int x, int y, int z) {
        return worldSectionKey(x + origin.x(), y + origin.y(), z + origin.z());
    }

    private boolean contains(int x, int y, int z) {
        return x >= bounds.min().x()
                && x < bounds.max().x()
                && y >= bounds.min().y()
                && y < bounds.max().y()
                && z >= bounds.min().z()
                && z < bounds.max().z();
    }

    static long worldSectionKey(int worldX, int worldY, int worldZ) {
        return SectionPos.asLong(
            SectionPos.blockToSectionCoord(worldX),
            SectionPos.blockToSectionCoord(worldY),
            SectionPos.blockToSectionCoord(worldZ)
        );
    }

    static int fluidIndex(int worldX, int worldY, int worldZ) {
        return ((worldY & 15) * sectionSize + (worldZ & 15)) * sectionSize + (worldX & 15);
    }

    private static long stateDigest(BlockState state) {
        long digest = registryId(BuiltInRegistries.BLOCK, state.getBlock());
        ArrayList<Property<?>> properties = new ArrayList<>(state.getProperties());
        properties.sort(Comparator.comparing(Property::getName));
        for (Property<?> property : properties) {
            digest = digest * 31L + property.getName().hashCode();
            digest = digest * 31L + state.getValue(property).toString().hashCode();
        }
        return digest;
    }

    private static long fluidDigest(FluidState state) {
        long digest = registryId(BuiltInRegistries.FLUID, state.getType());
        return digest * 31L + state.getAmount();
    }

    private static <T> long registryId(Registry<T> registry, T value) {
        ResourceLocation key = registry.getKey(value);
        return key == null ? 0L : key.toString().hashCode();
    }

    record CapturedEntity(UUID id, NamespacedId type, Cuboid footprint, EntityType<?> entityType) {

        CapturedEntity {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(footprint, "footprint");
            Objects.requireNonNull(entityType, "entityType");
        }
    }
}
