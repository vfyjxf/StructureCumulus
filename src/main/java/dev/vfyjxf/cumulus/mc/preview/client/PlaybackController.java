package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.ClientConfig;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewPresentation;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import dev.vfyjxf.cumulus.mc.preview.net.Stage;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The staged playback of one preview session: the view cursor, the reveal count per stage and the
 * banner. All of it is view state; every stage index, count and name arrives on the wire.
 */
public final class PlaybackController {

    /** The nominal delay between two cells of one stage at speed 1.0. */
    static final int cellRevealTicks = 2;
    static final int stageRevealMinTicks = 10;
    static final int stageRevealMaxTicks = 100;
    static final int stagePauseTicks = 10;
    /** The banner's full life, 1.2 s. */
    static final int bannerHoldTicks = 24;
    static final int bannerFadeTicks = 3;

    /** The ghost's alpha share of the base alpha: done, current and faint (design 16 §6.2). */
    private static final float doneAlpha = 0.25F;
    private static final float currentAlpha = 1.0F;
    private static final float faintAlpha = 0.08F;

    private static @Nullable NamespacedId blueprintId;
    private static @Nullable PreviewPresentation presentation;
    private static Map<LatticePoint, Integer> stageOfCell = Map.of();
    private static Map<LatticePoint, Integer> positionOfCell = Map.of();
    private static int[] counts = new int[0];
    private static int[] revealed = new int[0];
    private static int selected = -1;
    private static boolean playing;
    private static int elapsed;
    private static int pause;
    private static int announcedStage = -1;
    private static @Nullable String bannerName;
    private static @Nullable String bannerDescription;
    private static int bannerTicks;

    private PlaybackController() {}

    static void accept(PreviewSyncPayload payload) {
        PreviewPresentation block = payload.presentation();
        boolean fresh = blueprintId == null || !blueprintId.equals(payload.blueprintId());
        int previousFrontier = presentation == null ? -1 : presentation.currentStage();
        boolean tracked = !fresh && selected == previousFrontier;
        blueprintId = payload.blueprintId();
        presentation = block;
        List<GhostCell> ghosts = payload.ghostCells();
        Map<LatticePoint, Integer> stages = new TreeMap<>();
        Map<LatticePoint, Integer> positions = new TreeMap<>();
        int[] perStage = new int[block.stages().size()];
        for (GhostCell ghost : ghosts) {
            int stage = ghost.stageIndex();
            if (stage >= perStage.length) {
                continue;
            }
            stages.put(ghost.cell(), stage);
            positions.put(ghost.cell(), perStage[stage]);
            perStage[stage]++;
        }
        stageOfCell = stages;
        positionOfCell = positions;
        counts = perStage;
        int stageCount = block.stages().size();
        int[] next = new int[stageCount];
        for (int stage = 0; stage < Math.min(revealed.length, stageCount); stage++) {
            next[stage] = Math.min(revealed[stage], counts[stage]);
        }
        if (fresh) {
            selected = clamp(block.currentStage());
        } else if (tracked && block.currentStage() >= 0) {
            selected = clamp(block.currentStage());
        } else {
            selected = clamp(selected);
        }
        revealed = next;
        for (int stage = 0; stage < selected; stage++) {
            revealed[stage] = counts[stage];
        }
        if (!fresh && block.currentStage() > previousFrontier && block.currentStage() >= 0) {
            announce(block.currentStage());
        }
        if (playing && !playable()) {
            playing = false;
        }
    }

    /** Drops the per-session facts; the banner goes with them. */
    static void clear() {
        blueprintId = null;
        presentation = null;
        stageOfCell = Map.of();
        positionOfCell = Map.of();
        counts = new int[0];
        revealed = new int[0];
        selected = -1;
        playing = false;
        elapsed = 0;
        pause = 0;
        announcedStage = -1;
        bannerName = null;
        bannerDescription = null;
        bannerTicks = 0;
    }

    /** One client tick: the banner fades and a running playback advances the reveal counts. */
    static void tick() {
        if (bannerTicks > 0) {
            bannerTicks--;
        }
        if (!playing) {
            return;
        }
        if (!playable() || selected < 0) {
            playing = false;
            return;
        }
        int count = counts[selected];
        if (revealed[selected] >= count) {
            if (pause < pauseSpan()) {
                pause++;
                return;
            }
            if (selected + 1 >= counts.length) {
                playing = false;
                revealAll(selected);
                return;
            }
            selected++;
            announce(selected);
            elapsed = 0;
            pause = 0;
            revealed[selected] = 0;
            return;
        }
        elapsed++;
        int span = revealSpan(count);
        int now = span <= 0 ? count : Math.min(count, (int) ((long) elapsed * count / span));
        if (now > revealed[selected]) {
            revealed[selected] = now;
        }
    }

    static void previousStage() {
        move(-1);
    }

    static void nextStage() {
        move(1);
    }

    /** Play or pause; a playback that starts on a finished stage advances after its own hold. */
    static void togglePlay() {
        if (!playable()) {
            playing = false;
            return;
        }
        playing = !playing;
        if (playing) {
            selected = clamp(selected < 0 ? 0 : selected);
            elapsed = 0;
            pause = 0;
        }
    }

