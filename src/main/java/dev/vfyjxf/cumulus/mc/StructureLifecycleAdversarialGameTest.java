package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.StructureAttempt;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.context.StructureSpec;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Real-level adversarial checks for the host lifecycle: the per-level manager and the claim ledger
 * must stay consistent through break/explosion/place/piston/load/unload and rejected events.
 */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class StructureLifecycleAdversarialGameTest {

    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");
    private static final NamespacedId diamond = NamespacedId.of("minecraft", "diamond_block");
    private static final NamespacedId cellBlueprintId = id("lifecycle_adversarial_cell");
    private static final NamespacedId stoneBayId = id("lifecycle_adversarial_stone_bay");
    private static final NamespacedId stoneCheckId = id("lifecycle_adversarial_stone_check");
    private static final NamespacedId diamondBayId = id("lifecycle_adversarial_diamond_bay");
    private static final NamespacedId diamondCheckId = id("lifecycle_adversarial_diamond_check");
    private static final NamespacedId claimBay = id("lifecycle_adversarial_claim_bay");
    private static final OwnerId ownerA = OwnerId.of(id("lifecycle_adversarial_owner_a"));
    private static final OwnerId ownerB = OwnerId.of(id("lifecycle_adversarial_owner_b"));
    private static final ContextKey<Long> count = ContextKeys
            .of(id("lifecycle_adversarial_count"), Long.class, Retention.instance);
    private static final Blueprint stoneCell = cellBlueprint(stoneBayId, stoneCheckId, stone);
    private static final Blueprint diamondCell = cellBlueprint(diamondBayId, diamondCheckId, diamond);

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void breakOverlappingStructuresReleasesEveryClaimOnce(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        SavedClaims ledger = state.claims(level);
        helper.setBlock(BlockPos.ZERO, Blocks.STONE);
        LatticePoint point = point(helper.absolutePos(BlockPos.ZERO));
        Siting siting = Siting.of(point, Orientation.identity);
        StructureInstance first = form(manager, state, level, ownerA, siting, point);
        StructureInstance second = form(manager, state, level, ownerB, siting, point);
        long base = ledger.revision();
        claim(ledger, ownerA, siting, point, Sharing.shared);
        claim(ledger, ownerB, siting, point, Sharing.shared);
        long settled = ledger.revision();
        assertEquals(helper, base + 2, settled, "two shared rows settle in two revisions");
        assertEquals(helper, 2, rows(ledger, point).size(), "two shared owners hold the cell");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        WatchEvents.blockBroken(
            new BlockEvent.BreakEvent(
                level,
                helper.absolutePos(BlockPos.ZERO),
                Blocks.STONE.defaultBlockState(),
                player
            )
        );

        assertTrue(helper, manager.find(first.id()) == null, "the first overlapping instance is broken");
        assertTrue(helper, manager.find(second.id()) == null, "the second overlapping instance is broken");
        assertEquals(helper, settled + 2, ledger.revision(), "each released owner bumps the revision once");
        assertEquals(helper, 0, rows(ledger, point).size(), "every overlapping claim row is released");

        WatchEvents.blockBroken(
            new BlockEvent.BreakEvent(
                level,
                helper.absolutePos(BlockPos.ZERO),
                Blocks.STONE.defaultBlockState(),
                player
            )
        );
        assertEquals(helper, settled + 2, ledger.revision(), "a duplicate break releases nothing more");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void explosionBreaksOnlyTheStructuresInsideItsAffectedBlocks(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        SavedClaims ledger = state.claims(level);
        BlockPos firstRel = BlockPos.ZERO;
        BlockPos secondRel = new BlockPos(4, 0, 0);
        helper.setBlock(firstRel, Blocks.STONE);
        helper.setBlock(secondRel, Blocks.STONE);
        LatticePoint firstPoint = point(helper.absolutePos(firstRel));
        LatticePoint secondPoint = point(helper.absolutePos(secondRel));
        Siting firstSiting = Siting.of(firstPoint, Orientation.identity);
        Siting secondSiting = Siting.of(secondPoint, Orientation.identity);
        StructureInstance first = form(manager, state, level, ownerA, firstSiting, firstPoint);
        StructureInstance second = form(manager, state, level, ownerB, secondSiting, secondPoint);
        claim(ledger, ownerA, firstSiting, firstPoint, Sharing.exclusive);
        claim(ledger, ownerB, secondSiting, secondPoint, Sharing.exclusive);
        long settled = ledger.revision();

        BlockPos firstAbs = helper.absolutePos(firstRel);
        Explosion explosion = new Explosion(
            level,
            null,
            firstAbs.getX() + 0.5D,
            firstAbs.getY() + 0.5D,
            firstAbs.getZ() + 0.5D,
            1.0F,
            false,
            Explosion.BlockInteraction.DESTROY,
            List.of(firstAbs)
        );
        WatchEvents.explosionDetonated(new ExplosionEvent.Detonate(level, explosion, List.of()));

        assertTrue(helper, manager.find(first.id()) == null, "the affected structure is broken");
        assertTrue(helper, manager.find(second.id()) != null, "the far structure survives");
        assertEquals(helper, settled + 1, ledger.revision(), "one broken structure releases one key");
        assertEquals(helper, 0, rows(ledger, firstPoint).size(), "the affected claims are released");
        assertEquals(helper, 1, rows(ledger, secondPoint).size(), "the far claims stay");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void placementInvalidatesOnlyTheStructureAtThePosition(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        SavedClaims ledger = state.claims(level);
        BlockPos firstRel = BlockPos.ZERO;
        BlockPos secondRel = new BlockPos(4, 0, 0);
        LatticePoint firstPoint = point(helper.absolutePos(firstRel));
        LatticePoint secondPoint = point(helper.absolutePos(secondRel));
        Siting firstSiting = Siting.of(firstPoint, Orientation.identity);
        Siting secondSiting = Siting.of(secondPoint, Orientation.identity);
        StructureInstance first = form(manager, state, level, ownerA, firstSiting, firstPoint);
        StructureInstance second = form(manager, state, level, ownerB, secondSiting, secondPoint);
        claim(ledger, ownerA, firstSiting, firstPoint, Sharing.exclusive);
        claim(ledger, ownerB, secondSiting, secondPoint, Sharing.exclusive);
        long settled = ledger.revision();

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, helper.absolutePos(firstRel));
        WatchEvents.blockPlaced(new BlockEvent.EntityPlaceEvent(snapshot, Blocks.AIR.defaultBlockState(), player));

        assertTrue(helper, manager.find(first.id()) == null, "a placement invalidates the structure");
        assertTrue(helper, manager.find(second.id()) != null, "a far structure is not invalidated");
        assertEquals(helper, settled + 1, ledger.revision(), "an invalidation releases its key once");
        assertEquals(helper, 0, rows(ledger, firstPoint).size(), "the affected claims are released");
        assertEquals(helper, 1, rows(ledger, secondPoint).size(), "the far claims stay");

        WatchEvents.blockPlaced(new BlockEvent.EntityPlaceEvent(snapshot, Blocks.AIR.defaultBlockState(), player));
        assertEquals(helper, settled + 1, ledger.revision(), "a duplicate placement is idempotent");
        assertTrue(helper, manager.find(second.id()) != null, "the unrelated structure is still formed");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void settleCommandReplacesSameSitingAndAbortsAFailedAttempt(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        SavedClaims ledger = state.claims(level);
        BlockPos originRel = BlockPos.ZERO;
        helper.setBlock(originRel, Blocks.STONE);
        LatticePoint origin = point(helper.absolutePos(originRel));
        Siting siting = Siting.of(origin, Orientation.identity);
        CommandSourceStack source = stack(helper, helper.absolutePos(originRel), new RecordingSource());
        cleanup(manager, ledger, state.owner(), siting);

        MatchResult first = SettleCommand.settle(source, state, level, stoneCell, origin);
        if (!(first instanceof MatchResult.Formed formed)) {
            helper.fail("the first settle did not form: " + first);
            return;
        }
        StructureInstance firstInstance = manager.find(state.owner(), siting);
        if (firstInstance == null) {
            helper.fail("the first settle formed no manager instance");
            return;
        }
        assertEquals(helper, 1L, formed.contextRevision(), "the settle publishes one context revision");
        assertEquals(helper, 1L, firstInstance.context().revision(), "the instance context is bound to the settle");
        assertEquals(helper, firstInstance.id(), firstInstance.context().instance(), "the context belongs to the id");
        assertEquals(helper, 1, rows(ledger, origin).size(), "the first settle writes one row");
        long afterFirst = ledger.revision();

        MatchResult second = SettleCommand.settle(source, state, level, stoneCell, origin);
        if (!(second instanceof MatchResult.Formed replaced)) {
            helper.fail("the second settle did not form: " + second);
            return;
        }
        StructureInstance secondInstance = manager.find(state.owner(), siting);
        if (secondInstance == null) {
            helper.fail("the replacement settle formed no manager instance");
            return;
        }
        assertTrue(helper, secondInstance.id() != firstInstance.id(), "a re-settle allocates a new instance id");
        assertTrue(helper, manager.find(firstInstance.id()) == null, "the replaced instance is gone");
        assertEquals(helper, 1, replaced.claims().rows().size(), "a re-settle leaves one row");
        assertEquals(helper, 1, rows(ledger, origin).size(), "a re-settle never duplicates the row");
        assertEquals(helper, afterFirst + 1, ledger.revision(), "a re-settle bumps the revision once");

        MatchResult failed = SettleCommand.settle(source, state, level, diamondCell, origin);
        if (!(failed instanceof MatchResult.Failed)) {
            helper.fail("a mismatching check must not settle: " + failed);
            return;
        }
        StructureInstance unchanged = manager.find(state.owner(), siting);
        assertTrue(helper, unchanged != null, "a failed settle aborts its draft and keeps the instance");
        if (unchanged != null) {
            assertEquals(helper, secondInstance.id(), unchanged.id(), "the formed instance is not replaced");
        }
        assertEquals(helper, 1, rows(ledger, origin).size(), "a failed attempt writes no row");
        assertEquals(helper, afterFirst + 1, ledger.revision(), "a failed attempt does not move the revision");
        cleanup(manager, ledger, state.owner(), siting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void pistonFullMoveTranslatesClaimsAndKeepsTheInstanceContext(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        SavedClaims ledger = state.claims(level);
        BlockPos pistonRel = new BlockPos(0, 1, 0);
        BlockPos stoneRel = new BlockPos(1, 1, 0);
        helper.setBlock(pistonRel, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(stoneRel, Blocks.STONE);
        LatticePoint origin = point(helper.absolutePos(stoneRel));
        Siting siting = Siting.of(origin, Orientation.identity);
        StructureInstance instance = form(manager, state, level, ownerA, siting, origin);
        manager.publish(instance.id(), Map.of(count, 5L), FindingBundle.empty(), Spend.zero);
        claim(ledger, ownerA, siting, origin, Sharing.exclusive);
        long beforeMove = ledger.revision();

        WatchEvents.pistonMoved(
            new PistonEvent.Pre(level, helper.absolutePos(pistonRel), Direction.EAST, PistonEvent.PistonMoveType.EXTEND)
        );

        LatticePoint moved = Objects.requireNonNull(origin.offset(1, 0, 0), "moved cell");
        StructureInstance movedInstance = manager.find(instance.id());
        if (movedInstance == null) {
            helper.fail("a full piston move dropped the instance instead of moving it");
            return;
        }
        assertEquals(helper, moved, movedInstance.siting().origin(), "the siting origin is translated");
        assertEquals(helper, CuboidSet.of(Cuboid.of(moved)), movedInstance.bounds(), "the bounds are translated");
        assertEquals(
            helper,
            5L,
            Objects.requireNonNull(movedInstance.context().get(count)),
            "the moved instance keeps its context"
        );
        assertEquals(helper, 0, rows(ledger, origin).size(), "the old claim row is released");
        assertEquals(helper, 1, rows(ledger, moved).size(), "the translated claim row is written");
        assertEquals(
            helper,
            movedInstance.siting(),
            rows(ledger, moved).get(0).siting(),
            "the row uses the moved siting"
        );
        assertEquals(helper, beforeMove + 2, ledger.revision(), "a move settles once and releases once");

        WatchEvents.pistonMoved(
            new PistonEvent.Pre(level, helper.absolutePos(pistonRel), Direction.EAST, PistonEvent.PistonMoveType.EXTEND)
        );
        assertEquals(helper, beforeMove + 2, ledger.revision(), "a duplicate piston event is idempotent");
        assertEquals(helper, 1, rows(ledger, moved).size(), "a duplicate piston event never duplicates a row");
        cleanup(manager, ledger, ownerA, movedInstance.siting());
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void pistonPartialMoveInvalidatesAndReleasesTheOldClaims(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        SavedClaims ledger = state.claims(level);
        BlockPos pistonRel = new BlockPos(0, 1, 0);
        BlockPos pushedRel = new BlockPos(1, 1, 0);
        BlockPos stillRel = new BlockPos(1, 2, 0);
        helper.setBlock(pistonRel, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(pushedRel, Blocks.STONE);
        helper.setBlock(stillRel, Blocks.STONE);
        LatticePoint pushed = point(helper.absolutePos(pushedRel));
        LatticePoint still = point(helper.absolutePos(stillRel));
        Siting siting = Siting.of(pushed, Orientation.identity);
        StructureInstance instance = formBounds(
            manager,
            state,
            level,
            ownerA,
            siting,
            CuboidSet.of(Cuboid.of(pushed), Cuboid.of(still))
        );
        claimRows(ledger, ownerA, siting, List.of(pushed, still), Sharing.exclusive);
        long beforeMove = ledger.revision();
        assertEquals(helper, 2, rows(ledger, pushed).size() + rows(ledger, still).size(), "two claim rows");

        WatchEvents.pistonMoved(
            new PistonEvent.Pre(level, helper.absolutePos(pistonRel), Direction.EAST, PistonEvent.PistonMoveType.EXTEND)
        );

        assertTrue(helper, manager.find(instance.id()) == null, "a partial move invalidates the structure");
        assertEquals(helper, beforeMove + 1, ledger.revision(), "an invalidation releases the key once");
        assertEquals(helper, 0, rows(ledger, pushed).size(), "the old pushed claim is released");
        assertEquals(helper, 0, rows(ledger, still).size(), "the old stationary claim is released");
        ledger.release(ownerA, siting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void pistonDestroyedPartInvalidatesAndReleasesTheOldClaims(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        SavedClaims ledger = state.claims(level);
        BlockPos pistonRel = new BlockPos(0, 1, 0);
        BlockPos torchRel = new BlockPos(1, 1, 0);
        helper.setBlock(pistonRel, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(torchRel, Blocks.TORCH);
        LatticePoint torch = point(helper.absolutePos(torchRel));
        Siting siting = Siting.of(torch, Orientation.identity);
        StructureInstance instance = form(manager, state, level, ownerA, siting, torch);
        claim(ledger, ownerA, siting, torch, Sharing.exclusive);
        long beforeMove = ledger.revision();

        WatchEvents.pistonMoved(
            new PistonEvent.Pre(level, helper.absolutePos(pistonRel), Direction.EAST, PistonEvent.PistonMoveType.EXTEND)
        );

        assertTrue(helper, manager.find(instance.id()) == null, "a destroyed part invalidates the structure");
        int destroyedRows = rows(ledger, torch).size();
        long destroyedDelta = ledger.revision() - beforeMove;
        helper.assertTrue(
            destroyedRows == 0 && destroyedDelta == 1L,
            "the destroyed-part Invalidated must release the claims (D-38): observed rows=" + destroyedRows
                    + ", revisionDelta=" + destroyedDelta
        );
        ledger.release(ownerA, siting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void pistonMoveClaimConflictInvalidatesAndKeepsTheIncumbent(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        SavedClaims ledger = state.claims(level);
        BlockPos pistonRel = new BlockPos(0, 1, 0);
        BlockPos stoneRel = new BlockPos(1, 1, 0);
        BlockPos targetRel = new BlockPos(2, 1, 0);
        helper.setBlock(pistonRel, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(stoneRel, Blocks.STONE);
        helper.setBlock(targetRel, Blocks.STONE);
        LatticePoint origin = point(helper.absolutePos(stoneRel));
        LatticePoint target = point(helper.absolutePos(targetRel));
        Siting siting = Siting.of(origin, Orientation.identity);
        Siting incumbents = Siting.of(target, Orientation.identity);
        StructureInstance instance = form(manager, state, level, ownerA, siting, origin);
        claim(ledger, ownerA, siting, origin, Sharing.exclusive);
        claim(ledger, ownerB, incumbents, target, Sharing.exclusive);
        long beforeMove = ledger.revision();
        assertEquals(helper, 1, rows(ledger, target).size(), "the incumbent row exists");

        WatchEvents.pistonMoved(
            new PistonEvent.Pre(level, helper.absolutePos(pistonRel), Direction.EAST, PistonEvent.PistonMoveType.EXTEND)
        );

        assertTrue(helper, manager.find(instance.id()) == null, "a conflicting move invalidates the structure");
        assertEquals(helper, beforeMove + 1, ledger.revision(), "only the invalidation release bumps the revision");
        assertEquals(helper, 0, rows(ledger, origin).size(), "the old claim row is released");
        assertEquals(helper, 1, rows(ledger, target).size(), "the incumbent row survives");
        assertEquals(helper, ownerB, rows(ledger, target).get(0).owner(), "the incumbent owner is untouched");
        ledger.release(ownerA, siting);
        ledger.release(ownerB, incumbents);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void levelUnloadDropsInstancesAndKeepsTheClaimRows(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        SavedClaims ledger = state.claims(level);
        BlockPos rel = new BlockPos(0, 1, 0);
        helper.setBlock(rel, Blocks.STONE);
        LatticePoint point = point(helper.absolutePos(rel));
        Siting siting = Siting.of(point, Orientation.identity);
        StructureInstance instance = form(manager, state, level, ownerA, siting, point);
        claim(ledger, ownerA, siting, point, Sharing.exclusive);
        long beforeUnload = ledger.revision();

        WatchEvents.levelUnloaded(new LevelEvent.Unload(level));

        assertTrue(helper, manager.find(instance.id()) == null, "an unload drops the instance");
        assertTrue(helper, manager.findAt(point).isEmpty(), "an unload empties the registry");
        int unloadRows = rows(ledger, point).size();
        long unloadDelta = ledger.revision() - beforeUnload;
        helper.assertTrue(
            unloadRows == 1 && unloadDelta == 0L,
            "a world unload drops only the in-memory instance; claims persist: observed rows=" + unloadRows
                    + ", revisionDelta=" + unloadDelta
        );
        ledger.release(ownerA, siting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void eventsOutsideEveryStructureLeaveTheManagerAndLedgerUntouched(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        SavedClaims ledger = state.claims(level);
        BlockPos structureRel = new BlockPos(0, 1, 0);
        BlockPos outsideRel = new BlockPos(6, 1, 0);
        helper.setBlock(structureRel, Blocks.STONE);
        LatticePoint point = point(helper.absolutePos(structureRel));
        Siting siting = Siting.of(point, Orientation.identity);
        StructureInstance instance = form(manager, state, level, ownerA, siting, point);
        claim(ledger, ownerA, siting, point, Sharing.exclusive);
        long base = ledger.revision();

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        WatchEvents.blockBroken(
            new BlockEvent.BreakEvent(level, helper.absolutePos(outsideRel), Blocks.STONE.defaultBlockState(), player)
        );
        WatchEvents.blockPlaced(
            new BlockEvent.EntityPlaceEvent(
                BlockSnapshot.create(level.dimension(), level, helper.absolutePos(outsideRel)),
                Blocks.AIR.defaultBlockState(),
                player
            )
        );
        WatchEvents.pistonMoved(
            new PistonEvent.Pre(level, helper.absolutePos(outsideRel), Direction.UP, PistonEvent.PistonMoveType.EXTEND)
        );

        assertTrue(
            helper,
            manager.find(instance.id()) != null,
            "an event outside the bounds never removes a structure"
        );
        assertTrue(helper, manager.findAt(point).size() == 1, "the instance is still found at its bounds");
        assertEquals(helper, base, ledger.revision(), "an outside event never touches the ledger");
        assertEquals(helper, 1, rows(ledger, point).size(), "the claim row is untouched");
        cleanup(manager, ledger, ownerA, siting);
        helper.succeed();
    }

    /**
     * A manager that rejects a Moved event (the target siting is already occupied) must not keep the
     * claim translation that the adapter performed before dispatching it.
     */
    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void rejectedPistonMoveMustNotLeaveTranslatedClaimsBehind(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        SavedClaims ledger = state.claims(level);
        BlockPos pistonRel = new BlockPos(0, 1, 0);
        BlockPos stoneRel = new BlockPos(1, 1, 0);
        helper.setBlock(pistonRel, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(stoneRel, Blocks.STONE);
        LatticePoint origin = point(helper.absolutePos(stoneRel));
        LatticePoint target = Objects.requireNonNull(origin.offset(1, 0, 0), "target cell");
        Siting siting = Siting.of(origin, Orientation.identity);
        Siting occupied = Siting.of(target, Orientation.identity);
        StructureInstance moving = form(manager, state, level, ownerA, siting, origin);
        StructureInstance occupant = form(manager, state, level, ownerA, occupied, target);
        claim(ledger, ownerA, siting, origin, Sharing.exclusive);
        long before = ledger.revision();

        WatchEvents.pistonMoved(
            new PistonEvent.Pre(level, helper.absolutePos(pistonRel), Direction.EAST, PistonEvent.PistonMoveType.EXTEND)
        );

        StructureInstance afterReject = manager.find(moving.id());
        boolean managerRejected = afterReject != null && afterReject.siting().equals(siting);
        int oldRows = rows(ledger, origin).size();
        int targetRows = rows(ledger, target).size();
        long revisionDelta = ledger.revision() - before;
        helper.assertTrue(
            managerRejected && oldRows == 1 && targetRows == 0 && revisionDelta == 0L,
            "a rejected Moved event must leave the old rows in place: managerAtOldSiting=" + managerRejected
                    + ", oldRows=" + oldRows + ", translatedRows=" + targetRows + ", revisionDelta=" + revisionDelta
                    + ", occupant=" + occupant.id()
        );
        cleanup(manager, ledger, ownerA, siting);
        cleanup(manager, ledger, ownerA, occupied);
        helper.succeed();
    }

    private static Blueprint cellBlueprint(NamespacedId bayId, NamespacedId checkId, NamespacedId expected) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(bayId, CuboidSet.of(bounds), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(cellBlueprintId).bounds(bounds)
                .check(Check.of(checkId, bay, Focuses.bay(), Expectations.eachCell(Expectations.block(expected))))
                .build();
    }

    private static ServerState requireState() {
        ServerState state = ServerState.current();
        if (state == null) {
            throw new GameTestAssertException("the server state is not open");
        }
        return state;
    }

    private static StructureInstance form(
        StructureManager manager,
        ServerState state,
        ServerLevel level,
        OwnerId owner,
        Siting siting,
        LatticePoint cell
    ) {
        return formBounds(manager, state, level, owner, siting, CuboidSet.of(Cuboid.of(cell)));
    }

    private static StructureInstance formBounds(
        StructureManager manager,
        ServerState state,
        ServerLevel level,
        OwnerId owner,
        Siting siting,
        CuboidSet bounds
    ) {
        StructureAttempt attempt = manager.begin(state.world(level), new StructureSpec(owner, siting, cellBlueprintId));
        return attempt.commit(bounds);
    }

    private static void claim(SavedClaims ledger, OwnerId owner, Siting siting, LatticePoint cell, Sharing sharing) {
        claimRows(ledger, owner, siting, List.of(cell), sharing);
    }

    private static void claimRows(
        SavedClaims ledger,
        OwnerId owner,
        Siting siting,
        List<LatticePoint> cells,
        Sharing sharing
    ) {
        long expected = ledger.revision();
        List<ClaimRow> rows = new ArrayList<>(cells.size());
        for (LatticePoint cell : cells) {
            rows.add(new ClaimRow(LatticeKey.of(cell), sharing, owner, siting, claimBay));
        }
        ClaimVerdict verdict = ledger.settle(new SettleRequest(owner, siting, rows, expected));
        if (!(verdict instanceof ClaimVerdict.Settled)) {
            throw new GameTestAssertException("the setup settle was rejected: " + verdict);
        }
    }

    private static List<ClaimRow> rows(SavedClaims ledger, LatticePoint cell) {
        return ledger.read(Cuboid.of(cell)).rows();
    }

    private static void cleanup(StructureManager manager, SavedClaims ledger, OwnerId owner, Siting siting) {
        StructureInstance instance = manager.find(owner, siting);
        if (instance != null) {
            manager.onEvent(new StructureEvent.Broken(instance.id()));
        }
        ledger.release(owner, siting);
    }

    private static LatticePoint point(BlockPos pos) {
        return LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }

    private static void assertEquals(GameTestHelper helper, Object actual, Object expected, String message) {
        helper.assertTrue(
            Objects.equals(actual, expected),
            message + " (expected " + expected + ", observed " + actual + ")"
        );
    }

    private static void assertTrue(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    private static CommandSourceStack stack(GameTestHelper helper, BlockPos origin, CommandSource source) {
        return new CommandSourceStack(
            source,
            Vec3.atLowerCornerOf(origin),
            Vec2.ZERO,
            helper.getLevel(),
            4,
            "gametest",
            Component.literal("gametest"),
            helper.getLevel().getServer(),
            null
        );
    }

    private static final class RecordingSource implements CommandSource {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        private String text() {
            return String.join("\n", messages);
        }
    }
}
