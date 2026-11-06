package dev.vfyjxf.cumulus.mc;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
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
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.function.Consumer;

/**
 * The observation funnel (D-47) end to end: real writes — /fill, fire, fluids, pistons, a
 * protected break — drive instance removal and claim release without any synthetic event calls.
 */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class ObservationFunnelGameTest {

    private static final OwnerId owner = OwnerId.of(id("funnel_owner"));
    private static final NamespacedId blueprint = id("funnel_blueprint");
    private static final NamespacedId claimBay = id("funnel_claim_bay");

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aFillCommandBreaksAStructure(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos rel = new BlockPos(0, 1, 0);
        helper.setBlock(rel, Blocks.STONE);
        BlockPos abs = helper.absolutePos(rel);
        LatticePoint cell = point(abs);
        Siting siting = Siting.of(cell, Orientation.identity);
        StructureInstance instance = form(manager, state, level, siting, cell);
        claim(ledger, instance.id(), siting, cell);

        CommandSourceStack source = new CommandSourceStack(
            CommandSource.NULL,
            Vec3.atLowerCornerOf(abs),
            Vec2.ZERO,
            level,
            4,
            "gametest",
            Component.literal("gametest"),
            level.getServer(),
            null
        );
        String coordinates = abs.getX() + " " + abs.getY() + " " + abs.getZ();
        execute(helper, source, "fill " + coordinates + " " + coordinates + " minecraft:air");

        helper.assertTrue(manager.find(instance.id()) == null, "a /fill write breaks the structure");
        helper.assertTrue(rows(ledger, cell).isEmpty(), "a /fill releases the claims");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 300)
    public void fireThatBurnsAStructureBreaksIt(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos rel = new BlockPos(0, 1, 0);
        helper.setBlock(rel, Blocks.WHITE_WOOL);
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.NETHERRACK);
        LatticePoint cell = point(helper.absolutePos(rel));
        Siting siting = Siting.of(cell, Orientation.identity);
        StructureInstance instance = form(manager, state, level, siting, cell);
        claim(ledger, instance.id(), siting, cell);

        helper.setBlock(rel, Blocks.FIRE);
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(manager.find(instance.id()) == null, "fire burning a structure cell breaks it");
                helper.assertTrue(rows(ledger, cell).isEmpty(), "the burned structure releases its claims");
                helper.succeed();
            } finally {
                helper.setBlock(rel, Blocks.AIR);
                helper.setBlock(new BlockPos(0, 0, 0), Blocks.AIR);
            }
        });
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 300)
    public void aFluidThatWashesAStructureAwayBreaksIt(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos rel = new BlockPos(0, 1, 0);
        helper.setBlock(rel, Blocks.TORCH);
        LatticePoint cell = point(helper.absolutePos(rel));
        Siting siting = Siting.of(cell, Orientation.identity);
        StructureInstance instance = form(manager, state, level, siting, cell);
        claim(ledger, instance.id(), siting, cell);

        helper.setBlock(new BlockPos(0, 2, 0), Blocks.WATER);
        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(manager.find(instance.id()) == null, "water washing the structure away breaks it");
                helper.assertTrue(rows(ledger, cell).isEmpty(), "the washed-away structure releases its claims");
                helper.succeed();
            } finally {
                for (BlockPos pos : List.of(
                    new BlockPos(0, 3, 0),
                    new BlockPos(0, 2, 0),
                    rel,
                    new BlockPos(1, 1, 0),
                    new BlockPos(-1, 1, 0),
                    new BlockPos(0, 1, 1),
                    new BlockPos(0, 1, -1)
                )) {
                    helper.setBlock(pos, Blocks.AIR);
                }
            }
        });
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aCancelledBreakLeavesTheInstanceAlone(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos rel = new BlockPos(0, 1, 0);
        helper.setBlock(rel, Blocks.STONE);
        BlockPos abs = helper.absolutePos(rel);
        LatticePoint cell = point(abs);
        Siting siting = Siting.of(cell, Orientation.identity);
        StructureInstance instance = form(manager, state, level, siting, cell);
        claim(ledger, instance.id(), siting, cell);

        Consumer<BlockEvent.BreakEvent> guard = event -> {
            if (event.getPos().equals(abs)) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, guard);
        try {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.gameMode.destroyBlock(abs);
            helper.assertValueEqual(
                Blocks.STONE,
                helper.getBlockState(rel).getBlock(),
                "a cancelled break changes nothing"
            );
            helper.assertTrue(manager.find(instance.id()) != null, "a cancelled break keeps the instance");
            helper.assertTrue(rows(ledger, cell).size() == 1, "a cancelled break keeps the claims");
        } finally {
            NeoForge.EVENT_BUS.unregister(guard);
        }
        cleanup(manager, ledger, owner, siting);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aStickyPistonThatPullsAStructureInvalidatesIt(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos pistonRel = new BlockPos(0, 1, 0);
        BlockPos stoneRel = new BlockPos(1, 1, 0);
        BlockPos pulledRel = new BlockPos(2, 1, 0);
        BlockPos powerRel = new BlockPos(0, 0, 0);
        helper.setBlock(
            pistonRel,
            Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST)
        );
        helper.setBlock(stoneRel, Blocks.STONE);
        helper.setBlock(powerRel, Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(
                Blocks.STONE,
                helper.getBlockState(pulledRel).getBlock(),
                "the extension pushed the stone"
            );
            LatticePoint pulled = point(helper.absolutePos(pulledRel));
            Siting siting = Siting.of(pulled, Orientation.identity);
            StructureInstance instance = form(manager, state, level, siting, pulled);
            claim(ledger, instance.id(), siting, pulled);

            helper.setBlock(powerRel, Blocks.AIR);
            helper.runAfterDelay(10, () -> {
                helper.assertValueEqual(
                    Blocks.STONE,
                    helper.getBlockState(stoneRel).getBlock(),
                    "the retraction pulled the stone"
                );
                helper.assertTrue(manager.find(instance.id()) == null, "a pulled structure is dropped, not translated");
                helper.assertTrue(rows(ledger, pulled).isEmpty(), "the pulled structure releases its claims");
                helper.assertTrue(manager.findAt(pulled).isEmpty(), "no instance is translated onto the pulled cell");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aNormalPistonRetractMovesNothing(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos pistonRel = new BlockPos(0, 1, 0);
        BlockPos stoneRel = new BlockPos(2, 1, 0);
        BlockPos powerRel = new BlockPos(0, 0, 0);
        helper.setBlock(pistonRel, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(stoneRel, Blocks.STONE);
        helper.setBlock(powerRel, Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(10, () -> {
            LatticePoint cell = point(helper.absolutePos(stoneRel));
            Siting siting = Siting.of(cell, Orientation.identity);
            StructureInstance instance = form(manager, state, level, siting, cell);
            claim(ledger, instance.id(), siting, cell);

            helper.setBlock(powerRel, Blocks.AIR);
            helper.runAfterDelay(10, () -> {
                try {
                    helper.assertValueEqual(
                        Blocks.STONE,
                        helper.getBlockState(stoneRel).getBlock(),
                        "a plain retraction moves nothing"
                    );
                    helper.assertTrue(manager.find(instance.id()) != null, "a plain retraction keeps the instance");
                    helper.assertTrue(rows(ledger, cell).size() == 1, "a plain retraction keeps the claims");
                    helper.succeed();
                } finally {
                    cleanup(manager, ledger, owner, siting);
                }
            });
        });
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aPistonThatPushesAForeignBlockInsideInvalidates(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos pistonRel = new BlockPos(0, 1, 0);
        BlockPos dirtRel = new BlockPos(1, 1, 0);
        BlockPos stoneRel = new BlockPos(2, 1, 0);
        BlockPos powerRel = new BlockPos(0, 0, 0);
        helper.setBlock(pistonRel, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(dirtRel, Blocks.DIRT);
        helper.setBlock(stoneRel, Blocks.STONE);
        LatticePoint cell = point(helper.absolutePos(stoneRel));
        Siting siting = Siting.of(cell, Orientation.identity);
        StructureInstance instance = form(manager, state, level, siting, cell);
        claim(ledger, instance.id(), siting, cell);

        helper.setBlock(powerRel, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(
                Blocks.DIRT,
                helper.getBlockState(stoneRel).getBlock(),
                "the foreign block landed inside"
            );
            helper.assertTrue(manager.find(instance.id()) == null, "a foreign write inside the bounds invalidates");
            helper.assertTrue(rows(ledger, cell).isEmpty(), "the invalidated structure releases its claims");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aBlockEntityChangeDoesNotInvalidate(GameTestHelper helper) {
        ServerState state = requireState();
        ServerLevel level = helper.getLevel();
        StructureManager manager = state.structures(level);
        ClaimLedger ledger = state.claims(level);
        BlockPos rel = new BlockPos(0, 1, 0);
        helper.setBlock(rel, Blocks.CHEST);
        LatticePoint cell = point(helper.absolutePos(rel));
        Siting siting = Siting.of(cell, Orientation.identity);
        StructureInstance instance = form(manager, state, level, siting, cell);
        claim(ledger, instance.id(), siting, cell);

        ChestBlockEntity chest = helper.getBlockEntity(rel);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));

        helper.assertTrue(manager.find(instance.id()) != null, "a content change keeps the instance (D-41)");
        helper.assertTrue(rows(ledger, cell).size() == 1, "a content change keeps the claims");
        cleanup(manager, ledger, owner, siting);
        helper.setBlock(rel, Blocks.AIR);
        helper.succeed();
    }

    private static ServerState requireState() {
        ServerState state = ServerState.current();
        if (state == null) {
            throw new GameTestAssertException("server state is not open");
        }
        return state;
    }

    private static StructureInstance form(
        StructureManager manager,
        ServerState state,
        ServerLevel level,
        Siting siting,
        LatticePoint cell
    ) {
        StructureAttempt attempt = manager.begin(state.world(level), new StructureSpec(owner, siting, blueprint));
        return attempt.commit(CuboidSet.of(Cuboid.of(cell)));
    }

    private static void claim(ClaimLedger ledger, InstanceId instance, Siting siting, LatticePoint cell) {
        ClaimRow row = new ClaimRow(LatticeKey.of(cell), Sharing.exclusive, siting, claimBay, instance);
        ClaimVerdict verdict = ledger.settle(new SettleRequest(instance, List.of(row), ledger.revision()));
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

    private static int execute(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException failure) {
            helper.fail("the command did not parse: " + failure.getMessage());
            return 0;
        }
    }

    private static LatticePoint point(BlockPos pos) {
        return LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }
}