    /** One cell step; past the cursor's last cell, the next stage opens with its first cell. */
    static void step() {
        int stageCount = counts.length;
        if (stageCount == 0) {
            return;
        }
        int stage = clamp(selected < 0 ? 0 : selected);
        if (revealed[stage] < counts[stage]) {
            if (selected < 0) {
                selected = stage;
            }
            revealed[stage] = revealed[stage] + 1;
            return;
        }
        if (stage + 1 >= stageCount) {
            selected = stage;
            revealAll(stage);
            return;
        }
        selected = stage + 1;
        announce(selected);
        revealed[selected] = Math.min(1, counts[selected]);
    }

    /** The frontier's stage name on one line and the bay's description on the next, or nothing. */
    public static List<String> bannerLines() {
        if (bannerTicks <= 0 || bannerName == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(2);
        lines.add(bannerName);
        if (bannerDescription != null) {
            lines.add(bannerDescription);
        }
        return List.copyOf(lines);
    }

    static float bannerAlpha() {
        if (bannerTicks <= 0) {
            return 0.0F;
        }
        int shown = bannerHoldTicks - bannerTicks + 1;
        float in = Math.min(1.0F, shown / (float) bannerFadeTicks);
        float out = Math.min(1.0F, bannerTicks / (float) bannerFadeTicks);
        return Math.min(in, out);
    }

    /** The ghost's alpha factor against the cursor, revealed or not (design 16 §6.2). */
    public static float alphaOf(GhostCell ghost) {
        int stage = ghost.stageIndex();
        if (selected < 0) {
            return currentAlpha;
        }
        if (stage < selected) {
            return doneAlpha;
        }
        if (stage == selected && revealed(ghost)) {
            return currentAlpha;
        }
        return faintAlpha;
    }

    /** True when the ghost's own stage reveals it already. */
    public static boolean revealed(GhostCell ghost) {
        return revealedCell(ghost.cell());
    }

    /** True when the cell's stage and the reveal count draw it as revealed; a stage-less cell counts. */
    public static boolean revealedCell(LatticePoint cell) {
        Integer stage = stageOfCell.get(cell);
        if (stage == null || selected < 0) {
            return true;
        }
        if (stage < selected) {
            return true;
        }
        if (stage != selected) {
            return false;
        }
        Integer position = positionOfCell.get(cell);
        return position != null && stage < revealed.length && position < revealed[stage];
    }

    /** The stage a ghost cell belongs to, or null when the cell carries no ghost. */
    public static @Nullable Integer stageOf(LatticePoint cell) {
        return stageOfCell.get(cell);
    }

    public static @Nullable PreviewPresentation presentation() {
        return presentation;
    }

    public static int stageCount() {
        return presentation == null ? 0 : presentation.stages().size();
    }

    /** The cursor, or -1 while there is no stage to look at. */
    public static int selected() {
        return selected;
    }

    public static int revealed(int stage) {
        return stage < 0 || stage >= revealed.length ? 0 : revealed[stage];
    }

    /** The ghosts one stage draws; the position of a cell inside its stage is the play order. */
    public static int ghostCount(int stage) {
        return stage < 0 || stage >= counts.length ? 0 : counts[stage];
    }

    public static boolean playing() {
        return playing;
    }

    public static @Nullable Stage stage(int index) {
        PreviewPresentation block = presentation;
        return block == null || index < 0 || index >= block.stages().size() ? null : block.stages().get(index);
    }

    private static void move(int delta) {
        if (counts.length == 0) {
            return;
        }
        selected = clamp(clamp(selected) + delta);
        revealAll(selected);
        announce(selected);
    }

    private static void announce(int stage) {
        Stage target = stage(stage);
        if (target == null) {
            return;
        }
        if (stage == announcedStage && bannerTicks > 0) {
            return;
        }
        announcedStage = stage;
        bannerName = target.name();
        bannerDescription = target.description();
        bannerTicks = bannerHoldTicks;
    }

    private static void revealAll(int stage) {
        if (stage >= 0 && stage < revealed.length) {
            revealed[stage] = counts[stage];
        }
    }

    private static boolean playable() {
        if (counts.length == 0) {
            return false;
        }
        for (int stage = 0; stage < counts.length; stage++) {
            if (counts[stage] > 0) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int stage) {
        if (counts.length == 0) {
            return -1;
        }
        return Math.max(0, Math.min(stage, counts.length - 1));
    }

    /** The span one stage is revealed over, scaled by the configured speed (design 16 §6.4). */
    private static int revealSpan(int cells) {
        if (!ClientConfig.animationEnabled()) {
            return 1;
        }
        int raw = Math.max(stageRevealMinTicks, Math.min(stageRevealMaxTicks, cells * cellRevealTicks));
        return Math.max(1, (int) Math.ceil(raw / speed()));
    }

    private static int pauseSpan() {
        if (!ClientConfig.animationEnabled()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(stagePauseTicks / speed()));
    }

    private static double speed() {
        double speed = ClientConfig.animationSpeed();
        return speed <= 0.0D ? 1.0D : speed;
    }
}
