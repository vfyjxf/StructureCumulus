package dev.vfyjxf.cumulus.mc.preview.net;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One session's presentation facts: the blueprint's own text, the stage table in index order, the
 * frontier stage and the plan's material shortages. Every one of them is server-computed.
 */
public record PreviewPresentation(
    @Nullable String description,
    List<Stage> stages,
    int currentStage,
    List<MaterialStatus> materials,
    int moreMaterials
) {

    public PreviewPresentation {
        stages = List.copyOf(stages);
        materials = List.copyOf(materials);
        boolean empty = stages.isEmpty();
        if (empty ? currentStage != -1 : currentStage < 0 || currentStage >= stages.size()) {
            throw new IllegalArgumentException("currentStage must name a stage, or -1 on an empty table");
        }
        if (moreMaterials < 0) {
            throw new IllegalArgumentException("moreMaterials must not be negative");
        }
    }
}
