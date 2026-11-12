package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 16 §12.3's next-step line, the pack's one formatter: the verb, the count and item of a stage's
 * unsatisfied cells, the stage reference and its carried hint. The world's HUD and the screen both
 * read this, so the wording exists once.
 */
public final class NextStepLine {

    private NextStepLine() {}

    /**
     * `place` when every unsatisfied cell of the group is missing, `replace` when one must be broken
     * first; the group's largest distinct required item wins; the stage's carried hint is the why.
     */
    public static String render(
        List<GhostCell> group,
        int unsatisfied,
        @Nullable Stage stage,
        int stageIndex,
        int stageCount,
        boolean complete,
        boolean breakable
    ) {
        StringBuilder text = new StringBuilder();
        // a stage with no ghost left to draw still has cells: nothing can be placed, so the verb is
        // replace; a shortened ghost list with a clean world stays place, because the cap is no fault
        boolean replace = group.isEmpty() && unsatisfied > 0 || group.size() < unsatisfied && breakable;
        if (group.isEmpty()) {
            text.append(replace ? "replace" : "nothing left to place");
        } else {
            NamespacedId item = null;
            int count = 0;
            Map<NamespacedId, Integer> counts = new LinkedHashMap<>();
            for (GhostCell ghost : group) {
                counts.merge(ghost.block(), 1, Integer::sum);
            }
            for (Map.Entry<NamespacedId, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > count) {
                    item = entry.getKey();
                    count = entry.getValue();
                }
            }
            text.append(replace ? "replace" : "place").append(' ').append(count).append('\u00D7').append(' ');
            text.append(item == null ? "" : item.text());
        }
        if (stage != null) {
            text.append(" (stage ").append(stageIndex + 1).append('/').append(stageCount).append(": ")
                    .append(stage.name()).append(')');
            // the hint is the why of a cell to place; a stage with nothing left to place has no why
            if (stage.hint() != null && !(group.isEmpty() && unsatisfied == 0)) {
                text.append(" \u00B7 ").append(stage.hint());
            }
        }
        if (stage != null && complete && unsatisfied == 0) {
            text.append(" \u00B7 settle takes the claim; a confirm builds nothing");
        }
        return text.toString();
    }
}
