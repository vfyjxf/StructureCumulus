package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** What one section run of a computation sees. It has no aggregate read. */
public interface SectionContext {

    SectionKey section();

    CuboidSet cells();

    WorldView view();

    Siting siting();

    Phase phase();

    CellRead read(LatticePoint cell);

    default @Nullable OccupantSnapshot occupantAt(LatticePoint cell) {
        return read(cell) instanceof CellRead.Found found ? found.occupant() : null;
    }

    default @Nullable ReadRefusal refusalAt(LatticePoint cell) {
        return read(cell) instanceof CellRead.Unreadable unreadable ? unreadable.refusal() : null;
    }

    Map<LatticePoint, ReadRefusal> unreadable();

    List<OccupantSnapshot> occupants();

    List<OccupantSnapshot> occupants(Cuboid box);

    Budget remaining();

    CancelToken cancel();

    void report(Finding finding);
}
