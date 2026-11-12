package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.client.PlaybackController;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewClientState;
import dev.vfyjxf.cumulus.mc.preview.client.PreviewKeys;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.MaterialStatus;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPresentation;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only view of the presentation block and the playback controller, for the stage scenario. */
final class PreviewStageProbe {

    private PreviewStageProbe() {}

    static @Nullable PreviewPresentation presentation() {
        return PlaybackController.presentation();
    }

    static int stageCount() {
        return PlaybackController.stageCount();
    }

    static @Nullable Stage stage(int index) {
        return PlaybackController.stage(index);
    }

    static int currentStage() {
        PreviewPresentation presentation = presentation();
        return presentation == null ? -2 : presentation.currentStage();
    }

    static int selectedStage() {
        return PlaybackController.selected();
    }

    static boolean playing() {
        return PlaybackController.playing();
    }

    static int revealedCells(int stage) {
        return PlaybackController.revealed(stage);
    }

    static int stageCells(int stage) {
        return PlaybackController.ghostCount(stage);
    }

    static List<GhostCell> ghosts(int stage) {
        PreviewSyncPayload session = PreviewClientState.session();
        if (session == null) {
            return List.of();
        }
        List<GhostCell> ghosts = new ArrayList<>();
        for (GhostCell ghost : session.ghostCells()) {
            if (ghost.stageIndex() == stage) {
                ghosts.add(ghost);
            }
        }
        return List.copyOf(ghosts);
    }

    /** True when the ghost list is ordered by stage and the index counts are non-decreasing. */
    static boolean ghostsInStageOrder() {
        PreviewSyncPayload session = PreviewClientState.session();
        if (session == null) {
            return false;
        }
        int previous = 0;
        for (GhostCell ghost : session.ghostCells()) {
            if (ghost.stageIndex() < previous) {
                return false;
            }
            previous = ghost.stageIndex();
        }
        return true;
    }

    static float alpha(int stage) {
        List<GhostCell> ghosts = ghosts(stage);
        return ghosts.isEmpty() ? -1.0F : PlaybackController.alphaOf(ghosts.get(0));
    }

    static List<String> bannerLines() {
        return PlaybackController.bannerLines();
    }

    static List<PlaybackController.PartLabel> partLabels() {
        return PlaybackController.partLabels();
    }

    static List<MaterialStatus> materials() {
        PreviewPresentation presentation = presentation();
        return presentation == null ? List.of() : presentation.materials();
    }

    static int moreMaterials() {
        PreviewPresentation presentation = presentation();
        return presentation == null ? 0 : presentation.moreMaterials();
    }

    /** The stage a world cell belongs to, read from the ghost list; -1 when it carries no ghost. */
    static int stageOf(LatticePoint cell) {
        Integer stage = PlaybackController.stageOf(cell);
        return stage == null ? -1 : stage;
    }

    /** No two preview actions may share a default key: the stage keys must not shadow a control. */
    static @Nullable String keyConflict() {
        Map<PreviewKeys.Action, String> bound = new EnumMap<>(PreviewKeys.Action.class);
        Set<String> seen = new HashSet<>();
        for (PreviewKeys.Action action : PreviewKeys.Action.values()) {
            var mapping = PreviewKeys.binding(action);
            if (mapping == null) {
                return "unbound action: " + action;
            }
            String key = mapping.getDefaultKey().getName();
            bound.put(action, key);
            if (!seen.add(key)) {
                return "two actions share " + key + ": " + bound.values();
            }
        }
        return null;
    }
}
