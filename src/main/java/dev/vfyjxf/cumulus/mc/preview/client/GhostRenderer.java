package dev.vfyjxf.cumulus.mc.preview.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.vfyjxf.cumulus.mc.preview.net.GhostCell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
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
            BlockState state = state(ghost);
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
                new FadingBuffers(source, alpha),
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

    private static @Nullable BlockState state(GhostCell ghost) {
        Block block = BuiltInRegistries.BLOCK
                .getOptional(ResourceLocation.fromNamespaceAndPath(ghost.block().namespace(), ghost.block().path()))
                .orElse(null);
        if (block == null) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        for (var entry : ghost.properties().entrySet()) {
            state = with(state, block, entry.getKey(), entry.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Block block, String name, String value) {
        Property<?> property = block.getStateDefinition().getProperty(name);
        if (property == null) {
            return state;
        }
        return withTyped(state, property, value);
    }

    private static <T extends Comparable<T>> BlockState withTyped(
        BlockState state,
        Property<T> property,
        String value
    ) {
        return property.getValue(value).map(found -> state.setValue(property, found)).orElse(state);
    }

    /** Forces one constant alpha on every vertex of the model pass. */
    private record FadingBuffers(MultiBufferSource delegate, float alpha) implements MultiBufferSource {

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return new FadingConsumer(delegate.getBuffer(type), alpha);
        }
    }

    private record FadingConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, Math.round(alpha * this.alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }
}
