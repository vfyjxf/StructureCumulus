package dev.vfyjxf.cumulus.mc.preview.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The expected blocks, drawn as translucent models after the translucent phase. No world access
 * beyond the model manager, and no block-entity data: the payload is the only source. A hand-off
 * animation adds a second pass for the pose the client is leaving (design 16 §6.7).
 */
final class GhostRenderer {

    private static final float baseAlpha = 0.55F;
    /** One shared buffer keeps a frame's ghost batch to one upload. */
    private static @Nullable ByteBufferBuilder buffers;

    private GhostRenderer() {}

    static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<GhostCell> ghosts = PreviewClientState.session() == null ? List.of() : PreviewClientState.drawableGhosts();
        TransitionController.@Nullable GhostLayer layer = TransitionController.extraLayer(partialTick);
        if (ghosts.isEmpty() && layer == null) {
            return;
        }
        if (buffers == null) {
            buffers = new ByteBufferBuilder(4_096);
        }
        MultiBufferSource.BufferSource source = MultiBufferSource.immediate(buffers);
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (GhostCell ghost : ghosts) {
            BlockState state = CellStates.of(ghost.block(), ghost.properties());
            if (state == null) {
                continue;
            }
            float alpha = baseAlpha
                    * PreviewClientState.layerAlpha(ghost.cell().y())
                    * TransitionController.ghostAlpha(ghost, partialTick)
                    * GuideController.pulseFactor(ghost.cell());
            if (alpha <= 0.001F) {
                continue;
            }
            float scale = TransitionController.ghostScale(ghost, partialTick);
            pose.pushPose();
            pose.translate(ghost.cell().x(), ghost.cell().y(), ghost.cell().z());
            if (scale != 1.0F) {
                pose.translate(0.5D, 0.5D, 0.5D);
                pose.scale(scale, scale, scale);
                pose.translate(-0.5D, -0.5D, -0.5D);
            }
            draw(minecraft, state, pose, source, alpha);
            pose.popPose();
        }
        if (layer != null) {
            drawLayer(minecraft, pose, source, layer);
        }
        pose.popPose();
        source.endBatch();
    }

    /** The pose being left: the old cells, swung about the placement's axis or merely faded. */
    private static void drawLayer(
        Minecraft minecraft,
        PoseStack pose,
        MultiBufferSource.BufferSource source,
        TransitionController.GhostLayer layer
    ) {
        double radians = Math.toRadians(layer.angleDeg());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        Vec3 pivot = layer.pivot();
        for (GhostCell ghost : layer.ghosts()) {
            if (!PreviewClientState.inRange(ghost.cell())) {
                continue;
            }
            BlockState state = CellStates.of(ghost.block(), ghost.properties());
            if (state == null) {
                continue;
            }
            float alpha = baseAlpha * PreviewClientState.layerAlpha(ghost.cell().y()) * layer.alpha();
            if (alpha <= 0.001F) {
                continue;
            }
            Vec3 offset = new Vec3(
                ghost.cell().x() + 0.5D - pivot.x,
                ghost.cell().y() + 0.5D - pivot.y,
                ghost.cell().z() + 0.5D - pivot.z
            );
            double x = pivot.x + offset.x * cos - offset.z * sin;
            double y = pivot.y + offset.y;
            double z = pivot.z + offset.x * sin + offset.z * cos;
            pose.pushPose();
            pose.translate(x - 0.5D, y - 0.5D, z - 0.5D);
            draw(minecraft, state, pose, source, alpha);
            pose.popPose();
        }
    }

    private static void draw(
        Minecraft minecraft,
        BlockState state,
        PoseStack pose,
        MultiBufferSource source,
        float alpha
    ) {
        minecraft.getBlockRenderer().renderSingleBlock(
            state,
            pose,
            TintedBuffers.of(source, alpha),
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            ModelData.EMPTY,
            RenderType.translucent()
        );
    }
}
