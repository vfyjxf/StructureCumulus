package dev.vfyjxf.cumulus.mc.preview.scene;

import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The screen's lighting law (design 17 §4.1): a face keeps its world brightness under every camera. */
class SceneLightingTest {

    private static final float eps = 1.0e-4F;
    private static final Vector3f[] faces = {new Vector3f(0.0F, -1.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F),
            new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f(-1.0F, 0.0F, 0.0F),
            new Vector3f(1.0F, 0.0F, 0.0F)};
    private static final String[] names = {"down", "up", "north", "south", "west", "east"};

    private static SceneCamera camera(float yaw, float pitch) {
        return new SceneCamera(1.0F, 2.0F, 3.0F, yaw, pitch, 32.0F, 1.0F, SceneCamera.Projection.orthographic);
    }

    /** What the entity shader computes for one world face under one panel's installed lights. */
    private static float shaded(SceneCamera camera, Vector3f face) {
        Matrix3f normals = SceneLighting.normals(camera);
        Vector3f[] lights = SceneLighting.forCamera(camera);
        return SceneLighting.brightness(normals.transform(new Vector3f(face)), lights[0], lights[1]);
    }

    @Test
    void aFaceKeepsItsWorldBrightnessUnderEveryOrbit() {
        for (float yaw : new float[]{0.0F, 45.0F, 90.0F, 134.0F, 180.0F, 270.0F, 359.0F}) {
            for (float pitch : new float[]{-89.0F, -35.0F, 0.0F, 20.0F, SceneCamera.defaultPitch, 89.0F}) {
                SceneCamera camera = camera(yaw, pitch);
                for (int index = 0; index < faces.length; index++) {
                    Vector3f face = faces[index];
                    assertEquals(
                        SceneLighting.brightness(face.x, face.y, face.z),
                        shaded(camera, face),
                        eps,
                        names[index] + " at yaw " + yaw + ", pitch " + pitch
                    );
                }
            }
        }
    }

    @Test
    void theTwoWorldLightsGiveTheVanillaDirectionalOrder() {
        float rounded = 1.0e-3F;
        assertEquals(1.0F, SceneLighting.brightness(0.0F, 1.0F, 0.0F), rounded, "up");
        assertEquals(0.74F, SceneLighting.brightness(0.0F, 0.0F, 1.0F), rounded, "south");
        assertEquals(0.74F, SceneLighting.brightness(0.0F, 0.0F, -1.0F), rounded, "north");
        assertEquals(0.497F, SceneLighting.brightness(1.0F, 0.0F, 0.0F), rounded, "east");
        assertEquals(0.497F, SceneLighting.brightness(-1.0F, 0.0F, 0.0F), rounded, "west");
        assertEquals(0.4F, SceneLighting.brightness(0.0F, -1.0F, 0.0F), rounded, "down");
    }

    @Test
    void theNormalMatrixIsTheRotationWithTheGuiMirror() {
        SceneCamera camera = camera(45.0F, SceneCamera.defaultPitch);
        Matrix3f normals = SceneLighting.normals(camera);
        for (Vector3f face : faces) {
            assertEquals(1.0F, normals.transform(new Vector3f(face)).length(), 1.0e-5F);
        }
        assertEquals(-1.0F, normals.determinant(), 1.0e-5F, "the GUI's y-down axis is a mirror");
        Vector3f up = normals.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        assertTrue(up.y < 0.0F, "the world's up lands on the GUI's screen-down axis");
    }

    @Test
    void theJeiViewArmKeepsTheWorldLaw() {
        PreviewScene.View view = new PreviewScene.View(0, 0, 160, 100, 135.0F, 30.0F, PreviewScene.LayerRule.upTo);
        PreviewScene.Shading shading = PreviewScene.shading(view);
        assertEquals(1.0F, shading.up(), 1.0e-3F, "up");
        assertEquals(0.74F, shading.north(), 1.0e-3F, "north");
        assertEquals(0.74F, shading.south(), 1.0e-3F, "south");
        assertEquals(0.497F, shading.east(), 1.0e-3F, "east");
        assertEquals(0.497F, shading.west(), 1.0e-3F, "west");
        assertEquals(0.4F, shading.down(), 1.0e-3F, "down");
        assertEquals(shading, PreviewScene.shading(view), "the same view gives the same six numbers, bit for bit");
    }

    @Test
    void theJeiNormalMatrixIsTheViewRotationWithTheGuiMirror() {
        PreviewScene.View view = new PreviewScene.View(
            0,
            0,
            160,
            100,
            45.0F,
            SceneCamera.defaultPitch,
            PreviewScene.LayerRule.upTo
        );
        Matrix3f normals = SceneLighting.normals(PreviewScene.rotation(view).get(new Matrix3f()));
        for (Vector3f face : faces) {
            assertEquals(1.0F, normals.transform(new Vector3f(face)).length(), 1.0e-5F);
        }
        assertEquals(-1.0F, normals.determinant(), 1.0e-5F, "the GUI's y-down axis is a mirror");
        assertTrue(normals.transform(new Vector3f(0.0F, 1.0F, 0.0F)).y < 0.0F);
    }

    @Test
    void theTwoPreviewSurfacesShadeTheSameFacesTheSameWay() {
        SceneCamera camera = SceneCamera.isometric();
        PreviewScene.Shading jei = PreviewScene.shading(
            new PreviewScene.View(0, 0, 160, 100, camera.yaw(), camera.pitch(), PreviewScene.LayerRule.upTo)
        );
        assertEquals(shaded(camera, faces[0]), jei.down(), eps, "down");
        assertEquals(shaded(camera, faces[1]), jei.up(), eps, "up");
        assertEquals(shaded(camera, faces[2]), jei.north(), eps, "north");
        assertEquals(shaded(camera, faces[3]), jei.south(), eps, "south");
        assertEquals(shaded(camera, faces[4]), jei.west(), eps, "west");
        assertEquals(shaded(camera, faces[5]), jei.east(), eps, "east");
    }

    @Test
    void everyPanelTypeShadesTheSameFaceTheSameWay() {
        SceneCamera isometric = SceneCamera.isometric();
        SceneCamera front = new SceneCamera(
            0.0F,
            0.0F,
            0.0F,
            180.0F,
            0.0F,
            32.0F,
            1.0F,
            SceneCamera.Projection.orthographic
        );
        SceneCamera side = new SceneCamera(
            0.0F,
            0.0F,
            0.0F,
            270.0F,
            0.0F,
            32.0F,
            1.0F,
            SceneCamera.Projection.orthographic
        );
        SceneCamera top = new SceneCamera(
            0.0F,
            0.0F,
            0.0F,
            0.0F,
            SceneCamera.maxPitch,
            32.0F,
            1.0F,
            SceneCamera.Projection.orthographic
        );
        for (Vector3f face : faces) {
            float expected = shaded(isometric, face);
            assertEquals(expected, shaded(front, face), eps);
            assertEquals(expected, shaded(side, face), eps);
            assertEquals(expected, shaded(top, face), eps);
        }
    }
}
