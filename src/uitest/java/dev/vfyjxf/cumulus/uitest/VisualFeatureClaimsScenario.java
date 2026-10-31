package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

@ClientScenario(
    name = "visual.feature.claims",
    group = "visual",
    tags = {"visual", "feature", "claims"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualFeatureClaimsScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build = VisualFeatureSupport.start(build, origin, "visual.feature.claims: 共享、独占与冲突声明");
        build = VisualFeatureSupport.view(build, origin).runCommand("cumulusvisual conflict 0 100 0")
                .waitUntil(
                    "the conflict fixture is staged",
                    () -> VisualProbe.instanceCount() == 2 && VisualProbe.claimCount() == 1
                ).check("HUD 显示声明账本", () -> VisualFeatureSupport.hasLine("声明: "))
                .check("HUD 显示冲突格", () -> VisualFeatureSupport.hasLine("冲突: 1 格"))
                .check("HUD 显示冲突错误码", () -> VisualFeatureSupport.hasLine("错误码: "))
                .check(
                    "HUD 显示独占声明",
                    () -> VisualFeatureSupport.hasLine("声明: ")
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.contains("独占 1"))
                ).screenshot("hud").frames(2).checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world");
        build = VisualFeatureSupport.finish(build, origin);
    }
}
