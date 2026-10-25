package dev.vfyjxf.cumulus.kernel.space;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

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
        return of(List.of(boxes));
    }

    public static CuboidSet of(Collection<Cuboid> boxes) {
        List<Cuboid> solid = new ArrayList<>(boxes.size());
        for (Cuboid box : boxes) {
            if (!box.isEmpty()) {
                solid.add(box);
            }
        }
        if (solid.isEmpty()) {
            return EMPTY;
        }
        List<Integer> xs = boundaries(solid, Axis.X);
        List<Integer> zs = boundaries(solid, Axis.Z);
        List<Cuboid> pieces = new ArrayList<>();
        for (int i = 0; i + 1 < xs.size(); i++) {
            int x0 = xs.get(i);
            int x1 = xs.get(i + 1);
            List<Cuboid> slab = new ArrayList<>();
            for (Cuboid box : solid) {
                if (box.min().x() <= x0 && box.max().x() >= x1) {
                    slab.add(box);
                }
            }
            if (slab.isEmpty()) {
                continue;
            }
            List<Integer> slabZ = boundaries(slab, Axis.Z);
            for (int j = 0; j + 1 < slabZ.size(); j++) {
                int z0 = slabZ.get(j);
                int z1 = slabZ.get(j + 1);
                mergeColumn(slab, x0, x1, z0, z1, pieces);
            }
        }
        pieces.sort(null);
        return new CuboidSet(List.copyOf(pieces));
    }

    private static void mergeColumn(List<Cuboid> slab, int x0, int x1, int z0, int z1, List<Cuboid> pieces) {
        List<Cuboid> column = new ArrayList<>();
        for (Cuboid box : slab) {
            if (box.min().z() <= z0 && box.max().z() >= z1) {
                column.add(box);
            }
        }
        if (column.isEmpty()) {
            return;
        }
        column.sort(Comparator.comparingInt(box -> box.min().y()));
        int start = column.get(0).min().y();
        int end = column.get(0).max().y();
        for (int i = 1; i < column.size(); i++) {
            Cuboid next = column.get(i);
            if (next.min().y() < end) {
                end = Math.max(end, next.max().y());
            } else {
                pieces.add(Cuboid.of(x0, start, z0, x1, end, z1));
                start = next.min().y();
                end = next.max().y();
            }
        }
        pieces.add(Cuboid.of(x0, start, z0, x1, end, z1));
    }

    private static List<Integer> boundaries(List<Cuboid> boxes, Axis axis) {
        TreeSet<Integer> values = new TreeSet<>();
        for (Cuboid box : boxes) {
            values.add(coordinate(box.min(), axis));
            values.add(coordinate(box.max(), axis));
        }
        return new ArrayList<>(values);
    }

    private static int coordinate(LatticePoint point, Axis axis) {
        return switch (axis) {
            case X -> point.x();
            case Y -> point.y();
            case Z -> point.z();
        };
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
        long total = 0L;
        for (Cuboid box : boxes) {
            long volume = box.volume();
            total = Long.MAX_VALUE - total < volume ? Long.MAX_VALUE : total + volume;
        }
        return total;
    }

    public @Nullable Cuboid bounds() {
        if (boxes.isEmpty()) {
            return null;
        }
        Cuboid bounds = boxes.get(0);
        for (Cuboid box : boxes) {
            bounds = bounds.bounding(box);
        }
        return bounds;
    }

    public boolean contains(LatticePoint point) {
        for (Cuboid box : boxes) {
            if (box.contains(point)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsAll(CuboidSet other) {
        return other.subtract(this).isEmpty();
    }

    public boolean intersects(CuboidSet other) {
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
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        List<Cuboid> merged = new ArrayList<>(boxes.size() + other.boxes.size());
        merged.addAll(boxes);
        merged.addAll(other.boxes);
        return of(merged);
    }

    public CuboidSet union(Cuboid box) {
        return union(of(box));
    }

    public CuboidSet intersect(CuboidSet other) {
        if (isEmpty() || other.isEmpty()) {
            return EMPTY;
        }
        List<Cuboid> pieces = new ArrayList<>();
        for (Cuboid a : boxes) {
            for (Cuboid b : other.boxes) {
                Cuboid piece = a.intersect(b);
                if (piece != null) {
                    pieces.add(piece);
                }
            }
        }
        return of(pieces);
    }

    public CuboidSet intersect(Cuboid box) {
        return intersect(of(box));
    }

    public CuboidSet subtract(CuboidSet other) {
        if (isEmpty() || other.isEmpty()) {
            return this;
        }
        CuboidSet result = this;
        for (Cuboid box : other.boxes) {
            result = result.subtract(box);
            if (result.isEmpty()) {
                return EMPTY;
            }
        }
        return result;
    }

    public CuboidSet subtract(Cuboid removed) {
        if (isEmpty() || removed.isEmpty()) {
            return this;
        }
        List<Cuboid> pieces = new ArrayList<>();
        for (Cuboid box : boxes) {
            if (!box.intersects(removed)) {
                pieces.add(box);
                continue;
            }
            int lowX = Math.max(box.min().x(), removed.min().x());
            int highX = Math.min(box.max().x(), removed.max().x());
            int lowY = Math.max(box.min().y(), removed.min().y());
            int highY = Math.min(box.max().y(), removed.max().y());
            int lowZ = Math.max(box.min().z(), removed.min().z());
            int highZ = Math.min(box.max().z(), removed.max().z());
            if (box.min().x() < lowX) {
                pieces.add(Cuboid.of(box.min().x(), box.min().y(), box.min().z(),
                        lowX, box.max().y(), box.max().z()));
            }
            if (highX < box.max().x()) {
                pieces.add(Cuboid.of(highX, box.min().y(), box.min().z(),
                        box.max().x(), box.max().y(), box.max().z()));
            }
            if (box.min().y() < lowY) {
                pieces.add(Cuboid.of(lowX, box.min().y(), box.min().z(),
                        highX, lowY, box.max().z()));
            }
            if (highY < box.max().y()) {
                pieces.add(Cuboid.of(lowX, highY, box.min().z(),
                        highX, box.max().y(), box.max().z()));
            }
            if (box.min().z() < lowZ) {
                pieces.add(Cuboid.of(lowX, lowY, box.min().z(),
                        highX, highY, lowZ));
            }
            if (highZ < box.max().z()) {
                pieces.add(Cuboid.of(lowX, lowY, highZ,
                        highX, highY, box.max().z()));
            }
        }
        return of(pieces);
    }

    public @Nullable CuboidSet translate(int dx, int dy, int dz) {
        List<Cuboid> moved = new ArrayList<>(boxes.size());
        for (Cuboid box : boxes) {
            Cuboid image = box.translate(dx, dy, dz);
            if (image == null) {
                return null;
            }
            moved.add(image);
        }
        return of(moved);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CuboidSet set && boxes.equals(set.boxes);
    }

    @Override
    public int hashCode() {
        return boxes.hashCode();
    }

    @Override
    public String toString() {
        return boxes.toString();
    }
}
