package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.CellMark;
import dev.vfyjxf.cumulus.kernel.remedy.Marker;
import dev.vfyjxf.cumulus.kernel.remedy.MarkerKind;
import dev.vfyjxf.cumulus.kernel.remedy.OverlayModel;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewControlPayload;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewSyncPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's half of a preview: the last synced payload, the local view toggles (Y slice, all vs
 * problems) and the key handling. It computes no candidate and no mark; it only renders and sends
 * controls.
 */
public final class PreviewClientState {

    /** The server heartbeats every 20 ticks; this long without one means the session is gone. */
    private static final long staleTicks = 60L;
    /** After this many ticks held, R returns the identity pose instead of the first candidate. */
    private static final int longPressTicks = 12;
    /** 4.8: ghosts and frames are drawn within this many blocks of the eye. */
    private static final double renderRadius = 48.0D;

    private static volatile @Nullable PreviewSyncPayload session;
    private static volatile Matrix4f viewMatrix = new Matrix4f();
    private static volatile Matrix4f projectionMatrix = new Matrix4f();
    private static volatile Vec3 cameraPosition = Vec3.ZERO;
    private static volatile boolean rendered;
    private static volatile boolean problemsOnly;
    private static volatile int sliceY = Integer.MIN_VALUE;
    private static volatile @Nullable String hoverTooltip;
    private static long lastSyncTick;
    private static int resetHeldTicks;

    private PreviewClientState() {}

    /** The latest synced session, or null when there is none. */
    public static @Nullable PreviewSyncPayload session() {
        return session;
    }

    public static boolean problemsOnly() {
        return problemsOnly;
    }

    /** The Y layer the slice is on; {@link Integer#MIN_VALUE} until a payload sets it. */
    public static int sliceY() {
        return sliceY;
    }

    /** The blocked-cell / marker tooltip under the crosshair, or null. */
    public static @Nullable String hoverTooltip() {
        return hoverTooltip;
    }

    /** True once the HUD drew a frame for the current session. */
    public static boolean visible() {
        return session != null && rendered;
    }

    public static void toggleProblemsOnly() {
        problemsOnly = !problemsOnly;
    }

    /** The HUD's exact text model, line by line; the scenarios assert on it. */
    public static List<String> hudLines() {
        PreviewSyncPayload current = session;
        if (current == null) {
            return List.of();
        }
        OverlayModel overlay = current.overlay();
        int present = 0;
        int problems = 0;
        for (CellMark mark : overlay.cells().values()) {
            if (mark == CellMark.present) {
                present++;
            } else {
                problems++;
            }
        }
        int total = present + problems;
        var siting = current.candidates().get(current.current()).siting();
        List<String> lines = new ArrayList<>();
        lines.add("preview: " + current.blueprintId().text() + " · " + present + "/" + total);
        lines.add(
            "candidate " + (current.current() + 1) + "/" + current.candidates().size() + " · turn "
                    + siting.placement().orientation().turn() + " · flip "
                    + (siting.placement().orientation().isMirror() ? "mirrored" : "none") + " · origin "
                    + describe(siting.origin())
        );
        lines.add(
            "mode: " + (problemsOnly ? "problems" : "all") + " · slice y=" + sliceLabel(sliceY()) + " · ghosts "
                    + current.ghostCells().size() + " · problems " + problems
        );
        int shown = 0;
        for (Marker marker : current.markers()) {
            if (shown >= 4) {
                lines.add("… " + (current.markers().size() - shown) + " more marker(s)");
                break;
            }
            lines.add(kindText(marker.kind()) + ": " + marker.label());
            shown++;
        }
        String tooltip = hoverTooltip;
        if (tooltip != null) {
            lines.add("hover: " + tooltip);
        }
        lines.add(
            "keys: [ ] turn · ; flip · , . origin · R reset (hold = identity) · P recall · PgUp/PgDn slice · T all/problems · Enter confirm · Esc cancel"
        );
        return List.copyOf(lines);
    }

