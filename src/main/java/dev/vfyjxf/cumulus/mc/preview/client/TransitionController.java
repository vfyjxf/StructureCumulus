package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.mc.preview.client.cine.Ease;
import dev.vfyjxf.cumulus.mc.preview.client.cine.TurntableTransition;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The hand-off animations of a preview (design 16 §6.7): the 12-tick stage advance, the 6-tick
 * turntable on a candidate switch, the 4-tick flip crossfade and the 8-tick session fade. All of it
 * is view state driven by the payloads and the playback cursor; nothing here reads the world, and
 * every duration is a tick constant.
 */
public final class TransitionController {

    /** The design's transition table: stage advance 12t, turntable 6t, flip 4t, session 8t. */
    static final int stageAdvanceTicks = 12;
    static final int turntableTicks = 6;
    static final int flipTicks = 4;
    static final int sessionFadeTicks = 8;
    /** The pop-in atom reused from E2: six ticks per cell, two ticks of stagger, at most half the span. */
    static final int popInTicks = 6;
    static final int popInStaggerTicks = 2;
    private static final float popInStartScale = 0.5F;

    private static int lastStage = -1;
    private static @Nullable StageAdvance stageAdvance;
    private static @Nullable Turntable turntable;
    private static @Nullable Crossfade crossfade;
    private static @Nullable FadeOut fadeOut;

    private TransitionController() {}

    /** One sync: a pose change starts its swing, a different blueprint a crossfade, the first one a fade-in. */
    static void onPayload(@Nullable PreviewSyncPayload previous, PreviewSyncPayload current) {
        observeCursor();
        if (previous == null) {
            crossfade = new Crossfade(FadeKind.session, List.of(), sessionFadeTicks);
            return;
        }
        if (!previous.blueprintId().equals(current.blueprintId())) {
            crossfade = new Crossfade(FadeKind.session, previous.ghostCells(), sessionFadeTicks);
            turntable = null;
            return;
        }
        Turn previousTurn = turnOf(previous);
        Turn currentTurn = turnOf(current);
        if (previousTurn != currentTurn) {
            turntable = new Turntable(previousTurn, currentTurn, previous.ghostCells(), pivotOf(current));
            crossfade = null;
        } else if (mirrorOf(previous) != mirrorOf(current)) {
            crossfade = new Crossfade(FadeKind.flip, previous.ghostCells(), flipTicks);
            turntable = null;
        }
    }

    /** The session is gone: keep its last ghost set a moment longer so the fade-out has something to fade. */
    static void onSessionClosed(@Nullable PreviewSyncPayload session) {
        if (session != null && !session.ghostCells().isEmpty()) {
            fadeOut = new FadeOut(session.ghostCells());
        }
        lastStage = -1;
        stageAdvance = null;
        turntable = null;
        crossfade = null;
    }

    static void clear() {
        lastStage = -1;
        stageAdvance = null;
        turntable = null;
        crossfade = null;
        fadeOut = null;
    }

    /** One client tick: every span advances and a moved playback cursor starts its advance. */
    static void tick() {
        if (stageAdvance != null) {
            stageAdvance.elapsed++;
            if (stageAdvance.elapsed >= stageAdvanceTicks) {
                stageAdvance = null;
            }
        }
        if (turntable != null) {
            turntable.elapsed++;
            if (turntable.elapsed >= turntableTicks) {
                turntable = null;
            }
        }
        if (crossfade != null) {
            crossfade.elapsed++;
            if (crossfade.elapsed >= crossfade.duration) {
                crossfade = null;
            }
        }
        if (fadeOut != null) {
            fadeOut.elapsed++;
            if (fadeOut.elapsed >= sessionFadeTicks) {
                fadeOut = null;
            }
        }
        observeCursor();
    }

    /** The stage alpha the ghost pass draws with, before the base and layer factors. */
    public static float ghostAlpha(GhostCell ghost, float partialTick) {
        float alpha = PlaybackController.alphaOf(ghost);
        if (stageAdvance != null) {
            alpha = stageAdvance.alpha(ghost, partialTick, alpha);
        }
        if (turntable != null) {
            return 0.0F;
        }
        if (crossfade != null) {
            alpha *= crossfade.progress(partialTick);
        }
        return alpha;
    }

    /** The pop-in scale of an arriving stage; one for every other ghost. */
    public static float ghostScale(GhostCell ghost, float partialTick) {
        return stageAdvance == null ? 1.0F : stageAdvance.scale(ghost, partialTick);
    }

    public static boolean turntableActive() {
        return turntable != null;
    }

    /** How many ticks the swing has run; the second half of the six gets it to the new pose. */
    public static int turntableTicks() {
        return turntable == null ? 0 : turntable.elapsed;
    }

    public static int turntableDurationTicks() {
        return turntable == null ? 0 : turntable.swing.durationTicks();
    }

    /** The swing angle in degrees; the shortest arc from the old turn, zero when no swing is running. */
    public static double turntableAngleDeg() {
        return turntable == null ? 0.0D : turntable.angle(0.0D);
    }

    /** The pose the baked model keeps: the old turn until the swing's last tick, then the new one. */
    public static @Nullable Turn turntableModelTurn() {
        return turntable == null ? null : turntable.modelTurn();
    }

    public static boolean stageAdvanceActive() {
        return stageAdvance != null;
    }

    public static int stageAdvanceTicks() {
        return stageAdvance == null ? 0 : stageAdvance.elapsed;
    }

    public static int stageAdvanceDurationTicks() {
        return stageAdvance == null ? 0 : stageAdvanceTicks;
    }

