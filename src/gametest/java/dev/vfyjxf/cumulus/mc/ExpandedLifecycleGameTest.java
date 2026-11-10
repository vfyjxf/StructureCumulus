package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
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
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Expanded multi-instance lifecycle checks on a real level: one funnel-reported write can hit
 * several overlapping instances and must release each claim key exactly once, with duplicate
 * writes leaving no partial state behind.
 */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class ExpandedLifecycleGameTest {

    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");
    private static final NamespacedId cellBlueprintId = id("expanded_lifecycle_cell");
    private static final NamespacedId stoneBayId = id("expanded_lifecycle_stone_bay");
    private static final NamespacedId stoneCheckId = id("expanded_lifecycle_stone_check");
    private static final NamespacedId claimBay = id("expanded_lifecycle_claim_bay");
    private static final OwnerId ownerA = OwnerId.of(id("expanded_lifecycle_owner_a"));
    private static final OwnerId ownerB = OwnerId.of(id("expanded_lifecycle_owner_b"));
    private static final OwnerId ownerC = OwnerId.of(id("expanded_lifecycle_owner_c"));
    private static final OwnerId ownerD = OwnerId.of(id("expanded_lifecycle_owner_d"));
    private static final ContextKey<Long> count = ContextKeys
            .of(id("expanded_lifecycle_count"), Long.class, Retention.instance);
    private static final Blueprint stoneCell = cellBlueprint(stoneBayId, stoneCheckId);

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void oneBreakRemovesEveryOverlappingInstanceAndReleasesEachKey(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        helper.setBlock(BlockPos.ZERO, Blocks.STONE);
        LatticePoint cell = point(helper.absolutePos(BlockPos.ZERO));
        Siting siting = Siting.of(cell, Orientation.identity);
        Siting secondSiting = aside(siting, Turn.clockwise);
        Siting thirdSiting = aside(siting, Turn.half);
        StructureInstance first = form(manager, state, level, ownerA, siting, cell);
        StructureInstance second = form(manager, state, level, ownerB, secondSiting, cell);
        StructureInstance third = form(manager, state, level, ownerC, thirdSiting, cell);
        claim(ledger, first.id(), siting, cell, Sharing.shared);
        claim(ledger, second.id(), secondSiting, cell, Sharing.shared);
        claim(ledger, third.id(), thirdSiting, cell, Sharing.shared);
        long base = ledger.revision();
        assertEquals(helper, 3, rows(ledger, cell).size(), "three overlapping owners hold one cell");

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(BlockPos.ZERO),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            false
        );

        assertTrue(helper, manager.find(first.id()) == null, "the first overlapping instance is broken");
        assertTrue(helper, manager.find(second.id()) == null, "the second overlapping instance is broken");
        assertTrue(helper, manager.find(third.id()) == null, "the third overlapping instance is broken");
        assertTrue(helper, manager.findAt(cell).isEmpty(), "nothing is left at the broken cell");
        assertEquals(helper, 0, rows(ledger, cell).size(), "every overlapping claim row is released");
        assertEquals(helper, base + 3, ledger.revision(), "each released key bumps the revision once");
        assertTrue(helper, manager.context(first.id()).revision() == 0, "the first context is gone");
        assertTrue(helper, manager.context(third.id()).revision() == 0, "the third context is gone");

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(BlockPos.ZERO),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            false
        );
        assertEquals(helper, base + 3, ledger.revision(), "a duplicate break releases nothing more");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void oneBreakLeavesInstancesWhoseBoundsMissTheCell(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos firstRel = BlockPos.ZERO;
        BlockPos farRel = new BlockPos(4, 0, 0);
        helper.setBlock(firstRel, Blocks.STONE);
        helper.setBlock(farRel, Blocks.STONE);
        LatticePoint firstCell = point(helper.absolutePos(firstRel));
        LatticePoint farCell = point(helper.absolutePos(farRel));
        Siting firstSiting = Siting.of(firstCell, Orientation.identity);
        Siting secondSiting = aside(firstSiting, Turn.clockwise);
        Siting farSiting = Siting.of(farCell, Orientation.identity);
        StructureInstance first = form(manager, state, level, ownerA, firstSiting, firstCell);
        StructureInstance second = form(manager, state, level, ownerB, secondSiting, firstCell);
        StructureInstance far = form(manager, state, level, ownerC, farSiting, farCell);
        claim(ledger, first.id(), firstSiting, firstCell, Sharing.shared);
        claim(ledger, second.id(), secondSiting, firstCell, Sharing.shared);
        claim(ledger, far.id(), farSiting, farCell, Sharing.shared);
        long base = ledger.revision();

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(firstRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            false
        );

        assertTrue(helper, manager.find(first.id()) == null);
        assertTrue(helper, manager.find(second.id()) == null);
        StructureInstance survivor = manager.find(far.id());
        if (survivor == null) {
            helper.fail("an instance whose bounds miss the cell must survive");
            return;
        }
        assertEquals(helper, 0L, survivor.context().revision(), "the surviving context is untouched");
        assertEquals(helper, 0, rows(ledger, firstCell).size(), "the shared cell rows are released");
        assertEquals(helper, 1, rows(ledger, farCell).size(), "the far claim row stays");
        assertEquals(helper, base + 2, ledger.revision(), "only the two overlapping keys are released");

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(firstRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            false
        );
        assertEquals(helper, base + 2, ledger.revision(), "a duplicate break is idempotent");
        cleanup(manager, ledger, ownerA, firstSiting);
        cleanup(manager, ledger, ownerB, firstSiting);
        cleanup(manager, ledger, ownerC, farSiting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void destructionBreaksEveryTouchedInstanceOnlyOnce(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos firstRel = BlockPos.ZERO;
        BlockPos secondRel = new BlockPos(4, 0, 0);
        BlockPos farRel = new BlockPos(8, 0, 0);
        helper.setBlock(firstRel, Blocks.STONE);
        helper.setBlock(secondRel, Blocks.STONE);
        helper.setBlock(farRel, Blocks.STONE);
        LatticePoint firstCell = point(helper.absolutePos(firstRel));
        LatticePoint secondCell = point(helper.absolutePos(secondRel));
        LatticePoint farCell = point(helper.absolutePos(farRel));
        Siting firstSiting = Siting.of(firstCell, Orientation.identity);
        Siting secondSiting = Siting.of(secondCell, Orientation.identity);
        Siting farSiting = Siting.of(farCell, Orientation.identity);
        Siting overlappingSiting = aside(firstSiting, Turn.clockwise);
        StructureInstance first = form(manager, state, level, ownerA, firstSiting, firstCell);
        StructureInstance second = form(manager, state, level, ownerB, overlappingSiting, firstCell);
        StructureInstance third = form(manager, state, level, ownerC, secondSiting, secondCell);
        StructureInstance far = form(manager, state, level, ownerD, farSiting, farCell);
        claim(ledger, first.id(), firstSiting, firstCell, Sharing.shared);
        claim(ledger, second.id(), overlappingSiting, firstCell, Sharing.shared);
        claim(ledger, third.id(), secondSiting, secondCell, Sharing.shared);
        claim(ledger, far.id(), farSiting, farCell, Sharing.shared);
        long base = ledger.revision();

        BlockPos firstAbs = helper.absolutePos(firstRel);
        BlockPos secondAbs = helper.absolutePos(secondRel);
        CumulusHooks
                .blockChanged(level, firstAbs, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
        CumulusHooks
                .blockChanged(level, firstAbs, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
        CumulusHooks.blockChanged(
            level,
            secondAbs,
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            false
        );

        assertTrue(helper, manager.find(first.id()) == null, "the first overlapped instance is broken");
        assertTrue(helper, manager.find(second.id()) == null, "the second overlapped instance is broken");
        assertTrue(helper, manager.find(third.id()) == null, "the second cell instance is broken");
        assertTrue(helper, manager.find(far.id()) != null, "an instance outside the affected cells stays");
        assertEquals(helper, 0, rows(ledger, firstCell).size());
        assertEquals(helper, 0, rows(ledger, secondCell).size());
        assertEquals(helper, 1, rows(ledger, farCell).size(), "the far claim row is untouched");
        assertEquals(helper, base + 3, ledger.revision(), "duplicate affected positions release once");

        CumulusHooks
                .blockChanged(level, firstAbs, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
        assertEquals(helper, base + 3, ledger.revision(), "a duplicate write releases nothing more");
        cleanup(manager, ledger, ownerD, farSiting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void onePistonPushBreaksEveryOverlappingInstance(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos stoneRel = new BlockPos(1, 1, 0);
        helper.setBlock(stoneRel, Blocks.STONE);
        LatticePoint origin = point(helper.absolutePos(stoneRel));
        Siting siting = Siting.of(origin, Orientation.identity);
        Siting secondSiting = aside(siting, Turn.clockwise);
        StructureInstance first = form(manager, state, level, ownerA, siting, origin);
        StructureInstance second = form(manager, state, level, ownerB, secondSiting, origin);
        manager.publish(first.id(), Map.of(count, 3L), FindingBundle.empty(), Spend.zero);
        manager.publish(second.id(), Map.of(count, 5L), FindingBundle.empty(), Spend.zero);
        claim(ledger, first.id(), siting, origin, Sharing.shared);
        claim(ledger, second.id(), secondSiting, origin, Sharing.shared);
        long base = ledger.revision();

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(stoneRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );

        assertTrue(helper, manager.find(first.id()) == null, "a piston push breaks the first instance");
        assertTrue(helper, manager.find(second.id()) == null, "a piston push breaks the second instance");
        assertEquals(
            helper,
            0L,
            manager.context(first.id()).revision(),
            "the first context is dropped, not translated"
        );
        assertEquals(
            helper,
            0L,
            manager.context(second.id()).revision(),
            "the second context is dropped, not translated"
        );
        assertEquals(helper, 0, rows(ledger, origin).size(), "both claim rows are released");
        assertEquals(helper, base + 2, ledger.revision(), "each broken key bumps the revision once");

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(stoneRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );
        assertEquals(helper, base + 2, ledger.revision(), "a duplicate moving write releases nothing more");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void onePistonPushDropsEveryInstanceTouchingTheWrittenCells(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos firstRel = new BlockPos(1, 1, 0);
        BlockPos secondRel = new BlockPos(2, 1, 0);
        BlockPos aboveRel = new BlockPos(1, 2, 0);
        helper.setBlock(firstRel, Blocks.STONE);
        helper.setBlock(secondRel, Blocks.STONE);
        helper.setBlock(aboveRel, Blocks.STONE);
        LatticePoint firstCell = point(helper.absolutePos(firstRel));
        LatticePoint secondCell = point(helper.absolutePos(secondRel));
        LatticePoint aboveCell = point(helper.absolutePos(aboveRel));
        Siting firstSiting = Siting.of(firstCell, Orientation.identity);
        Siting secondSiting = Siting.of(secondCell, Orientation.identity);
        StructureInstance moving = form(manager, state, level, ownerA, firstSiting, firstCell);
        StructureInstance following = form(manager, state, level, ownerB, secondSiting, secondCell);
        Siting partialSiting = aside(firstSiting, Turn.clockwise);
        StructureInstance partial = formBounds(
            manager,
            state,
            level,
            ownerC,
            partialSiting,
            CuboidSet.of(Cuboid.of(firstCell), Cuboid.of(aboveCell))
        );
        manager.publish(moving.id(), Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        manager.publish(following.id(), Map.of(count, 2L), FindingBundle.empty(), Spend.zero);
        claim(ledger, moving.id(), firstSiting, firstCell, Sharing.shared);
        claim(ledger, following.id(), secondSiting, secondCell, Sharing.shared);
        claimRows(ledger, partial.id(), partialSiting, List.of(firstCell, aboveCell), Sharing.shared);
        long base = ledger.revision();

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(firstRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );
        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(secondRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );

        assertTrue(helper, manager.find(moving.id()) == null, "the pushed instance is broken");
        assertTrue(helper, manager.find(following.id()) == null, "the instance in the push chain is broken");
        assertTrue(helper, manager.find(partial.id()) == null, "a partly pushed instance is broken");
        assertEquals(helper, 0, rows(ledger, firstCell).size(), "no claim row is left on the shared old cells");
        assertEquals(helper, 0, rows(ledger, aboveCell).size(), "the partial instance's above claim is released");
        assertEquals(helper, 0, rows(ledger, secondCell).size(), "the second cell row is released");
        assertEquals(helper, base + 3, ledger.revision(), "each broken key releases once");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void pistonPushReleasesEachTouchedKeyExactlyOnce(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos stoneRel = new BlockPos(1, 1, 0);
        BlockPos occupiedRel = new BlockPos(2, 1, 0);
        helper.setBlock(stoneRel, Blocks.STONE);
        helper.setBlock(occupiedRel, Blocks.STONE);
        LatticePoint origin = point(helper.absolutePos(stoneRel));
        LatticePoint occupied = point(helper.absolutePos(occupiedRel));
        Siting siting = Siting.of(origin, Orientation.identity);
        Siting secondSiting = aside(siting, Turn.clockwise);
        Siting occupying = Siting.of(occupied, Orientation.identity);
        StructureInstance first = form(manager, state, level, ownerA, siting, origin);
        StructureInstance second = form(manager, state, level, ownerB, secondSiting, origin);
        StructureInstance occupant = form(manager, state, level, ownerA, occupying, occupied);
        claim(ledger, first.id(), siting, origin, Sharing.shared);
        claim(ledger, second.id(), secondSiting, origin, Sharing.shared);
        claim(ledger, occupant.id(), occupying, occupied, Sharing.exclusive);
        long base = ledger.revision();

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(stoneRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );
        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(occupiedRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.MOVING_PISTON.defaultBlockState(),
            true
        );

        assertTrue(helper, manager.find(first.id()) == null, "the first pushed instance is broken");
        assertTrue(helper, manager.find(second.id()) == null, "the second pushed instance is broken");
        assertTrue(helper, manager.find(occupant.id()) == null, "the occupant is invalidated by the incoming block");
        assertEquals(helper, 0, rows(ledger, origin).size(), "the shared origin rows are released");
        assertEquals(helper, 0, rows(ledger, occupied).size(), "the occupant row is released");
        assertEquals(helper, base + 3, ledger.revision(), "each dropped key bumps the revision once");

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(stoneRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );
        assertEquals(helper, base + 3, ledger.revision(), "a duplicate pass releases nothing more");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void pistonPushKeepsABareIncumbentClaimRowUntouched(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos firstRel = new BlockPos(1, 1, 0);
        BlockPos middleRel = new BlockPos(2, 1, 0);
        BlockPos lastRel = new BlockPos(3, 1, 0);
        helper.setBlock(firstRel, Blocks.STONE);
        helper.setBlock(middleRel, Blocks.STONE);
        helper.setBlock(lastRel, Blocks.STONE);
        LatticePoint firstCell = point(helper.absolutePos(firstRel));
        LatticePoint middleCell = point(helper.absolutePos(middleRel));
        LatticePoint lastCell = point(helper.absolutePos(lastRel));
        Siting firstSiting = Siting.of(firstCell, Orientation.identity);
        Siting lastSiting = Siting.of(lastCell, Orientation.identity);
        Siting incumbentSiting = Siting.of(middleCell, Orientation.identity);
        StructureInstance conflicting = form(manager, state, level, ownerA, firstSiting, firstCell);
        StructureInstance moving = form(manager, state, level, ownerB, lastSiting, lastCell);
        InstanceId incumbentInstance = new InstanceId(state.world(level), 1_000_000L);
        claim(ledger, conflicting.id(), firstSiting, firstCell, Sharing.shared);
        claim(ledger, moving.id(), lastSiting, lastCell, Sharing.shared);
        claim(ledger, incumbentInstance, incumbentSiting, middleCell, Sharing.exclusive);
        long base = ledger.revision();

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(firstRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );
        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(middleRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.MOVING_PISTON.defaultBlockState(),
            true
        );
        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(lastRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );

        assertTrue(helper, manager.find(conflicting.id()) == null, "the pushed instance is broken");
        assertTrue(helper, manager.find(moving.id()) == null, "the chain instance is broken");
        assertTrue(helper, rows(ledger, firstCell).isEmpty(), "the pushed old row is released");
        assertEquals(helper, 1, rows(ledger, middleCell).size(), "the incumbent row survives");
        assertEquals(helper, incumbentInstance, rows(ledger, middleCell).get(0).instance());
        assertTrue(helper, rows(ledger, lastCell).isEmpty(), "the chain row is released");
        assertEquals(helper, base + 2, ledger.revision(), "each broken key releases once");
        cleanup(manager, ledger, ownerA, firstSiting);
        cleanup(manager, ledger, ownerB, lastSiting);
        ledger.release(incumbentInstance);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void sameSitingReplacementLeavesADistantPeerAndItsClaimsAlone(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos originRel = BlockPos.ZERO;
        BlockPos peerRel = new BlockPos(4, 0, 0);
        helper.setBlock(originRel, Blocks.STONE);
        LatticePoint origin = point(helper.absolutePos(originRel));
        LatticePoint peerCell = point(helper.absolutePos(peerRel));
        Siting siting = Siting.of(origin, Orientation.identity);
        Siting peerSiting = Siting.of(peerCell, Orientation.identity);
        StructureInstance peer = form(manager, state, level, ownerB, peerSiting, peerCell);
        claim(ledger, peer.id(), peerSiting, peerCell, Sharing.shared);
        cleanup(manager, ledger, state.owner(), siting);
        long base = ledger.revision();
        CommandSourceStack source = stack(helper, helper.absolutePos(originRel), new RecordingSource());

        MatchResult first = SettleCommand.settle(source, state, level, stoneCell, origin);
        if (!(first instanceof MatchResult.Formed)) {
            helper.fail("the first settle did not form: " + first);
            return;
        }
        StructureInstance firstInstance = manager.find(state.owner(), siting);
        if (firstInstance == null) {
            helper.fail("the first settle formed no manager instance");
            return;
        }
        MatchResult second = SettleCommand.settle(source, state, level, stoneCell, origin);
        if (!(second instanceof MatchResult.Formed)) {
            helper.fail("the replacement settle did not form: " + second);
            return;
        }
        StructureInstance replacement = manager.find(state.owner(), siting);
        if (replacement == null) {
            helper.fail("the replacement settle formed no manager instance");
            return;
        }
        assertTrue(helper, replacement.id() != firstInstance.id(), "a re-settle allocates a new instance id");
        assertTrue(helper, manager.find(firstInstance.id()) == null, "the replaced instance is gone");
        assertTrue(helper, manager.find(peer.id()) != null, "the distant peer survives the replacement");
        assertEquals(helper, 1, rows(ledger, origin).size(), "the replacement keeps exactly one row");
        assertEquals(helper, 1, rows(ledger, peerCell).size(), "the peer claim row is untouched");
        assertEquals(helper, peerCell, rows(ledger, peerCell).get(0).cell().point(), "the peer row keeps its cell");
        assertEquals(helper, base + 2, ledger.revision(), "two settles bump the ledger twice");
        assertEquals(helper, replacement.id(), replacement.context().instance(), "the replacement context is bound");
        cleanup(manager, ledger, state.owner(), siting);
        cleanup(manager, ledger, ownerB, peerSiting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100, batch = "unload")
    public void levelUnloadDropsEveryInstanceAndItsClaimRows(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos firstRel = BlockPos.ZERO;
        BlockPos secondRel = new BlockPos(4, 0, 0);
        helper.setBlock(firstRel, Blocks.STONE);
        helper.setBlock(secondRel, Blocks.STONE);
        LatticePoint firstCell = point(helper.absolutePos(firstRel));
        LatticePoint secondCell = point(helper.absolutePos(secondRel));
        Siting firstSiting = Siting.of(firstCell, Orientation.identity);
        Siting secondSiting = Siting.of(secondCell, Orientation.identity);
        StructureInstance first = form(manager, state, level, ownerA, firstSiting, firstCell);
        StructureInstance second = form(manager, state, level, ownerB, secondSiting, secondCell);
        StructureAttempt draft = manager
                .begin(state.world(level), new StructureSpec(ownerC, firstSiting, cellBlueprintId));
        claim(ledger, first.id(), firstSiting, firstCell, Sharing.shared);
        claim(ledger, second.id(), secondSiting, secondCell, Sharing.shared);
        long maxSerial = Math.max(first.id().serial(), second.id().serial());

        ServerState.unload(new LevelEvent.Unload(level));

        assertTrue(helper, manager.find(first.id()) == null, "an unload drops the first instance");
        assertTrue(helper, manager.find(second.id()) == null, "an unload drops the second instance");
        assertTrue(helper, manager.findAt(firstCell).isEmpty(), "the unloaded registry is empty");
        assertThrowsClosed(helper, draft);

        WatchEvents.levelLoaded(new LevelEvent.Load(level));
        assertTrue(helper, manager.findAt(firstCell).isEmpty(), "a load installs an empty registry");
        ClaimLedger reloaded = state.claims(level);
        assertTrue(
            helper,
            reloaded.read(Cuboid.of(firstCell)).rows().isEmpty()
                    && reloaded.read(Cuboid.of(secondCell)).rows().isEmpty(),
            "the unload drops the store and its rows"
        );
        assertEquals(helper, 0L, reloaded.revision(), "a fresh store starts at revision zero");
        StructureAttempt next = manager
                .begin(state.world(level), new StructureSpec(ownerD, secondSiting, cellBlueprintId));
        try {
            assertTrue(helper, next.id().serial() > maxSerial, "the serial continues across unload and load");
        } finally {
            next.abort();
        }
        cleanup(manager, ledger, ownerA, firstSiting);
        cleanup(manager, ledger, ownerB, secondSiting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void placementOutsideAndDuplicatePlacementKeepPerInstanceClaims(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos firstRel = BlockPos.ZERO;
        BlockPos farRel = new BlockPos(4, 0, 0);
        BlockPos outsideRel = new BlockPos(8, 0, 0);
        helper.setBlock(firstRel, Blocks.STONE);
        helper.setBlock(farRel, Blocks.STONE);
        LatticePoint firstCell = point(helper.absolutePos(firstRel));
        LatticePoint farCell = point(helper.absolutePos(farRel));
        Siting firstSiting = Siting.of(firstCell, Orientation.identity);
        Siting farSiting = Siting.of(farCell, Orientation.identity);
        Siting secondSiting = aside(firstSiting, Turn.clockwise);
        StructureInstance first = form(manager, state, level, ownerA, firstSiting, firstCell);
        StructureInstance second = form(manager, state, level, ownerB, secondSiting, firstCell);
        StructureInstance far = form(manager, state, level, ownerC, farSiting, farCell);
        claim(ledger, first.id(), firstSiting, firstCell, Sharing.shared);
        claim(ledger, second.id(), secondSiting, firstCell, Sharing.shared);
        claim(ledger, far.id(), farSiting, farCell, Sharing.shared);
        long base = ledger.revision();

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(outsideRel),
            Blocks.AIR.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
            false
        );
        assertTrue(helper, manager.find(first.id()) != null, "a placement outside every bounds is a no-op");
        assertEquals(helper, base, ledger.revision(), "an outside placement never touches claims");

        BlockPos firstAbs = helper.absolutePos(firstRel);
        CumulusHooks
                .blockChanged(level, firstAbs, Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), false);
        assertTrue(helper, manager.find(first.id()) == null, "the first overlapping instance is invalidated");
        assertTrue(helper, manager.find(second.id()) == null, "the second overlapping instance is invalidated");
        assertTrue(helper, manager.find(far.id()) != null, "the far instance survives");
        assertEquals(helper, 0, rows(ledger, firstCell).size(), "the invalidated rows are released");
        assertEquals(helper, 1, rows(ledger, farCell).size(), "the far row is untouched");
        assertEquals(helper, base + 2, ledger.revision(), "each invalidated key releases once");

        CumulusHooks
                .blockChanged(level, firstAbs, Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), false);
        assertEquals(helper, base + 2, ledger.revision(), "a duplicate placement releases nothing more");
        cleanup(manager, ledger, ownerC, farSiting);
        helper.succeed();
    }

    private static void assertThrowsClosed(GameTestHelper helper, StructureAttempt draft) {
        try {
            draft.commit(CuboidSet.of(Cuboid.of(LatticePoint.origin)));
            helper.fail("an unloaded draft must be closed");
        } catch (IllegalStateException expected) {
            helper.assertTrue(true, "the unload closed the draft");
        }
        try {
            draft.abort();
            helper.fail("an unloaded draft must be closed for abort too");
        } catch (IllegalStateException expected) {
            helper.assertTrue(true, "the unload closed the draft for abort");
        }
    }

    private static Blueprint cellBlueprint(NamespacedId bayId, NamespacedId checkId) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        Bay bay = Bay.of(bayId, CuboidSet.of(bounds), Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        return Blueprint.builder(cellBlueprintId).bounds(bounds)
                .check(Check.of(checkId, bay, Focuses.bay(), Expectations.eachCell(Expectations.block(stone)))).build();
    }

    private static ServerState requireState() {
        ServerState state = ServerState.current();
        if (state == null) {
            throw new GameTestAssertException("the server state is not open");
        }
        return state;
    }

    private static Siting aside(Siting siting, Turn turn) {
        return Siting.of(siting.origin(), Orientation.of(turn, Tilt.level, Flip.none));
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

    private static void claim(
        ClaimLedger ledger,
        InstanceId instance,
        Siting siting,
        LatticePoint cell,
        Sharing sharing
    ) {
        claimRows(ledger, instance, siting, List.of(cell), sharing);
    }

    private static void claimRows(
        ClaimLedger ledger,
        InstanceId instance,
        Siting siting,
        List<LatticePoint> cells,
        Sharing sharing
    ) {
        long expected = ledger.revision();
        List<ClaimRow> rows = new ArrayList<>(cells.size());
        for (LatticePoint cell : cells) {
            rows.add(new ClaimRow(LatticeKey.of(cell), sharing, siting, claimBay, instance));
        }
        ClaimVerdict verdict = ledger.settle(new SettleRequest(instance, rows, expected));
        if (!(verdict instanceof ClaimVerdict.Settled)) {
            throw new GameTestAssertException("the setup settle was rejected: " + verdict);
        }
    }

    private static List<ClaimRow> rows(ClaimLedger ledger, LatticePoint cell) {
        return ledger.read(Cuboid.of(cell)).rows();
    }

    private static void cleanup(StructureManager manager, ClaimLedger ledger, OwnerId owner, Siting siting) {
        StructureInstance instance = manager.find(owner, siting);
        if (instance == null) {
            return;
        }
        manager.onEvent(new StructureEvent.Broken(instance.id()));
        ledger.release(instance.id());
    }

    private static LatticePoint point(BlockPos pos) {
        return LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }

    private static void assertTrue(GameTestHelper helper, boolean condition) {
        helper.assertTrue(condition, "expected true");
    }

    private static void assertTrue(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    private static void assertEquals(GameTestHelper helper, Object actual, Object expected) {
        helper.assertTrue(Objects.equals(actual, expected), "expected " + expected + ", observed " + actual);
    }

    private static void assertEquals(GameTestHelper helper, Object actual, Object expected, String message) {
        helper.assertTrue(
            Objects.equals(actual, expected),
            message + " (expected " + expected + ", observed " + actual + ")"
        );
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
    }
}
