package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chrome outline of design 17 §5.3: the projected silhouette, not its screen bounding box. */
class CellOutlineTest {

    private static final float eps = 1.0e-3F;
    private static final Hull page = new Hull(0.0F, 0.0F, 0.0F, 4.0F, 3.0F, 4.0F);
    private static final LatticePoint cell = LatticePoint.of(1, 1, 1);

    private static SceneCamera orthographic(float yaw, float pitch) {
        return new SceneCamera(2.0F, 1.5F, 2.0F, yaw, pitch, 32.0F, 1.0F, SceneCamera.Projection.orthographic);
    }

    @Test
    void theIsometricOutlineIsTheSixVertexSilhouette() {
        Fit fit = Fit.of(page, 0, 0, 400, 300, orthographic(45.0F, SceneCamera.defaultPitch));
        CellOutline outline = CellOutline.of(fit, cell, 0.0F, 0.0F, 0.0F, 1.0F);
        assertEquals(6, outline.points().size());
    }

    @Test
    void anAxisLockedViewSeesTheFourVertexRectangle() {
        for (SceneCamera camera : List
                .of(orthographic(180.0F, 0.0F), orthographic(270.0F, 0.0F), orthographic(0.0F, SceneCamera.maxPitch))) {
            Fit fit = Fit.of(page, 0, 0, 300, 200, camera);
            assertEquals(4, CellOutline.of(fit, cell, 0.0F, 0.0F, 0.0F, 1.0F).points().size());
        }
    }

    @Test
    void everyOutlinePointIsAProjectedCorner() {
        Fit fit = Fit.of(page, 0, 0, 400, 300, orthographic(45.0F, SceneCamera.defaultPitch));
        List<Vector2f> corners = new ArrayList<>(8);
        for (int corner = 0; corner < 8; corner++) {
            corners.add(
                fit.project(
                    cell.x() + ((corner & 1) == 0 ? 0.0F : 1.0F),
                    cell.y() + ((corner & 2) == 0 ? 0.0F : 1.0F),
                    cell.z() + ((corner & 4) == 0 ? 0.0F : 1.0F)
                )
            );
        }
        for (Vector2f point : CellOutline.of(fit, cell, 0.0F, 0.0F, 0.0F, 1.0F).points()) {
            assertTrue(
                corners.stream()
                        .anyMatch(corner -> Math.abs(corner.x - point.x) < eps && Math.abs(corner.y - point.y) < eps),
                "the outline is built from projected corners"
            );
        }
    }

    @Test
    void theOutlineCarriesTheNearestCornerOfItsOwnCell() {
        Fit fit = Fit.of(page, 0, 0, 400, 300, orthographic(45.0F, SceneCamera.defaultPitch));
        CellOutline outline = CellOutline.of(fit, cell, 0.0F, 0.0F, 0.0F, 1.0F);
        float nearest = Float.NEGATIVE_INFINITY;
        float farthest = Float.POSITIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            Vector3f point = new Vector3f(
                cell.x() + ((corner & 1) == 0 ? 0.0F : 1.0F),
                cell.y() + ((corner & 2) == 0 ? 0.0F : 1.0F),
                cell.z() + ((corner & 4) == 0 ? 0.0F : 1.0F)
            );
            float depth = fit.matrix().transformPosition(point, new Vector3f()).z;
            nearest = Math.max(nearest, depth);
            farthest = Math.min(farthest, depth);
        }
        assertEquals(nearest, outline.depth(), eps);
        assertTrue(outline.depth() > 0.0F && outline.depth() < PreviewScene.sceneSpan, "inside the scene's band");
        assertTrue(nearest > farthest, "the cell has depth, larger is nearer");
    }

    @Test
    void theDisplacedCellOutlinesWhereItIsDrawn() {
        Fit fit = Fit.of(page, 0, 0, 400, 300, orthographic(45.0F, SceneCamera.defaultPitch));
        CellOutline shifted = CellOutline.of(fit, cell, 0.0F, 2.0F, 0.0F, 1.0F);
        CellOutline plain = CellOutline.of(fit, cell, 0.0F, 0.0F, 0.0F, 1.0F);
        Vector2f shiftedCentre = shifted.points().get(0);
        Vector2f plainCentre = plain.points().get(0);
        assertTrue(Math.abs(shiftedCentre.y - plainCentre.y) > 1.0F, "the explode offset moves the outline");
    }

    @Test
    void thePerspectiveOutlineAlsoStaysInsideTheBand() {
        SceneCamera camera = orthographic(45.0F, SceneCamera.defaultPitch)
                .withProjection(SceneCamera.Projection.perspective, page).frame(page, 1.0F);
        Fit fit = Fit.of(page, 0, 0, 400, 300, camera);
        CellOutline outline = CellOutline.of(fit, cell, 0.0F, 0.0F, 0.0F, 1.0F);
        assertEquals(6, outline.points().size());
        assertTrue(outline.depth() > 0.0F && outline.depth() < PreviewScene.sceneSpan, "inside the scene's band");
    }
}
