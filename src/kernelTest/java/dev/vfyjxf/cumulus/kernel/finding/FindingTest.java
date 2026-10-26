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
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingTest {

    private static final NamespacedId doc = NamespacedId.of("test", "doc");
    private static final World world = World.of(NamespacedId.of("test", "world"));
    private static final InstanceId instance = new InstanceId(world, 1);

    private static Locus local(int x) {
        return Locus.local(CuboidSet.of(Cuboid.of(x, 0, 0, x + 1, 1, 1)));
    }

    private static Locus world(int x) {
        return Locus.world(CuboidSet.of(Cuboid.of(x, 0, 0, x + 1, 1, 1)));
    }

    private static Locus key(String path) {
        return Locus.key(instance, NamespacedId.of("test", path));
    }

    @Test
    void builderDefaultsMessageFromPrimary() {
        Finding finding = Finding.builder(Codes.expected).phase(Phase.check).label(local(0), "expected oak").build();
        assertEquals("expected oak", finding.message());
        assertEquals(Severity.error, finding.severity());
        assertEquals(Codes.expected.code(), finding.code());
        assertEquals(Phase.check, finding.phase());
        assertTrue(finding.hasPrimary());
        assertEquals("expected oak", Objects.requireNonNull(finding.primaryLabel(), "primary").message());
    }

    @Test
    void labelLessFindingRequiresAMessageAndPhase() {
        assertThrows(
            IllegalArgumentException.class,
            () -> Finding.builder(Codes.budgetExhausted).message("budget").build()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> Finding.builder(Codes.budgetExhausted).phase(Phase.check).build()
        );
        Finding finding = Finding.builder(Codes.budgetExhausted).phase(Phase.check).message("budget").build();
        assertNull(finding.primaryLabel());
        assertFalse(finding.hasPrimary());
        assertEquals("budget", finding.message());
    }

    @Test
    void atMostOnePrimaryLabelAndNonBlankProse() {
        Finding.Builder builder = Finding.builder(Codes.expected).phase(Phase.check).label(local(0), "one")
                .label(local(1), "two");
        assertThrows(IllegalArgumentException.class, builder::build);

        assertThrows(IllegalArgumentException.class, () -> Label.primary(local(0), " "));
        assertThrows(IllegalArgumentException.class, () -> Note.of(""));
        assertThrows(
            IllegalArgumentException.class,
            () -> Finding.builder(Codes.expected).phase(Phase.check).message(" ").build()
        );
    }

    @Test
    void labelsSortByRoleFramePositionAndInsertion() {
        Finding finding = Finding.builder(Codes.expected).phase(Phase.check).label(Label.context(key("k"), "key"))
                .label(Label.secondary(world(3), "world3")).label(Label.secondary(local(0), "local"))
                .label(Label.secondary(Locus.definition(new SourceSpan(doc, 10, 12)), "d10"))
                .label(Label.secondary(Locus.definition(new SourceSpan(doc, 4, 6)), "d4"))
                .label(Label.primary(world(5), "primary")).label(Label.secondary(world(1), "world1")).build();
        List<String> messages = finding.labels().stream().map(Label::message).toList();
        assertEquals(List.of("primary", "d4", "d10", "local", "world1", "world3", "key"), messages);
    }

    @Test
    void keyLabelsKeepInsertionOrder() {
        Finding finding = Finding.builder(Codes.expected).phase(Phase.check).label(Label.primary(local(0), "primary"))
                .label(Label.context(key("b"), "first")).label(Label.context(key("a"), "second")).build();
        assertEquals(
            List.of("first", "second"),
            finding.labelsOf(LabelRole.context).stream().map(Label::message).toList()
        );
    }

    @Test
    void listsAreCopiedAndImmutable() {
        List<Label> labels = new ArrayList<>();
        labels.add(Label.primary(local(0), "primary"));
        List<Note> notes = new ArrayList<>();
        notes.add(Note.of("note"));
        Finding finding = new Finding(
            Codes.expected.code(),
            Severity.error,
            "message",
            labels,
            notes,
            null,
            Phase.check
        );
        labels.clear();
        notes.clear();
        assertEquals(1, finding.labels().size());
        assertEquals(1, finding.notes().size());
        assertThrows(UnsupportedOperationException.class, () -> finding.labels().add(Label.context(key("x"), "x")));
    }

    @Test
    void notesAndSuggestionArePreserved() {
        Finding finding = Finding.builder(Codes.expected).phase(Phase.check).message("door mismatch").note("a note")
                .note("located", local(2))
                .suggest("replace the door", Applicability.machineApplicable, NamespacedId.of("test", "remedy"))
                .build();
        assertEquals(2, finding.notes().size());
        assertEquals("located", finding.notes().get(1).message());
        assertEquals(
            Applicability.machineApplicable,
            Objects.requireNonNull(finding.suggestion(), "suggestion").applicability()
        );
        assertEquals(
            NamespacedId.of("test", "remedy"),
            Objects.requireNonNull(finding.suggestion(), "suggestion").remedy()
        );
    }

    @Test
    void builderFromEntryUsesTheCanonicalSeverity() {
        Finding finding = Finding.builder(Codes.bundleTruncated).phase(Phase.settle).message("truncated").build();
        assertEquals(Severity.warning, finding.severity());
        assertEquals(Codes.bundleTruncated.code(), finding.code());
    }

    @Test
    void labelsOfFiltersByRole() {
        Finding finding = Finding.builder(Codes.expected).phase(Phase.check).label(Label.primary(local(0), "p"))
                .label(Label.secondary(local(1), "s")).label(Label.secondary(local(2), "s2"))
                .label(Label.context(local(3), "c")).build();
        assertEquals(2, finding.labelsOf(LabelRole.secondary).size());
        assertEquals(1, finding.labelsOf(LabelRole.primary).size());
        assertEquals(1, finding.labelsOf(LabelRole.context).size());
    }
}
