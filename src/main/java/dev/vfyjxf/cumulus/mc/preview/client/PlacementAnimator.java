package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.ClientConfig;
import dev.vfyjxf.cumulus.mc.preview.net.BuildCommitPayload;
import dev.vfyjxf.cumulus.mc.preview.scene.CellStates;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The placement feedback of one run: the pop-in, the rollback fade, the rate-limited cue and the
 * formed celebration. View-only state; every cell and every order arrives on the wire.
 */
public final class PlacementAnimator {

    /** One cell's own scale-and-fade duration. */
    static final int popInTicks = 6;
    static final int popInStaggerTicks = 2;
    static final int popInSpanTicks = 40;
    static final int fadeOutTicks = 8;
    /** At most one cue per this many ticks, so a run sounds like a rhythm, not an assault. */
    static final int soundCooldownTicks = 4;

    private static final int celebrateTicks = 6;
    private static final int ringParticles = 12;
    private static final double ringRadius = 1.5D;
    private static final float baseAlpha = 0.55F;
    private static final float startScale = 0.5F;
    private static final float cueVolume = 0.5F;

    private static @Nullable Run run;
    private static List<String> note = List.of();
    private static int noteTicks;
    private static int bursts;
    private static double burstX;
    private static double burstY;
    private static double burstZ;
    private static int started;
    private static int finished;
    private static int cues;
    private static int celebrations;
    private static BuildCommitPayload.@Nullable Status lastStatus;

    private PlacementAnimator() {}

    /** One run: the cells in payload order and the tick its own fade begins. */
    static void accept(BuildCommitPayload payload) {
        lastStatus = payload.status();
        if (payload.truncated() > 0) {
            note = List.of(
                "placement animation: " + payload.cells().size() + " of "
                        + (payload.cells().size() + payload.truncated()) + " cells"
            );
            noteTicks = PlaybackController.bannerHoldTicks;
        }
        if (!ClientConfig.animationEnabled()) {
            return;
        }
        int duration = scaled(payload.status() == BuildCommitPayload.Status.committed ? popInTicks : fadeOutTicks);
        int gaps = Math.max(0, payload.cells().size() - 1);
        int stagger = scaled(gaps == 0 ? 0 : Math.min(popInStaggerTicks, popInSpanTicks / gaps));
        int start = 0;
        List<Cell> cells = new ArrayList<>(payload.cells().size());
        for (BuildCommitPayload.CommitCell cell : payload.cells()) {
            cells.add(new Cell(cell.cell(), CellStates.of(cell.block(), cell.properties()), start));
            start += stagger;
        }
        run = new Run(payload.status(), payload.formed(), cells, duration, start + duration);
        started++;
    }

    /** One client tick: the note and the run's own beats. */
    static void tick() {
        if (noteTicks > 0) {
            noteTicks--;
        }
        if (bursts > 0) {
            ring();
            bursts--;
        }
        Run current = run;
        if (current == null) {
            return;
        }
        current.elapsed++;
        if (current.cooldown > 0) {
            current.cooldown--;
        }
        cue(current);
        if (current.elapsed >= current.span) {
            if (current.formed && !current.celebrated) {
                current.celebrated = true;
                celebrate(current);
            }
            finish();
        }
    }

    /** Drops the run on logout; the celebration's own state goes with it. */
    static void clear() {
        run = null;
        note = List.of();
        noteTicks = 0;
        bursts = 0;
    }

    /** The cells to draw this frame, with the alpha, scale and treatment their own beat implies. */
    static List<Drawn> drawn() {
        Run current = run;
        if (current == null) {
            return List.of();
        }
        List<Drawn> out = new ArrayList<>(current.cells.size());
        boolean removal = current.status != BuildCommitPayload.Status.committed;
        for (Cell cell : current.cells) {
            if (cell.state() == null) {
                continue;
            }
            float progress = progress(current, cell);
            if (progress <= 0.0F) {
                continue;
            }
            if (removal) {
                out.add(new Drawn(cell.cell(), cell.state(), baseAlpha * (1.0F - progress), 1.0F, true));
            } else {
                float scale = startScale + (1.0F - startScale) * progress;
                out.add(new Drawn(cell.cell(), cell.state(), baseAlpha * progress, scale, false));
            }
        }
        return out;
    }

    /** The truncation note's line, empty when there is nothing to say. */
    public static List<String> noteLines() {
        return noteTicks <= 0 ? List.of() : note;
    }

    public static boolean animating() {
        return run != null;
    }

