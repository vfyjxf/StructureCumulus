package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.List;
/** What one section run of a computation sees. It has no aggregate read. */
public interface SectionContext {

    SectionKey section();

    CuboidSet cells();

    WorldView view();

    Siting siting();

    @Nullable OccupantSnapshot occupantAt(LatticePoint cell);

    List<OccupantSnapshot> occupants();

    List<OccupantSnapshot> occupants(Cuboid box);

    Budget remaining();

    CancelToken cancel();

    void report(Finding finding);
}
