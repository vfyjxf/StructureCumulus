package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.remedy.EditPlan;
import dev.vfyjxf.cumulus.kernel.remedy.EditStep;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The materials list of a plan: the same materialization and item simulation the build's dry run uses,
 * read by the preview's materials HUD (4.4) without writing anything.
 */
public final class BuildMaterials {

    /** The missing items of a plan as {@code item: available/required}, in first-seen order. */
    public static List<String> shortages(EditPlan plan, Siting siting, ServerLevel level, ItemSource source) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        Materializer materializer = new VanillaMaterializer();
        List<MaterializedEdit> steps = new ArrayList<>(plan.steps().size());
        for (EditStep step : plan.steps()) {
            MaterializedEdit materialized = materializer
                    .materialize(step.edit(), siting.placement().orientation(), new MaterializeContext(level));
            if (materialized != null) {
                steps.add(materialized);
            }
        }
        return BuildGates.shortages(BuildGates.merged(steps), source);
    }

    private BuildMaterials() {}
}
