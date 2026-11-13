package dev.vfyjxf.cumulus.mc.preview.scene;

import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chrome stroke of design 17 §5.3: a vector quad strip between the unrounded projected endpoints. */
class ChromeStrokeTest {

    private static final float eps = 1.0E-4F;

    @Test
    void anEdgeBecomesAQuadOfTheFixedWidth() {
        List<ChromeStroke.Quad> quads = ChromeStroke
                .quads(List.of(new Vector2f(10.3F, 20.7F), new Vector2f(40.9F, 20.7F)));
        assertEquals(2, quads.size(), "an open two-point stroke is that edge, both ways round");
        ChromeStroke.Quad edge = quads.get(0);
        assertEquals(10.3F, edge.a().x, eps, "the endpoints are never rounded");
        assertEquals(20.7F - ChromeStroke.widthPx * 0.5F, edge.a().y, eps, "half the stroke lies above the edge");
        assertEquals(40.9F, edge.c().x, eps);
        assertEquals(20.7F + ChromeStroke.widthPx * 0.5F, edge.b().y, eps, "and half below it");
        assertEquals(ChromeStroke.widthPx, Math.abs(edge.b().y - edge.a().y), eps);
    }

    @Test
    void theQuadsWindTheWayTheGuiCullExpects() {
        for (List<Vector2f> silhouette : List.of(
            List.of(
                new Vector2f(10.0F, 10.0F),
                new Vector2f(40.0F, 10.0F),
                new Vector2f(30.0F, 40.0F),
                new Vector2f(20.0F, 30.0F)
            ),
            List.of(
                new Vector2f(10.0F, 10.0F),
                new Vector2f(20.0F, 30.0F),
                new Vector2f(30.0F, 40.0F),
                new Vector2f(40.0F, 10.0F)
            )
        )) {
            for (ChromeStroke.Quad quad : ChromeStroke.quads(silhouette)) {
                assertTrue(area(quad) < 0.0F, "a GUI quad's screen-space area is negative, as fill's is");
            }
        }
    }

    @Test
    void aDegenerateSilhouetteStrokesNothing() {
        assertTrue(ChromeStroke.quads(List.of()).isEmpty());
        assertTrue(ChromeStroke.quads(List.of(new Vector2f(1.0F, 2.0F))).isEmpty());
        assertTrue(
            ChromeStroke.quads(List.of(new Vector2f(1.0F, 2.0F), new Vector2f(1.0F, 2.0F))).isEmpty(),
            "a zero-length edge has no direction to widen along"
        );
    }

    private static float area(ChromeStroke.Quad quad) {
        List<Vector2f> corners = List.of(quad.a(), quad.b(), quad.c(), quad.d());
        float sum = 0.0F;
        for (int index = 0; index < corners.size(); index++) {
            Vector2f from = corners.get(index);
            Vector2f to = corners.get((index + 1) % corners.size());
            sum += from.x * to.y - to.x * from.y;
        }
        return sum;
    }
}
