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
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.engine.MatchResult;
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
import java.util.Objects;

/**
 * Real-level adversarial checks for the host lifecycle: the per-level manager and the claim ledger
 * must stay consistent when the observation funnel reports writes inside, outside and across
 * structure bounds, including moving (piston) writes, and through load/unload.
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

    private static final Blueprint stoneCell = cellBlueprint(stoneBayId, stoneCheckId, stone);
    private static final Blueprint diamondCell = cellBlueprint(diamondBayId, diamondCheckId, diamond);

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void breakOverlappingStructuresReleasesEveryClaimOnce(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        helper.setBlock(BlockPos.ZERO, Blocks.STONE);
        LatticePoint point = point(helper.absolutePos(BlockPos.ZERO));
        Siting siting = Siting.of(point, Orientation.identity);
        Siting secondSiting = aside(siting);
        StructureInstance first = form(manager, state, level, ownerA, siting, point);
        StructureInstance second = form(manager, state, level, ownerB, secondSiting, point);
        long base = ledger.revision();
        claim(ledger, first.id(), siting, point, Sharing.shared);
        claim(ledger, second.id(), secondSiting, point, Sharing.shared);
        long settled = ledger.revision();
        assertEquals(helper, base + 2, settled, "two shared rows settle in two revisions");
        assertEquals(helper, 2, rows(ledger, point).size(), "two shared owners hold the cell");

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(BlockPos.ZERO),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            false
        );

        assertTrue(helper, manager.find(first.id()) == null, "the first overlapping instance is broken");
        assertTrue(helper, manager.find(second.id()) == null, "the second overlapping instance is broken");
        assertEquals(helper, settled + 2, ledger.revision(), "each released owner bumps the revision once");
        assertEquals(helper, 0, rows(ledger, point).size(), "every overlapping claim row is released");

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(BlockPos.ZERO),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            false
        );
        assertEquals(helper, settled + 2, ledger.revision(), "a duplicate break releases nothing more");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void destructionBreaksOnlyTheStructuresAtTheAffectedCells(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
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
        claim(ledger, first.id(), firstSiting, firstPoint, Sharing.exclusive);
        claim(ledger, second.id(), secondSiting, secondPoint, Sharing.exclusive);
        long settled = ledger.revision();

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(firstRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            false
        );

        assertTrue(helper, manager.find(first.id()) == null, "the affected structure is broken");
        assertTrue(helper, manager.find(second.id()) != null, "the far structure survives");
        assertEquals(helper, settled + 1, ledger.revision(), "one broken structure releases one key");
        assertEquals(helper, 0, rows(ledger, firstPoint).size(), "the affected claims are released");
        assertEquals(helper, 1, rows(ledger, secondPoint).size(), "the far claims stay");
        cleanup(manager, ledger, ownerB, secondSiting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void placementInvalidatesOnlyTheStructureAtThePosition(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos firstRel = BlockPos.ZERO;
        BlockPos secondRel = new BlockPos(4, 0, 0);
        LatticePoint firstPoint = point(helper.absolutePos(firstRel));
        LatticePoint secondPoint = point(helper.absolutePos(secondRel));
        Siting firstSiting = Siting.of(firstPoint, Orientation.identity);
        Siting secondSiting = Siting.of(secondPoint, Orientation.identity);
        StructureInstance first = form(manager, state, level, ownerA, firstSiting, firstPoint);
        StructureInstance second = form(manager, state, level, ownerB, secondSiting, secondPoint);
        claim(ledger, first.id(), firstSiting, firstPoint, Sharing.exclusive);
        claim(ledger, second.id(), secondSiting, secondPoint, Sharing.exclusive);
        long settled = ledger.revision();

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(firstRel),
            Blocks.AIR.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
            false
        );

        assertTrue(helper, manager.find(first.id()) == null, "a placement invalidates the structure");
        assertTrue(helper, manager.find(second.id()) != null, "a far structure is not invalidated");
        assertEquals(helper, settled + 1, ledger.revision(), "an invalidation releases its key once");
        assertEquals(helper, 0, rows(ledger, firstPoint).size(), "the affected claims are released");
        assertEquals(helper, 1, rows(ledger, secondPoint).size(), "the far claims stay");

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(firstRel),
            Blocks.AIR.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
            false
        );
        assertEquals(helper, settled + 1, ledger.revision(), "a duplicate placement is idempotent");
        assertTrue(helper, manager.find(second.id()) != null, "the unrelated structure is still formed");
        cleanup(manager, ledger, ownerB, secondSiting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void settleCommandReplacesSameSitingAndAbortsAFailedAttempt(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
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
    public void movingWriteBreaksTheStructureAndReleasesEveryClaimRow(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos pushedRel = new BlockPos(1, 1, 0);
        BlockPos stillRel = new BlockPos(1, 2, 0);
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
        claimRows(ledger, instance.id(), siting, List.of(pushed, still), Sharing.exclusive);
        long beforeMove = ledger.revision();
        assertEquals(helper, 2, rows(ledger, pushed).size() + rows(ledger, still).size(), "two claim rows");

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(pushedRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );

        assertTrue(helper, manager.find(instance.id()) == null, "a moving write breaks the structure");
        assertEquals(helper, beforeMove + 1, ledger.revision(), "a break releases the key once");
        assertEquals(helper, 0, rows(ledger, pushed).size(), "the pushed claim is released");
        assertEquals(helper, 0, rows(ledger, still).size(), "the stationary claim is released");
        cleanup(manager, ledger, ownerA, siting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void movingWriteKeepsAnUnformedIncumbentClaimRow(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos stoneRel = new BlockPos(1, 1, 0);
        BlockPos targetRel = new BlockPos(2, 1, 0);
        helper.setBlock(stoneRel, Blocks.STONE);
        helper.setBlock(targetRel, Blocks.STONE);
        LatticePoint origin = point(helper.absolutePos(stoneRel));
        LatticePoint target = point(helper.absolutePos(targetRel));
        Siting siting = Siting.of(origin, Orientation.identity);
        Siting incumbents = Siting.of(target, Orientation.identity);
        StructureInstance instance = form(manager, state, level, ownerA, siting, origin);
        InstanceId incumbentInstance = new InstanceId(state.world(level), 1_000_000L);
        claim(ledger, instance.id(), siting, origin, Sharing.exclusive);
        claim(ledger, incumbentInstance, incumbents, target, Sharing.exclusive);
        long beforeMove = ledger.revision();
        assertEquals(helper, 1, rows(ledger, target).size(), "the incumbent row exists");

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(stoneRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );
        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(targetRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.MOVING_PISTON.defaultBlockState(),
            true
        );

        assertTrue(helper, manager.find(instance.id()) == null, "the pushed structure is broken");
        assertEquals(helper, beforeMove + 1, ledger.revision(), "only the break release bumps the revision");
        assertEquals(helper, 0, rows(ledger, origin).size(), "the old claim row is released");
        assertEquals(helper, 1, rows(ledger, target).size(), "the incumbent row survives");
        assertEquals(helper, incumbentInstance, rows(ledger, target).get(0).instance(), "the incumbent is untouched");
        cleanup(manager, ledger, ownerA, siting);
        ledger.release(incumbentInstance);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100, batch = "unload")
    public void levelUnloadDropsInstancesAndTheirClaimRows(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos rel = new BlockPos(0, 1, 0);
        helper.setBlock(rel, Blocks.STONE);
        LatticePoint point = point(helper.absolutePos(rel));
        Siting siting = Siting.of(point, Orientation.identity);
        StructureInstance instance = form(manager, state, level, ownerA, siting, point);
        claim(ledger, instance.id(), siting, point, Sharing.exclusive);
        long beforeUnload = ledger.revision();

        ServerState.unload(new LevelEvent.Unload(level));

        assertTrue(helper, manager.find(instance.id()) == null, "an unload drops the instance");
        assertTrue(helper, manager.findAt(point).isEmpty(), "an unload empties the registry");
        state.openLevel(level);
        ClaimLedger reloaded = state.claims(level);
        helper.assertTrue(
            reloaded.read(Cuboid.of(point)).rows().isEmpty() && reloaded.revision() == 0L,
            "a world unload drops the store and its rows: observed rows="
                    + reloaded.read(Cuboid.of(point)).rows().size() + ", revision=" + reloaded.revision() + ", before="
                    + beforeUnload
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void writesOutsideEveryStructureLeaveTheManagerAndLedgerUntouched(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos structureRel = new BlockPos(0, 1, 0);
        BlockPos outsideRel = new BlockPos(6, 1, 0);
        helper.setBlock(structureRel, Blocks.STONE);
        LatticePoint point = point(helper.absolutePos(structureRel));
        Siting siting = Siting.of(point, Orientation.identity);
        StructureInstance instance = form(manager, state, level, ownerA, siting, point);
        claim(ledger, instance.id(), siting, point, Sharing.exclusive);
        long base = ledger.revision();

        BlockPos outside = helper.absolutePos(outsideRel);
        CumulusHooks
                .blockChanged(level, outside, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
        CumulusHooks
                .blockChanged(level, outside, Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), false);
        CumulusHooks
                .blockChanged(level, outside, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), true);

        assertTrue(helper, manager.find(instance.id()) != null, "a write outside the bounds never removes a structure");
        assertTrue(helper, manager.findAt(point).size() == 1, "the instance is still found at its bounds");
        assertEquals(helper, base, ledger.revision(), "an outside write never touches the ledger");
        assertEquals(helper, 1, rows(ledger, point).size(), "the claim row is untouched");
        cleanup(manager, ledger, ownerA, siting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void pushIntoAnOccupiedCellInvalidatesBothInstances(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos stoneRel = new BlockPos(1, 1, 0);
        BlockPos targetRel = new BlockPos(2, 1, 0);
        helper.setBlock(stoneRel, Blocks.STONE);
        helper.setBlock(targetRel, Blocks.STONE);
        LatticePoint origin = point(helper.absolutePos(stoneRel));
        LatticePoint target = point(helper.absolutePos(targetRel));
        Siting siting = Siting.of(origin, Orientation.identity);
        Siting occupied = Siting.of(target, Orientation.identity);
        StructureInstance moving = form(manager, state, level, ownerA, siting, origin);
        StructureInstance occupant = form(manager, state, level, ownerB, occupied, target);
        claim(ledger, moving.id(), siting, origin, Sharing.exclusive);
        claim(ledger, occupant.id(), occupied, target, Sharing.exclusive);
        long before = ledger.revision();

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(stoneRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );
        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(targetRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.MOVING_PISTON.defaultBlockState(),
            true
        );

        assertTrue(helper, manager.find(moving.id()) == null, "the pushed instance is broken");
        assertTrue(helper, manager.find(occupant.id()) == null, "the occupant is invalidated by the incoming block");
        assertEquals(helper, 0, rows(ledger, origin).size(), "the pushed instance's rows are released");
        assertEquals(helper, 0, rows(ledger, target).size(), "the occupant's rows are released");
        assertEquals(helper, before + 2, ledger.revision(), "each dropped key bumps the revision once");

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(stoneRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );
        assertEquals(helper, before + 2, ledger.revision(), "a duplicate pass releases nothing more");
        cleanup(manager, ledger, ownerA, siting);
        cleanup(manager, ledger, ownerB, occupied);
        helper.succeed();
    }

    /**
     * The whole claim key is released on a break, including rows on cells the write never touched
     * (D-38); an untouched occupant at another cell keeps its rows.
     */
    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void breakReleasesClaimRowsOutsideTheBoundsToo(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos stoneRel = new BlockPos(1, 1, 0);
        BlockPos extraRel = new BlockPos(1, 2, 0);
        BlockPos targetRel = new BlockPos(2, 1, 0);
        helper.setBlock(stoneRel, Blocks.STONE);
        helper.setBlock(targetRel, Blocks.STONE);
        LatticePoint origin = point(helper.absolutePos(stoneRel));
        LatticePoint extraCell = point(helper.absolutePos(extraRel));
        LatticePoint target = point(helper.absolutePos(targetRel));
        Siting siting = Siting.of(origin, Orientation.identity);
        Siting occupying = Siting.of(target, Orientation.identity);
        StructureInstance stale = form(manager, state, level, ownerA, siting, origin);
        StructureInstance occupant = form(manager, state, level, ownerB, occupying, target);
        claimRows(ledger, stale.id(), siting, List.of(origin, extraCell), Sharing.shared);
        claim(ledger, occupant.id(), occupying, target, Sharing.exclusive);
        assertEquals(
            helper,
            2,
            rows(ledger, origin).size() + rows(ledger, extraCell).size(),
            "the key has two rows before the pass"
        );
        long before = ledger.revision();

        CumulusHooks.blockChanged(
            level,
            helper.absolutePos(stoneRel),
            Blocks.STONE.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            true
        );

        boolean staleInstanceLeft = manager.find(stale.id()) != null;
        int oldRows = rows(ledger, origin).size();
        int extraRows = rows(ledger, extraCell).size();
        int targetRows = rows(ledger, target).size();
        long revisionDelta = ledger.revision() - before;
        boolean occupantKept = manager.find(occupant.id()) != null;
        helper.assertTrue(
            !staleInstanceLeft
                    && oldRows == 0
                    && extraRows == 0
                    && targetRows == 1
                    && revisionDelta == 1L
                    && occupantKept,
            "a break must drop the instance and release its whole key: staleInstance=" + staleInstanceLeft
                    + ", oldRows=" + oldRows + ", extraRows=" + extraRows + ", occupantRows=" + targetRows
                    + ", revisionDelta=" + revisionDelta + ", occupantKept=" + occupantKept
        );
        cleanup(manager, ledger, ownerA, siting);
        cleanup(manager, ledger, ownerB, occupying);
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

    private static Siting aside(Siting siting) {
        return Siting.of(siting.origin(), Orientation.of(Turn.clockwise, Tilt.level, Flip.none));
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
    }
}
