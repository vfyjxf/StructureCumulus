package dev.vfyjxf.cumulus.kernel.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class FindingBundleTest {

    private static final Locus NEAR = Locus.world(CuboidSet.of(Cuboid.of(0, 64, 0, 1, 65, 1)));
    private static final Locus FAR = Locus.world(CuboidSet.of(Cuboid.of(9, 64, 9, 10, 65, 10)));
    private static final Locus LOCAL = Locus.local(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)));
    private static final FindingCode SPC = FindingCode.of("SC-SPC-0001");
    private static final FindingCode OCC = FindingCode.of("SC-OCC-0001");

    private static Finding finding(FindingCode code, Severity severity, Stage stage,
                                   @Nullable Locus locus, String message) {
        Finding.Builder builder = Finding.builder(code)
                .severity(severity)
                .stage(stage)
                .message(message);
        if (locus != null) {
            builder.label(locus, "locus");
        }
        return builder.build();
    }

    @Test
    void ordersBySeverityThenStageThenCodeThenLocusThenInsertion() {
        Finding info = finding(SPC, Severity.INFO, Stage.DESIGN, NEAR, "info");
        Finding laterStage = finding(SPC, Severity.WARNING, Stage.SETTLE, NEAR, "settle");
        Finding far = finding(SPC, Severity.WARNING, Stage.DESIGN, FAR, "far");
        Finding local = finding(SPC, Severity.WARNING, Stage.DESIGN, LOCAL, "local");
        Finding otherCode = finding(OCC, Severity.WARNING, Stage.DESIGN, FAR, "other code");
        Finding labelLess = finding(SPC, Severity.WARNING, Stage.DESIGN, null, "no label");
        Finding near = finding(SPC, Severity.WARNING, Stage.DESIGN, NEAR, "near");
        Finding sibling = Finding.builder(SPC)
                .severity(Severity.WARNING)
                .stage(Stage.DESIGN)
                .message("sibling")
                .label(NEAR, "locus")
                .note("facing: east")
                .build();

        FindingBundle bundle = FindingBundle.of(List.of(
                info, labelLess, laterStage, far, otherCode, local, near, sibling));
        List<Finding> ordered = bundle.findings();

        assertEquals(8, bundle.size());
        assertEquals(Severity.INFO, ordered.get(ordered.size() - 1).severity());
        assertEquals(Stage.DESIGN, ordered.get(0).stage());
        assertTrue(ordered.indexOf(labelLess) < ordered.indexOf(otherCode));
        assertTrue(ordered.indexOf(local) < ordered.indexOf(near));
        assertTrue(ordered.indexOf(near) < ordered.indexOf(far));
        assertTrue(ordered.indexOf(near) < ordered.indexOf(labelLess));
        assertTrue(ordered.indexOf(near) < ordered.indexOf(sibling));

        assertEquals(sibling, FindingBundle.of(List.of(sibling, near)).findings().get(0));
        assertEquals(near, FindingBundle.of(List.of(near, sibling)).findings().get(0));
    }

    @Test
    void dedupsOnValueAndMergesHints() {
        Finding first = Finding.builder(FindingCode.of("SC-EXP-0002"))
                .severity(Severity.ERROR)
                .stage(Stage.PROBE)
                .message("first wording")
                .label(NEAR, "wrong block")
                .related(FindingId.of(2))
                .build();
        Finding second = Finding.builder(FindingCode.of("SC-EXP-0002"))
                .severity(Severity.ERROR)
                .stage(Stage.PROBE)
                .message("second wording")
                .label(NEAR, "wrong block")
                .related(FindingId.of(1))
                .suggest("replace it", Applicability.MACHINE_APPLICABLE, null)
                .build();
        Finding otherNote = Finding.builder(FindingCode.of("SC-EXP-0002"))
                .severity(Severity.ERROR)
                .stage(Stage.PROBE)
                .message("first wording")
                .label(NEAR, "wrong block")
                .note("facing: east")
                .build();

        FindingBundle bundle = FindingBundle.of(List.of(first, second, otherNote));
        assertEquals(2, bundle.size());

        Finding survivor = bundle.findings().get(0);
        assertEquals("first wording", survivor.message());
        assertEquals(List.of(FindingId.of(1), FindingId.of(2)), survivor.related());
        assertNotNull(survivor.suggestion());
        assertTrue(bundle.plus(second).equals(bundle));
    }

    @Test
    void emptyBundleHasNoPrimary() {
        FindingBundle empty = FindingBundle.empty();
        assertTrue(empty.isEmpty());
        assertNull(empty.primary());
        assertEquals(0, empty.size());
        assertEquals(FindingBundle.empty(), FindingBundle.of(List.of()));
    }
}
