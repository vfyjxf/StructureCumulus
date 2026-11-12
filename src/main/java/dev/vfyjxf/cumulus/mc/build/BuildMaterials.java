package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.remedy.EditPlan;
import dev.vfyjxf.cumulus.kernel.remedy.EditStep;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The materials list of a plan: the same materialization and item simulation the build's dry run uses,
 * read by the preview's materials HUD (4.4) without writing anything.
 */
public final class BuildMaterials {

    /** One short item of a plan: the stack it asks for, what the source can give and what it needs. */
    public record MaterialShortage(ItemStack template, int have, int need) {

        public MaterialShortage {
            Objects.requireNonNull(template, "template");
            if (need < 1 || have < 0 || have >= need) {
                throw new IllegalArgumentException("a shortage needs 0 <= have < need: " + have + "/" + need);
            }
        }
    }

    /** The built-in chain's view of a plan; a session with plugin materializers passes its own. */
    public static List<String> shortages(EditPlan plan, Siting siting, ServerLevel level, ItemSource source) {
        return shortages(plan, siting, level, source, Materializers.builtin());
    }

    /** The missing items of a plan as {@code item: available/required}, in first-seen order. */
    public static List<String> shortages(
        EditPlan plan,
        Siting siting,
        ServerLevel level,
        ItemSource source,
        Materializers materializers
    ) {
        return shortages(missing(plan, siting, level, source, materializers));
    }

    /** The same simulation as {@link #shortages}, one typed line per short item. */
    public static List<MaterialShortage> missing(
        EditPlan plan,
        Siting siting,
        ServerLevel level,
        ItemSource source,
        Materializers materializers
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(materializers, "materializers");
        MaterializeContext context = new MaterializeContext(level);
        List<MaterializedEdit> steps = new ArrayList<>(plan.steps().size());
        for (EditStep step : plan.steps()) {
            MaterializedEdit materialized = materializers
                    .materialize(step.edit(), siting.placement().orientation(), context);
            if (materialized != null) {
                steps.add(materialized);
            }
        }
        List<MaterialShortage> missing = new ArrayList<>();
        for (ItemRequirement requirement : BuildGates.merged(steps)) {
            int available = source.available(requirement.template());
            if (available < requirement.count()) {
                missing.add(new MaterialShortage(requirement.template(), available, requirement.count()));
            }
        }
        return List.copyOf(missing);
    }

    /** The 4.4 marker text of a shortage list. */
    public static List<String> shortages(List<MaterialShortage> missing) {
        List<String> lines = new ArrayList<>(missing.size());
        for (MaterialShortage shortage : missing) {
            lines.add(shortage.template().getItem() + ": " + shortage.have() + "/" + shortage.need() + " available");
        }
        return List.copyOf(lines);
    }

    private BuildMaterials() {}
}
