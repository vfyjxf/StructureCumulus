package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

/** Complex fixture: a logical subnetwork whose four satellites are physically far from the root. */
@ClientScenario(
    name = "visual.complex.logical",
    group = "visual",
    tags = {"visual", "complex", "logical"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class VisualComplexLogicalScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(128, 0, 0);

    public void define(ScenarioBuilder build) {
        VisualComplexFixtures.Fixture fixture = VisualComplexFixtures
                .logicalSubnetwork(VisualScenarioSupport.point(origin));
        VisualComplexScenarioSupport.define(
            build,
            fixture,
            "cumulusvisual complex logical 128 100 0",
            "visual.complex.logical: four non-adjacent logical satellites and four links"
        );
    }
}
