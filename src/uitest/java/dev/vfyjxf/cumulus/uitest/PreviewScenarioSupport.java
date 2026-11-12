package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

/** The shared preview scenario setup: the dev-only probe blueprint, a floor and one open session. */
final class PreviewScenarioSupport {

    static final NamespacedId probe = NamespacedId.of("structurecumulus", "preview_probe");
    /** The two-tier coil variant group of the uitest datapack (design 15 §12). */
    static final NamespacedId coilBasic = NamespacedId.of("structurecumulus", "coil_basic");
    static final NamespacedId coilReinforced = NamespacedId.of("structurecumulus", "coil_reinforced");
    /** The siting origin every preview scenario uses; the floor sits one block below it. */
    static final BlockPos origin = new BlockPos(0, 100, 0);

    private PreviewScenarioSupport() {}

    static ScenarioBuilder stage(ScenarioBuilder build) {
        return build.guiScale(2).hideGui(false).runCommand("gamemode creative @a")
                .fill(-6, 99, -6, 7, 99, 7, Blocks.STONE.defaultBlockState())
                .fill(-6, 100, -6, 7, 103, 7, Blocks.AIR.defaultBlockState()).teleportPlayer(0.5, 101.0, 4.5)
                .yawPitch(180.0F, 20.0F).awaitClientChunk(0, 0);
    }

    static ScenarioBuilder open(ScenarioBuilder build) {
        return open(build, probe);
    }

    static ScenarioBuilder open(ScenarioBuilder build, NamespacedId id) {
        return build.runCommand(
            "cumuluspreview " + id.text() + " " + origin.getX() + " " + origin.getY() + " " + origin.getZ()
        );
    }

    /** A teardown cancel goes through the real control payload; Esc is a user key, not a test key. */
    static ScenarioBuilder cancel(ScenarioBuilder build) {
        return build.action("cancel the preview session", context -> send(PreviewControlPayload.Control.cancel))
                .waitUntil("the client dropped the cancelled session", () -> !PreviewProbe.hasSession());
    }

    /**
     * Runs one production command as the local player: the harness {@code runCommand} source is the
     * console, and the player-owned nodes ({@code /cumulus preview}, {@code /cumulus prefs},
     * {@code /cumulus build preset}) refuse it, so the scenario makes the player the source.
     */
    static ScenarioBuilder runAsPlayer(ScenarioBuilder build, String label, String command) {
        return build.action(label, context -> {
            Minecraft minecraft = Minecraft.getInstance();
            MinecraftServer server = minecraft.getSingleplayerServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                var players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    return;
                }
                server.getCommands()
                        .performPrefixedCommand(players.get(0).createCommandSourceStack().withPermission(4), command);
            });
        });
    }

    static void send(PreviewControlPayload.Control control) {
        PacketDistributor.sendToServer(new PreviewControlPayload(control, null));
    }

    static ScenarioBuilder cleanup(ScenarioBuilder build) {
        return build.fill(-6, 99, -6, 7, 99, 7, Blocks.AIR.defaultBlockState())
                .fill(-6, 100, -6, 7, 103, 7, Blocks.AIR.defaultBlockState());
    }

    /** The dev-open outcome; the command output is suppressed, so a failure must be readable here. */
    static String openResult() {
        return PreviewDevCommand.lastResult;
    }

    /** The waiting condition every scene shares: a synced session, ghosts and a drawn HUD. */
    static boolean synced() {
        return PreviewProbe.hasSession() && PreviewProbe.hudVisible() && PreviewProbe.ghostCount() > 0;
    }
}
