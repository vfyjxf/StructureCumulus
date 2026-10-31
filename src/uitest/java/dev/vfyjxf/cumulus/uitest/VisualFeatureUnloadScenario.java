package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

@ClientScenario(
    name = "visual.feature.unload",
    group = "visual",
    tags = {"visual", "feature", "unload"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualFeatureUnloadScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build = VisualFeatureSupport.start(build, origin, "visual.feature.unload: 卸载与重新加载");
        build = VisualFeatureSupport.view(build, origin).runCommand("cumulusvisual pair 0 100 0")
                .waitUntil(
                    "the pair is staged",
                    () -> VisualProbe.instanceCount() == 2 && VisualProbe.claimCount() == 2
                ).runCommand("cumulusvisual unload")
                .waitUntil(
                    "the level reports unloaded",
                    () -> VisualProbe.instanceCount() == 0 && VisualProbe.claimCount() == 2
                ).check("卸载后声明保留", () -> VisualProbe.claimCount() == 2)
                .check(
                    "HUD 显示卸载事件",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("事件: ") && line.contains("最近 卸载"))
                ).runCommand("cumulusvisual load")
                .waitUntil("the level reloads", () -> VisualProbe.instanceCount() == 2 && VisualProbe.claimCount() == 2)
                .check(
                    "HUD 显示加载事件",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("事件: ") && line.contains("最近 加载"))
                ).screenshot("hud").frames(2).checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world");
        build = VisualFeatureSupport.finish(build, origin);
    }
}
