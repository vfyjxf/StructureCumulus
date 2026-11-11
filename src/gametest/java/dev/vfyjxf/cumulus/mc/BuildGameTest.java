package dev.vfyjxf.cumulus.mc;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.build.ApplyReport;
import dev.vfyjxf.cumulus.mc.build.BuildCodes;
import dev.vfyjxf.cumulus.mc.build.BuildExecutor;
import dev.vfyjxf.cumulus.mc.build.BuildHistory;
import dev.vfyjxf.cumulus.mc.build.BuildPolicy;
import dev.vfyjxf.cumulus.mc.build.BuildRequest;
import dev.vfyjxf.cumulus.mc.build.CreativeItemSource;
import dev.vfyjxf.cumulus.mc.build.ExtractionMode;
import dev.vfyjxf.cumulus.mc.build.ItemHandlerSource;
import dev.vfyjxf.cumulus.mc.build.ItemSource;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The build flow end to end on a real level: place, replace, the removal gate, rollback, undo. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class BuildGameTest {

    private static final NamespacedId stoneDocument = NamespacedId.of(Constants.modId, "build_stone");
    private static final NamespacedId pairDocument = NamespacedId.of(Constants.modId, "build_stone_pair");
    private static final NamespacedId airDocument = NamespacedId.of(Constants.modId, "build_air");

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void placesAStoneCellThenReportsFormed(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        LatticePoint origin = origin(helper);
        UUID player = playerId("places");
        ApplyReport first = build(
            helper,
            stoneDocument,
            origin,
            new CreativeItemSource(),
            BuildPolicy.defaults(),
            false,
            player
        );
        assertStatus(helper, first, ApplyReport.Status.committed, "the first build commits");
        helper.assertTrue(first.steps().size() == 1, "one plan step");
        helper.assertTrue(first.steps().get(0).outcome() == ApplyReport.Outcome.applied, "the step applied");
        helper.assertTrue(stoneAt(helper), "the cell holds stone");

        ApplyReport second = build(
            helper,
            stoneDocument,
            origin,
            new CreativeItemSource(),
            BuildPolicy.defaults(),
            false,
            player
        );
        assertStatus(helper, second, ApplyReport.Status.committed, "a formed structure is the empty report");
        helper.assertTrue(second.steps().isEmpty(), "a formed structure applies nothing");
        helper.assertTrue(second.itemsConsumed().isEmpty(), "a formed structure consumes nothing");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void replacesAWrongBlock(GameTestHelper helper) {
        if (state(helper) == null) {
            return;
        }
        helper.getLevel().setBlockAndUpdate(at(helper, 0, 0), Blocks.DIRT.defaultBlockState());
        ApplyReport report = build(
            helper,
            stoneDocument,
            origin(helper),
            new CreativeItemSource(),
            BuildPolicy.defaults(),
            false,
            playerId("replace")
        );
        assertStatus(helper, report, ApplyReport.Status.committed, "the replacement commits");
        helper.assertTrue(stoneAt(helper), "the dirt was replaced with stone");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void theRemovalGateRefusesUntilLifted(GameTestHelper helper) {
        if (state(helper) == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos cell = at(helper, 0, 0);
        level.setBlockAndUpdate(cell, Blocks.DIRT.defaultBlockState());
        UUID player = playerId("removal");
        ApplyReport refused = build(
            helper,
            airDocument,
            origin(helper),
            new CreativeItemSource(),
            BuildPolicy.defaults(),
            false,
            player
        );
        assertStatus(helper, refused, ApplyReport.Status.refused, "removal is refused by default");
        helper.assertTrue(hasCode(refused, BuildCodes.policyRefusal), "the refusal names the removal gate");
        helper.assertTrue(level.getBlockState(cell).is(Blocks.DIRT), "a refusal writes nothing");

        ApplyReport committed = build(
            helper,
            airDocument,
            origin(helper),
            new CreativeItemSource(),
            BuildPolicy.defaults().withAllowRemoval(true),
            false,
            player
        );
        assertStatus(helper, committed, ApplyReport.Status.committed, "the lifted gate allows it");
        helper.assertTrue(level.getBlockState(cell).isAir(), "the dirt was removed");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aFailedCommitRollsBackToThePreImage(GameTestHelper helper) {
        if (state(helper) == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos cell = at(helper, 0, 0);
        ApplyReport report = build(
            helper,
            stoneDocument,
            origin(helper),
            new StarvingItemSource(),
            BuildPolicy.defaults(),
            false,
            playerId("rollback")
        );
        assertStatus(helper, report, ApplyReport.Status.rolledBack, "the failed commit rolled the build back");
        helper.assertTrue(level.getBlockState(cell).isAir(), "no cell kept a write " + live(helper, report));
        helper.assertTrue(hasCode(report, BuildCodes.itemShortage), "the commit-time shortage is reported");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void anItemShortageRefusesBeforeAnyWrite(GameTestHelper helper) {
        if (state(helper) == null) {
            return;
        }
        BlockPos cell = at(helper, 0, 0);
        ApplyReport report = build(
            helper,
            stoneDocument,
            origin(helper),
            new EmptyItemSource(),
            BuildPolicy.defaults(),
            false,
            playerId("shortage")
        );
        assertStatus(helper, report, ApplyReport.Status.refused, "the shortage refuses the run");
        helper.assertTrue(hasCode(report, BuildCodes.itemShortage), "the shortage is reported");
        helper.assertTrue(
            helper.getLevel().getBlockState(cell).isAir(),
            "a refusal writes nothing " + live(helper, report)
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aTwoCellBlueprintFormsAcrossTwoRuns(GameTestHelper helper) {
        if (state(helper) == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos first = at(helper, 0, 0);
        BlockPos second = first.offset(1, 0, 0);
        LatticePoint origin = origin(helper);
        UUID player = playerId("pair");
        ApplyReport firstRun = build(
            helper,
            pairDocument,
            origin,
            new CreativeItemSource(),
            BuildPolicy.defaults(),
            false,
            player
        );
        assertStatus(helper, firstRun, ApplyReport.Status.committed, "the first pair run commits");
        ApplyReport run = build(
            helper,
            pairDocument,
            origin,
            new CreativeItemSource(),
            BuildPolicy.defaults(),
            false,
            player
        );
        assertStatus(helper, run, ApplyReport.Status.committed, "the second pair run commits");
        helper.assertTrue(!hasCode(run, BuildCodes.recheckNotFormed), "the re-check is formed " + codes(run));
        helper.assertTrue(level.getBlockState(first).is(Blocks.STONE), "the first cell holds stone");
        helper.assertTrue(level.getBlockState(second).is(Blocks.STONE), "the second cell holds stone");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void undoRestoresThePreImage(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos cell = at(helper, 0, 0);
        UUID player = playerId("undo");
        ApplyReport report = build(
            helper,
            stoneDocument,
            origin(helper),
            new CreativeItemSource(),
            BuildPolicy.defaults(),
            false,
            player
        );
        assertStatus(helper, report, ApplyReport.Status.committed, "the build commits");
        helper.assertTrue(stoneAt(helper), "the cell holds stone");
        ApplyReport undone = BuildHistory.undo(level, player);
        if (undone == null) {
            helper.fail("the undo history is empty");
            return;
        }
        assertStatus(helper, undone, ApplyReport.Status.committed, "the undo commits");
        helper.assertTrue(
            level.getBlockState(cell).isAir(),
            "the cell is air again, was " + level.getBlockState(cell) + " " + codes(undone)
        );
        helper.assertTrue(BuildHistory.undo(level, player) == null, "a second undo has nothing to restore");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aContainerSourceSuppliesTheItems(GameTestHelper helper) {
        if (state(helper) == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos chestPos = at(helper, 2, 0);
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (!(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            helper.fail("the chest has no block entity");
            return;
        }
        chest.setItem(0, new ItemStack(Items.STONE, 3));
        ItemHandlerSource source = ItemHandlerSource.at(level, chestPos, null);
        if (source == null) {
            helper.fail("the chest exposes no item handler");
            return;
        }
        ApplyReport report = build(
            helper,
            stoneDocument,
            origin(helper),
            source,
            BuildPolicy.defaults(),
            false,
            playerId("container")
        );
        assertStatus(helper, report, ApplyReport.Status.committed, "the container supplies the build");
        helper.assertTrue(stoneAt(helper), "the cell holds stone");
        helper.assertTrue(
            source.available(new ItemStack(Items.STONE)) == 2,
            "the chest paid one stone, has " + source.available(new ItemStack(Items.STONE))
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void theCommandChainBuildsAndUndoes(GameTestHelper helper) {
        if (state(helper) == null) {
            return;
        }
        BlockPos cell = at(helper, 0, 0);
        helper.getLevel().setBlockAndUpdate(cell, Blocks.DIRT.defaultBlockState());
        FakePlayer player = player(helper, playerId("command"));
        CommandSourceStack source = stack(helper, player, player, cell);
        String origin = cell.getX() + " " + cell.getY() + " " + cell.getZ();
        int refused = execute(helper, source, "cumulus build " + airDocument.text() + " " + origin);
        helper.assertTrue(refused == 0, "the default build refuses a removal, exit " + refused);
        int committed = execute(
            helper,
            source,
            "cumulus build " + airDocument.text() + " " + origin + " --allow-removal"
        );
        helper.assertTrue(committed == 1, "the lifted build commits, exit " + committed);
        helper.assertTrue(helper.getLevel().getBlockState(cell).isAir(), "the dirt was removed");
        int undone = execute(helper, source, "cumulus build undo");
        helper.assertTrue(undone == 1, "the undo commits, exit " + undone);
        helper.assertTrue(helper.getLevel().getBlockState(cell).is(Blocks.DIRT), "the undo restored the dirt");
        int empty = execute(helper, source, "cumulus build undo");
        helper.assertTrue(empty == 0, "a second undo has nothing to restore, exit " + empty);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void theSettleFlagChainsTheSingleStructureSettle(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        ServerLevel level = helper.getLevel();
        FakePlayer player = player(helper, playerId("settle_flag"));
        NamespacedId claimDocument = NamespacedId.of(Constants.modId, "build_set_root");

        BlockPos plainCell = at(helper, 2, 0);
        CommandSourceStack plainSource = stack(helper, player, player, plainCell);
        int plain = execute(
            helper,
            plainSource,
            "cumulus build " + claimDocument.text() + " " + point(plainCell) + " with creative"
        );
        helper.assertValueEqual(1, plain, "the plain build commits");
        helper.assertTrue(level.getBlockState(plainCell).is(Blocks.STONE), "the plain build wrote the cell");
        helper.assertValueEqual(
            0,
            state.claims(level).read(cellBox(plainCell)).rows().size(),
            "a plain build takes no claim"
        );

        BlockPos settleCell = at(helper, 0, 0);
        CommandSourceStack settleSource = stack(helper, player, player, settleCell);
        int settled = execute(
            helper,
            settleSource,
            "cumulus build " + claimDocument.text() + " " + point(settleCell) + " with creative --settle"
        );
        helper.assertValueEqual(1, settled, "the settle flag forms the structure");
        helper.assertTrue(level.getBlockState(settleCell).is(Blocks.STONE), "the settling build wrote the cell");
        helper.assertValueEqual(
            1,
            state.claims(level).read(cellBox(settleCell)).rows().size(),
            "the settle pass wrote the claim row"
        );
        helper.succeed();
    }

    private static String point(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static Cuboid cellBox(BlockPos pos) {
        return Cuboid.of(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    private static ServerState state(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
            throw new IllegalStateException("server state is not open");
        }
        return state;
    }

    /** The template's first cell: the structure block sits one block below the test origin. */
    private static BlockPos at(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 1, z));
    }

    private static LatticePoint origin(GameTestHelper helper) {
        BlockPos origin = at(helper, 0, 0);
        return LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
    }

    private static ApplyReport build(
        GameTestHelper helper,
        NamespacedId document,
        LatticePoint origin,
        ItemSource items,
        BuildPolicy policy,
        boolean dry,
        UUID player
    ) {
        ServerState state = ServerState.current();
        if (state == null) {
            throw new IllegalStateException("server state is not open");
        }
        ServerLevel level = helper.getLevel();
        BuildExecutor executor = new BuildExecutor(state.capture(level), state.kernel(level), state.owner(), policy);
        return executor.run(
            new BuildRequest(
                document,
                Siting.of(origin, Orientation.identity),
                items,
                policy,
                dry,
                null,
                player(helper, player)
            )
        );
    }

    private static void assertStatus(
        GameTestHelper helper,
        ApplyReport report,
        ApplyReport.Status expected,
        String message
    ) {
        if (report.status() != expected) {
            helper.fail(message + ": expected " + expected + ", was " + report.status() + " " + codes(report));
        }
    }

    private static boolean hasCode(ApplyReport report, FindingCode code) {
        return report.findings().findings().stream().anyMatch(finding -> finding.code().equals(code));
    }

    private static String codes(ApplyReport report) {
        List<String> text = new ArrayList<>();
        for (Finding finding : report.findings().findings()) {
            text.add(finding.code().text() + " '" + finding.message() + "'");
        }
        return text.toString();
    }

    private static String live(GameTestHelper helper, ApplyReport report) {
        return "state " + helper.getLevel().getBlockState(at(helper, 0, 0)) + " " + codes(report);
    }

    private static boolean stoneAt(GameTestHelper helper) {
        return helper.getLevel().getBlockState(at(helper, 0, 0)).is(Blocks.STONE);
    }

    private static UUID playerId(String tag) {
        return UUID.nameUUIDFromBytes(("cumulus-build-gametest/" + tag).getBytes(StandardCharsets.UTF_8));
    }

    private static FakePlayer player(GameTestHelper helper, UUID id) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "cumulus_gametest"));
    }

    private static CommandSourceStack stack(GameTestHelper helper, CommandSource source, Entity entity, BlockPos at) {
        return new CommandSourceStack(
            source,
            Vec3.atLowerCornerOf(at),
            Vec2.ZERO,
            helper.getLevel(),
            4,
            "gametest",
            Component.literal("gametest"),
            helper.getLevel().getServer(),
            entity
        );
    }

    private static int execute(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException failure) {
            helper.fail("the command did not parse: " + failure.getMessage());
            return 0;
        }
    }

    private static final class EmptyItemSource implements ItemSource {

        @Override
        public NamespacedId id() {
            return NamespacedId.of(Constants.modId, "gametest_empty");
        }

        @Override
        public int available(ItemStack template) {
            return 0;
        }

        @Override
        public ItemStack extract(ItemStack template, int count, ExtractionMode mode) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insert(ItemStack stack, ExtractionMode mode) {
            return ItemStack.EMPTY;
        }
    }

    /** Passes the dry run and then answers nothing, so the commit's own extraction runs dry. */
    private static final class StarvingItemSource implements ItemSource {

        @Override
        public NamespacedId id() {
            return NamespacedId.of(Constants.modId, "gametest_starving");
        }

        @Override
        public int available(ItemStack template) {
            return 64;
        }

        @Override
        public ItemStack extract(ItemStack template, int count, ExtractionMode mode) {
            return mode == ExtractionMode.simulate ? template.copyWithCount(count) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insert(ItemStack stack, ExtractionMode mode) {
            return ItemStack.EMPTY;
        }
    }
}
