package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;

import java.util.List;

/** A focus context plus the subject the test may inspect. */
public interface ExpectationContext<T> extends FocusContext {

    T subject();

    List<OccupantSnapshot> subjects();

    <V> ExpectationContext<V> withSubject(V subject, List<OccupantSnapshot> subjects);

    static <T> ExpectationContext<T> of(FocusContext base, T subject, List<OccupantSnapshot> subjects) {
        return new BasicExpectationContext<>(base, subject, subjects);
    }
}
