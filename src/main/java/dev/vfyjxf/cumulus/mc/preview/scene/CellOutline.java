package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One cell's chrome outline (design 17 §5.3): the convex hull of the eight projected corners, and the
 * nearest corner's GUI depth, so the outline rides on its own cell and a nearer model still hides it.
 */
public record CellOutline(List<Vector2f> points, float depth) {

    /** Below this many pixels two projected corners are the same point (an axis-locked view). */
    private static final float sameCorner = 1.0E-3F;

    public CellOutline {
        points = List.copyOf(points);
    }

    /** The outline of one cell, displaced and scaled by this frame's treatment (§7.2, §7.6, §8.1). */
    public static CellOutline of(Fit fit, LatticePoint at, float dx, float dy, float dz, float scale) {
        Matrix4f matrix = fit.matrix();
        List<Vector2f> corners = new ArrayList<>(8);
        float depth = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            float x = at.x() + dx + 0.5F + (((corner & 1) == 0 ? 0.0F : 1.0F) - 0.5F) * scale;
            float y = at.y() + dy + 0.5F + (((corner & 2) == 0 ? 0.0F : 1.0F) - 0.5F) * scale;
            float z = at.z() + dz + 0.5F + (((corner & 4) == 0 ? 0.0F : 1.0F) - 0.5F) * scale;
            corners.add(fit.project(x, y, z));
            depth = Math.max(depth, matrix.transformPosition(new Vector3f(x, y, z)).z);
        }
        return new CellOutline(hull(corners), depth);
    }

    /** The convex hull of the projected corners, in drawing order (monotone chain). */
    private static List<Vector2f> hull(List<Vector2f> projected) {
        List<Vector2f> points = new ArrayList<>(projected.size());
        for (Vector2f point : projected) {
            if (points.stream().noneMatch(kept -> same(kept, point))) {
                points.add(point);
            }
        }
        points.sort(Comparator.<Vector2f>comparingDouble(point -> point.x).thenComparingDouble(point -> point.y));
        if (points.size() < 3) {
            return List.copyOf(points);
        }
        List<Vector2f> hull = new ArrayList<>(points.size() * 2);
        for (Vector2f point : points) {
            while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 0.0F) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        int lower = hull.size() + 1;
        for (int index = points.size() - 2; index >= 0; index--) {
            Vector2f point = points.get(index);
            while (hull.size() >= lower && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 0.0F) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        hull.remove(hull.size() - 1);
        return List.copyOf(hull);
    }

    private static boolean same(Vector2f first, Vector2f second) {
        return Math.abs(first.x - second.x) < sameCorner && Math.abs(first.y - second.y) < sameCorner;
    }

    private static float cross(Vector2f origin, Vector2f first, Vector2f second) {
        return (first.x - origin.x) * (second.y - origin.y) - (first.y - origin.y) * (second.x - origin.x);
    }
}
