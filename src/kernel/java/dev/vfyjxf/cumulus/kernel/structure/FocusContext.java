package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** What a focus sees during one check. */
public interface FocusContext {

    BayView bay();

    WorldView view();

    Siting siting();

    InstanceId instance();

    CuboidSet cells();

    Phase phase();

    Locus locus();

    ContextView context();

    Budget remaining();

    CancelToken cancel();

    CellRead read(LatticePoint cell);

    default @Nullable OccupantSnapshot occupantAt(LatticePoint cell) {
        return read(cell) instanceof CellRead.Found found ? found.occupant() : null;
    }

    default @Nullable ReadRefusal refusalAt(LatticePoint cell) {
        return read(cell) instanceof CellRead.Unreadable unreadable ? unreadable.refusal() : null;
    }

    List<ClaimRow> claimsAt(LatticePoint cell);
}
