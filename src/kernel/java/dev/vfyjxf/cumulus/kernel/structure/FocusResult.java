package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
/** What a focus bound, or why it could not. */
public sealed interface FocusResult<T> {
    record Taken<T>(T value, List<OccupantSnapshot> subjects, FindingBundle notes)
            implements FocusResult<T> {
        public Taken {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(subjects, "subjects");
            Objects.requireNonNull(notes, "notes");
            subjects = List.copyOf(subjects);
            for (OccupantSnapshot subject : subjects) {
                Objects.requireNonNull(subject, "subject");
            }
        }

        @Override
        public <R> R fold(BiFunction<T, List<OccupantSnapshot>, R> onTaken,
                          Function<FindingBundle, R> onUnavailable) {
            return onTaken.apply(value, subjects);
        }
    }

    record Unavailable<T>(FindingBundle findings) implements FocusResult<T> {
        public Unavailable {
            Objects.requireNonNull(findings, "findings");
            if (findings.isEmpty()) {
                throw new IllegalArgumentException("an unavailable focus needs a finding");
            }
        }

        @Override
        public <R> R fold(BiFunction<T, List<OccupantSnapshot>, R> onTaken,
                          Function<FindingBundle, R> onUnavailable) {
            return onUnavailable.apply(findings);
        }
    }

    <R> R fold(BiFunction<T, List<OccupantSnapshot>, R> onTaken,
               Function<FindingBundle, R> onUnavailable);
}
