package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPresentation;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import dev.vfyjxf.cumulus.mc.preview.scene.CellStates;
import dev.vfyjxf.cumulus.mc.preview.scene.NextStepLine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The guided build: a view of an open session that points at the stage the world is working on, says
 * in one line what to place, lets its ghosts pulse for the item in hand and answers a hovered ghost
 * with its expected state. It is a view: it writes nothing, sends no control, derives no stage, count
 * or order, and dies with the session (design 16 §12, D-97).
 */
public final class GuideController {

    /** The hand pulse's period in ticks: a slow oscillation, not a blink. */
    static final int pulsePeriodTicks = 40;
    /** A pulsing ghost draws at this share of its alpha or brighter, up to {@link #pulsePeak}. */
    static final float pulseFloor = 1.0F;
    static final float pulsePeak = 1.5F;

    private static boolean guided;
    private static boolean confirmPending;
    private static Set<LatticePoint> pulsing = Set.of();

    private GuideController() {}

    /** True while the session's guide is on; false in every other session state. */
    public static boolean guided() {
        return guided;
    }

    /**
     * `G`: on with the whole cursor stage revealed, off with the emphasis gone (design 16 §6.3, §12.1).
     * The guide and the cinema mode look at the same session from two sides — one wants a hand, the
     * other an eye — so arming either leaves the other (design 16 §6.7).
     */
    static void toggle() {
        if (!guided) {
            CinematicController.leaveForGuide();
        }
        guided = !guided;
        if (guided) {
            PlaybackController.revealStage();
        }
    }

    /** The cinema mode takes the frame: the guide goes quiet without a key change. */
    static void disarm() {
        guided = false;
        pulsing = Set.of();
    }

    /** A confirm is on its way; whether the plan survives it decides the automatic entry (§12.1). */
    static void confirmSent() {
        confirmPending = true;
    }

    /** Every sync: a confirm that left the plan in the session arms the guide, a closed one does not. */
    static void accept(PreviewSyncPayload payload) {
        if (!confirmPending) {
            return;
        }
        confirmPending = false;
        if (complete(payload.presentation())) {
            return;
        }
        guided = true;
        PlaybackController.revealStage();
    }

    static void clear() {
        guided = false;
        confirmPending = false;
        pulsing = Set.of();
    }

    /** One client tick: the hand the pulse reads is the hand of this frame. */
    static void tick() {
        pulsing = guided ? readPulse() : Set.of();
    }

    /** The cursor stage's unsatisfied cells in the wire's own order: the next group to place (§12.2). */
    public static List<LatticePoint> nextGroup() {
        List<LatticePoint> cells = new ArrayList<>();
        for (GhostCell ghost : groupGhosts()) {
            cells.add(ghost.cell());
        }
        return List.copyOf(cells);
    }

    /** The guide's one line, or null when no session is open (design 16 §12.3). */
    public static @Nullable String nextStep() {
        PreviewSyncPayload session = PreviewClientState.session();
        if (session == null) {
            return null;
        }
        PreviewPresentation presentation = session.presentation();
        int index = PlaybackController.selected();
        Stage stage = PlaybackController.stage(index);
        int unsatisfied = stage == null ? 0 : Math.max(0, stage.expectedCells() - stage.satisfiedCells());
        List<GhostCell> group = groupGhosts();
        boolean breakable = false;
        for (CellMark mark : session.overlay().cells().values()) {
            if (mark == CellMark.wrong || mark == CellMark.blocked) {
                breakable = true;
                break;
            }
        }
        return line(group, unsatisfied, stage, index, presentation.stages().size(), complete(presentation), breakable);
    }

    /** The formatter: the data comes from the payload, the wording is {@link NextStepLine}'s (design 16 §12.3). */
    static String line(
        List<GhostCell> group,
        int unsatisfied,
        @Nullable Stage stage,
        int stageIndex,
        int stageCount,
        boolean complete,
        boolean breakable
    ) {
        return NextStepLine.render(group, unsatisfied, stage, stageIndex, stageCount, complete, breakable);
    }