    static void accept(PreviewSyncPayload payload) {
        session = payload;
        lastSyncTick = now();
        hoverTooltip = null;
        if (sliceY == Integer.MIN_VALUE) {
            sliceY = payload.overlay().bounds().min().y();
        }
        PreviewOverlayRenderer.onPayload(payload);
    }

    static void clear() {
        session = null;
        rendered = false;
        hoverTooltip = null;
        sliceY = Integer.MIN_VALUE;
        resetHeldTicks = 0;
        PreviewOverlayRenderer.clear();
    }

    static void markRendered() {
        rendered = true;
    }

    /** The level pass's own matrices, kept so a marker billboard projects exactly as the world did. */
    static void captureMatrices(Matrix4f view, Matrix4f projection, Vec3 camera) {
        viewMatrix = new Matrix4f(view);
        projectionMatrix = new Matrix4f(projection);
        cameraPosition = camera;
    }

    static Matrix4f viewMatrix() {
        return viewMatrix;
    }

    static Matrix4f projectionMatrix() {
        return projectionMatrix;
    }

    static Vec3 cameraPosition() {
        return cameraPosition;
    }

    static boolean isVisible() {
        return session != null;
    }

    /** Called once per client tick: drops a stale session and dispatches the pending controls. */
    static void tick() {
        PreviewSyncPayload current = session;
        if (current == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }
        if (now() - lastSyncTick > staleTicks) {
            clear();
            return;
        }
        updateTooltip(current);
        dispatchControls();
    }

    private static void dispatchControls() {
        if (clicked(PreviewKeys.Action.turnCw)) {
            send(PreviewControlPayload.Control.turnCw, null);
        }
        if (clicked(PreviewKeys.Action.turnCcw)) {
            send(PreviewControlPayload.Control.turnCcw, null);
        }
        if (clicked(PreviewKeys.Action.flip)) {
            send(PreviewControlPayload.Control.flip, null);
        }
        if (clicked(PreviewKeys.Action.nextOrigin)) {
            send(PreviewControlPayload.Control.nextOrigin, null);
        }
        if (clicked(PreviewKeys.Action.prevOrigin)) {
            send(PreviewControlPayload.Control.prevOrigin, null);
        }
        if (clicked(PreviewKeys.Action.recall)) {
            send(PreviewControlPayload.Control.recall, null);
        }
        if (clicked(PreviewKeys.Action.confirm)) {
            send(PreviewControlPayload.Control.confirm, null);
        }
        if (clicked(PreviewKeys.Action.cancel)) {
            send(PreviewControlPayload.Control.cancel, null);
            clear();
            return;
        }
        if (clicked(PreviewKeys.Action.sliceUp)) {
            sliceY++;
        }
        if (clicked(PreviewKeys.Action.sliceDown)) {
            sliceY--;
        }
        if (clicked(PreviewKeys.Action.toggleProblems)) {
            problemsOnly = !problemsOnly;
        }
        trackReset();
    }

    /** 3.7: a tap resets to the first candidate, a hold returns the identity pose. */
    private static void trackReset() {
        KeyMapping reset = PreviewKeys.binding(PreviewKeys.Action.reset);
        if (reset == null) {
            return;
        }
        if (reset.isDown()) {
            resetHeldTicks++;
            return;
        }
        if (resetHeldTicks > 0) {
            boolean longPress = resetHeldTicks >= longPressTicks;
            resetHeldTicks = 0;
            drain(reset);
            send(longPress ? PreviewControlPayload.Control.resetIdentity : PreviewControlPayload.Control.reset, null);
            return;
        }
        if (reset.consumeClick()) {
            send(PreviewControlPayload.Control.reset, null);
        }
    }

    /** A held press leaves one click behind; it must not read as a second tap. */
    private static void drain(KeyMapping mapping) {
        boolean pending = mapping.consumeClick();
        while (pending) {
            pending = mapping.consumeClick();
        }
    }

    static void send(PreviewControlPayload.Control control, @Nullable LatticePoint target) {
        PacketDistributor.sendToServer(new PreviewControlPayload(control, target));
    }

