package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * V4 set scenario: a root plus six satellites with one claim each and explicit root-to-satellite
 * links. The HUD set card replaces the placeholder, the highlight layer submits seven bounds, seven
 * claim cells and six link strokes.
 */
@ClientScenario(
    name = "visual.set",
    group = "visual",
    tags = {"visual", "set", "large"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualSetScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);
    private static final int[][] satellites = {{2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}, {2, 0, 2}, {-2, 0, -2}};

    public void define(ScenarioBuilder build) {
        ScenarioBuilder placed = build.guiScale(2).hideGui(false)
                .log("visual.set: root plus six satellites, claims per member, HUD set summary and links")
                .runCommand("cumulusvisual clearall")
                .setBlock(origin.getX(), origin.getY(), origin.getZ(), Blocks.EMERALD_BLOCK.defaultBlockState());
        for (int[] offset : satellites) {
            BlockPos satellite = origin.offset(offset[0], offset[1], offset[2]);
            placed = placed.setBlock(
                satellite.getX(),
                satellite.getY(),
                satellite.getZ(),
                Blocks.EMERALD_BLOCK.defaultBlockState()
            );
        }
        placed.fill(-3, 100, 3, 3, 100, 4, Blocks.STONE.defaultBlockState()).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual set 0 100 0").teleportPlayer(0.5, 101.0, 8.5).yawPitch(180.0F, 8.0F)
                .awaitClientChunk(0, 0)
                .waitUntil(
                    "the snapshot holds seven set 成员 and seven claim rows",
                    () -> VisualProbe.instanceCount() == 7 && VisualProbe.claimCount() == 7
                )
                .waitUntil(
                    "the HUD renders the real set summary",
                    () -> VisualProbe.hudVisible()
                            && VisualProbe.hudLines()
                                    .contains("结构集: structurecumulus:visual_set 成员 7, 已形成 7, 声明 7, 预算 7/64, 链接 6")
                ).waitUntil("the highlight layer submits the set bounds and link strokes", () -> {
                    VisualHighlightSummary summary = VisualProbe.highlightSummary();
                    return summary.boundsBoxes() == 7 && summary.claimCells() == 7 && summary.setLinks() == 6;
                }).check("the set summary has seven 成员 and six links", () -> {
                    VisualSetSummary summary = VisualProbe.setSummary();
                    return summary != null
                            && summary.members() == 7
                            && summary.formed() == 7
                            && summary.linkCount() == 6;
                })
                .check(
                    "the HUD reports member, formed, claim and 预算 counts",
                    () -> VisualProbe.hudLines()
                            .contains("结构集: structurecumulus:visual_set 成员 7, 已形成 7, 声明 7, 预算 7/64, 链接 6")
                )
                .check(
                    "all seven member bounds are highlighted",
                    () -> VisualProbe.highlightSummary().boundsBoxes() == 7
                ).check("all seven claim cells are highlighted", () -> VisualProbe.highlightSummary().claimCells() == 7)
                .check("all six root-to-satellite 链接 are submitted", () -> VisualProbe.setLinkCount() == 6)
                .timelineRecord("set-state", () -> VisualScenarioSupport.describe(VisualProbe.highlightSummary()))
                .screenshot("hud").frames(3).checkCaptured("hud").screenshot("world").frames(3).checkCaptured("world")
                .runCommand("cumulusvisual clearall").fill(-3, 100, 3, 3, 100, 4, Blocks.AIR.defaultBlockState());
        BlockPos[] cleanup = new BlockPos[satellites.length + 1];
        cleanup[0] = origin;
        for (int index = 0; index < satellites.length; index++) {
            cleanup[index + 1] = origin.offset(satellites[index][0], satellites[index][1], satellites[index][2]);
        }
        for (BlockPos pos : cleanup) {
            placed = placed.setBlock(pos.getX(), pos.getY(), pos.getZ(), Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : cleanup) {
            BlockPos cell = pos;
            placed = placed.setBlock(cell.getX(), cell.getY(), cell.getZ(), Blocks.AIR.defaultBlockState());
        }
    }
}
