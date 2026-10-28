package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The mark counter: event-edged invalidation plus lazy entity and block-entity fingerprints. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class LeaseMarkCounterGameTest {

    private static final int maxElements = 1_000_000;

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void setBlockInvalidatesTheLease(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, cell(helper), SnapshotPolicy.minimal());
        try (SnapshotLease lease = captured.lease()) {
            helper.setBlock(BlockPos.ZERO, Blocks.STONE);
            helper.assertFalse(lease.isCurrent(), "a watched block change invalidates the lease");
        } finally {
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void blockChangeOutsideTheWatchedChunkKeepsTheLeaseCurrent(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        BlockPos outside = helper.absolutePos(BlockPos.ZERO).offset(32, 0, 0);
        CaptureResult.Captured captured = captured(capture, cell(helper), SnapshotPolicy.minimal());
        try (SnapshotLease lease = captured.lease()) {
            helper.getLevel().setBlock(outside, Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(lease.isCurrent(), "another chunk is outside the watched region");
            helper.assertValueEqual(0L, watch(capture).marks(), "an unwatched chunk never marks");
        } finally {
            helper.getLevel().setBlock(outside, Blocks.AIR.defaultBlockState(), 3);
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void marksAreMonotoneAfterARevert(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, cell(helper), SnapshotPolicy.minimal());
        try (SnapshotLease lease = captured.lease()) {
            helper.setBlock(BlockPos.ZERO, Blocks.STONE);
            helper.setBlock(BlockPos.ZERO, Blocks.AIR);
            helper.assertFalse(lease.isCurrent(), "a reverted change still leaves a mark");
        } finally {
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void entityJoinInBoundsInvalidatesTheLease(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, around(helper, new BlockPos(0, 1, 0), 3), entityPolicy());
        Entity zombie = null;
        try (SnapshotLease lease = captured.lease()) {
            zombie = helper.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(new BlockPos(1, 1, 1)));
            helper.assertFalse(lease.isCurrent(), "an entity joining a watched bound invalidates");
        } finally {
            if (zombie != null) {
                zombie.discard();
            }
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void entityJoinOutsideBoundsThenMoveInIsLazy(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        CaptureResult.Captured captured = captured(capture, around(helper, new BlockPos(0, 1, 0), 1), entityPolicy());
        Entity zombie = null;
        try (SnapshotLease lease = captured.lease()) {
            zombie = helper.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(new BlockPos(4, 1, 0)));
            helper.assertTrue(lease.isCurrent(), "a join outside the bounds does not invalidate");

            zombie.setPos(origin.getX() + 0.5, zombie.getY(), origin.getZ() + 0.5);
            helper.assertFalse(lease.isCurrent(), "the lazy bounds sweep sees the entity enter");
        } finally {
            if (zombie != null) {
                zombie.discard();
            }
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void entityLeaveInvalidatesTheLease(GameTestHelper helper) {
        Entity zombie = helper.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(new BlockPos(1, 1, 1)));
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, around(helper, new BlockPos(0, 1, 0), 3), entityPolicy());
        try (SnapshotLease lease = captured.lease()) {
            helper.assertTrue(lease.isCurrent(), "the captured entity is current");
            zombie.discard();
            helper.assertFalse(lease.isCurrent(), "an entity leaving the level invalidates");
        } finally {
            zombie.discard();
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void entityMoveAcrossBlocksInvalidatesOnlyAtTheGate(GameTestHelper helper) {
        Entity zombie = helper.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(new BlockPos(1, 1, 1)));
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, around(helper, new BlockPos(0, 1, 0), 3), entityPolicy());
        try (SnapshotLease lease = captured.lease()) {
            long before = WatchEvents.dispatchCount();
            zombie.setPos(zombie.getX() + 1.5, zombie.getY(), zombie.getZ());
            helper.assertValueEqual(before, WatchEvents.dispatchCount(), "a move fires no event");
            helper.assertValueEqual(0L, watch(capture).marks(), "a move does not mark");
            helper.assertFalse(lease.isCurrent(), "the gate sees the moved fingerprint");
        } finally {
            zombie.discard();
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void highFrequencyEntityMovesDoNoLeaseWork(GameTestHelper helper) {
        Entity zombie = helper.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(new BlockPos(1, 1, 1)));
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, around(helper, new BlockPos(0, 1, 0), 3), entityPolicy());
        try (SnapshotLease lease = captured.lease()) {
            double baseX = zombie.getX();
            double baseY = zombie.getY();
            double baseZ = zombie.getZ();
            long before = WatchEvents.dispatchCount();

            for (int i = 0; i < 10_000; i++) {
                zombie.setPos(baseX + ((i % 5) - 2) * 0.02, baseY, baseZ);
            }

            helper.assertValueEqual(before, WatchEvents.dispatchCount(), "10k moves fire no event");
            helper.assertValueEqual(0L, watch(capture).marks(), "10k moves do not mark");
            helper.assertTrue(lease.isCurrent(), "the fingerprint is unchanged by sub-block jitter");

            zombie.setPos(baseX + 1.5, baseY, baseZ);
            helper.assertValueEqual(before, WatchEvents.dispatchCount(), "a cross-block move fires no event either");
            helper.assertValueEqual(0L, watch(capture).marks(), "a cross-block move does not mark");
            helper.assertFalse(lease.isCurrent(), "the gate sees the cross-block move");
        } finally {
            zombie.discard();
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void chunkUnloadHandlerInvalidatesTheLease(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, cell(helper), SnapshotPolicy.minimal());
        try (SnapshotLease lease = captured.lease()) {
            LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO));
            WatchEvents.chunkUnloaded(new ChunkEvent.Unload(chunk));
            helper.assertFalse(lease.isCurrent(), "a watched chunk unload invalidates");
        } finally {
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void levelUnloadEpochInvalidatesARawCapture(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, cell(helper), SnapshotPolicy.minimal());
        try (SnapshotLease lease = captured.lease()) {
            WatchEvents.levelUnloaded(new LevelEvent.Unload(helper.getLevel()));
            helper.assertFalse(lease.isCurrent(), "the level epoch invalidates every capture");
        } finally {
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aNewCaptureReplacesTheWatchReference(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        long chunk = ChunkPos.asLong(origin);
        int baseline = LevelWatch.refCount(helper.getLevel(), chunk);
        CaptureResult.Captured first = captured(capture, cell(helper), SnapshotPolicy.minimal());
        try (SnapshotLease firstLease = first.lease()) {
            helper.assertValueEqual(baseline + 1, LevelWatch.refCount(helper.getLevel(), chunk), "one watch reference");

            CaptureResult.Captured second = captured(capture, cell(helper), SnapshotPolicy.minimal());
            try (SnapshotLease secondLease = second.lease()) {
                helper.assertFalse(firstLease.isCurrent(), "the successor invalidates the old lease");
                helper.assertTrue(secondLease.isCurrent(), "the successor is current");
                helper.assertValueEqual(
                    baseline + 1,
                    LevelWatch.refCount(helper.getLevel(), chunk),
                    "the watch reference is replaced"
                );

                CaptureResult tooLarge = capture.capture(
                    new CaptureRequest(cell(helper), new SnapshotPolicy(0, 0, Set.of(), Set.of(), List.of()))
                );
                if (!(tooLarge instanceof CaptureResult.TooLarge)) {
                    throw new GameTestAssertException("the tiny ceiling did not reject the capture: " + tooLarge);
                }
                helper.assertTrue(secondLease.isCurrent(), "a failed capture leaves the watch and lease current");
                helper.assertValueEqual(
                    baseline + 1,
                    LevelWatch.refCount(helper.getLevel(), chunk),
                    "a failed capture keeps the watch"
                );
            }
            helper.assertValueEqual(
                baseline,
                LevelWatch.refCount(helper.getLevel(), chunk),
                "closing the lease releases the watch"
            );
        } finally {
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void twoCapturesShareTheChunkRefcount(GameTestHelper helper) {
        LevelCapture first = new LevelCapture(helper.getLevel());
        LevelCapture second = new LevelCapture(helper.getLevel());
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        long chunk = ChunkPos.asLong(origin);
        int baseline = LevelWatch.refCount(helper.getLevel(), chunk);
        CaptureResult.Captured firstCaptured = captured(first, cell(helper), SnapshotPolicy.minimal());
        CaptureResult.Captured secondCaptured = captured(second, cell(helper), SnapshotPolicy.minimal());
        try (SnapshotLease firstLease = firstCaptured.lease()) {
            try (SnapshotLease secondLease = secondCaptured.lease()) {
                helper.assertValueEqual(
                    baseline + 2,
                    LevelWatch.refCount(helper.getLevel(), chunk),
                    "two capture handles share a chunk"
                );

                helper.setBlock(BlockPos.ZERO, Blocks.STONE);
                helper.assertFalse(firstLease.isCurrent(), "the event marks the first handle");
                helper.assertFalse(secondLease.isCurrent(), "the event marks the second handle");
            }
            helper.assertValueEqual(baseline + 1, LevelWatch.refCount(helper.getLevel(), chunk), "one handle remains");
        } finally {
            first.close();
            second.close();
        }
        helper.assertValueEqual(baseline, LevelWatch.refCount(helper.getLevel(), chunk), "the last handle releases");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void nestedChildFrameChunksAreWatched(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos child = anchor.offset(32, 0, 0);
        BlockPos outside = anchor.offset(64, 0, 0);
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.STONE);
        helper.getLevel().getChunkAt(child);
        NestedExtent extent = new NestedExtent(
            NamespacedId.of("test", "nested"),
            Cuboid.of(child.getX(), child.getY(), child.getZ(), child.getX() + 1, child.getY() + 1, child.getZ() + 1),
            LatticePoint.of(child.getX(), child.getY(), child.getZ())
        );
        FakeLookup lookup = new FakeLookup(Map.of(anchor, extent));
        LevelCapture capture = new LevelCapture(helper.getLevel(), lookup);
        CaptureResult.Captured captured = captured(capture, around(helper, new BlockPos(0, 1, 0), 1), nestedPolicy());
        try (SnapshotLease lease = captured.lease()) {
            helper.getLevel().setBlock(outside, Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(lease.isCurrent(), "a chunk outside root and child frames is not watched");
            helper.assertValueEqual(0L, watch(capture).marks(), "the outside change does not mark");

            helper.getLevel().setBlock(child, Blocks.DIRT.defaultBlockState(), 3);
            helper.assertFalse(lease.isCurrent(), "a change in the child frame chunk is watched");
        } finally {
            helper.getLevel().setBlock(outside, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(child, Blocks.AIR.defaultBlockState(), 3);
            capture.close();
            helper.setBlock(new BlockPos(0, 1, 0), Blocks.AIR);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void blockEntityNbtChangeInvalidatesLazily(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos);
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, around(helper, chestPos, 1), SnapshotPolicy.minimal());
        try (SnapshotLease lease = captured.lease()) {
            helper.assertTrue(lease.isCurrent(), "the untouched block entity is current");
            long before = WatchEvents.dispatchCount();

            chest.setItem(0, new ItemStack(Items.DIAMOND, 2));

            helper.assertValueEqual(before, WatchEvents.dispatchCount(), "a block-entity data change fires no event");
            helper.assertValueEqual(0L, watch(capture).marks(), "a block-entity data change does not mark");
            helper.assertFalse(lease.isCurrent(), "the lazy NBT snapshot sees the change");
        } finally {
            chest.setItem(0, ItemStack.EMPTY);
            capture.close();
            helper.setBlock(chestPos, Blocks.AIR);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void blockEntityRemovalWithoutEventInvalidatesLazily(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, around(helper, chestPos, 1), SnapshotPolicy.minimal());
        try (SnapshotLease lease = captured.lease()) {
            long before = WatchEvents.dispatchCount();
            LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(chestPos));
            chunk.removeBlockEntity(helper.absolutePos(chestPos));

            helper.assertValueEqual(
                before,
                WatchEvents.dispatchCount(),
                "a direct block-entity removal fires no event"
            );
            helper.assertValueEqual(0L, watch(capture).marks(), "a direct block-entity removal does not mark");
            helper.assertFalse(lease.isCurrent(), "the lazy lookup sees the missing block entity");
        } finally {
            capture.close();
            helper.setBlock(chestPos, Blocks.AIR);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void blockEntityDataOutsideTheRegionDoesNotInvalidate(GameTestHelper helper) {
        BlockPos watchedPos = new BlockPos(0, 1, 0);
        BlockPos otherPos = new BlockPos(4, 1, 0);
        helper.setBlock(watchedPos, Blocks.CHEST);
        helper.setBlock(otherPos, Blocks.CHEST);
        ChestBlockEntity other = helper.getBlockEntity(otherPos);
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, around(helper, watchedPos, 1), SnapshotPolicy.minimal());
        try (SnapshotLease lease = captured.lease()) {
            other.setItem(0, new ItemStack(Items.DIAMOND, 2));
            helper.assertTrue(lease.isCurrent(), "an uncaptured block entity is not compared");
        } finally {
            other.setItem(0, ItemStack.EMPTY);
            capture.close();
            helper.setBlock(watchedPos, Blocks.AIR);
            helper.setBlock(otherPos, Blocks.AIR);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void breakEventInvalidatesTheLease(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, cell(helper), SnapshotPolicy.minimal());
        try (SnapshotLease lease = captured.lease()) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockState state = Blocks.STONE.defaultBlockState();
            WatchEvents.blockBroken(
                new BlockEvent.BreakEvent(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), state, player)
            );
            helper.assertFalse(lease.isCurrent(), "a break event in the watched chunk invalidates");
        } finally {
            capture.close();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void pistonEventInvalidatesTheLease(GameTestHelper helper) {
        LevelCapture capture = new LevelCapture(helper.getLevel());
        CaptureResult.Captured captured = captured(capture, cell(helper), SnapshotPolicy.minimal());
        try (SnapshotLease lease = captured.lease()) {
            BlockPos pos = helper.absolutePos(BlockPos.ZERO);
            WatchEvents.pistonMoved(
                new PistonEvent.Pre(helper.getLevel(), pos, Direction.UP, PistonEvent.PistonMoveType.EXTEND)
            );
            helper.assertFalse(lease.isCurrent(), "a piston move in the watched chunk invalidates");
        } finally {
            capture.close();
        }
        helper.succeed();
    }

    private static SnapshotPolicy entityPolicy() {
        return new SnapshotPolicy(maxElements, 0, Set.of(OccupantKind.entity), Set.of(), List.of());
    }

    private static SnapshotPolicy nestedPolicy() {
        return new SnapshotPolicy(maxElements, 1, Set.of(OccupantKind.nested), Set.of(), List.of());
    }

    private static Cuboid cell(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return Cuboid.of(
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            origin.getX() + 1,
            origin.getY() + 1,
            origin.getZ() + 1
        );
    }

    private static Cuboid around(GameTestHelper helper, BlockPos relative, int size) {
        BlockPos origin = helper.absolutePos(relative);
        return Cuboid.of(
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            origin.getX() + size,
            origin.getY() + size,
            origin.getZ() + size
        );
    }

    private static CaptureResult.Captured captured(LevelCapture capture, Cuboid bounds, SnapshotPolicy policy) {
        CaptureResult result = capture.capture(new CaptureRequest(bounds, policy));
        if (result instanceof CaptureResult.Captured captured) {
            return captured;
        }
        throw new GameTestAssertException("capture did not succeed: " + result);
    }

    private static LevelWatch.WatchHandle watch(LevelCapture capture) {
        LevelWatch.WatchHandle handle = capture.watchHandle();
        if (handle == null) {
            throw new GameTestAssertException("the capture has no watch");
        }
        return handle;
    }

    private record FakeLookup(Map<BlockPos, NestedExtent> blocks) implements NestedExtentLookup {

        @Override
        public @Nullable NestedExtent block(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity
        ) {
            return blocks.get(pos);
        }

        @Override
        public @Nullable NestedExtent entity(Entity entity) {
            return null;
        }
    }
}
