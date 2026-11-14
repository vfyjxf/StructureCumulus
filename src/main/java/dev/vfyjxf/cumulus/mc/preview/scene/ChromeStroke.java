package dev.vfyjxf.cumulus.mc.preview.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The chrome stroke of design 17 §5.3: each edge of a projected silhouette as a quad strip between its
 * unrounded endpoints, above the scene at the chrome depth with the depth test off, so a highlight
 * reads through the models and lands where the projection put it. A two-point stroke is one open
 * segment, the compass's axis (design 17 §6), never a closed polygon of two edges.
 */
public final class ChromeStroke {

    /** The stroke's width in GUI pixels: a vector line, not a row of GUI pixels (§5.3). */
    public static final float widthPx = 1.5F;
    /** One dash's and one gap's length: an optional member's silhouette reads as dashed (§5.3). */
    public static final float dashedStepPx = 3.0F;

    /** One stroke: a closed projected silhouette, or one open segment, and its chrome colour. */
    public record Stroke(List<Vector2f> points, int color) {

        public Stroke {
            points = List.copyOf(points);
        }
    }

    /** One edge's quad; the order is {@code GuiGraphics.fill}'s, the GUI's flipped y making it front. */
    public record Quad(Vector2f a, Vector2f b, Vector2f c, Vector2f d) {}

    private ChromeStroke() {}

    /** The quads of one stroke, one per edge, in a winding the GUI's back-face cull keeps. */
    public static List<Quad> quads(List<Vector2f> points) {
        if (points.size() < 2) {
            return List.of();
        }
        if (points.size() == 2) {
            Quad edge = quad(points.get(0), points.get(1));
            return edge == null ? List.of() : List.of(edge);
        }
        List<Vector2f> ordered = area(points) > 0.0F ? reversed(points) : points;
        List<Quad> quads = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            Vector2f from = ordered.get(index);
            Vector2f to = ordered.get((index + 1) % ordered.size());
            Quad quad = quad(from, to);
            if (quad != null) {
                quads.add(quad);
            }
        }
        return List.copyOf(quads);
    }

    /** Draws every stroke in one batch at {@code chromeDepth}; the type's depth test is off (§5.3). */
    public static void draw(GuiGraphics graphics, List<Stroke> strokes) {
        if (strokes.isEmpty()) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, PreviewScene.chromeDepth);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.guiOverlay());
        for (Stroke stroke : strokes) {
            for (Quad quad : quads(stroke.points())) {
                vertex(consumer, matrix, quad.a(), stroke.color());
                vertex(consumer, matrix, quad.b(), stroke.color());
                vertex(consumer, matrix, quad.c(), stroke.color());
                vertex(consumer, matrix, quad.d(), stroke.color());
            }
        }
        graphics.bufferSource().endBatch(RenderType.guiOverlay());
        pose.popPose();
    }

    private static @Nullable Quad quad(Vector2f from, Vector2f to) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1.0E-4F) {
            return null;
        }
        float nx = -dy / length * (widthPx * 0.5F);
        float ny = dx / length * (widthPx * 0.5F);
        return new Quad(
            new Vector2f(from.x - nx, from.y - ny),
            new Vector2f(from.x + nx, from.y + ny),
            new Vector2f(to.x + nx, to.y + ny),
            new Vector2f(to.x - nx, to.y - ny)
        );
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vector2f point, int color) {
        consumer.addVertex(matrix, point.x, point.y, 0.0F).setColor(color);
    }

    /** One closed stroke as dashes: an optional member's silhouette on both surfaces (design 18 §5). */
    public static List<Stroke> dashed(List<Vector2f> points, int color) {
        List<Stroke> dashes = new ArrayList<>();
        if (points.size() < 2) {
            return dashes;
        }
        for (int index = 0; index < points.size(); index++) {
            Vector2f from = points.get(index);
            Vector2f to = points.get((index + 1) % points.size());
            float length = (float) Math.hypot(to.x - from.x, to.y - from.y);
            if (length < 1.0E-3F) {
                continue;
            }
            float step = dashedStepPx;
            int count = Math.max(1, (int) Math.ceil(length / (step * 2.0F)));
            for (int dash = 0; dash < count; dash++) {
                float start = dash * step * 2.0F;
                float end = Math.min(length, start + step);
                dashes.add(
                    new Stroke(
                        List.of(interpolate(from, to, start / length), interpolate(from, to, end / length)),
                        color
                    )
                );
            }
        }
        return dashes;
    }

    private static Vector2f interpolate(Vector2f from, Vector2f to, float t) {
        return new Vector2f(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t);
    }

    /** The shoelace area of the projected polygon: its sign is the winding the cull sees. */
    private static float area(List<Vector2f> points) {
        float sum = 0.0F;
        for (int index = 0; index < points.size(); index++) {
            Vector2f from = points.get(index);
            Vector2f to = points.get((index + 1) % points.size());
            sum += from.x * to.y - to.x * from.y;
        }
        return sum;
    }

    private static List<Vector2f> reversed(List<Vector2f> points) {
        List<Vector2f> reversed = new ArrayList<>(points.size());
        for (int index = points.size() - 1; index >= 0; index--) {
            reversed.add(points.get(index));
        }
        return reversed;
    }
}
