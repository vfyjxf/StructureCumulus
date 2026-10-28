package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.HostType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotLevelTest {

    private static final Cuboid cell = Cuboid.of(0, 0, 0, 1, 1, 1);

    @Test
    void readsBlockFluidEntityAndBlockEntityByLocalCoordinates() {
        LatticePoint origin = LatticePoint.of(100, 200, 300);
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 2, 2);
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        PalettedContainer<BlockState> states = states();
        states.set(100 & 15, 200 & 15, 300 & 15, water);
        states.set(101 & 15, 200 & 15, 300 & 15, log);
        FluidState[] fluids = fluids();
        fluids[SnapshotLevel.fluidIndex(100, 200, 300)] = water.getFluidState();
        fluids[SnapshotLevel.fluidIndex(101, 200, 300)] = log.getFluidState();
        BlockEntity chest = BlockEntityType.CHEST.create(new BlockPos(0, 0, 0), Blocks.CHEST.defaultBlockState());
        Entity marker = marker();
        SnapshotLevel.CapturedEntity captured = new SnapshotLevel.CapturedEntity(
            marker.getUUID(),
            NamespacedId.of("minecraft", "marker"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            marker
        );
        long key = SnapshotLevel.worldSectionKey(100, 200, 300);
        SnapshotLevel snapshot = new SnapshotLevel(
            origin,
            bounds,
            -64,
            384,
            Map.of(key, states),
            Map.of(key, fluids),
            Map.of(new BlockPos(0, 0, 0), chest),
            List.of(captured)
        );

        assertSame(water, snapshot.getBlockState(new BlockPos(0, 0, 0)));
        assertSame(log, snapshot.getBlockState(new BlockPos(1, 0, 0)));
        assertSame(water, snapshot.get(LatticePoint.origin, HostTypes.blockState));
        assertEquals(water.getFluidState(), snapshot.getFluidState(new BlockPos(0, 0, 0)));
        assertEquals(water.getFluidState(), snapshot.get(LatticePoint.origin, HostTypes.fluidState));
        assertSame(chest, snapshot.getBlockEntity(new BlockPos(0, 0, 0)));
        assertSame(chest, snapshot.get(LatticePoint.origin, HostTypes.blockEntity));
        assertSame(marker, snapshot.get(LatticePoint.origin, HostTypes.entity));
        assertNull(snapshot.getBlockEntity(new BlockPos(1, 0, 0)));
        assertNull(snapshot.get(LatticePoint.of(1, 0, 0), HostTypes.entity));
        assertNull(snapshot.get(LatticePoint.origin, HostType.of(NamespacedId.of("test", "unknown"))));
    }

    @Test
    void outOfBoundsAnswersAirEmptyAndNull() {
        SnapshotLevel snapshot = snapshot(Blocks.STONE.defaultBlockState());

        assertTrue(snapshot.getBlockState(new BlockPos(1, 0, 0)).isAir());
        assertTrue(snapshot.getBlockState(new BlockPos(-1, 0, 0)).isAir());
        assertTrue(snapshot.getBlockState(new BlockPos(0, 1, 0)).isAir());
        assertEquals(Fluids.EMPTY.defaultFluidState(), snapshot.getFluidState(new BlockPos(1, 0, 0)));
        assertNull(snapshot.getBlockEntity(new BlockPos(1, 0, 0)));
        assertNull(snapshot.get(LatticePoint.of(1, 0, 0), HostTypes.entity));
        assertTrue(Objects.requireNonNull(snapshot.get(LatticePoint.of(1, 0, 0), HostTypes.blockState), "air").isAir());
        assertEquals(Fluids.EMPTY.defaultFluidState(), snapshot.get(LatticePoint.of(1, 0, 0), HostTypes.fluidState));
    }

    @Test
    void contentDigestTracksTheCapturedBlockState() {
        SnapshotLevel x = snapshot(
            Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X)
        );
        SnapshotLevel y = snapshot(
            Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
        );
        SnapshotLevel stone = snapshot(Blocks.STONE.defaultBlockState());

        assertNotEquals(x.contentDigest(cell), y.contentDigest(cell));
        assertNotEquals(x.contentDigest(cell), stone.contentDigest(cell));
    }

    @Test
    void sectionDigestTracksTheSnapshotState() {
        SnapshotLevel x = snapshot(
            Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X)
        );
        SnapshotLevel y = snapshot(
            Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
        );

        assertNotEquals(LevelCapture.contentDigest(42L, x, cell), LevelCapture.contentDigest(42L, y, cell));
    }

    @Test
    void contentDigestTracksTheCapturedFluid() {
        SnapshotLevel water = snapshot(Blocks.WATER.defaultBlockState());
        SnapshotLevel air = snapshot(Blocks.AIR.defaultBlockState());

        assertNotEquals(water.contentDigest(cell), air.contentDigest(cell));
    }

    @Test
    void contentDigestTracksTheCapturedEntity() {
        SnapshotLevel without = snapshot(Blocks.STONE.defaultBlockState());
        SnapshotLevel with = snapshot(Blocks.STONE.defaultBlockState(), marker());

        assertNotEquals(without.contentDigest(cell), with.contentDigest(cell));
    }

    @Test
    void keepsNoLiveLevel() {
        for (Field field : SnapshotLevel.class.getDeclaredFields()) {
            assertFalse(Level.class.isAssignableFrom(field.getType()), field.getName());
        }
    }

    private static SnapshotLevel snapshot(BlockState state) {
        return snapshot(state, null);
    }

    private static SnapshotLevel snapshot(BlockState state, @Nullable Entity entity) {
        PalettedContainer<BlockState> states = states();
        states.set(0, 0, 0, state);
        FluidState[] fluids = fluids();
        fluids[SnapshotLevel.fluidIndex(0, 0, 0)] = state.getFluidState();
        long key = SnapshotLevel.worldSectionKey(0, 0, 0);
        List<SnapshotLevel.CapturedEntity> entities = entity == null
                ? List.of()
                : List.of(
                    new SnapshotLevel.CapturedEntity(
                        UUID.nameUUIDFromBytes(new byte[]{1}),
                        NamespacedId.of("minecraft", "marker"),
                        cell,
                        entity
                    )
                );
        return new SnapshotLevel(
            LatticePoint.origin,
            cell,
            -64,
            384,
            Map.of(key, states),
            Map.of(key, fluids),
            Map.of(),
            entities
        );
    }

    private static PalettedContainer<BlockState> states() {
        return new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
    }

    private static FluidState[] fluids() {
        FluidState[] fluids = new FluidState[SnapshotLevel.sectionSize
                * SnapshotLevel.sectionSize
                * SnapshotLevel.sectionSize];
        Arrays.fill(fluids, Fluids.EMPTY.defaultFluidState());
        return fluids;
    }

    private static Marker marker() {
        return new Marker(EntityType.MARKER, null);
    }
}
