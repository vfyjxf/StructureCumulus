package dev.vfyjxf.cumulus.kernel.space;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record Orientation(int ordinal) implements Comparable<Orientation> {

    private static final int COUNT = 48;
    private static final int IDENTITY_CODE = encode(new int[] {0, 1, 2}, new int[] {1, 1, 1});
    private static final int MIRROR_CODE = encode(new int[] {0, 1, 2}, new int[] {-1, 1, 1});

    private static final Turn[] TURNS = Turn.values();
    private static final Tilt[] TILTS = Tilt.values();
    private static final int[] MATRIX = new int[COUNT];
    private static final int[] ORDINAL = new int[512];
    private static final List<Orientation> ALL;

    public static final Orientation IDENTITY = new Orientation(0);

    static {
        Arrays.fill(ORDINAL, -1);
        for (int index = 0; index < COUNT; index++) {
            int turn = index / 12;
            int tilt = (index % 12) / 2;
            boolean mirror = (index & 1) == 1;
            int code = multiply(turnCode(TURNS[turn]),
                    multiply(tiltCode(TILTS[tilt]), mirror ? MIRROR_CODE : IDENTITY_CODE));
            MATRIX[index] = code;
            ORDINAL[code] = index;
        }
        Orientation[] all = new Orientation[COUNT];
        for (int index = 0; index < COUNT; index++) {
            all[index] = new Orientation(index);
        }
        ALL = List.of(all);
    }

    public Orientation {
        if (ordinal < 0 || ordinal >= COUNT) {
            throw new IllegalArgumentException("ordinal must be within 0..47: " + ordinal);
        }
    }

    public static Orientation of(Turn turn, Tilt tilt, Flip flip) {
        int code = multiply(turnCode(turn), multiply(tiltCode(tilt), flipCode(flip)));
        return ofMatrix(code);
    }

    public static List<Orientation> all() {
        return ALL;
    }

    public Orientation compose(Orientation other) {
        return ofMatrix(multiply(matrix(), other.matrix()));
    }

    /** This orientation, then {@code other}. */
    public Orientation then(Orientation other) {
        return ofMatrix(multiply(other.matrix(), matrix()));
    }

    public Orientation inverse() {
        return ofMatrix(invert(matrix()));
    }

    public Heading apply(Heading heading) {
        int[] vector = vector(heading);
        int code = matrix();
        int[] image = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            image[axis] = sign(code, axis) * vector[perm(code, axis)];
        }
        return headingOf(image);
    }

    public @Nullable LatticePoint apply(LatticePoint point) {
        int code = matrix();
        int x = sign(code, 0) * coordinate(point, perm(code, 0));
        int y = sign(code, 1) * coordinate(point, perm(code, 1));
        int z = sign(code, 2) * coordinate(point, perm(code, 2));
        return LatticePoint.inLattice(x, y, z) ? new LatticePoint(x, y, z) : null;
    }

    public @Nullable Cuboid apply(Cuboid box) {
        int code = matrix();
        int[] low = new int[3];
        int[] high = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            int source = perm(code, axis);
            if (sign(code, axis) > 0) {
                low[axis] = coordinate(box.min(), source);
                high[axis] = coordinate(box.max(), source);
            } else {
                low[axis] = 1 - coordinate(box.max(), source);
                high[axis] = 1 - coordinate(box.min(), source);
            }
        }
        if (!LatticePoint.inLattice(low[0], low[1], low[2])
                || !LatticePoint.inLattice(high[0], high[1], high[2])) {
            return null;
        }
        return Cuboid.of(low[0], low[1], low[2], high[0], high[1], high[2]);
    }

    public @Nullable CuboidSet apply(CuboidSet cells) {
        List<Cuboid> images = new ArrayList<>(cells.boxCount());
        for (Cuboid box : cells.boxes()) {
            Cuboid image = apply(box);
            if (image == null) {
                return null;
            }
            images.add(image);
        }
        return CuboidSet.of(images);
    }

    public Turn turn() {
        return TURNS[ordinal / 12];
    }

    public Tilt tilt() {
        return TILTS[(ordinal % 12) / 2];
    }

    public boolean isMirror() {
        return (ordinal & 1) == 1;
    }

    public int id() {
        return ordinal;
    }

    @Override
    public int compareTo(Orientation other) {
        return Integer.compare(ordinal, other.ordinal);
    }

    private int matrix() {
        return MATRIX[ordinal];
    }

    private static Orientation ofMatrix(int code) {
        int index = ORDINAL[code];
        if (index < 0) {
            throw new IllegalStateException("not a lattice isometry: " + code);
        }
        return new Orientation(index);
    }

    private static int encode(int[] perm, int[] signs) {
        return perm[0]
                | perm[1] << 2
                | perm[2] << 4
                | (signs[0] < 0 ? 1 : 0) << 6
                | (signs[1] < 0 ? 1 : 0) << 7
                | (signs[2] < 0 ? 1 : 0) << 8;
    }

    private static int perm(int code, int axis) {
        return (code >>> (2 * axis)) & 0b11;
    }

    private static int sign(int code, int axis) {
        return ((code >>> (6 + axis)) & 1) == 0 ? 1 : -1;
    }

    private static int multiply(int first, int second) {
        int[] perm = new int[3];
        int[] signs = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            int outer = perm(first, axis);
            perm[axis] = perm(second, outer);
            signs[axis] = sign(first, axis) * sign(second, outer);
        }
        return encode(perm, signs);
    }

    private static int invert(int code) {
        int[] perm = new int[3];
        int[] signs = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            int local = perm(code, axis);
            perm[local] = axis;
            signs[local] = sign(code, axis);
        }
        return encode(perm, signs);
    }

    private static int turnCode(Turn turn) {
        return switch (turn) {
            case NONE -> IDENTITY_CODE;
            case CLOCKWISE -> encode(new int[] {2, 1, 0}, new int[] {-1, 1, 1});
            case HALF -> encode(new int[] {0, 1, 2}, new int[] {-1, 1, -1});
            case COUNTERCLOCKWISE -> encode(new int[] {2, 1, 0}, new int[] {1, 1, -1});
        };
    }

    private static int tiltCode(Tilt tilt) {
        return switch (tilt) {
            case LEVEL -> IDENTITY_CODE;
            case PITCH_UP -> encode(new int[] {0, 2, 1}, new int[] {1, -1, 1});
            case PITCH_DOWN -> encode(new int[] {0, 2, 1}, new int[] {1, 1, -1});
            case ROLL_LEFT -> encode(new int[] {1, 0, 2}, new int[] {-1, 1, 1});
            case ROLL_RIGHT -> encode(new int[] {1, 0, 2}, new int[] {1, -1, 1});
            case INVERTED -> encode(new int[] {0, 1, 2}, new int[] {1, -1, -1});
        };
    }

    private static int flipCode(Flip flip) {
        return switch (flip) {
            case NONE -> IDENTITY_CODE;
            case LEFT_RIGHT -> encode(new int[] {0, 1, 2}, new int[] {1, 1, -1});
            case FRONT_BACK -> MIRROR_CODE;
        };
    }

    private static int coordinate(LatticePoint point, int axis) {
        return switch (axis) {
            case 0 -> point.x();
            case 1 -> point.y();
            default -> point.z();
        };
    }

    private static int[] vector(Heading heading) {
        return switch (heading) {
            case NORTH -> new int[] {0, 0, -1};
            case SOUTH -> new int[] {0, 0, 1};
            case WEST -> new int[] {-1, 0, 0};
            case EAST -> new int[] {1, 0, 0};
            case UP -> new int[] {0, 1, 0};
            case DOWN -> new int[] {0, -1, 0};
        };
    }

    private static Heading headingOf(int[] vector) {
        if (vector[0] != 0) {
            return vector[0] > 0 ? Heading.EAST : Heading.WEST;
        }
        if (vector[1] != 0) {
            return vector[1] > 0 ? Heading.UP : Heading.DOWN;
        }
        return vector[2] > 0 ? Heading.SOUTH : Heading.NORTH;
    }
}
