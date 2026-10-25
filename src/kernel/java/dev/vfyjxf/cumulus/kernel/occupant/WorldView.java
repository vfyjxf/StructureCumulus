package dev.vfyjxf.cumulus.kernel.occupant;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The only world window: every answer comes from a frozen snapshot and never from a live world.
 */
public interface WorldView {

    SectionSlot slot(SectionKey key);

    /**
     * Returns the covering occupant with air included, or {@code null} when the cell cannot be read —
     * outside the view or behind a refused slot, which {@link #slot(SectionKey)} names.
     */
    @Nullable OccupantSnapshot occupant(LatticePoint cell);

    @Nullable OccupantSnapshot occupant(OccupantRef ref);

    List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);

    @Nullable NestedLevelSnapshot nested(LatticePoint cell);

    long contentDigest(SectionKey key);
}
