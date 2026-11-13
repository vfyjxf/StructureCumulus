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
 * A structure set's page, derived on the client from the catalogue's composed entry and no kernel
 * decision: the members are the stages, root first, and the member labels are the stage names
 * (design 18 §4). The cells are already composed by the server (design 18 §3).
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
        if (stages.size() != entry.members().size()) {
            throw new IllegalArgumentException("one stage per member");
        }
    }

    /** One stage per member, root first and satellites in declaration order (design 18 §4). */
    public static SetPage of(PreviewCatalogPayload.SetEntry entry) {
        List<Stage> stages = new ArrayList<>(entry.members().size());
        List<GhostCell> cells = new ArrayList<>();
        Map<NamespacedId, String> labels = new LinkedHashMap<>();
        for (int index = 0; index < entry.members().size(); index++) {
            PreviewCatalogPayload.Member member = entry.members().get(index);
            NamespacedId bay = bay(entry.setId(), index);
            stages.add(new Stage(member.label(), member.cells().size(), 0, null, null, bay));
            labels.put(bay, member.label());
            cells.addAll(member.cells());
        }
        return new SetPage(entry, stages, cells, labels);
    }

    /** The one bay a member's stage owns; never a document bay, only the page's grouping key. */
    public static NamespacedId bay(NamespacedId setId, int member) {
        return NamespacedId.of(setId.namespace(), setId.path() + "/member/" + member);
    }

    public NamespacedId id() {
        return entry.setId();
    }

    /** The stage table the screen, the stage bar and the playback read (design 16 §5's shape). */
    public PreviewPresentation presentation() {
        return new PreviewPresentation(null, stages, stages.isEmpty() ? -1 : 0, List.of(), 0);
    }

    /** The member a cell belongs to, or -1 when the composed cell names no member. */
    public int memberOf(GhostCell ghost) {
        int index = ghost.stageIndex();
        return index >= 0 && index < entry.members().size() ? index : -1;
    }

    /** The blueprint a click on one of the member's cells opens, or null when it names no member. */
    public @Nullable NamespacedId memberBlueprint(int member) {
        return member < 0 || member >= entry.members().size() ? null : entry.members().get(member).blueprintId();
    }
}
