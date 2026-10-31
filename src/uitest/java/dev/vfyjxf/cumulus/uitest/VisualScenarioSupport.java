package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.mc.visual.VisualInstance;
import dev.vfyjxf.cumulus.mc.visual.VisualSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Shared geometry helpers for the visual scenarios. */
final class VisualScenarioSupport {

    private VisualScenarioSupport() {}

    static BlockPos cell(int dx, int dy, int dz) {
        return new BlockPos(0, 100, 0).offset(dx, dy, dz);
    }

    static LatticePoint point(BlockPos pos) {
        return LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
    }

    /** The yaw/pitch pair that points the local player's eye at the block center. */
    static float[] lookAt(BlockPos block) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return new float[]{0.0F, 0.0F};
        }
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 target = Vec3.atCenterOf(block);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double ground = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, ground));
        return new float[]{yaw, pitch};
    }

    /** A one-line camera/player state for timeline debugging. */
    static String cameraState() {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        HitResult hit = minecraft.hitResult;
        String hitText = hit == null ? "none" : hit.getType().name();
        LatticePoint hitCell = null;
        if (hit instanceof BlockHitResult block) {
            BlockPos pos = block.getBlockPos();
            hitCell = LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
            hitText += "@" + pos + ",loc=" + block.getLocation() + ",side=" + block.getDirection();
        }
        VisualSnapshot snapshot = VisualProbe.snapshot();
        List<String> containing = new ArrayList<>();
        if (snapshot != null && hitCell != null) {
            for (VisualInstance instance : snapshot.instances()) {
                if (instance.bounds().contains(hitCell)) {
                    containing.add(instance.id().world().id() + "#" + instance.id().serial());
                }
            }
        }
        return "pos=" + (player == null ? "none" : player.position()) + ", yaw/pitch="
                + (player == null ? "none" : player.getYRot() + "/" + player.getXRot()) + ", hit=" + hitText
                + ", hitCell=" + hitCell + ", containing=" + containing + ", selection=" + VisualProbe.selectionSource()
                + " " + VisualProbe.selectedInstanceId() + ", level="
                + (minecraft.level == null ? "none" : minecraft.level.dimension().location()) + ", block="
                + (minecraft.level == null ? "none" : minecraft.level.getBlockState(new BlockPos(0, 100, 0)));
    }

    /** True when the local player's crosshair currently hits the given block. */
    static boolean hitBlock(BlockPos block) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(block);
    }

    static String describe(@Nullable VisualHighlightSummary summary) {
        if (summary == null) {
            return "no highlights";
        }
        return "bounds=" + summary.boundsBoxes() + ", claimCells=" + summary.claimCells() + ", conflicts="
                + summary.conflicts() + ", drafts=" + summary.draftCells() + ", moved=" + summary.movedInstances()
                + ", invalidated=" + summary.invalidatedInstances() + ", selected=" + summary.hasSelection();
    }
}
