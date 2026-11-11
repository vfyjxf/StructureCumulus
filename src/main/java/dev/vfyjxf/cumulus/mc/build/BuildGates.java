package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.remedy.Edit;
import dev.vfyjxf.cumulus.kernel.remedy.EditPlan;
import dev.vfyjxf.cumulus.kernel.remedy.EditStep;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The host-side dry run's gates: volume, removal, foreign claims and item shortage. */
final class BuildGates {

    static List<Finding> evaluate(
        EditPlan plan,
        List<MaterializedEdit> steps,
        BuildPolicy policy,
        @Nullable ClaimView claims,
        ItemSource source
    ) {
        List<Finding> findings = new ArrayList<>();
        CuboidSet touched = touched(plan);
        Cuboid bounds = touched.bounds();
        if (touched.volume() > policy.maxTouchedVolume()) {
            findings.add(
                BuildFindings.at(
                    BuildCodes.policyRefusalEntry,
                    Phase.check,
                    "the plan touches " + touched.volume() + " cells, over maxTouchedVolume "
                            + policy.maxTouchedVolume(),
                    bounds,
                    "raise maxTouchedVolume in config/structurecumulus-server.toml or build a smaller structure"
                )
            );
        }
        if (!policy.allowRemoval() && plan.steps().stream().anyMatch(step -> step.edit() instanceof Edit.Remove)) {
            findings.add(
                BuildFindings.at(
                    BuildCodes.policyRefusalEntry,
                    Phase.check,
                    "the plan removes blocks and removal is off",
                    bounds,
                    "allowRemoval in config/structurecumulus-server.toml or the command's --allow-removal lifts this"
                )
            );
        }
        if (claims != null && bounds != null) {
            List<ClaimRow> foreign = new ArrayList<>();
            for (ClaimRow row : claims.rowsIn(bounds)) {
                if (row.sharing().blocks()) {
                    foreign.add(row);
                }
            }
            if (!foreign.isEmpty()) {
                findings.add(
                    BuildFindings.at(
                        BuildCodes.policyRefusalEntry,
                        Phase.check,
                        foreign.size() + " touched cells are held by a foreign exclusive claim",
                        bounds,
                        "first held by " + incumbent(foreign.get(0))
                    )
                );
            }
        }
        List<String> shortages = shortages(merged(steps), source);
        if (!shortages.isEmpty()) {
            findings.add(
                BuildFindings.at(
                    BuildCodes.itemShortageEntry,
                    Phase.check,
                    "the item source cannot cover the plan",
                    bounds,
                    shortages.toArray(new String[0])
                )
            );
        }
        return findings;
    }

    /** The cells a plan writes: the union of its edits' cells. */
    static CuboidSet touched(EditPlan plan) {
        List<Cuboid> boxes = new ArrayList<>();
        for (EditStep step : plan.steps()) {
            boxes.addAll(cellsOf(step.edit()).decomposition());
        }
        return CuboidSet.of(boxes);
    }

    static CuboidSet cellsOf(Edit edit) {
        return switch (edit) {
            case Edit.Place place -> place.cells();
            case Edit.Remove remove -> remove.cells();
            case Edit.Replace replace -> replace.cells();
            case Edit.SetTrait trait -> trait.cells();
        };
    }

    /** One requirement per item and components pair, in first-seen order. */
    static List<ItemRequirement> merged(List<MaterializedEdit> steps) {
        List<ItemRequirement> merged = new ArrayList<>();
        for (MaterializedEdit step : steps) {
            for (ItemRequirement requirement : step.items()) {
                int index = indexOf(merged, requirement.template());
                if (index < 0) {
                    merged.add(requirement);
                } else {
                    ItemRequirement existing = merged.get(index);
                    merged.set(index, new ItemRequirement(existing.template(), existing.count() + requirement.count()));
                }
            }
        }
        return merged;
    }

    static List<String> shortages(List<ItemRequirement> requirements, ItemSource source) {
        List<String> shortages = new ArrayList<>();
        for (ItemRequirement requirement : requirements) {
            int available = source.available(requirement.template());
            if (available < requirement.count()) {
                shortages.add(
                    requirement.template().getItem() + ": " + available + "/" + requirement.count() + " available"
                );
            }
        }
        return shortages;
    }

    private static int indexOf(List<ItemRequirement> requirements, ItemStack template) {
        for (int index = 0; index < requirements.size(); index++) {
            if (ItemStack.isSameItemSameComponents(requirements.get(index).template(), template)) {
                return index;
            }
        }
        return -1;
    }

    private static String incumbent(ClaimRow row) {
        return row.instance() + " bay " + row.bay().text() + " at " + row.siting().origin();
    }

    private BuildGates() {}
}
