package dev.vfyjxf.cumulus.mc;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.pref.BuildPreferences;
import dev.vfyjxf.cumulus.mc.pref.BuildPreset;
import dev.vfyjxf.cumulus.mc.pref.OrientationMode;
import dev.vfyjxf.cumulus.mc.pref.PreferencesSavedData;
import dev.vfyjxf.cumulus.mc.pref.RemovalPolicy;
import dev.vfyjxf.cumulus.mc.preview.PreviewContext;
import dev.vfyjxf.cumulus.mc.preview.PreviewService;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewControlPayload;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The build presets on a real level: the session save, the apply that refuses a changed world instead
 * of blind-writing it, the store bound, the routing exit codes and the pinned re-open.
 */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class BuildPresetGameTest {

    private static final NamespacedId stoneDocument = NamespacedId.of(Constants.modId, "build_stone");
    private static final NamespacedId airDocument = NamespacedId.of(Constants.modId, "build_air");
    private static final NamespacedId lDocument = NamespacedId.of(Constants.modId, "build_stone_l");

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aSavedPresetRefusesAChangedWorldThenCommitsAfterTheRestore(GameTestHelper helper) {
        FakePlayer player = player(helper, playerId("preset-refuse"));
        player.setGameMode(GameType.CREATIVE);
        PreferencesSavedData store = store(helper, player);
        store.set(
            player.getUUID(),
            BuildPreferences.of(OrientationMode.autoAnchor, null, RemovalPolicy.forbid, List.of())
        );
        CommandSourceStack source = stack(helper, player, at(helper, 0, 0));
        int opened = PreviewService.open(source, airDocument, origin(helper));
        helper.assertTrue(opened == 1, "the preview opens, exit " + opened);
        PreviewContext context = PreviewService.context(player);
        if (context == null) {
            helper.fail("the preview has no session");
            return;
        }
        PreviewService.control(player, PreviewControlPayload.Control.savePreset, null);
        BuildPreset saved = store.preset(player.getUUID(), "preset-1");
        if (saved == null) {
            helper.fail("the K control stored no preset: " + store.presets(player.getUUID()));
            return;
        }
        helper.assertTrue(saved.blueprintId().equals(airDocument), "the preset carries the session's blueprint");
        helper.assertTrue(saved.siting().equals(context.siting()), "the preset carries the session's pose");
        helper.assertTrue(
            saved.source().kind() == BuildPreset.Source.Kind.creative,
            "a creative session stores the creative form, was " + saved.source()
        );
        helper.assertTrue(
            saved.removalPolicy() == RemovalPolicy.forbid,
            "the player's policy is recorded, was " + saved.removalPolicy()
        );
        PreviewService.control(player, PreviewControlPayload.Control.cancel, null);

        BlockPos cell = at(helper, 0, 0);
        helper.getLevel().setBlockAndUpdate(cell, Blocks.DIRT.defaultBlockState());
        int refused = execute(helper, source, "cumulus build preset apply preset-1");
        helper.assertTrue(refused == 0, "the changed world refuses the run, exit " + refused);
        helper.assertTrue(
            helper.getLevel().getBlockState(cell).is(Blocks.DIRT),
            "a refusal never writes the stored pose over the world"
        );

        helper.getLevel().setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
        int committed = execute(helper, source, "cumulus build preset apply preset-1");
        helper.assertTrue(committed == 1, "the restored world commits the run, exit " + committed);
        helper.assertTrue(helper.getLevel().getBlockState(cell).isAir(), "the air blueprint is satisfied");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aPresetAppliesTheStoredBlueprintAndTheRunAliasAnswersTheSame(GameTestHelper helper) {
        FakePlayer player = player(helper, playerId("preset-apply"));
        player.setGameMode(GameType.CREATIVE);
        CommandSourceStack source = stack(helper, player, at(helper, 0, 0));
        helper.assertTrue(PreviewService.open(source, stoneDocument, origin(helper)) == 1, "the preview opens");
        PreferencesSavedData store = store(helper, player);
        helper.assertTrue(
            execute(helper, source, "cumulus build preset save mine") == 1,
            "the save verb answers 1 from a live session"
        );
        helper.assertTrue(store.preset(player.getUUID(), "mine") != null, "the named preset is stored");
        PreviewService.control(player, PreviewControlPayload.Control.cancel, null);
        helper.assertTrue(
            execute(helper, source, "cumulus build preset save late") == 0,
            "a save without a session is refused"
        );

        helper.assertTrue(execute(helper, source, "cumulus build preset apply mine") == 1, "the apply commits");
        helper.assertTrue(helper.getLevel().getBlockState(at(helper, 0, 0)).is(Blocks.STONE), "the cell is stone");
        helper.assertTrue(
            execute(helper, source, "cumulus build preset run mine") == 1,
            "the run alias answers the same on the now-formed structure"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aFullStoreRefusesTheThirtyThirdPresetAndTheListsStillAnswer(GameTestHelper helper) {
        FakePlayer player = player(helper, playerId("preset-full"));
        player.setGameMode(GameType.CREATIVE);
        CommandSourceStack source = stack(helper, player, at(helper, 0, 0));
        PreferencesSavedData store = store(helper, player);
        LatticePoint origin = origin(helper);
        for (int index = 0; index < BuildPreset.capacity; index++) {
            helper.assertTrue(
                store.savePreset(player.getUUID(), filler(origin, index)) == PreferencesSavedData.SaveResult.saved,
                "filler " + index + " is stored"
            );
        }
        helper.assertTrue(store.presetCount(player.getUUID()) == BuildPreset.capacity, "the store is full");
        helper.assertTrue(PreviewService.open(source, stoneDocument, origin) == 1, "the preview opens");

        PreviewService.control(player, PreviewControlPayload.Control.savePreset, null);
        helper.assertTrue(
            store.presetCount(player.getUUID()) == BuildPreset.capacity,
            "the K control refused instead of evicting one, count " + store.presetCount(player.getUUID())
        );
        helper.assertTrue(store.preset(player.getUUID(), "preset-1") == null, "no preset was written");
        helper.assertTrue(
            execute(helper, source, "cumulus build preset save extra") == 0,
            "the save verb refuses on a full store"
        );
        helper.assertTrue(
            execute(helper, source, "cumulus build preset list") == 1,
            "the list still answers 1 on a full store"
        );
        helper.assertTrue(store.presetCount(player.getUUID()) == BuildPreset.capacity, "still full");
        PreviewService.control(player, PreviewControlPayload.Control.cancel, null);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void theListDeleteAndApplyVerbsRouteTheirExitCodes(GameTestHelper helper) {
        FakePlayer player = player(helper, playerId("preset-routing"));
        player.setGameMode(GameType.CREATIVE);
        CommandSourceStack source = stack(helper, player, at(helper, 0, 0));
        PreferencesSavedData store = store(helper, player);
        helper.assertTrue(execute(helper, source, "cumulus build preset list") == 1, "an empty list answers 1");
        helper.assertTrue(
            execute(helper, source, "cumulus build preset apply missing") == 0,
            "an unknown name answers 0"
        );
        helper.assertTrue(
            execute(helper, source, "cumulus build preset delete missing") == 0,
            "deleting an unknown name answers 0"
        );
        helper.assertTrue(PreviewService.open(source, stoneDocument, origin(helper)) == 1, "the preview opens");
        helper.assertTrue(execute(helper, source, "cumulus build preset save routed") == 1, "the save answers 1");
        helper.assertTrue(execute(helper, source, "cumulus build preset list") == 1, "the list answers 1");
        helper.assertTrue(execute(helper, source, "cumulus build preset delete routed") == 1, "the delete answers 1");
        helper.assertTrue(store.preset(player.getUUID(), "routed") == null, "the deleted name is gone");
        helper.assertTrue(
            execute(helper, source, "cumulus build preset delete routed") == 0,
            "a second delete answers 0"
        );
        helper.assertTrue(
            execute(helper, source, "cumulus build preset apply routed") == 0,
            "applying a deleted name answers 0"
        );
        PreviewService.control(player, PreviewControlPayload.Control.cancel, null);
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aPresetPreviewReopensTheStoredPoseAndRefusesCycling(GameTestHelper helper) {
        FakePlayer player = player(helper, playerId("preset-preview"));
        player.setGameMode(GameType.CREATIVE);
        CommandSourceStack source = stack(helper, player, at(helper, 0, 0));
        helper.assertTrue(PreviewService.open(source, lDocument, origin(helper)) == 1, "the preview opens");
        PreviewContext opened = PreviewService.context(player);
        if (opened == null) {
            helper.fail("the session has no context");
            return;
        }
        store(helper, player);
        PreviewService.control(player, PreviewControlPayload.Control.turnCw, null);
        PreviewContext turned = PreviewService.context(player);
        if (turned == null) {
            helper.fail("the turned session has no context");
            return;
        }
        helper.assertTrue(
            !turned.siting().placement().orientation().turn().equals(opened.siting().placement().orientation().turn()),
            "the turn moved the pose away from the session's opening one, was " + turned.siting()
        );
        PreviewService.control(player, PreviewControlPayload.Control.savePreset, null);
        PreviewService.control(player, PreviewControlPayload.Control.cancel, null);

        int reopened = execute(helper, source, "cumulus build preset preview preset-1");
        helper.assertTrue(reopened == 1, "the preset preview opens, exit " + reopened);
        PreviewContext back = PreviewService.context(player);
        if (back == null) {
            helper.fail("the preset preview has no session");
            return;
        }
        helper.assertTrue(back.blueprintId().equals(lDocument), "the reopened session is the stored blueprint");
        helper.assertTrue(back.siting().equals(turned.siting()), "the stored pose is the session's pose");

        PreviewService.control(player, PreviewControlPayload.Control.turnCw, null);
        PreviewContext still = PreviewService.context(player);
        helper.assertTrue(still != null, "a refused cycle keeps the session");
        helper.assertTrue(
            still != null && still.siting().equals(turned.siting()),
            "the preset fixes the pose; cycling is off"
        );
        PreviewService.control(player, PreviewControlPayload.Control.cancel, null);
        helper.succeed();
    }

    /**
     * This level's store with the invoking player's presets cleared: the run directory's world keeps
     * SavedData across runs, and a preset left by an earlier run would shadow the new one.
     */
    private static PreferencesSavedData store(GameTestHelper helper, FakePlayer player) {
        PreferencesSavedData store = PreferencesSavedData.get(helper.getLevel());
        for (BuildPreset preset : store.presets(player.getUUID())) {
            store.deletePreset(player.getUUID(), preset.name());
        }
        return store;
    }

    private static BuildPreset filler(LatticePoint origin, int index) {
        return new BuildPreset(
            "filler-" + index,
            stoneDocument,
            Siting.of(origin, Orientation.identity),
            BuildPreset.Source.creative(),
            RemovalPolicy.ask
        );
    }

    private static BlockPos at(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 1, z));
    }

    private static LatticePoint origin(GameTestHelper helper) {
        BlockPos origin = at(helper, 0, 0);
        return LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
    }

    private static UUID playerId(String tag) {
        return UUID.nameUUIDFromBytes(("cumulus-preset-gametest/" + tag).getBytes(StandardCharsets.UTF_8));
    }

    private static FakePlayer player(GameTestHelper helper, UUID id) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "cumulus_gametest"));
    }

    private static CommandSourceStack stack(GameTestHelper helper, FakePlayer player, BlockPos at) {
        return stack(helper, player, player, at);
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
            return -1;
        }
    }
}
