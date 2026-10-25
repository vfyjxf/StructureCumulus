package dev.vfyjxf.cumulus.kernel.base;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record Finding(FindingId id, FindingCode code, Severity severity, String message,
                      List<Label> labels, List<Note> notes, @Nullable Suggestion suggestion,
                      List<FindingId> related, Stage stage) {

    private static final Comparator<Label> LABEL_ORDER = Finding::compareLabels;

    public Finding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(labels, "labels");
        Objects.requireNonNull(notes, "notes");
        Objects.requireNonNull(related, "related");
        Objects.requireNonNull(stage, "stage");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        List<Label> canonical = new ArrayList<>(labels);
        int primaries = 0;
        for (Label label : canonical) {
            if (label.role() == LabelRole.PRIMARY) {
                primaries++;
            }
        }
        if (primaries > 1) {
            throw new IllegalArgumentException("at most one PRIMARY label, found " + primaries);
        }
        canonical.sort(LABEL_ORDER);
        if (related.contains(FindingId.NONE)) {
            throw new IllegalArgumentException("related must not hold FindingId.NONE");
        }
        labels = List.copyOf(canonical);
        notes = List.copyOf(notes);
        related = List.copyOf(related);
    }

    public static Builder builder(FindingCode code) {
        return new Builder(code);
    }

    public @Nullable Label primaryLabel() {
        for (Label label : labels) {
            if (label.role() == LabelRole.PRIMARY) {
                return label;
            }
        }
        return null;
    }

    public List<Label> labelsOf(LabelRole role) {
        List<Label> matching = new ArrayList<>();
        for (Label label : labels) {
            if (label.role() == role) {
                matching.add(label);
            }
        }
        return List.copyOf(matching);
    }

    public boolean hasPrimary() {
        return primaryLabel() != null;
    }

    public @Nullable Locus primaryLocus() {
        Label primary = primaryLabel();
        return primary == null ? null : primary.locus();
    }

    public Finding withSuggestion(Suggestion suggestion) {
        return new Finding(id, code, severity, message, labels, notes, suggestion, related, stage);
    }

    public Finding withRelated(List<FindingId> related) {
        return new Finding(id, code, severity, message, labels, notes, suggestion, related, stage);
    }

    public Finding withId(FindingId id) {
        return new Finding(id, code, severity, message, labels, notes, suggestion, related, stage);
    }

    Finding mergeDuplicate(Finding other) {
        List<FindingId> union = new ArrayList<>(related);
        for (FindingId id : other.related) {
            if (!union.contains(id)) {
                union.add(id);
            }
        }
        union.sort(Comparator.comparingInt(FindingId::value));
        @Nullable Suggestion chosen = suggestion;
        if (chosen == null) {
            chosen = other.suggestion;
        } else if (other.suggestion != null
                && other.suggestion.applicability().rank() < chosen.applicability().rank()) {
            chosen = other.suggestion;
        }
        return new Finding(id, code, severity, message, labels, notes, chosen, union, stage);
    }

    static int[] locusOrder(@Nullable Locus locus) {
        if (locus == null) {
            return new int[] {4, 0, 0, 0};
        }
        LocusFrame frame = locus.frame();
        return switch (frame) {
            case DEFINITION -> {
                SourceSpan source = locus.source();
                yield source == null ? new int[] {0, 0, 0, 0} : new int[] {0, source.start(), source.end(), 0};
            }
            case KEY -> new int[] {3, 0, 0, 0};
            case LOCAL, WORLD -> {
                LatticePoint cell = firstCell(locus.cells());
                yield cell == null
                        ? new int[] {frame.rank(), 0, 0, 0}
                        : new int[] {frame.rank(), cell.y(), cell.z(), cell.x()};
            }
        };
    }

    static @Nullable LatticePoint firstCell(CuboidSet cells) {
        LatticePoint first = null;
        for (Cuboid box : cells.boxes()) {
            if (first == null || box.min().compareTo(first) < 0) {
                first = box.min();
            }
        }
        return first;
    }

    private static int compareLabels(Label left, Label right) {
        int result = Integer.compare(left.role().rank(), right.role().rank());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(left.locus().frame().rank(), right.locus().frame().rank());
        if (result != 0) {
            return result;
        }
        result = comparePosition(left.locus(), right.locus());
        if (result != 0) {
            return result;
        }
        return Integer.compare(spanStart(left.locus()), spanStart(right.locus()));
    }

    private static int comparePosition(Locus left, Locus right) {
        LocusFrame frame = left.frame();
        if (frame == LocusFrame.DEFINITION) {
            return Integer.compare(spanStart(left), spanStart(right));
        }
        if (frame == LocusFrame.KEY) {
            return 0;
        }
        LatticePoint leftCell = firstCell(left.cells());
        LatticePoint rightCell = firstCell(right.cells());
        if (leftCell == null || rightCell == null) {
            return leftCell == null ? (rightCell == null ? 0 : -1) : 1;
        }
        return leftCell.compareTo(rightCell);
    }

    private static int spanStart(Locus locus) {
        SourceSpan source = locus.source();
        return source == null ? 0 : source.start();
    }

    public static final class Builder {

        private final FindingCode code;
        private FindingId id = FindingId.NONE;
        private @Nullable Severity severity;
        private @Nullable String message;
        private final List<Label> labels = new ArrayList<>();
        private final List<Note> notes = new ArrayList<>();
        private @Nullable Suggestion suggestion;
        private final List<FindingId> related = new ArrayList<>();
        private @Nullable Stage stage;

        private Builder(FindingCode code) {
            this.code = Objects.requireNonNull(code, "code");
        }

        public Builder id(FindingId id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = Objects.requireNonNull(severity, "severity");
            return this;
        }

        public Builder message(String message) {
            this.message = Objects.requireNonNull(message, "message");
            return this;
        }

        public Builder label(Label label) {
            labels.add(Objects.requireNonNull(label, "label"));
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

        public Builder note(String message) {
            notes.add(Note.of(message));
            return this;
        }

        public Builder note(String message, @Nullable Locus locus) {
            notes.add(new Note(message, locus));
            return this;
        }

        public Builder suggest(String message, Applicability applicability, @Nullable NamespacedId remedy) {
            this.suggestion = new Suggestion(message, applicability, remedy);
            return this;
        }

        public Builder related(FindingId id) {
            related.add(Objects.requireNonNull(id, "id"));
            return this;
        }

        public Builder stage(Stage stage) {
            this.stage = Objects.requireNonNull(stage, "stage");
            return this;
        }

        public Finding build() {
            if (severity == null) {
                throw new IllegalArgumentException("severity is required: " + code);
            }
            if (stage == null) {
                throw new IllegalArgumentException("stage is required: " + code);
            }
            String text = message;
            if (text == null) {
                for (Label label : labels) {
                    if (label.role() == LabelRole.PRIMARY) {
                        text = label.message();
                        break;
                    }
                }
            }
            if (text == null) {
                throw new IllegalArgumentException("message is required when there is no PRIMARY label: " + code);
            }
            return new Finding(id, code, severity, text, labels, notes, suggestion, related, stage);
        }
    }
}
