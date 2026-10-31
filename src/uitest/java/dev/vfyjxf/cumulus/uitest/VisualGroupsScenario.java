package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

/** Three colored systems with eight instances each; group counts and hierarchy are visible in the HUD. */
@ClientScenario(
    name = "visual.groups",
    group = "visual",
    tags = {"visual", "groups", "systems"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class VisualGroupsScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(0, 0, 0);

    public void define(ScenarioBuilder build) {
        VisualHierarchyFixtures.Fixture fixture = VisualHierarchyFixtures.groups(VisualScenarioSupport.point(origin));
        VisualHierarchyScenarioSupport.define(
            build,
            fixture,
            "cumulusvisual groups 0 100 0",
            "visual.groups: 3 systems x 8 instances, grouped palette and two-level hierarchy"
        );
    }
}
