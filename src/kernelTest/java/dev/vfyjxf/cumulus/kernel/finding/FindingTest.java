package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingTest {

    private static final NamespacedId DOC = NamespacedId.of("test", "doc");
    private static final World WORLD = World.of(NamespacedId.of("test", "world"));
    private static final InstanceId INSTANCE = new InstanceId(WORLD, 1);

    private static Locus local(int x) {
        return Locus.local(CuboidSet.of(Cuboid.of(x, 0, 0, x + 1, 1, 1)));
    }

    private static Locus world(int x) {
        return Locus.world(CuboidSet.of(Cuboid.of(x, 0, 0, x + 1, 1, 1)));
    }

    private static Locus key(String path) {
        return Locus.key(INSTANCE, NamespacedId.of("test", path));
    }

    @Test
    void builderDefaultsMessageFromPrimary() {
        Finding finding = Finding.builder(Codes.EXPECTED).phase(Phase.CHECK).label(local(0), "expected oak").build();
        assertEquals("expected oak", finding.message());
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals(Codes.EXPECTED.code(), finding.code());
        assertEquals(Phase.CHECK, finding.phase());
        assertTrue(finding.hasPrimary());
        assertEquals("expected oak", java.util.Objects.requireNonNull(finding.primaryLabel(), "primary").message());
    }

    @Test
    void labelLessFindingRequiresAMessageAndPhase() {
        assertThrows(
            IllegalArgumentException.class,
            () -> Finding.builder(Codes.BUDGET_EXHAUSTED).message("budget").build()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> Finding.builder(Codes.BUDGET_EXHAUSTED).phase(Phase.CHECK).build()
        );
        Finding finding = Finding.builder(Codes.BUDGET_EXHAUSTED).phase(Phase.CHECK).message("budget").build();
        assertNull(finding.primaryLabel());
        assertFalse(finding.hasPrimary());
        assertEquals("budget", finding.message());
    }

    @Test
    void atMostOnePrimaryLabelAndNonBlankProse() {
        Finding.Builder builder = Finding.builder(Codes.EXPECTED).phase(Phase.CHECK).label(local(0), "one")
                .label(local(1), "two");
        assertThrows(IllegalArgumentException.class, builder::build);

        assertThrows(IllegalArgumentException.class, () -> Label.primary(local(0), " "));
        assertThrows(IllegalArgumentException.class, () -> Note.of(""));
        assertThrows(
            IllegalArgumentException.class,
            () -> Finding.builder(Codes.EXPECTED).phase(Phase.CHECK).message(" ").build()
        );
    }

    @Test
    void labelsSortByRoleFramePositionAndInsertion() {
        Finding finding = Finding.builder(Codes.EXPECTED).phase(Phase.CHECK).label(Label.context(key("k"), "key"))
                .label(Label.secondary(world(3), "world3")).label(Label.secondary(local(0), "local"))
                .label(Label.secondary(Locus.definition(new SourceSpan(DOC, 10, 12)), "d10"))
                .label(Label.secondary(Locus.definition(new SourceSpan(DOC, 4, 6)), "d4"))
                .label(Label.primary(world(5), "primary")).label(Label.secondary(world(1), "world1")).build();
        List<String> messages = finding.labels().stream().map(Label::message).toList();
        assertEquals(List.of("primary", "d4", "d10", "local", "world1", "world3", "key"), messages);
    }

    @Test
    void keyLabelsKeepInsertionOrder() {
        Finding finding = Finding.builder(Codes.EXPECTED).phase(Phase.CHECK).label(Label.primary(local(0), "primary"))
                .label(Label.context(key("b"), "first")).label(Label.context(key("a"), "second")).build();
        assertEquals(
            List.of("first", "second"),
            finding.labelsOf(LabelRole.CONTEXT).stream().map(Label::message).toList()
        );
    }

    @Test
    void listsAreCopiedAndImmutable() {
        List<Label> labels = new ArrayList<>();
        labels.add(Label.primary(local(0), "primary"));
        List<Note> notes = new ArrayList<>();
        notes.add(Note.of("note"));
        Finding finding = new Finding(
            Codes.EXPECTED.code(),
            Severity.ERROR,
            "message",
            labels,
            notes,
            null,
            Phase.CHECK
        );
        labels.clear();
        notes.clear();
        assertEquals(1, finding.labels().size());
        assertEquals(1, finding.notes().size());
        assertThrows(UnsupportedOperationException.class, () -> finding.labels().add(Label.context(key("x"), "x")));
    }

    @Test
    void notesAndSuggestionArePreserved() {
        Finding finding = Finding.builder(Codes.EXPECTED).phase(Phase.CHECK).message("door mismatch").note("a note")
                .note("located", local(2))
                .suggest("replace the door", Applicability.MACHINE_APPLICABLE, NamespacedId.of("test", "remedy"))
                .build();
        assertEquals(2, finding.notes().size());
        assertEquals("located", finding.notes().get(1).message());
        assertEquals(
            Applicability.MACHINE_APPLICABLE,
            java.util.Objects.requireNonNull(finding.suggestion(), "suggestion").applicability()
        );
        assertEquals(
            NamespacedId.of("test", "remedy"),
            java.util.Objects.requireNonNull(finding.suggestion(), "suggestion").remedy()
        );
    }

    @Test
    void builderFromEntryUsesTheCanonicalSeverity() {
        Finding finding = Finding.builder(Codes.BUNDLE_TRUNCATED).phase(Phase.SETTLE).message("truncated").build();
        assertEquals(Severity.WARNING, finding.severity());
        assertEquals(Codes.BUNDLE_TRUNCATED.code(), finding.code());
    }

    @Test
    void labelsOfFiltersByRole() {
        Finding finding = Finding.builder(Codes.EXPECTED).phase(Phase.CHECK).label(Label.primary(local(0), "p"))
                .label(Label.secondary(local(1), "s")).label(Label.secondary(local(2), "s2"))
                .label(Label.context(local(3), "c")).build();
        assertEquals(2, finding.labelsOf(LabelRole.SECONDARY).size());
        assertEquals(1, finding.labelsOf(LabelRole.PRIMARY).size());
        assertEquals(1, finding.labelsOf(LabelRole.CONTEXT).size());
    }
}
