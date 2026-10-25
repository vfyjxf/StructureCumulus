package dev.vfyjxf.cumulus.kernel.base;

import dev.vfyjxf.cumulus.kernel.occupant.OccupantRef;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public sealed interface Outcome<T> permits Outcome.Found, Outcome.Missing {

    record Found<T>(T value, List<OccupantRef> subjects, FindingBundle notes) implements Outcome<T> {

        public Found {
            subjects = List.copyOf(subjects);
        }

        @Override
        public <R> R fold(BiFunction<T, List<OccupantRef>, R> onFound, Function<FindingBundle, R> onMissing) {
            return onFound.apply(value, subjects);
        }
    }

    record Missing<T>(FindingBundle findings) implements Outcome<T> {

        @Override
        public <R> R fold(BiFunction<T, List<OccupantRef>, R> onFound, Function<FindingBundle, R> onMissing) {
            return onMissing.apply(findings);
        }
    }

    <R> R fold(BiFunction<T, List<OccupantRef>, R> onFound, Function<FindingBundle, R> onMissing);
}
