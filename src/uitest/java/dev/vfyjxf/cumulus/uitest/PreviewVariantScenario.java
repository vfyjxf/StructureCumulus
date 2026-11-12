package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import dev.vfyjxf.cumulus.mc.preview.net.VariantSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The variant switch end to end: the coil group arrives on the session, the HUD names its member, the
 * {@code N} key re-opens the session on the next member at the same origin, and a confirm builds the
 * selected tier's blocks — the build path is the unmodified one of design 14 §10.
 */
@ClientScenario(
    name = "preview.variants",
    group = "preview",
    tags = {"preview", "variants"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class PreviewVariantScenario {

    /** The pose only moves the 2x1x2 coil inside this square around the origin. */
    private static final int[] offsets = {-1, 0, 1};

    public void define(ScenarioBuilder build) {
        build = PreviewScenarioSupport.stage(build).log(
            "preview.variants: N re-opens the session on the next member; the HUD names it; a confirm builds the selected tier"
        );

        int[] origin = {0, 0, 0};
        build = PreviewScenarioSupport.open(build, PreviewScenarioSupport.coilBasic)
                .check(
                    "the dev command reported an open",
                    () -> PreviewScenarioSupport.openResult().startsWith("opened"),
                    PreviewScenarioSupport::openResult
                ).waitUntil("the basic coil synced with ghosts and a drawn HUD", PreviewScenarioSupport::synced)
                .check("the session carries the two-member group in declaration order", () -> {
                    VariantSummary variants = PreviewProbe.variants();
                    return variants != null
                            && variants.members().size() == 2
                            && variants.members().get(0).id().equals(PreviewScenarioSupport.coilBasic)
                            && variants.members().get(1).id().equals(PreviewScenarioSupport.coilReinforced);
                }, () -> String.valueOf(PreviewProbe.variants()))
                .check(
                    "the HUD names the first member and its group",
                    () -> PreviewProbe.hudHas("variant 1/2: 基础线圈 · group structurecumulus:coil"),
                    () -> PreviewProbe.hudLines().toString()
                ).check("the basic tier expects four copper windings", () -> PreviewProbe.ghostCount() == 4)
                .action("remember the pose origin before the switch", context -> {
                    var siting = PreviewProbe.siting();
                    if (siting != null) {
                        origin[0] = siting.origin().x();
                        origin[1] = siting.origin().y();
                        origin[2] = siting.origin().z();
                    }
                }).pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.nextVariant))
                .waitUntil(
                    "the next variant re-opened the session",
                    () -> PreviewScenarioSupport.coilReinforced.equals(PreviewProbe.blueprintId())
                )
                .check(
                    "the reopened session keeps the origin",
                    () -> PreviewProbe.siting() != null
                            && PreviewProbe.siting().origin().x() == origin[0]
                            && PreviewProbe.siting().origin().y() == origin[1]
                            && PreviewProbe.siting().origin().z() == origin[2],
                    () -> String.valueOf(PreviewProbe.siting())
                )
                .check(
                    "the HUD names the second member",
                    () -> PreviewProbe.hudHas("variant 2/2: 强化线圈 · group structurecumulus:coil"),
                    () -> PreviewProbe.hudLines().toString()
                ).check("the reinforced tier expects four iron windings", () -> PreviewProbe.ghostCount() == 4)
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.nextVariant))
                .waitUntil(
                    "the second switch wraps to the first member",
                    () -> PreviewScenarioSupport.coilBasic.equals(PreviewProbe.blueprintId())
                ).check("the wrap returns to the basic tier", () -> PreviewProbe.hudHas("variant 1/2: 基础线圈"))
                .pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.nextVariant)).waitUntil(
                    "the third switch selects the reinforced tier for the build",
                    () -> PreviewScenarioSupport.coilReinforced.equals(PreviewProbe.blueprintId())
                );

        // one build run applies one repair, so the four-cell tier needs four confirms (design 14 §10)
        for (int press = 1; press <= 4; press++) {
            int expected = press;
            build = build.pressBind(options -> PreviewKeys.binding(PreviewKeys.Action.confirm)).waitUntil(
                "confirm " + expected + " built one more reinforced cell",
                () -> built(Blocks.IRON_BLOCK) == expected
            );
        }
        build.waitUntil("the final sync reports the committed build", () -> PreviewProbe.hasMarker("build committed"))
                .check(
                    "the selected tier built and the unselected one did not",
                    () -> built(Blocks.IRON_BLOCK) == 4 && built(Blocks.COPPER_BLOCK) == 0,
                    () -> "iron=" + built(Blocks.IRON_BLOCK) + " copper=" + built(Blocks.COPPER_BLOCK)
                ).waitUntil("the client drops the closed session", () -> !PreviewProbe.hasSession())
                .timelineRecord(
                    "preview-variants",
                    () -> "blueprint=" + PreviewProbe.blueprintId() + ", ghosts=" + PreviewProbe.ghostCount()
                            + ", iron=" + built(Blocks.IRON_BLOCK)
                ).screenshot("hud").frames(3).checkCaptured("hud");
        PreviewScenarioSupport.cleanup(build);
    }

    /** Counts one block in the pose's square around the origin; the session may have closed already. */
    private static int built(Block block) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return 0;
        }
        BlockPos origin = PreviewScenarioSupport.origin;
        int count = 0;
        for (int x : offsets) {
            for (int z : offsets) {
                if (level.getBlockState(origin.offset(x, 0, z)).is(block)) {
                    count++;
                }
            }
        }
        return count;
    }
}
