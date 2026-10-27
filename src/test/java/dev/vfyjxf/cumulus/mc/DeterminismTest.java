package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentCompiler;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingRenderer;
import dev.vfyjxf.cumulus.kernel.finding.RenderContext;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The determinism contract: canonical bytes, the parse/write fixed point and equal compile runs. */
class DeterminismTest {

    private static final NamespacedId documentId = NamespacedId.of("test", "determinism.json");

    private static final String orderedDocument = """
            {"schemaVersion":1,"id":"test:determinism","bounds":[0,0,0,3,3,3],
             "definitions":{"test:unit":{"bounds":[0,0,0,1,1,1],
               "seams":[{"id":"port","box":[0,0,0,1,1,1],"kind":"item",
                         "properties":{"side":"north","rate":"fast"}}]}},
             "placements":[{"definition":"test:unit","as":"test:left","at":[0,0,0]}],
             "bays":[{"id":"shell","boxes":[[0,0,0,3,3,3]],"minus":[[1,1,1,1,1,1]],
                      "coverage":"every_cell","footprint":"box","sharing":"exclusive"}],
             "checks":[{"id":"shell_air","bay":"shell","focus":{"kind":"bay"},
                        "expect":{"kind":"each_cell","element":{"kind":"air"}}}],
             "arguments":{"tier":2,"flags":["a","b"],"missing":null},
             "extensions":{"test:vendor":{"z":1,"a":2}}}
            """;

    private static final String permutedDocument = """
            {"extensions":{"test:vendor":{"a":2,"z":1}},
             "arguments":{"missing":null,"flags":["a","b"],"tier":2},
             "checks":[{"expect":{"element":{"kind":"air"},"kind":"each_cell"},
                        "focus":{"kind":"bay"},"bay":"shell","id":"shell_air"}],
             "bays":[{"sharing":"exclusive","footprint":"box","coverage":"every_cell",
                      "minus":[[1,1,1,1,1,1]],"boxes":[[0,0,0,3,3,3]],"id":"shell"}],
             "placements":[{"at":[0,0,0],"as":"test:left","definition":"test:unit"}],
             "definitions":{"test:unit":{
               "seams":[{"properties":{"rate":"fast","side":"north"},
                         "kind":"item","box":[0,0,0,1,1,1],"id":"port"}],
               "bounds":[0,0,0,1,1,1]}},
             "bounds":[0,0,0,3,3,3],"id":"test:determinism","schemaVersion":1}
            """;

    private static final String failingDocument = """
            {"schemaVersion":1,"id":"test:findings","bounds":[0,0,0,1,1,1],
             "bays":[{"id":"cell","box":[0,0,0,1,1,1],"coverage":"every_cell",
                      "footprint":"box","sharing":"shared"}],
             "checks":[
               {"id":"unknown_focus","bay":"cell","focus":{"kind":"test:missing_focus"},
                "expect":{"kind":"air"}},
               {"id":"unknown_expectation","bay":"cell","focus":{"kind":"bay"},
                "expect":{"kind":"test:missing_expectation"}}]}
            """;

    @Test
    void canonicalWriteIsByteStableAcrossMemberOrder() {
        String canonical = new JsonWriter().write(read(orderedDocument));

        assertEquals(canonical, new JsonWriter().write(read(orderedDocument)), "a second write is byte-stable");
        assertEquals(
            canonical,
            new JsonWriter().write(read(permutedDocument)),
            "member order does not change the canonical bytes"
        );
    }

    @Test
    void parseWriteParseIsContentEqual() {
        String canonical = new JsonWriter().write(read(orderedDocument));
        Document parsed = read(canonical);
        String written = new JsonWriter().write(parsed);

        assertEquals(canonical, written, "write -> parse -> write is byte-stable");
        assertEquals(parsed, read(written), "the canonical text is a fixed point of parse -> write");
    }

    @Test
    void twoCompileRunsProduceTheSameBlueprintAndFindings() {
        Document document = read(orderedDocument);
        CompileResult first = compile(document);
        CompileResult second = compile(document);

        assertTrue(first.succeeded(), () -> "the fixture must compile: " + render(first));
        assertTrue(first.findings().isEmpty(), () -> "a clean fixture reports nothing: " + render(first));
        assertEquals(first.succeeded(), second.succeeded(), "both runs agree on success");
        assertEquals(first.findings().findings(), second.findings().findings(), "findings are value-equal");
        assertEquals(render(first), render(second), "the rendered findings are byte-equal");
        assertEquals(first.blueprint(), second.blueprint(), "the blueprints are value-equal");
        assertEquals(require(first).plan(), require(second).plan(), "the plans are value-equal");
        assertEquals(require(first).reads(), require(second).reads(), "the read scopes are value-equal");
    }

    @Test
    void compileIgnoresSourceMemberOrder() {
        CompileResult ordered = compile(read(orderedDocument));
        CompileResult permuted = compile(read(permutedDocument));

        assertTrue(ordered.succeeded(), () -> "the fixture must compile: " + render(ordered));
        assertTrue(permuted.succeeded(), () -> "the permuted fixture must compile: " + render(permuted));
        assertEquals(ordered.blueprint(), permuted.blueprint(), "the source member order is not content");
    }

    @Test
    void findingOrderIsStableAcrossCompileRuns() {
        Document document = read(failingDocument);
        CompileResult first = compile(document);
        CompileResult second = compile(document);

        assertFalse(first.succeeded(), "the fixture must not compile");
        assertTrue(first.findings().size() >= 2, () -> "the fixture reports several findings: " + render(first));
        assertEquals(first.findings().findings(), second.findings().findings(), "the finding order is equal");
        assertEquals(render(first), render(second), "the rendered findings are byte-equal");
        assertEquals(
            first.findings().findings().stream().map(finding -> finding.code()).toList(),
            second.findings().findings().stream().map(finding -> finding.code()).toList(),
            "the finding codes stay in the same order"
        );
    }

    private static Document read(String text) {
        List<Finding> errors = new ArrayList<>();
        Document document = new JsonReader().read(documentId, text, errors);
        assertTrue(errors.isEmpty(), () -> "unexpected findings: " + errors);
        assertNotNull(document, "the document resolves");
        return document;
    }

    private static CompileResult compile(Document document) {
        return DocumentCompiler.compile(document, context());
    }

    private static CompilerContext context() {
        Catalogs catalogs = Catalogs.builtin();
        return new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            DocumentSource.empty()
        );
    }

    private static String render(CompileResult result) {
        return FindingRenderer.human(result.findings(), RenderContext.plain());
    }

    private static Blueprint require(CompileResult result) {
        assertNotNull(result.blueprint(), "the compile produced a blueprint");
        return result.blueprint();
    }
}