    /** True when every expected cell of the session is present: the guide's completion state (§12.5). */
    public static boolean complete() {
        PreviewSyncPayload session = PreviewClientState.session();
        return session != null && complete(session.presentation());
    }

    private static boolean complete(PreviewPresentation presentation) {
        for (Stage stage : presentation.stages()) {
            if (stage.satisfiedCells() < stage.expectedCells()) {
                return false;
            }
        }
        return true;
    }

    /** The ghosts the main-hand item can satisfy now; empty when the hand adds nothing (§12.4). */
    public static List<LatticePoint> pulsingCells() {
        return List.copyOf(new TreeSet<>(pulsing));
    }

    public static boolean pulsing(LatticePoint cell) {
        return !pulsing.isEmpty() && pulsing.contains(cell);
    }

    /** The alpha factor a pulsing ghost draws at; 1.0 for every other ghost. */
    public static float pulseFactor(LatticePoint cell) {
        if (!pulsing(cell)) {
            return 1.0F;
        }
        return pulseAt(PreviewClientState.gameTime());
    }

    /** The pure oscillation: the factor in [floor, peak], one period per {@link #pulsePeriodTicks}. */
    static float pulseAt(long tick) {
        double phase = 2.0D * Math.PI * Math.floorMod(tick, (long) pulsePeriodTicks) / pulsePeriodTicks;
        return (float) (pulseFloor + (pulsePeak - pulseFloor) * ((1.0D + Math.sin(phase)) / 2.0D));
    }

    /** The cursor stage's ghost cells, in the wire order the server emitted. */
    private static List<GhostCell> groupGhosts() {
        PreviewSyncPayload session = PreviewClientState.session();
        int stage = PlaybackController.selected();
        if (session == null || stage < 0) {
            return List.of();
        }
        List<GhostCell> cells = new ArrayList<>();
        for (GhostCell ghost : session.ghostCells()) {
            if (ghost.stageIndex() == stage) {
                cells.add(ghost);
            }
        }
        return List.copyOf(cells);
    }

    /** The match is by item, against the expected state's block item, from payload data only (§12.4). */
    private static Set<LatticePoint> readPulse() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return Set.of();
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            return Set.of();
        }
        Set<LatticePoint> cells = new TreeSet<>();
        for (GhostCell ghost : groupGhosts()) {
            BlockState state = CellStates.of(ghost.block(), ghost.properties());
            if (state != null && state.getBlock().asItem() == held.getItem()) {
                cells.add(ghost.cell());
            }
        }
        return Set.copyOf(cells);
    }

    /**
     * One ghost's tooltip: its expected block and properties, the part that owns it and its stage
     * (design 16 §10's cell lines, in one HUD line). Null when the cell carries no ghost.
     */
    static @Nullable String hoverText(LatticePoint cell) {
        PreviewSyncPayload session = PreviewClientState.session();
        if (session == null) {
            return null;
        }
        for (GhostCell ghost : session.ghostCells()) {
            if (!ghost.cell().equals(cell)) {
                continue;
            }
            StringBuilder text = new StringBuilder(ghost.block().text());
            if (!ghost.properties().isEmpty()) {
                text.append('[');
                boolean first = true;
                for (Map.Entry<String, String> property : new TreeMap<>(ghost.properties()).entrySet()) {
                    if (!first) {
                        text.append(',');
                    }
                    text.append(property.getKey()).append('=').append(property.getValue());
                    first = false;
                }
                text.append(']');
            }
            Stage stage = PlaybackController.stage(ghost.stageIndex());
            if (stage != null) {
                text.append(" \u00B7 part ").append(stage.name()).append(" \u00B7 stage ")
                        .append(ghost.stageIndex() + 1).append('/').append(PlaybackController.stageCount());
            }
            return text.toString();
        }
        return null;
    }

    /** An overlay frame without a ghost: the mark the server shipped, and nothing it did not. */
    static String markText(CellMark mark) {
        return "overlay " + mark;
    }
}
