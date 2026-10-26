package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** A canonical, disjoint, sorted set of boxes. */
public final class CuboidSet {

    private static final CuboidSet EMPTY = new CuboidSet(List.of());

    private final List<Cuboid> boxes;

    private CuboidSet(List<Cuboid> boxes) {
        this.boxes = boxes;
    }

    public static CuboidSet empty() {
        return EMPTY;
    }

    public static CuboidSet of(Cuboid... boxes) {
        return of(Arrays.asList(boxes));
    }

    public static CuboidSet of(Collection<Cuboid> boxes) {
        Objects.requireNonNull(boxes, "boxes");
        List<Cuboid> nonEmpty = new ArrayList<>();
        for (Cuboid box : boxes) {
            Objects.requireNonNull(box, "box");
            if (!box.isEmpty()) {
                nonEmpty.add(box);
            }
        }
        if (nonEmpty.isEmpty()) {
            return EMPTY;
        }
        TreeSet<Integer> xBoundaries = new TreeSet<>();
        TreeSet<Integer> zBoundaries = new TreeSet<>();
        for (Cuboid box : nonEmpty) {
            xBoundaries.add(box.min().x());
            xBoundaries.add(box.max().x());
            zBoundaries.add(box.min().z());
            zBoundaries.add(box.max().z());
        }
        List<Integer> xs = List.copyOf(xBoundaries);
        List<Integer> zs = List.copyOf(zBoundaries);
        List<Cuboid> result = new ArrayList<>();
        for (int xi = 0; xi < xs.size() - 1; xi++) {
            int x0 = xs.get(xi);
            int x1 = xs.get(xi + 1);
            for (int zi = 0; zi < zs.size() - 1; zi++) {
                int z0 = zs.get(zi);
                int z1 = zs.get(zi + 1);
                record Interval(int lo, int hi) {}
                List<Interval> intervals = new ArrayList<>();
                for (Cuboid box : nonEmpty) {
                    if (box.min().x() <= x0 && x1 <= box.max().x() && box.min().z() <= z0 && z1 <= box.max().z()) {
                        intervals.add(new Interval(box.min().y(), box.max().y()));
                    }
                }
                if (intervals.isEmpty()) {
                    continue;
                }
                intervals.sort(Comparator.comparingInt(Interval::lo).thenComparingInt(Interval::hi));
                int lo = intervals.get(0).lo();
                int hi = intervals.get(0).hi();
                for (int i = 1; i < intervals.size(); i++) {
                    Interval next = intervals.get(i);
                    if (next.lo() <= hi) {
                        hi = Math.max(hi, next.hi());
                    } else {
                        result.add(Cuboid.of(x0, lo, z0, x1, hi, z1));
                        lo = next.lo();
                        hi = next.hi();
                    }
                }
                result.add(Cuboid.of(x0, lo, z0, x1, hi, z1));
            }
        }
        result.sort(Comparator.naturalOrder());
        return new CuboidSet(List.copyOf(result));
    }

    public List<Cuboid> boxes() {
        return boxes;
    }

    public int boxCount() {
        return boxes.size();
    }

    public boolean isEmpty() {
        return boxes.isEmpty();
    }

    public long volume() {
        long total = 0;
        for (Cuboid box : boxes) {
            long volume = box.volume();
            if (total > Long.MAX_VALUE - volume) {
                return Long.MAX_VALUE;
            }
            total += volume;
        }
        return total;
    }

