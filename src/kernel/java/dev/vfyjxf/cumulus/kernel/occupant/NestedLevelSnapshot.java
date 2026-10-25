package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The frozen interior of one nested occupant, read in its own frame.
 */
public final class NestedLevelSnapshot {

    private final OccupantRef parent;
    private final int depth;
    private final SectionSnapshot.CellIndex cells;

    private NestedLevelSnapshot(OccupantRef parent, int depth, SectionSnapshot.CellIndex cells) {
        this.parent = parent;
        this.depth = depth;
        this.cells = cells;
    }

    public static NestedLevelSnapshot of(OccupantRef parent, int depth,
                                         Map<LatticePoint, OccupantSnapshot> occupants) {
        Objects.requireNonNull(parent, "parent");
        if (depth < 1) {
            throw new IllegalArgumentException("depth must be at least 1: " + depth);
        }
        return new NestedLevelSnapshot(parent, depth, new SectionSnapshot.CellIndex(occupants));
    }

    public OccupantRef parent() {
        return parent;
    }

    public int depth() {
        return depth;
    }

    public long contentDigest() {
        return cells.digest();
    }

    /**
     * {@code null} only when the point is outside the cells this view enumerated.
     */
    public @Nullable OccupantSnapshot occupantAt(LatticePoint cell) {
        return cells.at(cell);
    }

    public List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter) {
        return cells.occupants(box, filter);
    }
}
