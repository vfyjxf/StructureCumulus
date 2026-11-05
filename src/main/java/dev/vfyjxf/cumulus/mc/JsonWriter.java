package dev.vfyjxf.cumulus.mc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.BaySpec;
import dev.vfyjxf.cumulus.kernel.document.BlueprintSpec;
import dev.vfyjxf.cumulus.kernel.document.CheckSpec;
import dev.vfyjxf.cumulus.kernel.document.ComputationSpec;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentWriter;
import dev.vfyjxf.cumulus.kernel.document.KeySpec;
import dev.vfyjxf.cumulus.kernel.document.PlacementSpec;
import dev.vfyjxf.cumulus.kernel.document.SeamSpec;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Writes the canonical v1 document text. */
@NullMarked
public final class JsonWriter implements DocumentWriter {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping()
            .create();

    private static final Set<String> kindMembers = Set.of("kind");

    private static final Set<String> computationMembers = Set.of("id", "kind", "output", "bay", "region", "inputs");

    @Override
    public String write(Document document) {
        Objects.requireNonNull(document, "document");
        if (document.schemaVersion() != Document.currentVersion) {
            throw new IllegalStateException(
                "the writer emits schema version " + Document.currentVersion + ", not " + document.schemaVersion()
            );
        }
        BlueprintSpec root = document.root();
        if (!root.seams().isEmpty()) {
            throw new IllegalStateException("schema version 1 has no root 'seams' member");
        }
        if (!root.extensions().isEmpty()) {
            throw new IllegalStateException("root spec extensions are not in the v1 envelope; use Document.extensions");
        }
        if (root.envelopeEntities() && !root.envelope()) {
            throw new IllegalStateException("envelopeEntities is written only on a root that declares an envelope");
        }
        JsonObject out = new JsonObject();
        out.addProperty("schemaVersion", document.schemaVersion());
        out.addProperty("id", JsonVocabulary.idText(root.id()));
        out.add("bounds", JsonValues.box(root.bounds()));
        if (root.envelope()) {
            out.addProperty("envelope", true);
        }
        if (root.envelopeEntities()) {
            out.addProperty("envelopeEntities", true);
        }
        if (!document.definitions().isEmpty()) {
            JsonObject definitions = new JsonObject();
            for (Map.Entry<NamespacedId, BlueprintSpec> entry : document.definitions().entrySet()) {
                if (!entry.getKey().equals(entry.getValue().id())) {
                    throw new IllegalStateException(
                        "definition key " + entry.getKey().text() + " does not match spec id "
                                + entry.getValue().id().text()
                    );
                }
                definitions.add(JsonVocabulary.idText(entry.getKey()), definition(entry.getValue()));
            }
            out.add("definitions", definitions);
        }
        addPlacements(out, root.placements());
        out.add("bays", bays(root.bays()));
        out.add("checks", checks(root.checks()));
        if (!root.computations().isEmpty()) {
            out.add("computations", computations(root.computations()));
        }
        if (!root.keys().isEmpty()) {
            out.add("keys", keys(root.keys()));
        }
        if (!root.derivations().isEmpty()) {
            out.add("derivations", derivations(root.derivations()));
        }
        if (!root.anchors().isEmpty()) {
            out.add("anchors", anchors(root.anchors()));
        }
        if (root.orientation() != null) {
            out.add("orientation", orientation(root.orientation()));
        }
        JsonValues.addArguments(out, root.arguments());
        JsonValues.addExtensions(out, document.extensions());
        return gson.toJson(out);
    }

