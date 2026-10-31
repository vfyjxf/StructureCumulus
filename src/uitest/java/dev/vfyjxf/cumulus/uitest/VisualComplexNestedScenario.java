package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.core.BlockPos;

/** Complex fixture: root, child-frame marker and entity-window marker with nested cuboid bounds. */
@ClientScenario(
    name = "visual.complex.nested",
    group = "visual",
    tags = {"visual", "complex", "nested"},
    timeoutMs = 90_000,
    scenarioTimeoutMs = 180_000
)
public final class VisualComplexNestedScenario {

    private static final BlockPos origin = VisualScenarioSupport.cell(192, 0, 0);

    public void define(ScenarioBuilder build) {
        VisualComplexFixtures.Fixture fixture = VisualComplexFixtures.nestedFrame(VisualScenarioSupport.point(origin));
        VisualComplexScenarioSupport.define(
            build,
            fixture,
            "cumulusvisual complex nested 192 100 0",
            "visual.complex.nested: root, child frame and entity window in multi-cuboid bounds"
        );
    }
}
