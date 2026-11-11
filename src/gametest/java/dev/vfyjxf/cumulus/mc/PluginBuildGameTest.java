package dev.vfyjxf.cumulus.mc;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.remedy.Edit;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.build.ApplyReport;
import dev.vfyjxf.cumulus.mc.build.BlockPlacement;
import dev.vfyjxf.cumulus.mc.build.BuildCodes;
import dev.vfyjxf.cumulus.mc.build.BuildExecutor;
import dev.vfyjxf.cumulus.mc.build.BuildPolicy;
import dev.vfyjxf.cumulus.mc.build.BuildRequest;
import dev.vfyjxf.cumulus.mc.build.CreativeItemSource;
import dev.vfyjxf.cumulus.mc.build.ExtractionMode;
import dev.vfyjxf.cumulus.mc.build.ItemRequirement;
import dev.vfyjxf.cumulus.mc.build.ItemSource;
import dev.vfyjxf.cumulus.mc.build.ItemSourceProvider;
import dev.vfyjxf.cumulus.mc.build.ItemSourceRegistrar;
import dev.vfyjxf.cumulus.mc.build.ItemSources;
import dev.vfyjxf.cumulus.mc.build.MaterializeContext;
import dev.vfyjxf.cumulus.mc.build.MaterializedEdit;
import dev.vfyjxf.cumulus.mc.build.Materializer;
import dev.vfyjxf.cumulus.mc.build.MaterializerRegistrar;
import dev.vfyjxf.cumulus.mc.build.Materializers;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** The plugin extension points on a real level: the materializer chain and a registered item source. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class PluginBuildGameTest {

    private static final NamespacedId chestDocument = NamespacedId.of(Constants.modId, "build_chest");
    private static final NamespacedId stoneDocument = NamespacedId.of(Constants.modId, "build_stone");
    private static final NamespacedId chestMaterializer = NamespacedId.of("test", "chest_payload");
    private static final NamespacedId countingSource = NamespacedId.of("test", "counting");

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aPluginMaterializerLowersTheBuild(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        MaterializerRegistrar registrar = MaterializerRegistrar.builtin();
        registrar.materializer(new ChestMaterializer());
        withTables(state, registrar.materializers(), ItemSources.empty(), () -> {
            FakePlayer player = player(helper, playerId("materializer"));
            RecordingSource recording = new RecordingSource();
            CommandSourceStack source = stack(helper, recording, player, at(helper));
            int exit = execute(
                helper,
                source,
                "cumulus build " + chestDocument + " " + arguments(helper) + " with creative"
            );
            helper.assertValueEqual(1, exit, "the build commits " + recording.text());
            BlockState placed = helper.getLevel().getBlockState(at(helper));
            helper.assertTrue(placed.is(Blocks.CHEST), "the plugin materializer placed a chest, was " + placed);
            ChestBlockEntity chest = chestAt(helper);
            if (chest == null) {
                helper.fail("the chest block entity is present");
                return;
            }
            helper.assertTrue(chest.hasCustomName(), "the block entity payload reached the world");
            Component customName = chest.getCustomName();
            helper.assertTrue(
                customName != null && customName.getString().equals("plugin chest"),
                "the payload names the chest: " + customName
            );
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aRegisteredItemSourceFeedsTheBuild(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        CountingProvider provider = new CountingProvider(1);
        withTables(state, Materializers.builtin(), tables(provider), () -> {
            FakePlayer player = player(helper, playerId("source"));
            RecordingSource recording = new RecordingSource();
            CommandSourceStack source = stack(helper, recording, player, at(helper));
            int exit = execute(
                helper,
                source,
                "cumulus build " + stoneDocument + " " + arguments(helper) + " with " + countingSource.text()
            );
            helper.assertValueEqual(1, exit, "the build commits " + recording.text());
            helper.assertTrue(
                helper.getLevel().getBlockState(at(helper)).is(Blocks.STONE),
                "the cell holds the stone the registered source provided"
            );
            helper.assertValueEqual(1, provider.created, "the command resolved the kind through the table");
            helper.assertTrue(provider.source.executed > 0, "the build executed the registered source's extraction");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void anUnknownSourceIdListsTheAvailableKinds(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        withTables(state, Materializers.builtin(), tables(new CountingProvider(1)), () -> {
            FakePlayer player = player(helper, playerId("unknown_source"));
            RecordingSource recording = new RecordingSource();
            CommandSourceStack source = stack(helper, recording, player, at(helper));
            int exit = execute(
                helper,
                source,
                "cumulus build " + stoneDocument + " " + arguments(helper) + " with test:absent"
            );
            helper.assertValueEqual(0, exit, "an unknown kind refuses " + recording.text());
            helper.assertTrue(
                recording.text().contains("available: creative, player, container, " + countingSource.text()),
                "the refusal lists the built-in forms and the registered kinds: " + recording.text()
            );
            helper.assertTrue(helper.getLevel().getBlockState(at(helper)).isAir(), "the refusal writes nothing");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aThrowingMaterializerRefusesTheRun(GameTestHelper helper) {
        ServerState state = state(helper);
        if (state == null) {
            return;
        }
        MaterializerRegistrar registrar = MaterializerRegistrar.builtin();
        registrar.materializer(new ThrowingMaterializer());
        withTables(state, registrar.materializers(), ItemSources.empty(), () -> {
            ServerLevel level = helper.getLevel();
            ApplyReport report = new BuildExecutor(
                state.capture(level),
                state.kernel(level),
                state.owner(),
                BuildPolicy.defaults(),
                state.materializers()
            ).run(
                new BuildRequest(
                    stoneDocument,
                    Siting.of(origin(helper), Orientation.identity),
                    new CreativeItemSource(),
                    BuildPolicy.defaults(),
                    false,
                    null,
                    player(helper, playerId("throwing"))
                )
            );
            helper.assertTrue(
                report.status() == ApplyReport.Status.refused,
                "a throwing materializer refuses the run: " + report.status() + " " + codes(report)
            );
            helper.assertTrue(hasCode(report, BuildCodes.materializationFailure), "the refusal is SC-BLD-0004");
            helper.assertTrue(
                codes(report).contains("test:exploding"),
                "the finding names the throwing materializer: " + codes(report)
            );
            helper.assertTrue(level.getBlockState(at(helper)).isAir(), "the refusal writes nothing");
        });
        helper.succeed();
    }

    private static void withTables(
        ServerState state,
        Materializers materializers,
        ItemSources itemSources,
        Runnable body
    ) {
        Materializers previousMaterializers = state.materializers();
        ItemSources previousSources = state.itemSources();
        state.installPluginTables(materializers, itemSources);
        try {
            body.run();
        } finally {
            state.installPluginTables(previousMaterializers, previousSources);
        }
    }

    private static ItemSources tables(ItemSourceProvider provider) {
        ItemSourceRegistrar registrar = ItemSourceRegistrar.empty();
        registrar.itemSource(provider);
        return registrar.itemSources();
    }

    private static @Nullable ServerState state(GameTestHelper helper) {
        ServerState state = ServerState.current();
        if (state == null) {
            helper.fail("server state is not open");
        }
        return state;
    }

    /** The template's first cell: the structure block sits one block below the test origin. */
    private static BlockPos at(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(0, 1, 0));
    }

    private static LatticePoint origin(GameTestHelper helper) {
        BlockPos origin = at(helper);
        return LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
    }

    private static String arguments(GameTestHelper helper) {
        BlockPos point = at(helper);
        return point.getX() + " " + point.getY() + " " + point.getZ();
    }

    private static @Nullable ChestBlockEntity chestAt(GameTestHelper helper) {
        BlockPos pos = at(helper);
        return helper.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity chest ? chest : null;
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

    private static UUID playerId(String tag) {
        return UUID.nameUUIDFromBytes(("cumulus-plugin-gametest/" + tag).getBytes(StandardCharsets.UTF_8));
    }

    private static FakePlayer player(GameTestHelper helper, UUID id) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "cumulus_plugin_gametest"));
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

    private static Map<LatticePoint, BlockPlacement> placements(CuboidSet cells, BlockPlacement placement) {
        Map<LatticePoint, BlockPlacement> placements = new TreeMap<>();
        for (Cuboid box : cells.decomposition()) {
            for (int x = box.min().x(); x < box.max().x(); x++) {
                for (int y = box.min().y(); y < box.max().y(); y++) {
                    for (int z = box.min().z(); z < box.max().z(); z++) {
                        placements.put(LatticePoint.of(x, y, z), placement);
                    }
                }
            }
        }
        return placements;
    }

    /** Lowers a chest placement with a block-entity payload; every other edit falls through to vanilla. */
    private static final class ChestMaterializer implements Materializer {

        private static final NamespacedId id = NamespacedId.of("test", "chest_payload");
        private static final NamespacedId chest = NamespacedId.of("minecraft", "chest");

        @Override
        public NamespacedId id() {
            return id;
        }

        @Override
        public @Nullable MaterializedEdit materialize(Edit edit, Orientation orientation, MaterializeContext context) {
            if (!(edit instanceof Edit.Place place) || !place.type().equals(chest) || !place.properties().isEmpty()) {
                return null;
            }
            CompoundTag payload = new CompoundTag();
            payload.putString("CustomName", "{\"text\":\"plugin chest\"}");
            Map<LatticePoint, BlockPlacement> placements = placements(
                place.cells(),
                new BlockPlacement(Blocks.CHEST.defaultBlockState(), payload)
            );
            return new MaterializedEdit(
                placements,
                List.of(new ItemRequirement(new ItemStack(Items.CHEST), placements.size())),
                List.of()
            );
        }
    }

    private static final class ThrowingMaterializer implements Materializer {

        private static final NamespacedId id = NamespacedId.of("test", "exploding");

        @Override
        public NamespacedId id() {
            return id;
        }

        @Override
        public @Nullable MaterializedEdit materialize(Edit edit, Orientation orientation, MaterializeContext context) {
            throw new IllegalStateException("the extension is broken");
        }
    }

    /** A source with a fixed budget that counts its real extractions. */
    private static final class CountingSource implements ItemSource {

        private final int budget;
        int executed;

        private CountingSource(int budget) {
            this.budget = budget;
        }

        @Override
        public NamespacedId id() {
            return countingSource;
        }

        @Override
        public int available(ItemStack template) {
            return Math.max(0, budget - executed);
        }

        @Override
        public ItemStack extract(ItemStack template, int count, ExtractionMode mode) {
            int taken = Math.min(count, available(template));
            if (mode == ExtractionMode.execute) {
                executed += taken;
            }
            return taken == 0 ? ItemStack.EMPTY : template.copyWithCount(taken);
        }

        @Override
        public ItemStack insert(ItemStack stack, ExtractionMode mode) {
            return ItemStack.EMPTY;
        }
    }

    private static final class CountingProvider implements ItemSourceProvider {

        private final CountingSource source;
        int created;

        private CountingProvider(int budget) {
            this.source = new CountingSource(budget);
        }

        @Override
        public NamespacedId id() {
            return countingSource;
        }

        @Override
        public @Nullable ItemSource create(ServerPlayer player, ServerLevel level) {
            created++;
            return source;
        }
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
            return String.join(" | ", messages);
        }
    }
}