    private static JsonObject definition(BlueprintSpec spec) {
        if (spec.envelope()) {
            throw new IllegalStateException("a definition may not declare an envelope");
        }
        if (spec.envelopeEntities()) {
            throw new IllegalStateException("a definition may not declare envelopeEntities");
        }
        JsonObject out = new JsonObject();
        out.add("bounds", JsonValues.box(spec.bounds()));
        JsonValues.addArguments(out, spec.arguments());
        if (!spec.bays().isEmpty()) {
            out.add("bays", bays(spec.bays()));
        }
        if (!spec.seams().isEmpty()) {
            out.add("seams", seams(spec.seams()));
        }
        if (!spec.checks().isEmpty()) {
            out.add("checks", checks(spec.checks()));
        }
        if (!spec.computations().isEmpty()) {
            out.add("computations", computations(spec.computations()));
        }
        if (!spec.keys().isEmpty()) {
            out.add("keys", keys(spec.keys()));
        }
        if (!spec.derivations().isEmpty()) {
            out.add("derivations", derivations(spec.derivations()));
        }
        if (!spec.anchors().isEmpty()) {
            out.add("anchors", anchors(spec.anchors()));
        }
        if (spec.orientation() != null) {
            out.add("orientation", orientation(spec.orientation()));
        }
        if (!spec.placements().isEmpty()) {
            addPlacements(out, spec.placements());
        }
        JsonValues.addExtensions(out, spec.extensions());
        return out;
    }

    private static void addPlacements(JsonObject out, List<PlacementSpec> placements) {
        if (placements.isEmpty()) {
            return;
        }
        JsonArray array = new JsonArray();
        for (PlacementSpec placement : placements) {
            JsonObject json = new JsonObject();
            json.addProperty("definition", JsonVocabulary.idText(placement.definition()));
            json.addProperty("as", JsonVocabulary.idText(placement.alias()));
            json.add("at", JsonValues.point(placement.offset()));
            array.add(json);
        }
        out.add("placements", array);
    }

    private static JsonArray bays(List<BaySpec> bays) {
        JsonArray array = new JsonArray();
        for (BaySpec bay : bays) {
            JsonObject json = new JsonObject();
            json.addProperty("id", JsonVocabulary.idText(bay.id()));
            addCells(json, bay.cells());
            json.addProperty("coverage", JsonVocabulary.coverageName(bay.coverage()));
            json.add("footprint", footprint(bay.footprint()));
            json.addProperty("sharing", JsonVocabulary.sharingName(bay.sharing()));
            if (bay.description() != null) {
                json.addProperty("description", bay.description());
            }
            array.add(json);
        }
        return array;
    }

    private static JsonArray seams(List<SeamSpec> seams) {
        JsonArray array = new JsonArray();
        for (SeamSpec seam : seams) {
            JsonObject json = new JsonObject();
            json.addProperty("id", JsonVocabulary.idText(seam.id().id()));
            json.add("box", JsonValues.box(seam.cells()));
            json.addProperty("kind", JsonVocabulary.seamKindName(seam.kind()));
            if (!seam.properties().isEmpty()) {
                JsonObject properties = new JsonObject();
                for (Map.Entry<String, String> entry : seam.properties().entrySet()) {
                    properties.addProperty(entry.getKey(), entry.getValue());
                }
                json.add("properties", properties);
            }
            array.add(json);
        }
        return array;
    }

    private static JsonArray checks(List<CheckSpec> checks) {
        JsonArray array = new JsonArray();
        for (CheckSpec check : checks) {
            JsonObject json = new JsonObject();
            json.addProperty("id", JsonVocabulary.idText(check.id()));
            json.addProperty("bay", JsonVocabulary.idText(check.bay()));
            json.add("focus", kindObject(check.focusKind(), check.focusArgs(), kindMembers, "focus"));
            json.add("expect", kindObject(check.expectationKind(), check.expectationArgs(), kindMembers, "expect"));
            if (check.description() != null) {
                json.addProperty("description", check.description());
            }
            array.add(json);
        }
        return array;
    }

