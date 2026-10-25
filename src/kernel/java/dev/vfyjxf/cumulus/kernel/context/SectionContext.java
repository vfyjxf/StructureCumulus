package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;

import java.util.List;

/** What one section run sees; there is deliberately no {@code read(ContextKey)}. */
public interface SectionContext {

    SectionKey section();

    CuboidSet cells();

    List<OccupantSnapshot> occupants();

    List<OccupantSnapshot> occupants(Cuboid box);
}
