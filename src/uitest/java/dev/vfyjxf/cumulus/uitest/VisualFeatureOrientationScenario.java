package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

@ClientScenario(
    name = "visual.feature.orientation",
    group = "visual",
    tags = {"visual", "feature", "orientation"},
    timeoutMs = 60_000,
    scenarioTimeoutMs = 120_000
)
public final class VisualFeatureOrientationScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        build = VisualFeatureSupport.start(build, origin, "visual.feature.orientation: 方向、镜像与世界边界");
        build = VisualFeatureSupport.view(build, origin).runCommand("cumulusvisual orientation 0 100 0")
                .waitUntil(
                    "the orientation fixture is staged",
                    () -> VisualProbe.instanceCount() == 1 && VisualProbe.claimCount() == 2
                )
                .check(
                    "HUD 显示朝向技术值",
                    () -> VisualProbe.hudLines().stream()
                            .anyMatch(line -> line.startsWith("朝向: ") && line.contains("序数="))
                ).check("HUD 显示世界边界", () -> VisualProbe.hudLines().contains("边界: [-1,100,0..1,101,1]"))
                .check("世界边界高亮存在", () -> VisualProbe.highlightSummary().boundsBoxes() == 1).screenshot("hud").frames(2)
                .checkCaptured("hud").screenshot("world").frames(2).checkCaptured("world");
        build = VisualFeatureSupport.finish(build, origin);
    }
}
