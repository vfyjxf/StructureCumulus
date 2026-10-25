package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.Locus;
import dev.vfyjxf.cumulus.kernel.base.Outcome;
import dev.vfyjxf.cumulus.kernel.base.Stage;
import dev.vfyjxf.cumulus.kernel.context.ContextKey;
import dev.vfyjxf.cumulus.kernel.context.ContextView;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import dev.vfyjxf.cumulus.kernel.occupant.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.occupant.WorldView;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.List;
import java.util.Objects;

/**
 * What one test may see: the bound subject and its subjects, plus the read surface of the check.
 * {@code withSubject} re-types the subject, so a bay-level test can hand one cell to a cell test.
 */
public interface ExpectationContext<T> extends FocusContext {

    T subject();

    List<OccupantRef> subjects();

    <V> ExpectationContext<V> withSubject(V subject, List<OccupantRef> subjects);

    static <T> ExpectationContext<T> of(FocusContext base, T subject, List<OccupantRef> subjects) {
        return new BoundExpectationContext<>(base, subject, subjects);
    }
}

record BoundExpectationContext<T>(FocusContext base, T subject, List<OccupantRef> subjects)
        implements ExpectationContext<T> {

    BoundExpectationContext {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(subject, "subject");
        subjects = List.copyOf(subjects);
    }

    @Override
    public BayView bay() {
        return base.bay();
    }

    @Override
    public WorldView view() {
        return base.view();
    }

    @Override
    public Siting siting() {
        return base.siting();
    }

    @Override
    public Cuboid cells() {
        return base.cells();
    }

    @Override
    public ContextView context() {
        return base.context();
    }

    @Override
    public <V> Outcome<V> read(ContextKey<V> key) {
        return base.read(key);
    }

    @Override
    public List<OccupantSnapshot> occupants(Cuboid box) {
        return base.occupants(box);
    }

    @Override
    public Stage stage() {
        return base.stage();
    }

    @Override
    public Locus locus() {
        return base.locus();
    }

    @Override
    public <V> ExpectationContext<V> withSubject(V subject, List<OccupantRef> subjects) {
        return new BoundExpectationContext<>(base, subject, subjects);
    }
}