    private static JsonArray computations(List<ComputationSpec> computations) {
        JsonArray array = new JsonArray();
        for (ComputationSpec computation : computations) {
            JsonObject json = new JsonObject();
            json.addProperty("kind", JsonVocabulary.idText(computation.kind()));
            json.addProperty("output", JsonVocabulary.idText(computation.output()));
            if (computation.bay() != null) {
                json.addProperty("bay", JsonVocabulary.idText(computation.bay()));
            }
            if (computation.region() != null) {
                json.add("region", JsonValues.boxSet(computation.region()));
            }
            if (!computation.inputs().isEmpty()) {
                JsonArray inputs = new JsonArray();
                for (NamespacedId input : new TreeSet<>(computation.inputs())) {
                    inputs.add(JsonVocabulary.idText(input));
                }
                json.add("inputs", inputs);
            }
            addArgs(json, computation.args(), computationMembers, "computation");
            array.add(json);
        }
        return array;
    }

    private static JsonArray keys(List<KeySpec> keys) {
        JsonArray array = new JsonArray();
        for (KeySpec key : keys) {
            JsonObject json = new JsonObject();
            json.addProperty("id", JsonVocabulary.idText(key.id()));
            json.addProperty("type", key.type());
            json.addProperty("retention", JsonVocabulary.retentionName(key.retention()));
            if (key.merge() != null) {
                json.addProperty("merge", JsonVocabulary.idText(key.merge()));
            }
            array.add(json);
        }
        return array;
    }

    private static JsonArray derivations(List<DerivationSpec> derivations) {
        JsonArray array = new JsonArray();
        for (DerivationSpec derivation : derivations) {
            JsonObject json = new JsonObject();
            json.addProperty("deriver", JsonVocabulary.idText(derivation.deriver()));
            json.addProperty("output", JsonVocabulary.idText(derivation.output()));
            json.addProperty("order", derivation.order());
            JsonValues.addArguments(json, derivation.arguments());
            array.add(json);
        }
        return array;
    }

    private static JsonArray anchors(List<AnchorSpec> anchors) {
        JsonArray array = new JsonArray();
        for (AnchorSpec anchor : anchors) {
            JsonObject json = new JsonObject();
            json.addProperty("role", JsonVocabulary.idText(anchor.role()));
            json.addProperty("property", anchor.property());
            json.addProperty("value", anchor.value());
            array.add(json);
        }
        return array;
    }

    private static JsonObject orientation(OrientationPolicy policy) {
        JsonObject json = new JsonObject();
        json.addProperty("primary", JsonVocabulary.idText(policy.primaryRole()));
        JsonArray allowed = new JsonArray();
        for (Orientation orientation : policy.allowed()) {
            allowed.add(JsonVocabulary.orientationName(orientation));
        }
        json.add("allowed", allowed);
        if (policy.uprightOnly()) {
            json.addProperty("upright", true);
        }
        return json;
    }

    private static void addCells(JsonObject json, CuboidSet cells) {
        if (cells.boxCount() == 1) {
            json.add("box", JsonValues.box(cells.boxes().get(0)));
        } else {
            json.add("boxes", JsonValues.boxSet(cells));
        }
    }

    private static JsonElement footprint(Footprint footprint) {
        return switch (footprint) {
            case Footprint.Box ignored -> new JsonPrimitive("box");
            case Footprint.Bound ignored -> new JsonPrimitive("bound");
            case Footprint.None ignored -> new JsonPrimitive("none");
            case Footprint.Declared declared -> {
                JsonObject json = new JsonObject();
                json.add("declared", JsonValues.boxSet(declared.cells()));
                yield json;
            }
        };
    }

    private static JsonObject kindObject(NamespacedId kind, Value args, Set<String> reserved, String what) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", JsonVocabulary.idText(kind));
        addArgs(json, args, reserved, what);
        return json;
    }

    private static void addArgs(JsonObject json, Value args, Set<String> reserved, String what) {
        if (!(args instanceof Value.ObjectValue object)) {
            throw new IllegalStateException(what + " arguments must be a JSON object");
        }
        for (Map.Entry<String, Value> entry : object.members().entrySet()) {
            if (reserved.contains(entry.getKey())) {
                throw new IllegalStateException(
                    what + " argument '" + entry.getKey() + "' collides with a schema member"
                );
            }
            json.add(entry.getKey(), JsonValues.value(entry.getValue()));
        }
    }

}
