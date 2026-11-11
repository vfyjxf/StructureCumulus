package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * Enter runs the real build executor on the session's siting: one build run applies one repair, so
 * the first expected cell becomes oak stairs, the final sync says the session closes, and the client
 * drops the ghosts on the next idle.
 */
@ClientScenario(
    name = "preview.confirm",
    group = "preview",
    tags = {"preview", "confirm"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class PreviewConfirmScenario {

    /** The probe is a 2x1x2 of oak stairs; a turn or flip keeps it inside this 3x3 footprint. */
    private static final int[] offsets = {-1, 0, 1};

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build)
                .log("preview.confirm: Enter four times; each confirm applies one repair and the last closes");
        build = PreviewScenarioSupport.open(build)
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                ).waitUntil("the session synced with ghosts and a drawn HUD", PreviewScenarioSupport::synced)
                .check("four ghost cells are drawn before the confirm", () -> PreviewProbe.ghostCount() == 4);
        // one build run applies one repair, so the four-cell probe needs four confirms (design 14 §10)
        for (int press = 1; press <= 4; press++) {
            int expected = press;
            build = build.pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.confirm)).waitUntil(
                "confirm " + expected + " built one more expected cell",
                () -> builtStairs() == expected
            );
        }
        build.waitUntil("the final sync reports the committed build", () -> PreviewProbe.hasMarker("build committed"))
                .check("the probe formed after the fourth confirm", () -> builtStairs() == 4)
                .waitUntil("the client drops the closed session", () -> !PreviewProbe.hasSession())
                .timelineRecord(
                    "preview-confirm",
                    () -> "markers=" + PreviewProbe.markerCount() + ", ghosts=" + PreviewProbe.ghostCount()
                            + ", stairs=" + builtStairs()
                ).screenshot("hud").frames(3).checkCaptured("hud");
        PreviewScenarioSupport.cleanup(build);
    }

    /** Counts the probe's stairs around the origin; the pose only moves them inside this square. */
    private static int builtStairs() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return 0;
        }
        BlockPos origin = PreviewScenarioSupport.origin;
        int stairs = 0;
        for (int x : offsets) {
            for (int z : offsets) {
                if (level.getBlockState(origin.offset(x, 0, z)).is(Blocks.OAK_STAIRS)) {
                    stairs++;
                }
            }
        }
        return stairs;
    }
}
