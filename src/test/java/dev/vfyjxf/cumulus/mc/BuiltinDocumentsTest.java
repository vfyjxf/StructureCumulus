package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.Host;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
