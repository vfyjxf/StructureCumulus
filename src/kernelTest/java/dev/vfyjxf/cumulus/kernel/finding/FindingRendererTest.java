package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingRendererTest {

    private static final NamespacedId DOC = NamespacedId.of("test", "doc.json");
    private static final String TEXT = "line one\nline two has a problem here\nline three";
    private static final SourceSpan SPAN = new SourceSpan(DOC, 24, 31);

    private static Finding doorFinding() {
        Locus world = Locus.world(CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1)));
        return Finding.builder(Codes.EXPECTED).phase(Phase.CHECK).message("a problem")
                .label(Label.primary(Locus.definition(SPAN), "found a problem")).secondary(world, "the door cells")
                .note("a note").suggest("fix it", Applicability.MACHINE_APPLICABLE, NamespacedId.of("test", "remedy"))
                .build();
    }

    @Test
    void humanRendersTheSourceFrameWhenTheTextIsAvailable() {
        SourceText source = SourceText.of(DOC, TEXT);
        String rendered = FindingRenderer.human(FindingBundle.of(List.of(doorFinding())), RenderContext.of(source));
        assertTrue(rendered.contains("error[SC-EXP-0001]: a problem"));
        assertTrue(rendered.contains(" --> test:doc.json:2:16"));
        assertTrue(rendered.contains("line two has a problem here"));
        assertTrue(rendered.contains("^^^^^^^ found a problem"));
        assertTrue(rendered.contains("= world"));
        assertTrue(rendered.contains("= note: a note"));
        assertTrue(rendered.contains("= help: fix it [remedy: test:remedy]"));
    }

    @Test
    void humanNeverFabricatesASourceLine() {
        String rendered = FindingRenderer.human(FindingBundle.of(List.of(doorFinding())), RenderContext.plain());
        assertTrue(rendered.contains("error[SC-EXP-0001]: a problem"));
        assertTrue(rendered.contains(" --> test:doc.json:24..31"));
        assertFalse(rendered.contains("line two has a problem here"));
    }

    @Test
    void humanUsesAnsiAndTheTruncationTrailer() {
        Finding warning = Finding.builder(Codes.EXPECTED.code(), Severity.WARNING).phase(Phase.CHECK).message("warning")
                .label(Label.primary(Locus.local(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))), "warning")).build();
        FindingBundle bundle = FindingBundle.builder(new FindingOptions(0, 0, true, true)).add(warning).build();
        RenderContext ansi = new RenderContext(List.of(), null, 80, RenderContext.ColorMode.ANSI, Locale.ROOT);
        String rendered = FindingRenderer.human(bundle, ansi);
        assertTrue(rendered.contains("\u001B[33mwarning\u001B[0m"));
        assertTrue(rendered.contains("= 1 elided, 0 filtered"));
    }

    @Test
    void jsonCarriesTheDocumentedFields() {
        String json = FindingRenderer.json(FindingBundle.of(List.of(doorFinding())));
        assertTrue(json.startsWith("{\"schema\":1,\"findings\":["));
        assertTrue(json.contains("\"code\":\"SC-EXP-0001\""));
        assertTrue(json.contains("\"severity\":\"ERROR\""));
        assertTrue(json.contains("\"phase\":\"CHECK\""));
        assertTrue(json.contains("\"message\":\"a problem\""));
        assertTrue(json.contains("\"labels\":["));
        assertTrue(json.contains("\"role\":\"PRIMARY\""));
        assertTrue(json.contains("\"frame\":\"DEFINITION\""));
        assertTrue(json.contains("\"cells\":[]"));
        assertTrue(json.contains("\"source\":{\"document\":\"test:doc.json\",\"start\":24," + "\"end\":31}"));
        assertTrue(json.contains("\"frame\":\"WORLD\""));
        assertTrue(json.contains("\"cells\":[[0,0,0,2,1,1]]"));
        assertTrue(json.contains("\"notes\":["));
        assertTrue(json.contains("\"suggestion\":{"));
        assertTrue(json.contains("\"applicability\":\"MACHINE_APPLICABLE\""));
        assertTrue(json.contains("\"remedy\":\"test:remedy\""));
        assertTrue(json.endsWith("\"elided\":0,\"filtered\":0}"));
    }

    @Test
    void jsonEscapesControlCharacters() {
        Finding finding = Finding.builder(Codes.BUDGET_EXHAUSTED.code(), Severity.ERROR).phase(Phase.CHECK)
                .message("a \"quoted\"\nmessage").build();
        String json = FindingRenderer.json(FindingBundle.of(List.of(finding)));
        assertTrue(json.contains("a \\\"quoted\\\"\\nmessage"));
    }
}
