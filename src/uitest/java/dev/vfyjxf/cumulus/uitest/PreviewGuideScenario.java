package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * The guided build on a two-bay document: the hovered ghost's tooltip, G, the next-step line, the
 * hand pulse, a stage placed by hand that advances the frontier with its banner, the same line
 * following it, and Enter building the rest — the hybrid build. A second session proves the guide
 * arms itself after a confirm that leaves the plan.
 */
@ClientScenario(
    name = "preview.guided",
    group = "guided",
    tags = {"preview", "guided"},
    timeoutMs = 120_000,
    scenarioTimeoutMs = 300_000
)
public final class PreviewGuideScenario {

    private static final NamespacedId staged = NamespacedId.of("structurecumulus", "preview_stages");
    /** The base bay's nine cells sit at y=100 over x/z 0..2; the chimney's three bars at (1,101..103,1). */
    private static final int[] footprint = {0, 1, 2};
    private static final String stageFloorHint = "lay the brick ring before the corners";
    private static final String stageStackHint = "place it after the floor is complete";
    private static final String stackDescription = "the chimney above the floor";

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).runCommand("cumulusvisual hud hide")
                .log("preview.guided: G, the next-step line, the hand pulse, a hand-placed stage and a hybrid confirm")
                .check(
                    "every preview action owns one key and G shadows no vanilla control",
                    () -> PreviewGuideProbe.keyConflict() == null,
                    PreviewGuideProbe::keyConflict
                );

        build = guidedSession(build);
        // the built stage stands exactly where the probe's cells are: the second session needs clear air
        build.fill(-6, 100, -6, 7, 103, 7, Blocks.AIR.defaultBlockState());
        build = autoEntry(build);
        PreviewScenarioSupport.cancel(build);
        build.runCommand("gamemode creative @a");
        PreviewScenarioSupport.cleanup(build).runCommand("cumulusvisual hud show");
    }

    /** The whole guided story on the staged document, ending in a mixed hand/machine build. */
    private static ScenarioBuilder guidedSession(ScenarioBuilder build) {
        build = PreviewScenarioSupport.open(build, staged)
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                ).waitUntil("the two-stage session synced", PreviewScenarioSupport::synced)
                .check("no guide is armed before G", () -> !PreviewGuideProbe.guided());
        // the crosshair's ray crosses the base ghost at (0,100,2) before it reaches the floor
        build = build.teleportPlayer(0.5, 101.0, 4.5).yawPitch(180.0F, 45.0F).frames(3)
                .waitUntil("the hovered ghost answers", () -> PreviewGuideProbe.hover() != null)
                .check(
                    "a ghost's tooltip names the expected block, the part and the stage",
                    () -> "minecraft:stone_bricks \u00B7 part structurecumulus:base \u00B7 stage 1/2"
                            .equals(PreviewGuideProbe.hover()),
                    PreviewGuideProbe::hover
                )
                .check(
                    "the HUD draws the hovered ghost's line",
                    () -> PreviewProbe.hudHas("hover: minecraft:stone_bricks")
                ).yawPitch(180.0F, -60.0F).frames(3)
                .check(
                    "the tooltip goes away with the crosshair",
                    () -> PreviewGuideProbe.hover() == null,
                    PreviewGuideProbe::hover
                ).yawPitch(180.0F, 45.0F).pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.guide))
                .waitUntil("G armed the guide", PreviewGuideProbe::guided)
                .check(
                    "the guide names the first stage's item and carries its hint verbatim",
                    () -> ("place 9\u00D7 minecraft:stone_bricks (stage 1/2: structurecumulus:base) \u00B7 "
                            + stageFloorHint).equals(PreviewGuideProbe.nextStep()),
                    PreviewGuideProbe::nextStep
                ).check("the HUD draws the guide's one line", PreviewGuideProbe::hudHasGuide)
                .check("the key legend names G", () -> PreviewProbe.hudHas("keys: ") && legendNamesGuide())
                .check(
                    "the next group is the stage's nine ghosts in the wire order",
                    () -> PreviewGuideProbe.nextGroup().size() == 9
                            && PreviewGuideProbe.nextGroup().get(0).equals(LatticePoint.of(0, 100, 0)),
                    () -> PreviewGuideProbe.nextGroup().toString()
                ).screenshot("01-guided").frames(3).checkCaptured("01-guided")
                // hand awareness: the right hand pulses the group, a wrong hand stays silent
                .runCommand("item replace entity @a weapon.mainhand with minecraft:stone_bricks")
                .waitUntil(
                    "the stone bricks in hand pulse the nine ghosts",
                    () -> PreviewGuideProbe.pulsing().size() == 9
                )
                .check(
                    "the pulse fills exactly the next group",
                    () -> PreviewGuideProbe.pulsing().equals(PreviewGuideProbe.nextGroup()),
                    () -> PreviewGuideProbe.pulsing().toString()
                ).check("a pulsing ghost's factor stays inside the pulse band", () -> {
                    float factor = PreviewGuideProbe.pulseFactor(PreviewGuideProbe.nextGroup().get(0));
                    return factor >= 1.0F && factor <= 1.5F;
                }, () -> Float.toString(PreviewGuideProbe.pulseFactor(PreviewGuideProbe.nextGroup().get(0))))
                .screenshot("02-guided-pulse").frames(3).checkCaptured("02-guided-pulse")
                .runCommand("item replace entity @a weapon.mainhand with minecraft:dirt")
                .waitUntil("a wrong hand pulses nothing", () -> PreviewGuideProbe.pulsing().isEmpty())
                .check(
                    "a wrong hand adds no message",
                    () -> PreviewGuideProbe.nextStep() != null
                            && PreviewGuideProbe.nextStep().startsWith("place 9\u00D7 minecraft:stone_bricks"),
                    PreviewGuideProbe::nextStep
                ).runCommand("item replace entity @a weapon.mainhand with minecraft:air")
                // hand placement: one cell, then the rest of the stage
                .setBlock(0, 100, 0, Blocks.STONE_BRICKS.defaultBlockState())
                .waitUntil("the hand-placed brick is counted", () -> satisfiedCells(0) == 1)
                .check(
                    "the guide asks for the remaining eight",
                    () -> PreviewGuideProbe.nextStep()
                            .startsWith("place 8\u00D7 minecraft:stone_bricks (stage 1/2: structurecumulus:base)"),
                    PreviewGuideProbe::nextStep
                ).check("the frontier stays on the stage being placed", () -> PreviewStageProbe.currentStage() == 0)
                .fill(0, 100, 0, 2, 100, 2, Blocks.STONE_BRICKS.defaultBlockState())
                .waitUntil("the finished stage advanced the frontier", () -> PreviewStageProbe.currentStage() == 1)
                .check(
                    "the cursor tracked the frontier into the next stage",
                    () -> PreviewStageProbe.selectedStage() == 1,
                    () -> Integer.toString(PreviewStageProbe.selectedStage())
                )
                .check(
                    "the banner announces the stage the world entered",
                    () -> PreviewStageProbe.bannerLines().equals(List.of("structurecumulus:stack", stackDescription)),
                    () -> PreviewStageProbe.bannerLines().toString()
                )
                .check(
                    "the guide now asks for the chimney's bars and carries its hint",
                    () -> ("place 3\u00D7 minecraft:iron_bars (stage 2/2: structurecumulus:stack) \u00B7 "
                            + stageStackHint).equals(PreviewGuideProbe.nextStep()),
                    PreviewGuideProbe::nextStep
                ).check("the guide is not complete yet", () -> !PreviewGuideProbe.complete())
                .screenshot("03-guided-advanced").frames(3).checkCaptured("03-guided-advanced");
        // Enter stays confirm: the machine finishes what the hand started (design 16 §12.6)
        for (int press = 1; press <= 3; press++) {
            int expected = press;
            build = build.pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.confirm))
                    .waitUntil("confirm " + expected + " built one more iron bar", () -> bars() == expected);
        }
        return build
                .waitUntil(
                    "the final sync reports the committed build",
                    () -> PreviewProbe.hasMarker("build committed")
                ).check("the machine finished the hand-started build", () -> bars() == 3)
                .waitUntil("the client drops the closed session", () -> !PreviewProbe.hasSession()).timelineRecord(
                    "preview-guided",
                    () -> "bars=" + bars() + ", guided=" + PreviewGuideProbe.guided() + ", next="
                            + PreviewGuideProbe.nextStep()
                );
    }

    /** A confirm that leaves the plan in the session arms the guide; a wrong-hand hover stays quiet. */
    private static ScenarioBuilder autoEntry(ScenarioBuilder build) {
        return PreviewScenarioSupport.open(build, PreviewScenarioSupport.probe)
                .waitUntil("the probe session synced", PreviewScenarioSupport::synced)
                .check("no guide is armed on a fresh session", () -> !PreviewGuideProbe.guided())
                .teleportPlayer(0.5, 100.0, 3.5).yawPitch(180.0F, 30.0F).frames(3)
                .waitUntil("the probe's ghost answers", () -> PreviewGuideProbe.hover() != null)
                .check(
                    "a stair ghost's tooltip carries its properties and its part",
                    () -> PreviewGuideProbe.hover().startsWith("minecraft:oak_stairs[")
                            && PreviewGuideProbe.hover().contains("facing=")
                            && PreviewGuideProbe.hover().endsWith("part structurecumulus:shell \u00B7 stage 1/1"),
                    PreviewGuideProbe::hover
                ).pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.confirm))
                .waitUntil("the confirm left the plan, so the guide armed itself", PreviewGuideProbe::guided)
                .check(
                    "the guide asks for the probe's remaining stairs",
                    () -> PreviewGuideProbe.nextStep()
                            .startsWith("place 3\u00D7 minecraft:oak_stairs (stage 1/1: structurecumulus:shell)"),
                    PreviewGuideProbe::nextStep
                )
                .check(
                    "the partial confirm built one stair",
                    () -> PreviewProbe.cells(CellMark.present) == 1,
                    () -> Integer.toString(PreviewProbe.cells(CellMark.present))
                ).screenshot("04-guided-auto").frames(3).checkCaptured("04-guided-auto")
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.guide))
                .waitUntil("G left the guide", () -> !PreviewGuideProbe.guided())
                .check("the guide's line leaves the HUD with the mode", () -> !PreviewGuideProbe.hudHasGuide());
    }

    private static boolean legendNamesGuide() {
        for (String line : PreviewProbe.hudLines()) {
            if (line.startsWith("keys: ") && line.contains("G guide")) {
                return true;
            }
        }
        return false;
    }

    private static int satisfiedCells(int index) {
        var stage = PreviewStageProbe.stage(index);
        return stage == null ? -1 : stage.satisfiedCells();
    }

    /** Counts the chimney's iron bars; the siting is the document's identity pose. */
    private static int bars() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return 0;
        }
        BlockPos origin = PreviewScenarioSupport.origin;
        int bars = 0;
        for (int x : footprint) {
            for (int z : footprint) {
                for (int y = 1; y <= 3; y++) {
                    if (level.getBlockState(origin.offset(x, y, z)).is(Blocks.IRON_BARS)) {
                        bars++;
                    }
                }
            }
        }
        return bars;
    }
}
