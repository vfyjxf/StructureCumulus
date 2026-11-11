package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import net.minecraft.world.level.block.Blocks;

/**
 * A block written inside the footprint invalidates the session with SC-BLD-0008: the server
 * recomputes and re-syncs instead of leaving a stale overlay, and the HUD says so.
 */
@ClientScenario(
    name = "preview.invalidated",
    group = "preview",
    tags = {"preview", "invalidated"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class PreviewInvalidateScenario {

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).log(
            "preview.invalidated: change a footprint cell and assert the invalidation marker and a live session"
        );
        PreviewScenarioSupport.open(build)
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                ).waitUntil("the session synced with ghosts and a drawn HUD", PreviewScenarioSupport::synced)
                .check("no invalidation before the change", () -> !PreviewProbe.hasMarker("invalidated"))
                .setBlock(1, 100, 1, Blocks.GOLD_BLOCK.defaultBlockState())
                .waitUntil("the invalidation marker arrived", () -> PreviewProbe.hasMarker("invalidated"))
                .check("the session survives the invalidation", () -> PreviewProbe.hasSession())
                .check(
                    "the changed cell leaves the ghost set; the other three stay expected",
                    () -> PreviewProbe.ghostCount() >= 3 && PreviewProbe.ghostCount() <= 4
                )
                .check(
                    "nothing is present, and every cell carries a non-present mark",
                    () -> PreviewProbe.cells(CellMark.present) == 0
                            && PreviewProbe.cells(CellMark.missing)
                                    + PreviewProbe.cells(CellMark.unknown)
                                    + PreviewProbe.cells(CellMark.wrong)
                                    + PreviewProbe.cells(CellMark.extra)
                                    == 4
                )
                .check(
                    "the HUD shows the invalidation line",
                    () -> PreviewProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("warning: ") && line.contains("invalidated"))
                )
                .timelineRecord(
                    "preview-invalidated",
                    () -> "ghosts=" + PreviewProbe.ghostCount() + ", markers=" + PreviewProbe.markerCount()
                ).screenshot("hud").frames(3).checkCaptured("hud")
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.turnCw))
                .waitUntil("a control re-syncs the session after the invalidation", () -> PreviewProbe.hasSession())
                .waitUntil(
                    "the next control clears the invalidation line",
                    () -> !PreviewProbe.hasMarker("invalidated")
                );
        PreviewScenarioSupport.cancel(build);
        PreviewScenarioSupport.cleanup(build);
    }
}
