package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

/** Complex fixture: a blast-furnace controller, two preheaters and four ports, with real blocks. */
@ClientScenario(
    name = "visual.complex.blast_furnace",
    group = "visual",
    tags = {"visual", "complex", "set"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class VisualComplexBlastFurnaceScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        VisualComplexFixtures.Fixture fixture = VisualComplexFixtures.blastFurnace(VisualScenarioSupport.point(origin));
        VisualComplexScenarioSupport.define(
            build,
            fixture,
            "cumulusvisual complex blast_furnace 0 100 0",
            "visual.complex.blast_furnace: controller, two preheaters, four ports, 6 links"
        );
    }
}
