package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import org.jspecify.annotations.Nullable;

/** What the world-highlight layer currently submits, as the scenarios read it. */
public record VisualHighlightSummary(
    int boundsBoxes,
    int boundsCuboids,
    int selectedBounds,
    int claimCells,
    int conflictCells,
    int conflictInstances,
    int draftCells,
    int movedInstances,
    int invalidatedInstances,
    int setLinks,
    int groupColors,
    @Nullable InstanceId selected
) {

    public int conflicts() {
        return conflictCells + conflictInstances;
    }

    public boolean hasSelection() {
        return selected != null;
    }
}
