package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;

/** Normal multiblock example: GT-style EBF with coils, hatches, muffler and maintenance contexts. */
@ClientScenario(
    name = "visual.complex.ebf",
    group = "visual",
    tags = {"visual", "complex", "industrial"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class VisualComplexEbfScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(32, 0, 0);

    public void define(ScenarioBuilder build) {
        VisualIndustrialFixtures.Fixture fixture = VisualIndustrialFixtures.ebf(VisualScenarioSupport.point(origin));
        LatticePoint root = fixture.members().get(0).siting().origin();
        build = build.guiScale(2).hideGui(false).log(
            "visual.complex.ebf: 3x3x4 shell, 1x1x4 hollow column, coil layers, hatches and maintenance contexts"
        ).runCommand("cumulusvisual clearall");
        build = VisualIndustrialScenarioSupport.place(build, fixture);
        build = VisualIndustrialScenarioSupport.view(build, fixture).runCommand("gamemode creative @a")
                .runCommand("cumulusvisual complex ebf 32 100 0")
                .waitUntil(
                    "the EBF parts are staged",
                    () -> VisualProbe.instanceCount() == fixture.members().size()
                            && VisualProbe.claimCount() == fixture.claimRows()
                            && VisualProbe.parts().size() == fixture.members().size()
                )
                .waitUntil(
                    "the HUD reports the EBF set and selection",
                    () -> VisualProbe.hudVisible()
                            && VisualProbe.selectedInstance() != null
                            && VisualProbe.hudLineStartsWith("结构集: ")
                ).check("the EBF has eleven parts", () -> VisualProbe.parts().size() == 11)
                .check("the EBF HUD reports dimensions", () -> VisualProbe.hudContextValue("dimensions=3x3x4"))
                .check(
                    "the EBF HUD reports the interior volume",
                    () -> VisualProbe.hudContextValue("interior_volume=4")
                )
                .check(
                    "the EBF HUD reports the coil tier and max temperature",
                    () -> VisualProbe.hudContextValue("coil_tier=3")
                            && VisualProbe.hudContextValue("max_temperature=2700")
                )
                .check(
                    "the EBF HUD reports energy tier, parallels and muffler",
                    () -> VisualProbe.hudContextValue("energy_tier=MV")
                            && VisualProbe.hudContextValue("parallels=8")
                            && VisualProbe.hudContextValue("muffler_count=1")
                )
                .check(
                    "the EBF context block is paired to five lines",
                    () -> VisualProbe.hudLines().stream().filter(line -> line.startsWith("上下文键值: ")).count() == 5
                )
                .check(
                    "the EBF part colors reach the highlight layer",
                    () -> VisualProbe.highlightColors()
                            .containsAll(fixture.members().stream().map(member -> member.role().color()).toList())
                ).runCommand("cumulusvisual inspect 32 100 0")
                .waitUntil(
                    "the EBF inspect card carries context",
                    () -> VisualProbe.inspectedPart() != null
                            && VisualProbe.hudLines().stream().anyMatch(line -> line.startsWith("检视上下文: "))
                )
                .check(
                    "the EBF inspect card reports coil tier and temperature",
                    () -> VisualProbe.hudInspectContextValue("coil_tier=3")
                            && VisualProbe.hudInspectContextValue("max_temperature=2700")
                )
                .check(
                    "the EBF inspect card reports maintenance",
                    () -> VisualProbe.hudInspectContextValue("maintenance_required=是")
                ).screenshot("inspect-hud").frames(2).checkCaptured("inspect-hud")
                .runCommand("cumulusvisual inspect clear").screenshot("hud").frames(2).checkCaptured("hud")
                .screenshot("world").frames(2).checkCaptured("world").runCommand("gamemode spectator @a").hideGui(true)
                .runCommand("cumulusvisual hud hide").runCommand("cumulusvisual highlights hide").frames(3)
                .teleportPlayer(root.x() - 3.2D, root.y() + 3.8D, root.z() - 5.0D)
                .lookSweepFrames(
                    () -> VisualScenarioSupport.lookAt(new BlockPos(root.x(), root.y() + 1, root.z() + 1)),
                    10,
                    null
                ).frames(6).screenshot("flyover").frames(2).checkCaptured("flyover").hideGui(false)
                .runCommand("cumulusvisual hud show").runCommand("cumulusvisual highlights show");
        build = VisualIndustrialScenarioSupport.clear(build, fixture).runCommand("cumulusvisual clearall");
    }
}
