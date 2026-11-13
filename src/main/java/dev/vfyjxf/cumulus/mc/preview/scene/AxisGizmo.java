package dev.vfyjxf.cumulus.mc.preview.scene;

import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;

/**
 * The screen's compass (design 17 §6): the three document axes as a fixed-pixel triad pinned to the
 * main viewport's top-right corner. Only the camera's rotation turns it; a pure function of the camera
 * and the viewport rectangle, no hull and no picture.
 */
public record AxisGizmo(Segment x, Segment y, Segment z) {

    /** The chrome margin every panel's chrome keeps from the edge (design 17 §5.1's panel gap). */
    public static final int chromeMarginPx = 4;
    /** How far a label reaches past its segment's end (design 17 §6). */
    public static final int labelPadPx = 4;
    /** The axis length in GUI pixels: independent of the page, the viewport and the zoom (§6). */
    public static final int gizmoAxisPx = 18;

    public static final int xColor = 0xFFFF5555;
    public static final int yColor = 0xFF55FF55;
    public static final int zColor = 0xFF5599FF;
    /** The triad's centre inset from the rectangle's top-right corner (§6). */
    public static final int insetPx = chromeMarginPx + gizmoAxisPx + labelPadPx;

    /**
     * One axis: its screen-space start and end, the anchor of its name past the end, its view depth
     * (larger is nearer) and its chrome colour.
     */
    public record Segment(Vector2f start, Vector2f end, Vector2f label, float depth, String name, int color) {}

    /** The compass of one viewport rectangle through one camera. */
    public static AxisGizmo of(SceneCamera camera, int x, int y, int width, int height) {
        Matrix3f rotation = SceneLighting.normals(camera);
        Vector2f centre = new Vector2f(x + width - insetPx, y + insetPx);
        return new AxisGizmo(
            segment(rotation, centre, 1.0F, 0.0F, 0.0F, "X", xColor),
            segment(rotation, centre, 0.0F, 1.0F, 0.0F, "Y", yColor),
            segment(rotation, centre, 0.0F, 0.0F, 1.0F, "Z", zColor)
        );
    }

    /** The three segments far-to-near, the order the painter draws them in (§6). */
    public List<Segment> ordered() {
        return List.of(x, y, z).stream().sorted(Comparator.comparingDouble(Segment::depth)).toList();
    }

    /** One unit axis rotated into the viewport's own frame, its length the fixed {@link #gizmoAxisPx}. */
    private static Segment segment(
        Matrix3f rotation,
        Vector2f centre,
        float x,
        float y,
        float z,
        String name,
        int color
    ) {
        Vector3f viewed = rotation.transform(new Vector3f(x, y, z));
        float onScreen = (float) Math.hypot(viewed.x, viewed.y);
        Vector2f end = onScreen < 1.0E-4F
                ? new Vector2f(centre)
                : new Vector2f(
                    centre.x + viewed.x / onScreen * gizmoAxisPx,
                    centre.y + viewed.y / onScreen * gizmoAxisPx
                );
        return new Segment(centre, end, end, viewed.z, name, color);
    }
}
