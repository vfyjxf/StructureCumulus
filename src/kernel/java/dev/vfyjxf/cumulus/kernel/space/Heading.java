package dev.vfyjxf.cumulus.kernel.space;

public enum Heading {
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0);

    private final int dx;
    private final int dy;
    private final int dz;

    Heading(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public LatticePoint step() {
        return LatticePoint.of(dx, dy, dz);
    }

    public Heading opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }

    public Axis axis() {
        return switch (this) {
            case WEST, EAST -> Axis.X;
            case UP, DOWN -> Axis.Y;
            case NORTH, SOUTH -> Axis.Z;
        };
    }
}