    /** The 8-tick session fade-in, fade-out or variant switch; the flip's shorter fade is not one. */
    public static boolean sessionFadeActive() {
        return crossfade != null && crossfade.kind == FadeKind.session || fadeOut != null;
    }

    public static int sessionFadeTicks() {
        if (crossfade != null && crossfade.kind == FadeKind.session) {
            return crossfade.elapsed;
        }
        return fadeOut == null ? 0 : fadeOut.elapsed;
    }

    /** The old ghost set a frame still draws: rotated for a turntable, fading for a flip or a close. */
    static @Nullable GhostLayer extraLayer(float partialTick) {
        if (turntable != null) {
            return new GhostLayer(turntable.ghosts, 1.0F, turntable.angle(partialTick), turntable.pivot);
        }
        if (crossfade != null) {
            return new GhostLayer(crossfade.ghosts, 1.0F - crossfade.progress(partialTick), 0.0D, Vec3.ZERO);
        }
        if (fadeOut != null) {
            return new GhostLayer(fadeOut.ghosts, 1.0F - fadeOut.progress(partialTick), 0.0D, Vec3.ZERO);
        }
        return null;
    }

    private static void observeCursor() {
        int selected = PlaybackController.selected();
        if (selected == lastStage) {
            return;
        }
        int previous = lastStage;
        lastStage = selected;
        if (previous >= 0 && selected >= 0) {
            stageAdvance = new StageAdvance(previous, selected);
        }
    }

    private static Turn turnOf(PreviewSyncPayload payload) {
        return payload.candidates().get(payload.current()).siting().placement().orientation().turn();
    }

    private static boolean mirrorOf(PreviewSyncPayload payload) {
        return payload.candidates().get(payload.current()).siting().placement().orientation().isMirror();
    }

    /** A turn rotates about the placement's own vertical axis: origin plus the placement offset. */
    private static Vec3 pivotOf(PreviewSyncPayload payload) {
        var siting = payload.candidates().get(payload.current()).siting();
        LatticePoint origin = siting.origin();
        LatticePoint offset = siting.placement().offset();
        return new Vec3(
            origin.x() + (double) offset.x(),
            origin.y() + (double) offset.y(),
            origin.z() + (double) offset.z()
        );
    }

    /** One frame's old ghost set, the angle to swing it about {@code pivot}, and its alpha. */
    record GhostLayer(List<GhostCell> ghosts, float alpha, double angleDeg, Vec3 pivot) {

        GhostLayer {
            ghosts = List.copyOf(ghosts);
            Objects.requireNonNull(pivot, "pivot");
        }
    }

    private enum FadeKind {
        flip, session
    }

    private static final class StageAdvance {

        private final int fromStage;
        private final int toStage;
        private int elapsed;

        private StageAdvance(int fromStage, int toStage) {
            this.fromStage = fromStage;
            this.toStage = toStage;
        }

        private float alpha(GhostCell ghost, float partialTick, float current) {
            double tick = elapsed + partialTick;
            if (ghost.stageIndex() == fromStage) {
                double progress = clamp(tick / stageAdvanceTicks);
                return (float) (1.0D + (current - 1.0D) * progress);
            }
            if (ghost.stageIndex() == toStage) {
                return (float) (current * popIn(ghost, tick));
            }
            return current;
        }

        private float scale(GhostCell ghost, float partialTick) {
            if (ghost.stageIndex() != toStage) {
                return 1.0F;
            }
            double progress = popIn(ghost, elapsed + partialTick);
            return (float) (popInStartScale + (1.0D - popInStartScale) * progress);
        }

        private double popIn(GhostCell ghost, double tick) {
            int position = PlaybackController.positionOf(ghost.cell());
            int delay = position < 0 ? 0 : Math.min(position * popInStaggerTicks, stageAdvanceTicks - popInTicks);
            return clamp((tick - delay) / popInTicks);
        }
    }

    private static final class Turntable {

        private final TurntableTransition swing;
        private final List<GhostCell> ghosts;
        private final Vec3 pivot;
        private int elapsed;

        private Turntable(Turn from, Turn to, List<GhostCell> ghosts, Vec3 pivot) {
            this.swing = new TurntableTransition(from, to, turntableTicks, Ease.inOutCubic);
            this.ghosts = List.copyOf(ghosts);
            this.pivot = pivot;
        }

        private double angle(double partialTick) {
            return swing.angleDeg(elapsed + partialTick);
        }

        private Turn modelTurn() {
            return swing.modelTurn(elapsed);
        }
    }

    private static final class Crossfade {

        private final FadeKind kind;
        private final List<GhostCell> ghosts;
        private final int duration;
        private int elapsed;

        private Crossfade(FadeKind kind, List<GhostCell> ghosts, int duration) {
            this.kind = kind;
            this.ghosts = List.copyOf(ghosts);
            this.duration = duration;
        }

        private float progress(float partialTick) {
            return (float) Ease.smoothstep.applyClamped((elapsed + partialTick) / duration);
        }
    }

    private static final class FadeOut {

        private final List<GhostCell> ghosts;
        private int elapsed;

        private FadeOut(List<GhostCell> ghosts) {
            this.ghosts = List.copyOf(ghosts);
        }

        private float progress(float partialTick) {
            return (float) Ease.smoothstep.applyClamped((elapsed + partialTick) / sessionFadeTicks);
        }
    }

    private static double clamp(double value) {
        return Math.min(1.0D, Math.max(0.0D, value));
    }
}
