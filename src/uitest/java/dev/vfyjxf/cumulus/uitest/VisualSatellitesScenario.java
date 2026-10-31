package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

/** One orbital root, four satellites and two sub-satellites in a three-level tree. */
@ClientScenario(
    name = "visual.satellites",
    group = "visual",
    tags = {"visual", "satellites", "hierarchy"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class VisualSatellitesScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(128, 0, 0);

    public void define(ScenarioBuilder build) {
        VisualHierarchyFixtures.Fixture fixture = VisualHierarchyFixtures
                .satellites(VisualScenarioSupport.point(origin));
        VisualHierarchyScenarioSupport.define(
            build,
            fixture,
            "cumulusvisual satellites 128 100 0",
            "visual.satellites: root, four satellites, two sub-satellites and six parent links"
        );
    }
}
