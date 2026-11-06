package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingBundleTest {

    private static Locus local(int x) {
        return Locus.local(CuboidSet.of(Cuboid.of(x, 0, 0, x + 1, 1, 1)));
    }

    private static Finding expected(String message, int cell) {
        return Finding.builder(Codes.expected.code(), Severity.warning).phase(Phase.check).message(message)
                .label(local(cell), message).build();
    }

    private static Finding negation(String message, int cell) {
        return Finding.builder(Codes.negationMatched.code(), Severity.warning).phase(Phase.check).message(message)
                .label(local(cell), message).build();
    }

    private static Finding error(String message, Phase phase) {
        return Finding.builder(Codes.budgetExhausted.code(), Severity.error).phase(phase).message(message).build();
    }

    @Test
    void identicalFindingsStillCollapse() {
        Finding first = Finding.builder(Codes.expected).phase(Phase.check).message("same").label(local(0), "same label")
                .build();
        Finding duplicate = Finding.builder(Codes.expected).phase(Phase.check).message("same")
                .label(local(0), "same label").build();
        FindingBundle bundle = FindingBundle.of(List.of(first, duplicate));
        assertEquals(1, bundle.size());
        assertEquals("same", Objects.requireNonNull(bundle.primary(), "primary").message());
        assertEquals(0, bundle.elided());
        assertEquals(0, bundle.filtered());
    }

    @Test
    void theSameCodeWithDifferentMessagesKeepsBoth() {
        Finding first = Finding.builder(Codes.expected).phase(Phase.check).message("first").build();
        Finding second = Finding.builder(Codes.expected).phase(Phase.check).message("second").build();
        FindingBundle bundle = FindingBundle.of(List.of(first, second));
        assertEquals(2, bundle.size());
        assertEquals(List.of("first", "second"), bundle.findings().stream().map(Finding::message).toList());
        assertEquals(0, bundle.elided());
        assertEquals(0, bundle.filtered());
    }

    @Test
    void orderIsSeverityThenPhaseThenCodeThenPrimaryLocus() {
        Finding warningCompile = Finding.builder(Codes.expected.code(), Severity.warning).phase(Phase.compile)
                .message("w-compile").label(local(0), "w-compile").build();
        Finding warningCheck = Finding.builder(Codes.expected.code(), Severity.warning).phase(Phase.check)
                .message("w-check").label(local(0), "w-check").build();
        Finding errorSettle = error("e-settle", Phase.settle);
        Finding errorCompile = error("e-compile", Phase.compile);
        Finding codeTwo = negation("n-check", 0);
        FindingBundle bundle = FindingBundle
                .of(List.of(warningCheck, errorSettle, codeTwo, errorCompile, warningCompile));

        assertEquals(
            List.of("e-compile", "e-settle", "w-compile", "w-check", "n-check"),
            bundle.findings().stream().map(Finding::message).toList()
        );
    }

    @Test
    void capsKeepErrorsAndAppendTheTruncationFinding() {
        FindingOptions options = new FindingOptions(1, 1, true, true);
        FindingBundle bundle = FindingBundle.builder(options).add(expected("a1", 0)).add(expected("a2", 1))
                .add(negation("b1", 0)).add(error("e1", Phase.check)).add(error("e2", Phase.settle)).build();

        assertEquals(4, bundle.size());
        assertEquals(2, bundle.ofSeverity(Severity.error).size());
        assertEquals(List.of("e1", "e2", "a1"), bundle.findings().stream().map(Finding::message).limit(3).toList());
        assertEquals(2, bundle.elided());
        assertEquals(0, bundle.filtered());
        Finding truncation = bundle.findings().get(bundle.size() - 1);
        assertEquals(Codes.bundleTruncated.code(), truncation.code());
        assertEquals(Severity.warning, truncation.severity());
        assertEquals(Phase.settle, truncation.phase());
        assertTrue(truncation.message().contains("2 elided"));
    }

    @Test
    void infoFloorCountsFilteredAndStillReportsTruncation() {
        Finding info = Finding.builder(Codes.noPositions).phase(Phase.compile).message("no spans").build();
        FindingBundle bundle = FindingBundle.builder(new FindingOptions(10, 10, false, true))
                .add(error("error", Phase.check)).add(info).build();
        assertEquals(2, bundle.size());
        assertEquals(1, bundle.filtered());
        assertEquals(0, bundle.elided());
        assertEquals(Codes.budgetExhausted.code(), Objects.requireNonNull(bundle.primary(), "primary").code());
        assertEquals(Codes.bundleTruncated.code(), bundle.findings().get(bundle.size() - 1).code());
    }

    @Test
    void includeNotesStripsNotesWithoutCounting() {
        Finding withNote = Finding.builder(Codes.expected.code(), Severity.warning).phase(Phase.check).message("m")
                .note("a note").build();
        FindingBundle bundle = FindingBundle.builder(new FindingOptions(10, 10, true, false)).add(withNote).build();
        assertEquals(1, bundle.size());
        assertTrue(Objects.requireNonNull(bundle.primary(), "primary").notes().isEmpty());
        assertEquals(0, bundle.elided());
        assertEquals(0, bundle.filtered());
    }

    @Test
    void filterNarrowsAndAddsItsCounters() {
        FindingBundle original = FindingBundle.builder(new FindingOptions(10, 2, true, true)).add(expected("a1", 0))
                .add(expected("a2", 1)).add(expected("a3", 2)).add(negation("b1", 0)).build();
        assertEquals(1, original.elided());
        assertEquals(List.of("a1", "a2", "b1"), original.findings().stream().map(Finding::message).limit(3).toList());

        FindingBundle narrowed = original.filter(new FindingOptions(10, 1, true, true));
        assertEquals(2, narrowed.elided());
        assertEquals(List.of("a1", "b1"), narrowed.findings().stream().map(Finding::message).limit(2).toList());
        assertEquals(
            1,
            narrowed.findings().stream().filter(finding -> finding.code().equals(Codes.bundleTruncated.code())).count()
        );
        assertEquals(3, narrowed.size());
    }

    @Test
    void filterCannotWidenTheStoredOptions() {
        FindingBundle capped = FindingBundle.builder(new FindingOptions(1, 10, true, true)).add(expected("a1", 0))
                .add(negation("b1", 0)).build();
        FindingBundle attempted = capped.filter(FindingOptions.defaults());
        assertEquals(capped.options(), attempted.options());
        assertEquals(1, attempted.elided());
    }

    @Test
    void buildIsIdempotent() {
        FindingBundle.Builder builder = FindingBundle.builder(FindingOptions.defaults()).add(expected("a1", 0))
                .add(expected("a1", 0));
        FindingBundle first = builder.build();
        FindingBundle second = builder.build();
        assertEquals(first.findings(), second.findings());
        assertEquals(first.elided(), second.elided());
        assertEquals(first.filtered(), second.filtered());
        assertEquals(first.options(), second.options());
    }

    @Test
    void emptyBundleHasNoPrimaryAndNoTruncation() {
        FindingBundle empty = FindingBundle.empty();
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());
        assertNull(empty.primary());
        assertEquals(0, empty.elided());
        assertEquals(0, empty.filtered());
    }

    @Test
    void lookupsFilterWithoutReordering() {
        Finding a = expected("a", 0);
        Finding b = negation("b", 0);
        FindingBundle bundle = FindingBundle.of(List.of(b, a));
        assertEquals(List.of(a), bundle.ofCode(Codes.expected.code()));
        assertEquals(List.of(a, b), bundle.ofSeverity(Severity.warning));
    }

    @Test
    void optionsNarrowByMinimumCapsAndConjunction() {
        FindingOptions wide = new FindingOptions(10, 5, true, true);
        FindingOptions narrow = new FindingOptions(3, 8, false, true);
        FindingOptions merged = wide.narrowedBy(narrow);
        assertEquals(3, merged.maxTotal());
        assertEquals(5, merged.maxPerCode());
        assertFalse(merged.includeInfo());
        assertTrue(merged.includeNotes());
        assertEquals(7, wide.withMaxTotal(7).maxTotal());
        assertEquals(2, wide.withMaxPerCode(2).maxPerCode());
    }

    @Test
    void optionsDefaultsAreUnbounded() {
        FindingOptions defaults = FindingOptions.defaults();
        assertEquals(Integer.MAX_VALUE, defaults.maxTotal());
        assertEquals(Integer.MAX_VALUE, defaults.maxPerCode());
        assertTrue(defaults.includeInfo());
        assertTrue(defaults.includeNotes());
    }

    @Test
    void truncationUsesTheLatestPhaseSeen() {
        FindingBundle bundle = FindingBundle.builder(new FindingOptions(0, 0, true, true)).add(expected("w", 0))
                .add(error("e", Phase.settle)).build();
        Finding truncation = bundle.findings().get(bundle.size() - 1);
        assertEquals(Phase.settle, truncation.phase());
        assertEquals(
            NamespacedId.of("structurecumulus", "modules/finding"),
            Codes.bundleTruncated.explanation().seeAlso().get(0)
        );
    }
}
