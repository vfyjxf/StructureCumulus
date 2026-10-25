package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;

import java.util.List;

public interface AggregateContext {

    <T> Outcome<T> read(ContextKey<T> key);

    List<OccupantSnapshot> occupants(Cuboid box);

    ContextView context();
}
