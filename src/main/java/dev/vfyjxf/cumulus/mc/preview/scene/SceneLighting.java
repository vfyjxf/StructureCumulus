package dev.vfyjxf.cumulus.mc.preview.scene;

import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * The scene's directional light (design 17 §4.1): the level pass's two world diffuse directions, seen
 * through one panel's camera. The mirror is the GUI's y-down axis, so the normal matrix the scene
 * installs is the camera's rotation and never the projection baked into the pose.
 */
public final class SceneLighting {

    /** Vanilla {@code Lighting}'s first world diffuse direction, the level pass's sun. */
    private static final Vector3f world0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    /** Vanilla {@code Lighting}'s second world diffuse direction, the level pass's fill light. */
    private static final Vector3f world1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();

    /** The ambient floor of the two-light mix, vanilla's {@code min(1, (l0 + l1) * 0.6 + 0.4)}. */
    public static final float ambient = 0.4F;
    /** The diffuse share of the two-light mix. */
    public static final float diffuse = 0.6F;

    private SceneLighting() {}

    /** The two shader-space light directions of one camera, in the order the scene installs them. */
    public static Vector3f[] forCamera(SceneCamera camera) {
        Matrix3f normals = normals(camera);
        return new Vector3f[]{normals.transform(new Vector3f(world0)), normals.transform(new Vector3f(world1))};
    }

    /**
     * The normal matrix of the scene pass: the camera's view rotation with the GUI's y-down mirror.
     * The pose also carries a projection and a pixel scale, whose normal transform would rotate every
     * face toward the view's depth axis; that transform is what this replaces.
     */
    public static Matrix3f normals(SceneCamera camera) {
        float[] origin = camera.view(0.0F, 0.0F, 0.0F);
        float[] xAxis = camera.view(1.0F, 0.0F, 0.0F);
        float[] yAxis = camera.view(0.0F, 1.0F, 0.0F);
        float[] zAxis = camera.view(0.0F, 0.0F, 1.0F);
        Matrix3f view = new Matrix3f(
            xAxis[0] - origin[0],
            xAxis[1] - origin[1],
            xAxis[2] - origin[2],
            yAxis[0] - origin[0],
            yAxis[1] - origin[1],
            yAxis[2] - origin[2],
            zAxis[0] - origin[0],
            zAxis[1] - origin[1],
            zAxis[2] - origin[2]
        );
        return new Matrix3f().scale(1.0F, -1.0F, 1.0F).mul(view);
    }

    /** The brightness of one world-space face normal under the two world lights, 0.4 to 1.0. */
    public static float brightness(float x, float y, float z) {
        return brightness(new Vector3f(x, y, z), world0, world1);
    }

    /** The two-light mix the entity shader applies, for one normal and one light pair. */
    public static float brightness(Vector3f normal, Vector3f light0, Vector3f light1) {
        Vector3f unit = new Vector3f(normal).normalize();
        float first = Math.max(0.0F, unit.dot(light0));
        float second = Math.max(0.0F, unit.dot(light1));
        return Math.min(1.0F, (first + second) * diffuse + ambient);
    }
}
