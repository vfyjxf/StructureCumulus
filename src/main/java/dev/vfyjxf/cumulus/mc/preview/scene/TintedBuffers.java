package dev.vfyjxf.cumulus.mc.preview.scene;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Forces one alpha and one rgb tint on every vertex of a model pass. */
public final class TintedBuffers implements MultiBufferSource {

    private final MultiBufferSource delegate;
    private final float alpha;
    private final float red;
    private final float green;
    private final float blue;

    private TintedBuffers(MultiBufferSource delegate, float alpha, float red, float green, float blue) {
        this.delegate = delegate;
        this.alpha = alpha;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public static MultiBufferSource of(MultiBufferSource delegate, float alpha) {
        return new TintedBuffers(delegate, alpha, 1.0F, 1.0F, 1.0F);
    }

    public static MultiBufferSource of(MultiBufferSource delegate, float alpha, float red, float green, float blue) {
        return new TintedBuffers(delegate, alpha, red, green, blue);
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return new TintedConsumer(delegate.getBuffer(type), alpha, red, green, blue);
    }

    private record TintedConsumer(VertexConsumer delegate, float alpha, float red, float green, float blue)
            implements
                VertexConsumer {

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(Math.round(r * red), Math.round(g * green), Math.round(b * blue), Math.round(a * alpha));
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
