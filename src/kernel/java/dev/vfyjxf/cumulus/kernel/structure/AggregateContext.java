package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;

import java.util.List;

/** What one aggregate run of a computation sees. It cannot contribute a section value. */
public interface AggregateContext {

    ContextView context();

    WorldView view();

    Siting siting();

    List<OccupantSnapshot> occupants(Cuboid box);

    Budget remaining();

    CancelToken cancel();

    void report(Finding finding);
}
