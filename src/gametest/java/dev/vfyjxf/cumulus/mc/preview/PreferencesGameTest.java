package dev.vfyjxf.cumulus.mc.preview;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.mc.LevelAccess;
import dev.vfyjxf.cumulus.mc.LevelCapture;
import dev.vfyjxf.cumulus.mc.build.ApplyReport;
import dev.vfyjxf.cumulus.mc.build.BuildExecutor;
import dev.vfyjxf.cumulus.mc.build.BuildPolicy;
import dev.vfyjxf.cumulus.mc.build.BuildRequest;
import dev.vfyjxf.cumulus.mc.build.CreativeItemSource;
import dev.vfyjxf.cumulus.mc.pref.BuildPreferences;
import dev.vfyjxf.cumulus.mc.pref.PlacementWeight;
import dev.vfyjxf.cumulus.mc.pref.PreferencesSavedData;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewControlPayload;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * The preference loop on a real level: a confirmed siting leads the next session, a part pin survives
 * a reopen, the built cells lock the cycle, and {@code /cumulus prefs} routes with its exit codes.
 */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class PreferencesGameTest {

    private static final NamespacedId stoneDocument = NamespacedId.of(Constants.modId, "build_stone");
    private static final NamespacedId pairDocument = NamespacedId.of(Constants.modId, "build_stone_pair");
    private static final NamespacedId lDocument = NamespacedId.of(Constants.modId, "build_stone_l");
    private static final NamespacedId pairBay = NamespacedId.of(Constants.modId, "cells");

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aConfirmedSitingLeadsTheNextSession(GameTestHelper helper) {
        FakePlayer player = player(helper, playerId("memory"));
        PreferencesSavedData.get(helper.getLevel()).forget(player.getUUID(), null);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().add(new ItemStack(Items.STONE, 8));
        BlockPos cell = at(helper, 0, 0);
        CommandSourceStack source = stack(helper, player, cell);
        int opened = PreviewService.open(source, stoneDocument, origin(helper));
        helper.assertTrue(opened == 1, "the preview opens, exit " + opened);
        PreviewSession session = PreviewService.session(player);
        if (session == null) {
            helper.fail("the preview has no session");
            return;
        }
        PreviewService.control(player, PreviewControlPayload.Control.turnCw, null);
        Siting confirmed = session.payload().candidates().get(session.payload().current()).siting();

        PreviewService.control(player, PreviewControlPayload.Control.confirm, null);
        helper.assertTrue(helper.getLevel().getBlockState(cell).is(Blocks.STONE), "the confirm built the cell");
        helper.assertTrue(PreviewService.session(player) == null, "a committed confirm closes the session");

        int reopened = PreviewService.open(source, stoneDocument, origin(helper));
        helper.assertTrue(reopened == 1, "the preview reopens, exit " + reopened);
        PreviewSession next = PreviewService.session(player);
        if (next == null) {
            helper.fail("the reopened preview has no session");
            return;
        }
        helper.assertTrue(
            next.payload().candidates().get(0).siting().equals(confirmed),
            "the remembered siting leads, was " + next.payload().candidates().get(0).siting() + " wanted " + confirmed
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void aPartPinSurvivesAReopen(GameTestHelper helper) {
        FakePlayer player = player(helper, playerId("pin"));
        PreferencesSavedData.get(helper.getLevel()).forget(player.getUUID(), null);
        CommandSourceStack source = stack(helper, player, at(helper, 0, 0));
        int opened = PreviewService.open(source, pairDocument, origin(helper));
        helper.assertTrue(opened == 1, "the preview opens, exit " + opened);
        PreviewSession session = PreviewService.session(player);
        if (session == null) {
            helper.fail("the preview has no session");
            return;
        }
        Siting siting = session.payload().candidates().get(session.payload().current()).siting();
        List<LatticePoint> legal = partCells(helper);
        if (legal.isEmpty()) {
            helper.fail("the pair blueprint has no part cells");
            return;
        }
        LatticePoint world = siting.toWorld(legal.get(0));
        PreviewService.control(player, PreviewControlPayload.Control.cyclePart, world);
        PreferencesSavedData store = PreferencesSavedData.get(helper.getLevel());
        helper.assertTrue(
            legal.get(0).equals(store.partPin(player.getUUID(), pairDocument, pairBay)),
            "the pin is written by the gesture itself"
        );

        PreviewService.control(player, PreviewControlPayload.Control.cancel, null);
        helper.assertTrue(PreviewService.session(player) == null, "cancel closes the session");
        int reopened = PreviewService.open(source, pairDocument, origin(helper));
        helper.assertTrue(reopened == 1, "the preview reopens, exit " + reopened);
        PreviewSession next = PreviewService.session(player);
        if (next == null) {
            helper.fail("the reopened preview has no session");
            return;
        }
        helper.assertTrue(
            legal.get(0).equals(next.pins().get(pairBay)),
            "the remembered part pin is applied to the reopened session, was " + next.pins()
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 200)
    public void theBuiltCellsLockTheCycleToTheirPose(GameTestHelper helper) {
        FakePlayer player = player(helper, playerId("lock"));
        PreferencesSavedData.get(helper.getLevel()).forget(player.getUUID(), null);
        Siting identity = Siting.of(origin(helper), Orientation.identity);
        StructureKernel kernel = LevelAccess.kernel();
        LevelCapture capture = LevelAccess.capture(helper.getLevel());
        OwnerId owner = LevelAccess.owner();
        if (kernel == null || capture == null || owner == null) {
            helper.fail("the server state is not open");
            return;
        }
        // one build run applies one repair, so the three-cell L needs three runs (design 14 §10)
        for (int run = 0; run < 4; run++) {
            ApplyReport built = new BuildExecutor(capture, kernel, owner, BuildPolicy.defaults()).run(
                new BuildRequest(
                    lDocument,
                    identity,
                    new CreativeItemSource(),
                    BuildPolicy.defaults(),
                    false,
                    null,
                    player
                )
            );
            helper.assertTrue(
                built.status() == ApplyReport.Status.committed,
                "run " + run + " builds the gap blueprint at the identity pose: " + built.status()
            );
        }
        int opened = PreviewService.open(stack(helper, player, at(helper, 0, 0)), lDocument, origin(helper));
        helper.assertTrue(opened == 1, "the preview opens over the built cells, exit " + opened);
        PreviewSession session = PreviewService.session(player);
        if (session == null) {
            helper.fail("the preview has no session");
            return;
        }
        Siting locked = session.payload().candidates().get(session.payload().current()).siting();
        helper.assertTrue(
            hasMarker(session, "locked"),
            "the session reports the lock: markers=" + session.markers() + " candidates="
                    + session.payload().candidates() + " current=" + session.payload().current() + " locked=" + locked
        );
        helper.assertTrue(locked.equals(identity), "the built pose is the only reading of the progress, was " + locked);
        PreviewService.control(player, PreviewControlPayload.Control.turnCw, null);
        PreviewSession still = PreviewService.session(player);
        if (still == null) {
            helper.fail("a refused turn must not close the session");
            return;
        }
        helper.assertTrue(
            still.payload().candidates().get(still.payload().current()).siting().equals(locked),
            "the locked siting cannot be cycled away from"
        );
        helper.assertTrue(hasMarker(still, "cycling is off"), "the HUD says why the turn did nothing");
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void thePrefsCommandShowsSetsAndForgets(GameTestHelper helper) {
        FakePlayer player = player(helper, playerId("command"));
        CommandSourceStack source = stack(helper, player, at(helper, 0, 0));
        helper.assertTrue(execute(helper, source, "cumulus prefs show") == 1, "prefs show answers 1");
        helper.assertTrue(execute(helper, source, "cumulus prefs set heading west") == 1, "a heading set answers 1");
        BuildPreferences afterHeading = PreferencesSavedData.get(helper.getLevel()).preferences(player.getUUID());
        helper.assertTrue(afterHeading.fixedHeading() == Heading.west, "the heading is stored, was " + afterHeading);
        helper.assertTrue(
            execute(helper, source, "cumulus prefs set weights facing_player,most_progress") == 1,
            "a weights set answers 1"
        );
        BuildPreferences afterWeights = PreferencesSavedData.get(helper.getLevel()).preferences(player.getUUID());
        helper.assertTrue(
            afterWeights.weights().equals(List.of(PlacementWeight.facingPlayer, PlacementWeight.mostProgress)),
            "the weights are stored in the given order, was " + afterWeights.weights()
        );
        helper.assertTrue(
            execute(helper, source, "cumulus prefs set heading sideways") == 0,
            "an unknown heading answers 0"
        );
        helper.assertTrue(execute(helper, source, "cumulus prefs set colour blue") == 0, "an unknown key answers 0");
        PreferencesSavedData.get(helper.getLevel())
                .remember(player.getUUID(), stoneDocument, Siting.of(origin(helper), Orientation.identity), 8);
        helper.assertTrue(
            execute(helper, source, "cumulus prefs forget " + stoneDocument.text()) == 1,
            "a forget answers 1"
        );
        helper.assertTrue(
            PreferencesSavedData.get(helper.getLevel()).rememberedBlueprints(player.getUUID()) == 0,
            "the forget cleared the memory"
        );
        helper.succeed();
    }

    /** The pair blueprint's one bay holds two legal part cells (design 15 §8). */
    private static List<LatticePoint> partCells(GameTestHelper helper) {
        StructureKernel kernel = LevelAccess.kernel();
        if (kernel == null) {
            return List.of();
        }
        Document document = kernel.document(pairDocument);
        Blueprint blueprint = document == null ? null : kernel.compile(document).blueprint();
        if (blueprint == null) {
            return List.of();
        }
        for (Bay bay : blueprint.bays()) {
            if (bay.id().equals(pairBay)) {
                return PreviewExpected.cells(bay.cells());
            }
        }
        return List.of();
    }

    private static boolean hasMarker(PreviewSession session, String fragment) {
        for (var marker : session.markers()) {
            if (marker.label().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos at(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 1, z));
    }

    private static LatticePoint origin(GameTestHelper helper) {
        BlockPos origin = at(helper, 0, 0);
        return LatticePoint.of(origin.getX(), origin.getY(), origin.getZ());
    }

    private static UUID playerId(String tag) {
        return UUID.nameUUIDFromBytes(("cumulus-pref-gametest/" + tag).getBytes(StandardCharsets.UTF_8));
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
