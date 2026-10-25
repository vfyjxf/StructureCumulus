package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.context.AggregateContext;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.ContextView;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.WorldView;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.List;
import java.util.Objects;

/** The read surface one check sees: its bay, its siting, the snapshot and the folded context. */
public interface FocusContext extends AggregateContext {

    BayView bay();

    WorldView view();

    Siting siting();

    Cuboid cells();

    Stage stage();

    Locus locus();

    static FocusContext of(BayView bay, Siting siting, ContextView context, Stage stage, Locus locus) {
        return new BasicFocusContext(bay, siting, context, stage, locus);
    }
}

record BasicFocusContext(BayView bay, Siting siting, ContextView context, Stage stage, Locus locus)
        implements FocusContext {

    BasicFocusContext {
        Objects.requireNonNull(bay, "bay");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(locus, "locus");
    }

    @Override
    public WorldView view() {
        return bay.view();
    }

    @Override
    public Cuboid cells() {
        return bay.cells();
    }

    @Override
    public <T> Outcome<T> read(ContextKey<T> key) {
        return context.require(key);
    }

    @Override
    public List<OccupantSnapshot> occupants(Cuboid box) {
        return view().occupants(box, OccupantFilter.any());
    }
}
