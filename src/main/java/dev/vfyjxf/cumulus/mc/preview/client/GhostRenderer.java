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
 * beyond the model manager, and no block-entity data: the payload is the only source.
 */
final class GhostRenderer {

    private static final float baseAlpha = 0.55F;
    /** One shared buffer keeps a frame's ghost batch to one upload. */
    private static @Nullable ByteBufferBuilder buffers;

    private GhostRenderer() {}

    static void render(RenderLevelStageEvent event) {
        if (PreviewClientState.session() == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        List<GhostCell> ghosts = PreviewClientState.drawableGhosts();
        if (ghosts.isEmpty()) {
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
                    * PlaybackController.alphaOf(ghost);
            pose.pushPose();
            pose.translate(ghost.cell().x(), ghost.cell().y(), ghost.cell().z());
            minecraft.getBlockRenderer().renderSingleBlock(
                state,
                pose,
                TintedBuffers.of(source, alpha),
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY,
                RenderType.translucent()
            );
            pose.popPose();
        }
        pose.popPose();
        source.endBatch();
    }
}
