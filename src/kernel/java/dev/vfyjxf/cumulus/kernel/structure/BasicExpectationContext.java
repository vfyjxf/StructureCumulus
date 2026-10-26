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
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

final class BasicExpectationContext<T> implements ExpectationContext<T> {

    private final FocusContext base;
    private final T subject;
    private final List<OccupantSnapshot> subjects;

    BasicExpectationContext(FocusContext base, T subject, List<OccupantSnapshot> subjects) {
        this.base = Objects.requireNonNull(base, "base");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.subjects = List.copyOf(subjects);
    }

    @Override
    public T subject() {
        return subject;
    }

    @Override
    public List<OccupantSnapshot> subjects() {
        return subjects;
    }

    @Override
    public <V> ExpectationContext<V> withSubject(V subject, List<OccupantSnapshot> subjects) {
        return new BasicExpectationContext<>(this, subject, subjects);
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
    public InstanceId instance() {
        return base.instance();
    }

    @Override
    public CuboidSet cells() {
        return base.cells();
    }

    @Override
    public Phase phase() {
        return base.phase();
    }

    @Override
    public Locus locus() {
        return base.locus();
    }

    @Override
    public ContextView context() {
        return base.context();
    }

    @Override
    public Budget remaining() {
        return base.remaining();
    }

    @Override
    public CancelToken cancel() {
        return base.cancel();
    }

    @Override
    public @Nullable OccupantSnapshot occupantAt(LatticePoint cell) {
        return base.occupantAt(cell);
    }

    @Override
    public List<ClaimRow> claimsAt(LatticePoint cell) {
        return base.claimsAt(cell);
    }
}
