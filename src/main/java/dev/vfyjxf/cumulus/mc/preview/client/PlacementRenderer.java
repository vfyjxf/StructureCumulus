package dev.vfyjxf.cumulus.mc.preview.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The placement animation's extra translucent model, drawn at each cell of the run. It never reads
 * the world's state: the payload's own state is what it draws, faded by the run's beats.
 */
final class PlacementRenderer {

    private static final float removalRed = 1.0F;
    private static final float removalGreen = 0.55F;
    private static final float removalBlue = 0.5F;

    /** One shared buffer keeps a frame's animation batch to one upload. */
    private static @Nullable ByteBufferBuilder buffers;

    private PlacementRenderer() {}

    static void render(RenderLevelStageEvent event) {
        List<PlacementAnimator.Drawn> cells = PlacementAnimator.drawn();
        if (cells.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
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
        for (PlacementAnimator.Drawn cell : cells) {
            if (!PreviewClientState.inRange(cell.cell())) {
                continue;
            }
            pose.pushPose();
            pose.translate(cell.cell().x(), cell.cell().y(), cell.cell().z());
            if (cell.scale() != 1.0F) {
                pose.translate(0.5D, 0.5D, 0.5D);
                pose.scale(cell.scale(), cell.scale(), cell.scale());
                pose.translate(-0.5D, -0.5D, -0.5D);
            }
            MultiBufferSource pass = cell.removal()
                    ? TintedBuffers.of(source, cell.alpha(), removalRed, removalGreen, removalBlue)
                    : TintedBuffers.of(source, cell.alpha());
            minecraft.getBlockRenderer().renderSingleBlock(
                cell.state(),
                pose,
                pass,
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
