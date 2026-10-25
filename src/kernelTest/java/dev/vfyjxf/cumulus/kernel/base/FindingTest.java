package dev.vfyjxf.cumulus.kernel.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class FindingTest {

    private static final NamespacedId DOC = NamespacedId.of("structurecumulus", "huge_furnace");
    private static final Locus WORLD = Locus.world(CuboidSet.of(Cuboid.of(112, 64, 208, 113, 65, 209)));
    private static final Locus DEFINITION = Locus.definition(SourceSpan.of(DOC, 14, 21));
    private static final Locus LOCAL = Locus.local(CuboidSet.of(Cuboid.of(3, 0, 0, 5, 1, 1)));
    private static final FindingCode CODE = FindingCode.of("SC-EXP-0002");

    @Test
    void nineComponentsRoundTrip() {
        Finding finding = new Finding(
                FindingId.of(2),
                CODE,
                Severity.ERROR,
                "the wall holds another block",
                List.of(Label.primary(DEFINITION, "declared here")),
                List.of(Note.of("property: facing"), new Note("state", DEFINITION)),
                new Suggestion("replace it", Applicability.MACHINE_APPLICABLE,
                        NamespacedId.of("structurecumulus", "replace_block")),
                List.of(FindingId.of(1), FindingId.of(3)),
                Stage.SETTLE);

        assertEquals(FindingId.of(2), finding.id());
        assertEquals(CODE, finding.code());
        assertEquals(Severity.ERROR, finding.severity());
        assertEquals("the wall holds another block", finding.message());
        assertEquals(1, finding.labels().size());
        assertEquals(2, finding.notes().size());
        assertTrue(finding.suggestion() != null);
        assertEquals(List.of(FindingId.of(1), FindingId.of(3)), finding.related());
        assertEquals(Stage.SETTLE, finding.stage());

        assertEquals(FindingId.of(9), finding.withId(FindingId.of(9)).id());
        Finding suggested = Objects.requireNonNull(finding.withSuggestion(
                new Suggestion("other", Applicability.UNSPECIFIED, null)));
        assertEquals("other", Objects.requireNonNull(suggested.suggestion()).message());
        assertTrue(finding.hasPrimary());
        assertEquals(LabelRole.PRIMARY, Objects.requireNonNull(finding.primaryLabel()).role());
        assertEquals(DEFINITION, finding.primaryLocus());
    }

    @Test
    void labelsAreSortedIntoCanonicalOrder() {
        Finding finding = Finding.builder(CODE)
                .severity(Severity.ERROR)
                .stage(Stage.PROBE)
                .message("two door halves disagree")
                .label(Label.context(LOCAL, "neighbourhood"))
                .label(Label.secondary(DEFINITION, "declared here"))
                .label(Label.primary(WORLD, "wrong block"))
                .build();

        assertEquals(List.of(LabelRole.PRIMARY, LabelRole.SECONDARY, LabelRole.CONTEXT),
                finding.labels().stream().map(Label::role).toList());
        assertEquals(DEFINITION, finding.labels().get(1).locus());
        assertEquals(1, finding.labelsOf(LabelRole.SECONDARY).size());
    }

    @Test
    void constructionRejectsMalformedValues() {
        assertThrows(IllegalArgumentException.class, () -> new Finding(
                FindingId.NONE, CODE, Severity.ERROR, "two primaries",
                List.of(Label.primary(WORLD, "a"), Label.primary(LOCAL, "b")),
                List.of(), null, List.of(), Stage.PROBE));
        assertThrows(IllegalArgumentException.class, () -> new Finding(
                FindingId.NONE, CODE, Severity.ERROR, " ",
                List.of(), List.of(), null, List.of(), Stage.PROBE));
        assertThrows(IllegalArgumentException.class, () -> new Finding(
                FindingId.NONE, CODE, Severity.ERROR, "related none",
                List.of(), List.of(), null, List.of(FindingId.NONE), Stage.PROBE));
        assertThrows(IllegalArgumentException.class, () -> new Label(WORLD, " ", LabelRole.PRIMARY));
        assertThrows(IllegalArgumentException.class, () -> new Note(" ", null));
        assertThrows(IllegalArgumentException.class,
                () -> new Suggestion(" ", Applicability.UNSPECIFIED, null));
    }

    @Test
    void builderRequiresSeverityStageAndMessage() {
        Finding finding = Finding.builder(CODE)
                .severity(Severity.WARNING)
                .stage(Stage.FORM)
                .label(WORLD, "a required occupant is absent")
                .build();
        assertEquals("a required occupant is absent", finding.message());
        assertFalse(finding.id().assigned());
        assertTrue(finding.notes().isEmpty() && finding.related().isEmpty());

        assertThrows(IllegalArgumentException.class,
                () -> Finding.builder(CODE).stage(Stage.FORM).label(WORLD, "message").build());
        assertThrows(IllegalArgumentException.class,
                () -> Finding.builder(CODE).severity(Severity.ERROR).label(WORLD, "message").build());
        assertThrows(IllegalArgumentException.class,
                () -> Finding.builder(CODE).severity(Severity.ERROR).stage(Stage.FORM).build());
    }
}