    /** How many cells the current run animates; zero between runs. */
    public static int animatingCells() {
        return run == null ? 0 : run.cells.size();
    }

    public static BuildCommitPayload.@Nullable Status lastStatus() {
        return lastStatus;
    }

    /** How many cues were actually played; the harness reads this instead of the sound manager. */
    public static int soundRequests() {
        return cues;
    }

    public static int celebrations() {
        return celebrations;
    }

    public static int startedRuns() {
        return started;
    }

    public static int finishedRuns() {
        return finished;
    }

    /** One cell of the run as the renderer draws it. */
    record Drawn(LatticePoint cell, BlockState state, float alpha, float scale, boolean removal) {}

    private static final class Run {

        private final BuildCommitPayload.Status status;
        private final boolean formed;
        private final List<Cell> cells;
        private final int duration;
        private final int span;
        private int elapsed;
        private int nextCue;
        private int cooldown;
        private boolean celebrated;

        private Run(BuildCommitPayload.Status status, boolean formed, List<Cell> cells, int duration, int span) {
            this.status = status;
            this.formed = formed;
            this.cells = List.copyOf(cells);
            this.duration = duration;
            this.span = span;
        }
    }

    /** One changed cell, the state it holds now and the tick its own beat starts at. */
    private record Cell(LatticePoint cell, @Nullable BlockState state, int start) {}

    private static float progress(Run run, Cell cell) {
        int local = run.elapsed - cell.start();
        if (local < 0) {
            return 0.0F;
        }
        return run.duration <= 0 ? 1.0F : Math.min(1.0F, local / (float) run.duration);
    }

    /** The cue of the most recently started cells: one sound per cooldown, pitched by the run's progress. */
    private static void cue(Run run) {
        int index = run.nextCue;
        boolean started = false;
        while (index < run.cells.size() && run.cells.get(index).start() <= run.elapsed) {
            index++;
            started = true;
        }
        run.nextCue = index;
        if (!started || run.cooldown > 0) {
            return;
        }
        Cell cell = run.cells.get(index - 1);
        Minecraft minecraft = Minecraft.getInstance();
        if (cell.state() == null || minecraft.level == null || !ClientConfig.animationSound()) {
            return;
        }
        BlockPos pos = new BlockPos(cell.cell().x(), cell.cell().y(), cell.cell().z());
        SoundType type = cell.state().getSoundType(minecraft.level, pos, null);
        SoundEvent sound = run.status == BuildCommitPayload.Status.committed
                ? type.getPlaceSound()
                : type.getBreakSound();
        float fraction = run.span <= 0 ? 1.0F : Math.min(1.0F, run.elapsed / (float) run.span);
        minecraft.level.playLocalSound(
            pos.getX() + 0.5D,
            pos.getY() + 0.5D,
            pos.getZ() + 0.5D,
            sound,
            SoundSource.BLOCKS,
            cueVolume,
            0.9F + 0.4F * fraction,
            false
        );
        cues++;
        run.cooldown = scaled(soundCooldownTicks);
    }

    /** The formed celebration: one level-up cue and a ring of vanilla particles over a few ticks. */
    private static void celebrate(Run run) {
        if (!ClientConfig.animationCelebrate()) {
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Cell cell : run.cells) {
            LatticePoint point = cell.cell();
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x() + 1);
            maxY = Math.max(maxY, point.y() + 1);
            maxZ = Math.max(maxZ, point.z() + 1);
        }
        burstX = (minX + maxX) / 2.0D;
        burstY = (minY + maxY) / 2.0D;
        burstZ = (minZ + maxZ) / 2.0D;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.level.playLocalSound(
                burstX,
                burstY,
                burstZ,
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                0.7F,
                1.0F,
                false
            );
        }
        bursts = celebrateTicks;
        celebrations++;
    }

    private static void ring() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (int index = 0; index < ringParticles; index++) {
            double angle = 2.0D * Math.PI * index / ringParticles;
            minecraft.level.addParticle(
                ParticleTypes.HAPPY_VILLAGER,
                burstX + Math.cos(angle) * ringRadius,
                burstY,
                burstZ + Math.sin(angle) * ringRadius,
                0.0D,
                0.05D,
                0.0D
            );
        }
    }

    private static void finish() {
        run = null;
        finished++;
    }

    private static int scaled(int ticks) {
        if (ticks <= 0) {
            return 0;
        }
        double speed = ClientConfig.animationSpeed();
        if (speed <= 0.0D) {
            speed = 1.0D;
        }
        return Math.max(1, (int) Math.ceil(ticks / speed));
    }
}