    public @Nullable Cuboid bounds() {
        if (boxes.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Cuboid box : boxes) {
            minX = Math.min(minX, box.min().x());
            minY = Math.min(minY, box.min().y());
            minZ = Math.min(minZ, box.min().z());
            maxX = Math.max(maxX, box.max().x());
            maxY = Math.max(maxY, box.max().y());
            maxZ = Math.max(maxZ, box.max().z());
        }
        return Cuboid.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean contains(LatticePoint cell) {
        for (Cuboid box : boxes) {
            if (box.contains(cell)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsAll(CuboidSet other) {
        Objects.requireNonNull(other, "other");
        return other.subtract(this).isEmpty();
    }

    public boolean intersects(CuboidSet other) {
        Objects.requireNonNull(other, "other");
        for (Cuboid a : boxes) {
            for (Cuboid b : other.boxes) {
                if (a.intersects(b)) {
                    return true;
                }
            }
        }
        return false;
    }

    public CuboidSet union(CuboidSet other) {
        Objects.requireNonNull(other, "other");
        List<Cuboid> all = new ArrayList<>(boxes.size() + other.boxes.size());
        all.addAll(boxes);
        all.addAll(other.boxes);
        return of(all);
    }

    public CuboidSet union(Cuboid box) {
        return union(of(box));
    }

    public CuboidSet intersect(CuboidSet other) {
        Objects.requireNonNull(other, "other");
        List<Cuboid> result = new ArrayList<>();
        for (Cuboid a : boxes) {
            for (Cuboid b : other.boxes) {
                Cuboid intersection = a.intersect(b);
                if (intersection != null) {
                    result.add(intersection);
                }
            }
        }
        return of(result);
    }

    public CuboidSet intersect(Cuboid box) {
        return intersect(of(box));
    }

    public CuboidSet subtract(CuboidSet other) {
        Objects.requireNonNull(other, "other");
        List<Cuboid> current = new ArrayList<>(boxes);
        for (Cuboid cut : other.boxes) {
            List<Cuboid> next = new ArrayList<>();
            for (Cuboid box : current) {
                next.addAll(subtractOne(box, cut));
            }
            current = next;
        }
        return of(current);
    }

    public CuboidSet subtract(Cuboid box) {
        return subtract(of(box));
    }

    private static List<Cuboid> subtractOne(Cuboid a, Cuboid b) {
        if (!a.intersects(b)) {
            return List.of(a);
        }
        List<Cuboid> pieces = new ArrayList<>(6);
        if (a.min().x() < b.min().x()) {
            pieces.add(Cuboid.of(a.min().x(), a.min().y(), a.min().z(), b.min().x(), a.max().y(), a.max().z()));
        }
        if (b.max().x() < a.max().x()) {
            pieces.add(Cuboid.of(b.max().x(), a.min().y(), a.min().z(), a.max().x(), a.max().y(), a.max().z()));
        }
        int x0 = Math.max(a.min().x(), b.min().x());
        int x1 = Math.min(a.max().x(), b.max().x());
        if (a.min().z() < b.min().z()) {
            pieces.add(Cuboid.of(x0, a.min().y(), a.min().z(), x1, a.max().y(), b.min().z()));
        }
        if (b.max().z() < a.max().z()) {
            pieces.add(Cuboid.of(x0, a.min().y(), b.max().z(), x1, a.max().y(), a.max().z()));
        }
        int z0 = Math.max(a.min().z(), b.min().z());
        int z1 = Math.min(a.max().z(), b.max().z());
        if (a.min().y() < b.min().y()) {
            pieces.add(Cuboid.of(x0, a.min().y(), z0, x1, b.min().y(), z1));
        }
        if (b.max().y() < a.max().y()) {
            pieces.add(Cuboid.of(x0, b.max().y(), z0, x1, a.max().y(), z1));
        }
        return pieces;
    }

    public @Nullable CuboidSet translate(int dx, int dy, int dz) {
        List<Cuboid> translated = new ArrayList<>(boxes.size());
        for (Cuboid box : boxes) {
            Cuboid moved = box.translate(dx, dy, dz);
            if (moved == null) {
                return null;
            }
            translated.add(moved);
        }
        return new CuboidSet(List.copyOf(translated));
    }

    public boolean contentEquals(CuboidSet other) {
        return containsAll(other) && other.containsAll(this);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CuboidSet set && contentEquals(set);
    }

    @Override
    public int hashCode() {
        long sumX = 0;
        long sumY = 0;
        long sumZ = 0;
        long volume = 0;
        for (Cuboid box : boxes) {
            long nx = (long) box.max().x() - box.min().x();
            long ny = (long) box.max().y() - box.min().y();
            long nz = (long) box.max().z() - box.min().z();
            long cells = nx * ny * nz;
            sumX += nx * ((long) box.min().x() + box.max().x() - 1) / 2 * ny * nz;
            sumY += ny * ((long) box.min().y() + box.max().y() - 1) / 2 * nx * nz;
            sumZ += nz * ((long) box.min().z() + box.max().z() - 1) / 2 * nx * ny;
            volume += cells;
        }
        return Long.hashCode(31L * sumX + 37L * sumY + 41L * sumZ + 43L * volume);
    }

    @Override
    public String toString() {
        return boxes.toString();
    }
}
