package dev.vfyjxf.cumulus.kernel.space;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One of the 48 signed axis permutations. */
public record Orientation(int ordinal) implements Comparable<Orientation> {

    public static final Orientation identity = new Orientation(0);

    private static final int[][][] matrices = new int[48][][];
    private static final Turn[] turns = new Turn[48];
    private static final Tilt[] tilts = new Tilt[48];
    private static final boolean[] mirrors = new boolean[48];
    private static final Map<String, Integer> ordinals = new HashMap<>();
    private static final List<Orientation> orientations;

    static {
        List<Orientation> all = new ArrayList<>(48);
        int[][] identity = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        int[][] frontBack = Flip.frontBack.matrix();
        for (int turn = 0; turn < 4; turn++) {
            for (int tilt = 0; tilt < 6; tilt++) {
                for (int parity = 0; parity < 2; parity++) {
                    int ordinal = (turn * 6 + tilt) * 2 + parity;
                    int[][] matrix = multiply(
                        Turn.values()[turn].matrix(),
                        multiply(Tilt.values()[tilt].matrix(), parity == 0 ? identity : frontBack)
                    );
                    matrices[ordinal] = matrix;
                    turns[ordinal] = Turn.values()[turn];
                    tilts[ordinal] = Tilt.values()[tilt];
                    mirrors[ordinal] = determinant(matrix) < 0;
                    ordinals.put(key(matrix), ordinal);
                    all.add(new Orientation(ordinal));
                }
            }
        }
        orientations = List.copyOf(all);
    }

    public Orientation {
        if (ordinal < 0 || ordinal >= 48) {
            throw new IllegalArgumentException("ordinal must be 0..47");
        }
    }

    public static Orientation of(Turn turn, Tilt tilt, Flip flip) {
        int[][] matrix = multiply(turn.matrix(), multiply(tilt.matrix(), flip.matrix()));
        Integer ordinal = ordinals.get(key(matrix));
        if (ordinal == null) {
            throw new IllegalStateException("generator triple is not an orientation");
        }
        return new Orientation(ordinal);
    }

    public static List<Orientation> all() {
        return orientations;
    }

    public Orientation compose(Orientation other) {
        int[][] matrix = multiply(matrices[ordinal], matrices[other.ordinal]);
        Integer result = ordinals.get(key(matrix));
        if (result == null) {
            throw new IllegalStateException("composition left the group");
        }
        return new Orientation(result);
    }

    public Orientation inverse() {
        int[][] inverse = transpose(matrices[ordinal]);
        Integer result = ordinals.get(key(inverse));
        if (result == null) {
            throw new IllegalStateException("inverse left the group");
        }
        return new Orientation(result);
    }

    public Heading apply(Heading heading) {
        int[] vector = applyVector(heading.stepX(), heading.stepY(), heading.stepZ());
        return Heading.ofStep(vector[0], vector[1], vector[2]);
    }

    public @Nullable LatticePoint apply(LatticePoint point) {
        int[] vector = applyVector(point.x(), point.y(), point.z());
        if (!LatticePoint.inLattice(vector[0], vector[1], vector[2])) {
            return null;
        }
        return LatticePoint.of(vector[0], vector[1], vector[2]);
    }

    public @Nullable Cuboid apply(Cuboid cuboid) {
        int[] lo = new int[3];
        int[] hi = new int[3];
        int[][] matrix = matrices[ordinal];
        for (int axis = 0; axis < 3; axis++) {
            int source = -1;
            int sign = 0;
            for (int column = 0; column < 3; column++) {
                if (matrix[axis][column] != 0) {
                    source = column;
                    sign = matrix[axis][column];
                }
            }
            if (source < 0) {
                throw new IllegalStateException("not a signed permutation");
            }
            int[] min = {cuboid.min().x(), cuboid.min().y(), cuboid.min().z()};
            int[] max = {cuboid.max().x(), cuboid.max().y(), cuboid.max().z()};
            if (sign > 0) {
                lo[axis] = min[source];
                hi[axis] = max[source];
            } else {
                lo[axis] = 1 - max[source];
                hi[axis] = 1 - min[source];
            }
        }
        if (!LatticePoint.inLattice(lo[0], lo[1], lo[2]) || !LatticePoint.inLattice(hi[0], hi[1], hi[2])) {
            return null;
        }
        return Cuboid.of(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }

    public @Nullable CuboidSet apply(CuboidSet cells) {
        List<Cuboid> boxes = new ArrayList<>(cells.boxCount());
        for (Cuboid box : cells.boxes()) {
            Cuboid image = apply(box);
            if (image == null) {
                return null;
            }
            boxes.add(image);
        }
        return CuboidSet.of(boxes);
    }

    public Turn turn() {
        return turns[ordinal];
    }

    public Tilt tilt() {
        return tilts[ordinal];
    }

    public boolean isMirror() {
        return mirrors[ordinal];
    }

    private int[] applyVector(int x, int y, int z) {
        int[][] matrix = matrices[ordinal];
        int[] result = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            result[axis] = matrix[axis][0] * x + matrix[axis][1] * y + matrix[axis][2] * z;
        }
        return result;
    }

    @Override
    public int compareTo(Orientation other) {
        return Integer.compare(ordinal, other.ordinal);
    }

    private static int[][] multiply(int[][] left, int[][] right) {
        int[][] result = new int[3][3];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int value = 0;
                for (int k = 0; k < 3; k++) {
                    value += left[row][k] * right[k][column];
                }
                result[row][column] = value;
            }
        }
        return result;
    }

    private static int[][] transpose(int[][] matrix) {
        int[][] result = new int[3][3];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                result[row][column] = matrix[column][row];
            }
        }
        return result;
    }

    private static int determinant(int[][] matrix) {
        return matrix[0][0] * (matrix[1][1] * matrix[2][2] - matrix[1][2] * matrix[2][1])
                - matrix[0][1] * (matrix[1][0] * matrix[2][2] - matrix[1][2] * matrix[2][0])
                + matrix[0][2] * (matrix[1][0] * matrix[2][1] - matrix[1][1] * matrix[2][0]);
    }

    private static String key(int[][] matrix) {
        StringBuilder builder = new StringBuilder(18);
        for (int[] row : matrix) {
            for (int value : row) {
                builder.append(value).append(',');
            }
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return "Orientation[" + ordinal + ":" + turn() + "/" + tilt() + (isMirror() ? "/mirror" : "") + "]";
    }
}
