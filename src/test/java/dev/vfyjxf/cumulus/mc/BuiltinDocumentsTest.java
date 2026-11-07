package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentCompiler;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompileResult;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompiler;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.Host;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinDocumentsTest {

    @Test
    void airCellCompilesWithoutFindings() {
        StructureKernel kernel = StructureKernel.create(
            Catalogs.builtin(),
            new Host(BuiltinDocuments.source(), null, StructureManager.create(), Map.of())
        );
        Document document = kernel.document(BuiltinDocuments.airCellId());
        assertNotNull(document, "the built-in document resolves");
        CompileResult result = kernel.compile(document);
        assertTrue(result.findings().isEmpty(), () -> "compile reported " + result.findings().findings());
        Blueprint blueprint = result.blueprint();
        assertNotNull(blueprint, "the built-in document compiles");
        assertEquals(BuiltinDocuments.airCellId(), blueprint.id());
        assertEquals(1, blueprint.bays().size());
        assertEquals(1, blueprint.checks().size());
    }

    @Test
    void builtinDocumentsStayInsideTheExpansionBudget() {
        Catalogs catalogs = Catalogs.builtin();
        CompilerContext context = new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            BuiltinDocuments.source()
        );
        for (NamespacedId documentId : List
                .of(BuiltinDocuments.airCellId(), BuiltinDocuments.setRootId(), BuiltinDocuments.setWingId())) {
            Document document = BuiltinDocuments.source().find(documentId);
            assertNotNull(document, documentId + " resolves");
            CompileResult result = DocumentCompiler.compile(document, context);
            assertTrue(
                result.findings().ofCode(Codes.documentTooLarge.code()).isEmpty(),
                documentId + " reported " + result.findings().findings()
            );
            assertNotNull(result.blueprint(), documentId + " compiles");
        }
    }

    @Test
    void theBuiltinSetCompilesInAUnitTest() {
        Catalogs catalogs = Catalogs.builtin();
        CompilerContext context = new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            BuiltinDocuments.source()
        );
        StructureSetDocument set = BuiltinDocuments.source().findSet(BuiltinDocuments.setDemoId());
        assertNotNull(set, "findSet serves the built-in set");
        StructureSetCompileResult result = StructureSetCompiler.compile(set, context);
        CompiledStructureSet compiledSet = assertInstanceOf(
            CompiledStructureSet.class,
            result.set(),
            () -> "the built-in set compiles: " + result.findings().findings()
        );
        assertEquals(1, compiledSet.satellites().size(), "one satellite");
        assertEquals(BuiltinDocuments.setRootId(), compiledSet.root().id());
        assertEquals(BuiltinDocuments.leftWingId(), compiledSet.satellites().get(0).spec().id());
        assertEquals(BuiltinDocuments.setWingId(), compiledSet.satellites().get(0).blueprint().id());

        for (NamespacedId cell : List.of(BuiltinDocuments.setRootId(), BuiltinDocuments.setWingId())) {
            Document document = BuiltinDocuments.source().find(cell);
            assertNotNull(document, "the set cell resolves: " + cell);
            CompileResult compiled = DocumentCompiler.compile(document, context);
            assertTrue(compiled.findings().isEmpty(), () -> cell + " reported " + compiled.findings().findings());
            assertNotNull(compiled.blueprint(), cell + " compiles");
        }
    }
}
