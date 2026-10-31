package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

/** A root containing adjacent child frames and nested substructures, with depth-two hierarchy lines. */
@ClientScenario(
    name = "visual.substructures",
    group = "visual",
    tags = {"visual", "substructures", "hierarchy"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class VisualSubstructuresScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(192, 0, 0);

    public void define(ScenarioBuilder build) {
        VisualHierarchyFixtures.Fixture fixture = VisualHierarchyFixtures
                .substructures(VisualScenarioSupport.point(origin));
        VisualHierarchyScenarioSupport.define(
            build,
            fixture,
            "cumulusvisual substructures 192 100 0",
            "visual.substructures: root with adjacent child frames and nested substructures"
        );
    }
}
