package dev.vfyjxf.cumulus.mc.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.Host;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.mc.JsonDocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three shipped demonstration documents, read from the mod's own resources and compiled. */
final class DemoDocuments {

    static final NamespacedId machine = NamespacedId.of("structurecumulus", "demo_machine");
    static final NamespacedId compact = NamespacedId.of("structurecumulus", "demo_machine_compact");
    static final NamespacedId tower = NamespacedId.of("structurecumulus", "demo_tower");
    static final List<NamespacedId> ids = List.of(machine, compact, tower);

    private DemoDocuments() {}

    static Map<NamespacedId, Document> load() {
        Map<NamespacedId, Document> documents = new LinkedHashMap<>();
        for (NamespacedId id : ids) {
            documents.put(id, read(id));
        }
        return documents;
    }

    static String text(NamespacedId id) {
        String path = path(id);
        try (InputStream stream = Objects.requireNonNull(
            DemoDocuments.class.getClassLoader().getResourceAsStream(path),
            path + " is on the classpath"
        )) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static Document read(NamespacedId id) {
        List<Finding> errors = new ArrayList<>();
        JsonDocument parsed = JsonDocument.read(id, text(id), errors);
        assertTrue(errors.isEmpty(), () -> id + " parsed with " + errors);
        return assertInstanceOf(JsonDocument.Blueprint.class, parsed, () -> id + " is a blueprint document").document();
    }

    /** The raw {@code variants} member, so the two members' declarations can be compared verbatim. */
    static JsonElement variants(NamespacedId id) {
        JsonElement variants = JsonParser.parseString(text(id)).getAsJsonObject().get("variants");
        assertNotNull(variants, () -> id + " declares a variant group");
        return variants;
    }

    static StructureKernel kernel() {
        return StructureKernel.create(
            Catalogs.builtin(),
            new Host(DocumentSource.of(load()), null, StructureManager.create(), Map.of())
        );
    }

    static Blueprint compiled(StructureKernel kernel, NamespacedId id) {
        Document document = kernel.document(id);
        assertNotNull(document, () -> id + " resolves");
        CompileResult result = kernel.compile(document);
        assertTrue(result.findings().isEmpty(), () -> id + " compiled with " + result.findings().findings());
        Blueprint blueprint = result.blueprint();
        assertNotNull(blueprint, () -> id + " lowered to a blueprint");
        return blueprint;
    }

    private static String path(NamespacedId id) {
        return "data/" + id.namespace() + "/cumulus/structure/" + id.path() + ".json";
    }
}
