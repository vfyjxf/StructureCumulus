package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.Checks;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.LocusFrame;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** One report value. */
public record Finding(
    FindingCode code,
    Severity severity,
    String message,
    List<Label> labels,
    List<Note> notes,
    @Nullable Suggestion suggestion,
    Phase phase
) {

    public Finding {
        Checks.checkNotNull(code, "code");
        Checks.checkNotNull(severity, "severity");
        Checks.checkNotNull(phase, "phase");
        Checks.checkNotNull(message, "message");
        Checks.checkArgument(!message.isBlank(), "a finding message must not be blank");
        List<Label> ordered = new ArrayList<>(Checks.checkNotNull(labels, "labels"));
        int primaryCount = 0;
        for (Label label : ordered) {
            Checks.checkNotNull(label, "label");
            if (label.role() == LabelRole.primary) {
                primaryCount++;
            }
        }
        Checks.checkArgument(primaryCount <= 1, "at most one PRIMARY label");
        ordered.sort(Finding::compareLabels);
        labels = List.copyOf(ordered);
        notes = List.copyOf(Checks.checkNotNull(notes, "notes"));
    }

    public static Builder builder(FindingCode code, Severity severity) {
        return new Builder(Checks.checkNotNull(code, "code"), Checks.checkNotNull(severity, "severity"));
    }

    public static Builder builder(CodeEntry entry) {
        Checks.checkNotNull(entry, "entry");
        return new Builder(entry.code(), entry.severity());
    }

    public @Nullable Label primaryLabel() {
        for (Label label : labels) {
            if (label.role() == LabelRole.primary) {
                return label;
            }
        }
        return null;
    }

    public List<Label> labelsOf(LabelRole role) {
        Checks.checkNotNull(role, "role");
        List<Label> selected = new ArrayList<>();
        for (Label label : labels) {
            if (label.role() == role) {
                selected.add(label);
            }
        }
        return List.copyOf(selected);
    }

    public boolean hasPrimary() {
        return primaryLabel() != null;
    }

    private static int compareLabels(Label left, Label right) {
        int byRole = Integer.compare(left.role().rank(), right.role().rank());
        if (byRole != 0) {
            return byRole;
        }
        Locus leftLocus = left.locus();
        Locus rightLocus = right.locus();
        int byFrame = Integer.compare(leftLocus.frame().rank(), rightLocus.frame().rank());
        if (byFrame != 0) {
            return byFrame;
        }
        if (leftLocus.frame() == LocusFrame.key) {
            return 0;
        }
        if (leftLocus.frame() == LocusFrame.definition) {
            SourceSpan leftSource = leftLocus.source();
            SourceSpan rightSource = rightLocus.source();
            if (leftSource == null || rightSource == null) {
                return 0;
            }
            int byStart = Integer.compare(leftSource.start(), rightSource.start());
            return byStart != 0 ? byStart : Integer.compare(leftSource.end(), rightSource.end());
        }
        return firstCell(leftLocus).compareTo(firstCell(rightLocus));
    }

    private static LatticePoint firstCell(Locus locus) {
        return locus.cells().decomposition().get(0).min();
    }

    public static final class Builder {
        private final FindingCode code;
        private final Severity severity;
        private final List<Label> labels = new ArrayList<>();
        private final List<Note> notes = new ArrayList<>();
        private @Nullable Phase phase;
        private @Nullable String message;
        private @Nullable Suggestion suggestion;

        private Builder(FindingCode code, Severity severity) {
            this.code = code;
            this.severity = severity;
        }

        public Builder phase(Phase phase) {
            this.phase = Checks.checkNotNull(phase, "phase");
            return this;
        }

        public Builder message(String message) {
            this.message = Checks.checkNotNull(message, "message");
            return this;
        }

        public Builder label(Label label) {
            labels.add(Checks.checkNotNull(label, "label"));
            return this;
        }

        public Builder label(Locus locus, String message) {
            return label(Label.primary(locus, message));
        }

        public Builder label(Locus locus, LabelRole role, String message) {
            return label(new Label(locus, message, role));
        }

        public Builder secondary(Locus locus, String message) {
            return label(Label.secondary(locus, message));
        }

        public Builder context(Locus locus, String message) {
            return label(Label.context(locus, message));
        }

        public Builder note(String message) {
            return note(message, null);
        }

        public Builder note(String message, @Nullable Locus locus) {
            notes.add(new Note(message, locus));
            return this;
        }

        public Builder suggest(String message, Applicability applicability, @Nullable NamespacedId remedy) {
            suggestion = new Suggestion(message, applicability, remedy);
            return this;
        }

        public Finding build() {
            Phase stamped = phase;
            if (stamped == null) {
                throw new IllegalArgumentException("a finding requires a phase");
            }
            Label primary = null;
            for (Label label : labels) {
                if (label.role() == LabelRole.primary) {
                    Checks.checkArgument(primary == null, "at most one PRIMARY label");
                    primary = label;
                }
            }
            String resolved = message;
            if (resolved == null) {
                Label fallback = primary;
                if (fallback == null) {
                    throw new IllegalArgumentException("a finding without a primary label must set a message");
                }
                resolved = fallback.message();
            }
            return new Finding(code, severity, resolved, labels, notes, suggestion, stamped);
        }
    }
}
