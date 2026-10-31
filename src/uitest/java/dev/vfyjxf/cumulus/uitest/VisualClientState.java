package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.visual.VisualInstance;
import dev.vfyjxf.cumulus.mc.visual.VisualSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** The client's latest read-only visual snapshot, the crosshair selection and the request cadence. */
public final class VisualClientState {

    private static final int refreshTicks = 10;
    private static final double nearestRadius = 32.0D;

    private static volatile @Nullable VisualSnapshot snapshot;
    private static volatile boolean hudRendered;
    private static volatile boolean hudEnabled = true;
    private static volatile boolean highlightsEnabled = true;
    private static volatile @Nullable InstanceId selectedInstance;
    private static volatile @Nullable InstanceId hoveredInstance;
    private static volatile @Nullable LatticePoint hoveredCell;
    private static volatile String selectionSource = "none";
    private static volatile boolean compact;
    private static volatile @Nullable String lastEvent;
    private static volatile @Nullable String error;
    private static volatile @Nullable String expectedLifecycle;
    private static volatile @Nullable VisualFlowModel flow;
    private static volatile @Nullable VisualSetSummary setSummary;
    private static int ticksSinceRequest;

    private VisualClientState() {}

    static void accept(VisualSnapshot latest) {
        VisualSnapshot previous = snapshot;
        snapshot = latest;
        VisualHighlights.onSnapshot(previous, latest);
        VisualFlowModel observed = flow;
        if (observed != null) {
            flow = observed.observe(latest.instances());
        }
        updateSelection();
    }

    public static @Nullable VisualSnapshot snapshot() {
        return snapshot;
    }

    public static boolean hudRendered() {
        return hudRendered;
    }

    static void markHudRendered() {
        hudRendered = true;
    }

    static @Nullable InstanceId selectedInstanceId() {
        return selectedInstance;
    }

    static String selectionSource() {
        return selectionSource;
    }

    static @Nullable InstanceId hoveredInstanceId() {
        return hoveredInstance;
    }

    static @Nullable LatticePoint hoveredCell() {
        return hoveredCell;
    }

    static boolean compact() {
        return compact;
    }

    static void setCompact(boolean value) {
        compact = value;
    }

    static @Nullable String lastEvent() {
        return lastEvent;
    }

    static @Nullable String error() {
        return error;
    }

    static void noteEvent(String event) {
        lastEvent = event;
    }

    static void noteError(@Nullable String message) {
        error = message;
    }

    static void expectLifecycle(String event) {
        expectedLifecycle = event;
    }

    static @Nullable String consumeExpectedLifecycle() {
        String expected = expectedLifecycle;
        expectedLifecycle = null;
        return expected;
    }

    static void resetTransientState() {
        lastEvent = null;
        error = null;
        expectedLifecycle = null;
        flow = null;
        hudEnabled = true;
        hudRendered = false;
        highlightsEnabled = true;
    }

    static @Nullable VisualFlowModel flow() {
        return flow;
    }

    static void advanceFlow(VisualFlowModel.Phase phase) {
        List<VisualInstance> instances = List.of();
        if (snapshot != null) {
            instances = snapshot.instances();
        }
        VisualFlowModel model = flow == null ? VisualFlowModel.start(List.of()) : flow;
        flow = model.observe(instances).advanceTo(phase);
    }

    static boolean hudEnabled() {
        return hudEnabled;
    }

    static void setHudEnabled(boolean enabled) {
        hudEnabled = enabled;
        hudRendered = false;
    }

    static boolean highlightsEnabled() {
        return highlightsEnabled;
    }

    static void setHighlightsEnabled(boolean enabled) {
        highlightsEnabled = enabled;
    }

    static void setSetSummary(@Nullable VisualSetSummary summary) {
        setSummary = summary;
    }

    static @Nullable VisualSetSummary setSummary() {
        return setSummary;
    }

    static void clearSetSummary() {
        setSummary = null;
    }

    static void toggleCompact() {
        compact = !compact;
    }

    /** Polls the integrated server while a world is joined; called once per client tick. */
    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null) {
            return;
        }
        updateSelection();
        VisualInspectState.tick();
        VisualHighlights.tick();
        ticksSinceRequest++;
        if (snapshot == null || ticksSinceRequest >= refreshTicks) {
            ticksSinceRequest = 0;
            PacketDistributor.sendToServer(new VisualSnapshotRequest());
        }
    }

    private static void updateSelection() {
        VisualSnapshot current = snapshot;
        Minecraft minecraft = Minecraft.getInstance();
        if (current == null || minecraft.player == null || minecraft.level == null) {
            selectedInstance = null;
            hoveredInstance = null;
            hoveredCell = null;
            selectionSource = "none";
            return;
        }
        InstanceId found = null;
        InstanceId hovered = null;
        LatticePoint hoverCell = null;
        String source = "none";
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
            hoverCell = LatticePoint
                    .of(block.getBlockPos().getX(), block.getBlockPos().getY(), block.getBlockPos().getZ());
            hovered = containing(current, hoverCell);
            found = hovered;
            if (found != null) {
                source = "crosshair";
            }
        }
        hoveredInstance = hovered;
        hoveredCell = hoverCell;
        if (found == null) {
            Vec3 eye = minecraft.player.getEyePosition();
            double best = nearestRadius * nearestRadius;
            for (VisualInstance instance : current.instances()) {
                double distance = eye.distanceToSqr(center(instance));
                if (distance <= best) {
                    best = distance;
                    found = instance.id();
                }
            }
            if (found != null) {
                source = "nearest";
            }
        }
        selectedInstance = found;
        selectionSource = source;
    }

    private static @Nullable InstanceId containing(VisualSnapshot snapshot, LatticePoint cell) {
        for (VisualInstance instance : snapshot.instances()) {
            if (instance.bounds().contains(cell)) {
                return instance.id();
            }
        }
        return null;
    }

    private static Vec3 center(VisualInstance instance) {
        var bounds = instance.bounds().bounds();
        if (bounds == null) {
            LatticePoint origin = instance.siting().origin();
            return new Vec3(origin.x() + 0.5D, origin.y() + 0.5D, origin.z() + 0.5D);
        }
        return new Vec3(
            (bounds.min().x() + bounds.max().x()) / 2.0D,
            (bounds.min().y() + bounds.max().y()) / 2.0D,
            (bounds.min().z() + bounds.max().z()) / 2.0D
        );
    }
}