    /** 2.1: right-clicking a ghost part cycles its pin; the server resolves the part from the cell. */
    static void cyclePartAtCrosshair() {
        Minecraft minecraft = Minecraft.getInstance();
        if (session == null || minecraft.hitResult == null) {
            return;
        }
        if (minecraft.hitResult.getType() != HitResult.Type.BLOCK
                || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return;
        }
        LatticePoint cell = LatticePoint
                .of(hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ());
        send(PreviewControlPayload.Control.cyclePart, cell);
    }

    /** True when the ghost at the cell is far enough to draw; counters are never culled (4.8). */
    static boolean inRange(LatticePoint cell) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        return minecraft.player.getEyePosition().distanceToSqr(cell.x() + 0.5D, cell.y() + 0.5D, cell.z() + 0.5D)
                <= renderRadius * renderRadius;
    }

    /** 4.2: layers away from the slice fade out; the slice itself is fully drawn. */
    static float layerAlpha(int y) {
        int distance = Math.abs(y - sliceY);
        return switch (distance) {
            case 0 -> 1.0F;
            case 1 -> 0.35F;
            case 2 -> 0.18F;
            default -> 0.08F;
        };
    }

    /** The mark a cell carries, or null when the overlay says nothing about it. */
    static @Nullable CellMark markOf(LatticePoint cell) {
        PreviewSyncPayload current = session;
        if (current == null) {
            return null;
        }
        CellMark mark = current.overlay().cells().get(LatticeKey.of(cell));
        return mark;
    }

    private static void updateTooltip(PreviewSyncPayload current) {
        Minecraft minecraft = Minecraft.getInstance();
        hoverTooltip = null;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || minecraft.player == null) {
            return;
        }
        LatticePoint cell = LatticePoint
                .of(hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ());
        if (markOf(cell) != CellMark.blocked) {
            return;
        }
        for (Marker marker : current.markers()) {
            if (marker.kind() != MarkerKind.warning || !marker.locus().hasCells()) {
                continue;
            }
            if (marker.locus().cells().contains(cell)) {
                hoverTooltip = marker.label();
                return;
            }
        }
    }

    private static boolean clicked(PreviewKeys.Action action) {
        KeyMapping mapping = PreviewKeys.binding(action);
        return mapping != null && mapping.consumeClick();
    }

    private static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    private static String describe(LatticePoint point) {
        return "(" + point.x() + "," + point.y() + "," + point.z() + ")";
    }

    private static String sliceLabel(int slice) {
        return slice == Integer.MIN_VALUE ? "auto" : Integer.toString(slice);
    }

    private static String kindText(MarkerKind kind) {
        return switch (kind) {
            case error -> "error";
            case warning -> "warning";
            case info -> "info";
            case anchor -> "next";
            case seam -> "seam";
        };
    }

    /** The ghosts the renderer draws after the range and problems-only filters. */
    static List<GhostCell> drawableGhosts() {
        PreviewSyncPayload current = session;
        if (current == null) {
            return List.of();
        }
        List<GhostCell> drawable = new ArrayList<>();
        Map<LatticeKey, CellMark> marks = current.overlay().cells();
        for (GhostCell ghost : current.ghostCells()) {
            if (!inRange(ghost.cell())) {
                continue;
            }
            CellMark mark = marks.get(LatticeKey.of(ghost.cell()));
            if (problemsOnly && mark == CellMark.present) {
                continue;
            }
            drawable.add(ghost);
        }
        return List.copyOf(drawable);
    }

    /** Counters shown in the HUD are not culled; this is the per-mark tally. */
    static Map<CellMark, Integer> markTally() {
        Map<CellMark, Integer> tally = new LinkedHashMap<>();
        PreviewSyncPayload current = session;
        if (current == null) {
            return tally;
        }
        for (CellMark mark : current.overlay().cells().values()) {
            tally.merge(mark, 1, Integer::sum);
        }
        return tally;
    }

    static @Nullable NamespacedId blueprintId() {
        PreviewSyncPayload current = session;
        return current == null ? null : current.blueprintId();
    }
}
