package dev.vfyjxf.cumulus.mc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** Reads the document kind and selects the blueprint or structure-set reader. */
@NullMarked
public sealed interface JsonDocument {

    record Blueprint(Document document) implements JsonDocument {

        public Blueprint {
            Objects.requireNonNull(document, "document");
        }
    }

    record StructureSet(StructureSetDocument document) implements JsonDocument {

        public StructureSet {
            Objects.requireNonNull(document, "document");
        }
    }

    static @Nullable JsonDocument read(NamespacedId source, String text, List<Finding> errors) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(errors, "errors");
        JsonElement element;
        try {
            element = JsonParser.parseString(text);
        } catch (RuntimeException failure) {
            errors.add(JsonReader.parseFailure(source, text, failure));
            return null;
        }
        if (!(element instanceof JsonObject object)) {
            new JsonReader().read(source, text, errors);
            return null;
        }
        @Nullable
        JsonElement kindElement = object.get("kind");
        if (kindElement == null) {
            Document document = new JsonReader().read(source, text, errors);
            return document == null ? null : new Blueprint(document);
        }
        if (!(kindElement instanceof JsonPrimitive primitive) || !primitive.isString()) {
            error(errors, source, text, kindPath(), "expected a string");
            return null;
        }
        String kind = primitive.getAsString();
        return switch (kind) {
            case "blueprint" -> {
                Document document = new JsonReader().read(source, text, errors);
                yield document == null ? null : new Blueprint(document);
            }
            case "structure_set" -> {
                StructureSetDocument document = new StructureSetReader().read(source, text, errors);
                yield document == null ? null : new StructureSet(document);
            }
            default -> {
                error(errors, source, text, kindPath(), "unknown document kind '" + kind + "'");
                yield null;
            }
        };
    }

    private static String kindPath() {
        return JsonSpans.member("$", "kind");
    }

    private static void error(List<Finding> errors, NamespacedId source, String text, String path, String message) {
        Finding.Builder builder = Finding.builder(Codes.documentField).phase(Phase.compile).message(message);
        JsonSpans spans = JsonSpans.scan(source, text);
        if (spans != null) {
            SourceSpan span = spans.name(path);
            if (span == null) {
                span = spans.value(path);
            }
            if (span != null) {
                builder.label(Label.primary(Locus.definition(span), message));
            }
        }
        errors.add(builder.build());
    }
}
