package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPresentation;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A structure set's page, read from the catalogue's composed entry and no kernel decision: the stage
 * table is the server's flattened (member × member-stage) build (design 19 §2), every stage naming its
 * member and carrying its own bay, and the member labels are the page's group keys. An older payload
 * with no stage table falls back to one stage per member, the pre-SC13 reading.
 */
public record SetPage(
    PreviewCatalogPayload.SetEntry entry,
    List<Stage> stages,
    List<GhostCell> cells,
    Map<NamespacedId, String> bayLabels
) {

    public SetPage {
        Objects.requireNonNull(entry, "entry");
        stages = List.copyOf(stages);
        cells = List.copyOf(cells);
        bayLabels = Map.copyOf(bayLabels);
        for (int index = 0; index < stages.size(); index++) {
            if (memberOfStage(stages, entry, index) < 0) {
                throw new IllegalArgumentException("stage " + index + " names no member");
            }
        }
    }

    /** The server's flattened table, root first and satellites in declaration order (design 19 §2). */
    public static SetPage of(PreviewCatalogPayload.SetEntry entry) {
        List<Stage> stages = entry.stages().isEmpty() ? memberStages(entry) : entry.stages();
        List<GhostCell> cells = new ArrayList<>();
        Map<NamespacedId, String> labels = new LinkedHashMap<>();
        for (int index = 0; index < entry.members().size(); index++) {
            NamespacedId bay = PreviewCatalogPayload.SetEntry.memberBay(entry.setId(), index);
            labels.put(bay, entry.members().get(index).label());
            cells.addAll(entry.members().get(index).cells());
        }
        return new SetPage(entry, stages, cells, labels);
    }

    /** The one bay a member's stages share; never a document bay, only the page's grouping key. */
    public static NamespacedId bay(NamespacedId setId, int member) {
        return PreviewCatalogPayload.SetEntry.memberBay(setId, member);
    }

    /** An older payload's one stage per member, the labels as the names (design 18 §4's first reading). */
    private static List<Stage> memberStages(PreviewCatalogPayload.SetEntry entry) {
        List<Stage> stages = new ArrayList<>(entry.members().size());
        for (int index = 0; index < entry.members().size(); index++) {
            PreviewCatalogPayload.Member member = entry.members().get(index);
            NamespacedId bay = bay(entry.setId(), index);
            stages.add(new Stage(member.label(), member.cells().size(), 0, null, null, bay, member.label()));
        }
        return List.copyOf(stages);
    }

    public NamespacedId id() {
        return entry.setId();
    }

    /** The stage table the screen, the timeline and the playback read (design 16 §5's shape). */
    public PreviewPresentation presentation() {
        return new PreviewPresentation(null, stages, stages.isEmpty() ? -1 : 0, List.of(), 0);
    }

    /** The member a stage belongs to, or -1 when the stage names no member. */
    public int memberOfStage(int stage) {
        return memberOfStage(stages, entry, stage);
    }

    /** The member a cell belongs to, or -1 when the composed cell names no stage. */
    public int memberOf(GhostCell ghost) {
        return memberOfStage(ghost.stageIndex());
    }

    /** The member a grouping key names, or -1 when the key is not one of the page's parts. */
    public int memberOfBay(NamespacedId bay) {
        for (int member = 0; member < entry.members().size(); member++) {
            if (bay(entry.setId(), member).equals(bay)) {
                return member;
            }
        }
        return -1;
    }

    /** The blueprint a click on one of the member's cells opens, or null when it names no member. */
    public @Nullable NamespacedId memberBlueprint(int member) {
        return member < 0 || member >= entry.members().size() ? null : entry.members().get(member).blueprintId();
    }

    private static int memberOfStage(List<Stage> stages, PreviewCatalogPayload.SetEntry entry, int stage) {
        if (stage < 0 || stage >= stages.size()) {
            return -1;
        }
        NamespacedId bay = stages.get(stage).bay();
        for (int member = 0; member < entry.members().size(); member++) {
            if (bay(entry.setId(), member).equals(bay)) {
                return member;
            }
        }
        return -1;
    }
}
