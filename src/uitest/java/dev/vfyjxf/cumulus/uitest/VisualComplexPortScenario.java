package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

/** Complex fixture: a port-envelope controller with six surrounding ports and envelope bounds. */
@ClientScenario(
    name = "visual.complex.port",
    group = "visual",
    tags = {"visual", "complex", "envelope"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class VisualComplexPortScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(64, 0, 0);

    public void define(ScenarioBuilder build) {
        VisualComplexFixtures.Fixture fixture = VisualComplexFixtures.portEnvelope(VisualScenarioSupport.point(origin));
        VisualComplexScenarioSupport.define(
            build,
            fixture,
            "cumulusvisual complex port 64 100 0",
            "visual.complex.port: port-envelope controller with six multi-cuboid ports"
        );
    }
}
