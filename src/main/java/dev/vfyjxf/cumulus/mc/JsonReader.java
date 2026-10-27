package dev.vfyjxf.cumulus.mc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.core.Values;
import dev.vfyjxf.cumulus.kernel.document.BaySpec;
import dev.vfyjxf.cumulus.kernel.document.BlueprintSpec;
import dev.vfyjxf.cumulus.kernel.document.CheckSpec;
import dev.vfyjxf.cumulus.kernel.document.ComputationSpec;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentReader;
import dev.vfyjxf.cumulus.kernel.document.KeySpec;
import dev.vfyjxf.cumulus.kernel.document.PlacementSpec;
import dev.vfyjxf.cumulus.kernel.document.SeamSpec;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.kernel.structure.SeamId;
import dev.vfyjxf.cumulus.kernel.structure.SeamKind;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the v1 document schema from a Gson tree and records the offsets Gson drops. */
@NullMarked
public final class JsonReader implements DocumentReader {

    private static final Set<String> documentMembers = Set.of(
        "schemaVersion",
        "id",
        "bounds",
        "envelope",
        "definitions",
        "placements",
        "bays",
        "checks",
        "computations",
        "keys",
        "derivations",
        "anchors",
        "orientation",
        "arguments",
        "extensions"
    );

    private static final Set<String> definitionMembers = Set.of(
        "bounds",
        "arguments",
        "bays",
        "seams",
        "checks",
        "computations",
        "keys",
        "derivations",
        "anchors",
        "orientation",
        "placements",
        "extensions"
    );

    private static final Set<String> bayMembers = Set
            .of("id", "box", "boxes", "minus", "coverage", "footprint", "sharing", "description");

    private static final Set<String> seamMembers = Set.of("id", "box", "kind", "properties");

    private static final Set<String> checkMembers = Set.of("id", "bay", "focus", "expect", "description");

    private static final Set<String> computationMembers = Set.of("id", "kind", "output", "bay", "region", "inputs");

    private static final Set<String> keyMembers = Set.of("id", "type", "retention", "merge", "whenAbsent");

    private static final Set<String> placementMembers = Set.of("definition", "as", "at");

    private static final Set<String> derivationMembers = Set.of("deriver", "output", "order", "arguments");

    private static final Set<String> anchorMembers = Set.of("role", "property", "value");

    private static final Set<String> orientationMembers = Set.of("primary", "allowed", "upright");

    private static final Set<String> kindMembers = Set.of("kind");

    private static final Pattern linePattern = Pattern.compile("line (\\d+)");
    private static final Pattern columnPattern = Pattern.compile("column (\\d+)");

    private static final int maxValueDepth = 256;

    @Override
    public @Nullable Document read(NamespacedId source, String text, List<Finding> errors) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(errors, "errors");
        JsonElement element;
        try {
            element = JsonParser.parseString(text);
        } catch (RuntimeException failure) {
            errors.add(parseFailure(source, text, failure));
            return null;
        }
        JsonSpans spans = JsonSpans.scan(source, text);
        Document document = new Decoder(errors, spans).decode(element);
        if (document != null && spans == null) {
            errors.add(
                Finding.builder(Codes.noPositions).phase(Phase.compile)
                        .message("the reader produced no source spans for " + source.text()).build()
            );
        }
        return document;
    }

    private static Finding parseFailure(NamespacedId source, String text, RuntimeException failure) {
        String detail = failure.getMessage();
        String message = "the document is not valid JSON: "
                + (detail == null ? failure.getClass().getSimpleName() : detail);
        Finding.Builder builder = Finding.builder(Codes.readerFailed).phase(Phase.compile).message(message);
        int offset = offsetOf(text, detail);
        if (offset >= 0) {
            SourceSpan span = new SourceSpan(source, offset, offset);
            builder.label(Label.primary(Locus.definition(span), message));
        }
        return builder.build();
    }

    private static int offsetOf(String text, @Nullable String detail) {
        if (detail == null) {
            return -1;
        }
        Matcher line = linePattern.matcher(detail);
        Matcher column = columnPattern.matcher(detail);
        if (!line.find() || !column.find()) {
            return -1;
        }
        int targetLine;
        int targetColumn;
        try {
            targetLine = Integer.parseInt(line.group(1));
            targetColumn = Integer.parseInt(column.group(1));
        } catch (NumberFormatException rejected) {
            return -1;
        }
        int currentLine = 1;
        int currentColumn = 1;
        int offset = 0;
        while (offset < text.length()) {
            if (currentLine == targetLine && currentColumn == targetColumn) {
                return offset;
            }
            char current = text.charAt(offset);
            if (current == '\n') {
                currentLine++;
                currentColumn = 1;
                offset++;
            } else if (current == '\r') {
                boolean crlf = offset + 1 < text.length() && text.charAt(offset + 1) == '\n';
                currentLine++;
                currentColumn = 1;
                offset += crlf ? 2 : 1;
            } else {
                currentColumn++;
                offset++;
            }
        }
        return currentLine == targetLine && currentColumn == targetColumn ? text.length() : -1;
    }

    private record Kind(NamespacedId id, Value args) {}

    private static final class Decoder {

        private final List<Finding> errors;
        private final @Nullable JsonSpans spans;
        private boolean failed;

        private Decoder(List<Finding> errors, @Nullable JsonSpans spans) {
            this.errors = errors;
            this.spans = spans;
        }

        private @Nullable Document decode(@Nullable JsonElement element) {
            JsonObject object = object(element, "$");
            if (object == null) {
                return null;
            }
            rejectUnknown(object, "$", documentMembers);
            @Nullable
            JsonElement versionElement = object.get("schemaVersion");
            Long version = versionElement == null ? null : integer(versionElement, field("$", "schemaVersion"));
            if (versionElement == null) {
                fail(Codes.documentField, field("$", "schemaVersion"), "$", "missing member 'schemaVersion'");
            }
            if (version == null) {
                return null;
            }
            if (version != Document.currentVersion) {
                fail(
                    Codes.schemaVersion,
                    field("$", "schemaVersion"),
                    "$",
                    "unsupported schemaVersion " + version + ", expected " + Document.currentVersion
                );
                return null;
            }
            NamespacedId id = id(object, "id", "$");
            Cuboid bounds = box(required(object, "bounds", "$"), field("$", "bounds"));
            Boolean envelope = optionalBoolean(object, "envelope", "$");
            Map<NamespacedId, BlueprintSpec> definitions = definitions(object);
            List<PlacementSpec> placements = placements(object, "$");
            List<BaySpec> bays = bays(object, "$", true);
            List<CheckSpec> checks = checks(object, "$", true);
            List<ComputationSpec> computations = computations(object, "$");
            List<KeySpec> keys = keys(object, "$");
            List<DerivationSpec> derivations = derivations(object, "$");
            List<AnchorSpec> anchors = anchors(object, "$");
            OrientationPolicy orientation = orientation(object, "$");
            Value arguments = arguments(object, "$");
            Map<String, Value> extensions = extensions(object, "$");
            if (id == null || bounds == null) {
                return null;
            }
            BlueprintSpec root = new BlueprintSpec(
                id,
                bounds,
                Boolean.TRUE.equals(envelope),
                arguments,
                bays,
                List.of(),
                checks,
                computations,
                keys,
                derivations,
                anchors,
                orientation,
                placements,
                Map.of(),
                span("$")
            );
            if (failed) {
                return null;
            }
            return new Document(version.intValue(), root, definitions, extensions);
        }

        private Map<NamespacedId, BlueprintSpec> definitions(JsonObject root) {
            @Nullable
            JsonElement element = root.get("definitions");
            if (element == null) {
                return Map.of();
            }
            String path = field("$", "definitions");
            JsonObject object = object(element, path);
            if (object == null) {
                return Map.of();
            }
            Map<NamespacedId, BlueprintSpec> definitions = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String keyPath = field(path, entry.getKey());
                NamespacedId id = NamespacedId.parse(entry.getKey());
                if (id == null) {
                    fail(Codes.documentField, keyPath, path, "invalid definition id '" + entry.getKey() + "'");
                    continue;
                }
                BlueprintSpec definition = definition(entry.getValue(), keyPath, id);
                if (definition != null) {
                    definitions.put(id, definition);
                }
            }
            return definitions;
        }

        private @Nullable BlueprintSpec definition(@Nullable JsonElement element, String path, NamespacedId id) {
            JsonObject object = object(element, path);
            if (object == null) {
                return null;
            }
            rejectUnknown(object, path, definitionMembers);
            Cuboid bounds = box(required(object, "bounds", path), field(path, "bounds"));
            if (bounds == null) {
                return null;
            }
            return new BlueprintSpec(
                id,
                bounds,
                false,
                arguments(object, path),
                bays(object, path, false),
                seams(object, path),
                checks(object, path, false),
                computations(object, path),
                keys(object, path),
                derivations(object, path),
                anchors(object, path),
                orientation(object, path),
                placements(object, path),
                extensions(object, path),
                span(path)
            );
        }

        private List<BaySpec> bays(JsonObject object, String path, boolean required) {
            JsonArray array = required ? requiredArray(object, "bays", path) : optionalArray(object, "bays", path);
            if (array == null) {
                return List.of();
            }
            String arrayPath = field(path, "bays");
            List<BaySpec> bays = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                BaySpec bay = bay(array.get(index), element(arrayPath, index));
                if (bay != null) {
                    bays.add(bay);
                }
            }
            return bays;
        }

        private @Nullable BaySpec bay(@Nullable JsonElement element, String path) {
            JsonObject object = object(element, path);
            if (object == null) {
                return null;
            }
            rejectUnknown(object, path, bayMembers);
            NamespacedId id = id(object, "id", path);
            CuboidSet cells = bayCells(object, path);
            Coverage coverage = coverage(object, path);
            Footprint footprint = footprint(object, path);
            Sharing sharing = sharing(object, path);
            String description = optionalString(object, "description", path);
            if (id == null || cells == null || coverage == null || footprint == null || sharing == null) {
                return null;
            }
            return new BaySpec(id, cells, coverage, footprint, sharing, description, span(path));
        }

        private @Nullable CuboidSet bayCells(JsonObject object, String path) {
            @Nullable
            JsonElement boxElement = object.get("box");
            @Nullable
            JsonElement boxesElement = object.get("boxes");
            if ((boxElement == null) == (boxesElement == null)) {
                fail(Codes.documentField, path, path, "a bay needs exactly one of 'box' and 'boxes'");
                return null;
            }
            CuboidSet cells;
            if (boxElement != null) {
                Cuboid box = box(boxElement, field(path, "box"));
                if (box == null) {
                    return null;
                }
                cells = CuboidSet.of(box);
            } else {
                cells = boxSet(boxesElement, field(path, "boxes"));
                if (cells == null) {
                    return null;
                }
            }
            @Nullable
            JsonElement minusElement = object.get("minus");
            if (minusElement != null) {
                CuboidSet minus = boxSet(minusElement, field(path, "minus"));
                if (minus == null) {
                    return null;
                }
                cells = cells.subtract(minus);
            }
            return cells;
        }

        private @Nullable Coverage coverage(JsonObject object, String path) {
            String name = string(object, "coverage", path);
            if (name == null) {
                return null;
            }
            Coverage coverage = JsonVocabulary.coverage(name);
            if (coverage == null) {
                fail(Codes.documentField, field(path, "coverage"), path, "unknown coverage '" + name + "'");
            }
            return coverage;
        }

        private @Nullable Footprint footprint(JsonObject object, String path) {
            @Nullable
            JsonElement element = object.get("footprint");
            if (element == null) {
                fail(Codes.documentField, field(path, "footprint"), path, "missing member 'footprint'");
                return null;
            }
            String footprintPath = field(path, "footprint");
            if (element instanceof JsonPrimitive primitive && primitive.isString()) {
                String name = primitive.getAsString();
                return switch (name) {
                    case "box" -> Footprint.box();
                    case "bound" -> Footprint.bound();
                    case "none" -> Footprint.none();
                    case "declared" -> {
                        fail(Codes.documentField, footprintPath, path, "a declared footprint needs its boxes");
                        yield null;
                    }
                    default -> {
                        fail(Codes.documentField, footprintPath, path, "unknown footprint '" + name + "'");
                        yield null;
                    }
                };
            }
            JsonObject declared = object(element, footprintPath);
            if (declared == null) {
                return null;
            }
            rejectUnknown(declared, footprintPath, Set.of("declared"));
            @Nullable
            JsonElement declaredCells = declared.get("declared");
            if (declaredCells == null) {
                fail(Codes.documentField, footprintPath, path, "a declared footprint needs 'declared' boxes");
                return null;
            }
            CuboidSet cells = boxSet(declaredCells, field(footprintPath, "declared"));
            return cells == null ? null : Footprint.declared(cells);
        }

        private @Nullable Sharing sharing(JsonObject object, String path) {
            String name = string(object, "sharing", path);
            if (name == null) {
                return null;
            }
            Sharing sharing = JsonVocabulary.sharing(name);
            if (sharing == null) {
                fail(Codes.documentField, field(path, "sharing"), path, "unknown sharing '" + name + "'");
            }
            return sharing;
        }

        private List<SeamSpec> seams(JsonObject object, String path) {
            JsonArray array = optionalArray(object, "seams", path);
            if (array == null) {
                return List.of();
            }
            String arrayPath = field(path, "seams");
            List<SeamSpec> seams = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                SeamSpec seam = seam(array.get(index), element(arrayPath, index));
                if (seam != null) {
                    seams.add(seam);
                }
            }
            return seams;
        }

        private @Nullable SeamSpec seam(@Nullable JsonElement element, String path) {
            JsonObject object = object(element, path);
            if (object == null) {
                return null;
            }
            rejectUnknown(object, path, seamMembers);
            NamespacedId id = id(object, "id", path);
            Cuboid cells = box(required(object, "box", path), field(path, "box"));
            String kindName = string(object, "kind", path);
            SeamKind kind = null;
            if (kindName != null) {
                kind = JsonVocabulary.seamKind(kindName);
                if (kind == null) {
                    fail(Codes.documentField, field(path, "kind"), path, "unknown seam kind '" + kindName + "'");
                }
            }
            Map<String, String> properties = properties(object, path);
            if (id == null || cells == null || kind == null) {
                return null;
            }
            return new SeamSpec(new SeamId(id), cells, kind, properties, span(path));
        }

        private Map<String, String> properties(JsonObject object, String path) {
            @Nullable
            JsonElement element = object.get("properties");
            if (element == null) {
                return Map.of();
            }
            String propertiesPath = field(path, "properties");
            JsonObject properties = object(element, propertiesPath);
            if (properties == null) {
                return Map.of();
            }
            Map<String, String> result = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
                String value = string(entry.getValue(), field(propertiesPath, entry.getKey()));
                if (value != null) {
                    result.put(entry.getKey(), value);
                }
            }
            return result;
        }

        private List<CheckSpec> checks(JsonObject object, String path, boolean required) {
            JsonArray array = required ? requiredArray(object, "checks", path) : optionalArray(object, "checks", path);
            if (array == null) {
                return List.of();
            }
            String arrayPath = field(path, "checks");
            List<CheckSpec> checks = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                CheckSpec check = check(array.get(index), element(arrayPath, index));
                if (check != null) {
                    checks.add(check);
                }
            }
            return checks;
        }

        private @Nullable CheckSpec check(@Nullable JsonElement element, String path) {
            JsonObject object = object(element, path);
            if (object == null) {
                return null;
            }
            rejectUnknown(object, path, checkMembers);
            NamespacedId id = id(object, "id", path);
            NamespacedId bay = id(object, "bay", path);
            Kind focus = kind(object, "focus", path);
            Kind expectation = kind(object, "expect", path);
            String description = optionalString(object, "description", path);
            if (id == null || bay == null || focus == null || expectation == null) {
                return null;
            }
            return new CheckSpec(
                id,
                bay,
                focus.id(),
                focus.args(),
                expectation.id(),
                expectation.args(),
                description,
                span(path)
            );
        }

        private @Nullable Kind kind(JsonObject object, String name, String path) {
            @Nullable
            JsonElement element = object.get(name);
            if (element == null) {
                fail(Codes.documentField, field(path, name), path, "missing member '" + name + "'");
                return null;
            }
            String kindPath = field(path, name);
            JsonObject kindObject = object(element, kindPath);
            if (kindObject == null) {
                return null;
            }
            NamespacedId id = id(kindObject, "kind", kindPath);
            Value args = args(kindObject, kindPath, kindMembers);
            if (id == null || args == null) {
                return null;
            }
            return new Kind(id, args);
        }

        private List<ComputationSpec> computations(JsonObject object, String path) {
            JsonArray array = optionalArray(object, "computations", path);
            if (array == null) {
                return List.of();
            }
            String arrayPath = field(path, "computations");
            List<ComputationSpec> computations = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                ComputationSpec computation = computation(array.get(index), element(arrayPath, index));
                if (computation != null) {
                    computations.add(computation);
                }
            }
            return computations;
        }

        private @Nullable ComputationSpec computation(@Nullable JsonElement element, String path) {
            JsonObject object = object(element, path);
            if (object == null) {
                return null;
            }
            NamespacedId id = id(object, "id", path);
            NamespacedId kind = id(object, "kind", path);
            NamespacedId output = id(object, "output", path);
            NamespacedId bay = optionalId(object, "bay", path);
            CuboidSet region = null;
            @Nullable
            JsonElement regionElement = object.get("region");
            if (regionElement != null) {
                region = boxSet(regionElement, field(path, "region"));
            }
            Set<NamespacedId> inputs = ids(object, "inputs", path);
            Value args = args(object, path, computationMembers);
            if (id == null || kind == null || output == null || args == null) {
                return null;
            }
            return new ComputationSpec(id, kind, args, region, bay, output, inputs, span(path));
        }

        private List<KeySpec> keys(JsonObject object, String path) {
            JsonArray array = optionalArray(object, "keys", path);
            if (array == null) {
                return List.of();
            }
            String arrayPath = field(path, "keys");
            List<KeySpec> keys = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                KeySpec key = key(array.get(index), element(arrayPath, index));
                if (key != null) {
                    keys.add(key);
                }
            }
            return keys;
        }

        private @Nullable KeySpec key(@Nullable JsonElement element, String path) {
            JsonObject object = object(element, path);
            if (object == null) {
                return null;
            }
            rejectUnknown(object, path, keyMembers);
            NamespacedId id = id(object, "id", path);
            String type = string(object, "type", path);
            String retentionName = string(object, "retention", path);
            Retention retention = null;
            if (retentionName != null) {
                retention = JsonVocabulary.retention(retentionName);
                if (retention == null) {
                    fail(
                        Codes.documentField,
                        field(path, "retention"),
                        path,
                        "unknown retention '" + retentionName + "'"
                    );
                }
            }
            NamespacedId merge = optionalId(object, "merge", path);
            Value whenAbsent = null;
            @Nullable
            JsonElement absentElement = object.get("whenAbsent");
            if (absentElement != null) {
                whenAbsent = value(absentElement, field(path, "whenAbsent"), 0);
            }
            if (id == null || type == null || retention == null) {
                return null;
            }
            return new KeySpec(id, type, retention, merge, whenAbsent, span(path));
        }

        private List<PlacementSpec> placements(JsonObject object, String path) {
            JsonArray array = optionalArray(object, "placements", path);
            if (array == null) {
                return List.of();
            }
            String arrayPath = field(path, "placements");
            List<PlacementSpec> placements = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                PlacementSpec placement = placement(array.get(index), element(arrayPath, index));
                if (placement != null) {
                    placements.add(placement);
                }
            }
            return placements;
        }

        private @Nullable PlacementSpec placement(@Nullable JsonElement element, String path) {
            JsonObject object = object(element, path);
            if (object == null) {
                return null;
            }
            rejectUnknown(object, path, placementMembers);
            NamespacedId definition = id(object, "definition", path);
            NamespacedId alias = id(object, "as", path);
            LatticePoint offset = point(required(object, "at", path), field(path, "at"));
            if (definition == null || alias == null || offset == null) {
                return null;
            }
            return new PlacementSpec(definition, offset, alias, span(path));
        }

        private List<DerivationSpec> derivations(JsonObject object, String path) {
            JsonArray array = optionalArray(object, "derivations", path);
            if (array == null) {
                return List.of();
            }
            String arrayPath = field(path, "derivations");
            List<DerivationSpec> derivations = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                DerivationSpec derivation = derivation(array.get(index), element(arrayPath, index));
                if (derivation != null) {
                    derivations.add(derivation);
                }
            }
            return derivations;
        }

        private @Nullable DerivationSpec derivation(@Nullable JsonElement element, String path) {
            JsonObject object = object(element, path);
            if (object == null) {
                return null;
            }
            rejectUnknown(object, path, derivationMembers);
            NamespacedId deriver = id(object, "deriver", path);
            NamespacedId output = id(object, "output", path);
            Integer order = requiredInteger(object, "order", path);
            Value arguments = arguments(object, path);
            if (deriver == null || output == null || order == null) {
                return null;
            }
            try {
                return new DerivationSpec(deriver, arguments, output, order);
            } catch (IllegalArgumentException rejected) {
                fail(Codes.documentField, path, path, "invalid derivation: " + rejected.getMessage());
                return null;
            }
        }

        private List<AnchorSpec> anchors(JsonObject object, String path) {
            JsonArray array = optionalArray(object, "anchors", path);
            if (array == null) {
                return List.of();
            }
            String arrayPath = field(path, "anchors");
            List<AnchorSpec> anchors = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                AnchorSpec anchor = anchor(array.get(index), element(arrayPath, index));
                if (anchor != null) {
                    anchors.add(anchor);
                }
            }
            return anchors;
        }

        private @Nullable AnchorSpec anchor(@Nullable JsonElement element, String path) {
            JsonObject object = object(element, path);
            if (object == null) {
                return null;
            }
            rejectUnknown(object, path, anchorMembers);
            NamespacedId role = id(object, "role", path);
            String property = string(object, "property", path);
            String value = string(object, "value", path);
            if (role == null || property == null || value == null) {
                return null;
            }
            return new AnchorSpec(role, property, value);
        }

        private @Nullable OrientationPolicy orientation(JsonObject object, String path) {
            @Nullable
            JsonElement element = object.get("orientation");
            if (element == null) {
                return null;
            }
            String orientationPath = field(path, "orientation");
            JsonObject orientation = object(element, orientationPath);
            if (orientation == null) {
                return null;
            }
            rejectUnknown(orientation, orientationPath, orientationMembers);
            NamespacedId primary = id(orientation, "primary", orientationPath);
            JsonArray allowedArray = requiredArray(orientation, "allowed", orientationPath);
            List<Orientation> allowed = new ArrayList<>();
            if (allowedArray != null) {
                String allowedPath = field(orientationPath, "allowed");
                for (int index = 0; index < allowedArray.size(); index++) {
                    String name = string(allowedArray.get(index), element(allowedPath, index));
                    if (name == null) {
                        continue;
                    }
                    Orientation value = JsonVocabulary.orientation(name);
                    if (value == null) {
                        fail(
                            Codes.documentField,
                            element(allowedPath, index),
                            orientationPath,
                            "unknown orientation '" + name + "'"
                        );
                        continue;
                    }
                    allowed.add(value);
                }
            }
            Boolean upright = optionalBoolean(orientation, "upright", orientationPath);
            if (primary == null || allowedArray == null) {
                return null;
            }
            try {
                return new OrientationPolicy(primary, allowed, Boolean.TRUE.equals(upright));
            } catch (IllegalArgumentException rejected) {
                fail(
                    Codes.documentField,
                    orientationPath,
                    path,
                    "invalid orientation policy: " + rejected.getMessage()
                );
                return null;
            }
        }

        private Value arguments(JsonObject object, String path) {
            @Nullable
            JsonElement element = object.get("arguments");
            if (element == null) {
                return Value.object();
            }
            String argumentsPath = field(path, "arguments");
            JsonObject arguments = object(element, argumentsPath);
            if (arguments == null) {
                return Value.object();
            }
            Value value = value(arguments, argumentsPath, 0);
            if (value == null) {
                return Value.object();
            }
            if (!(value instanceof Value.ObjectValue)) {
                fail(Codes.documentField, argumentsPath, path, "'arguments' must be a JSON object");
                return Value.object();
            }
            return value;
        }

        private Map<String, Value> extensions(JsonObject object, String path) {
            @Nullable
            JsonElement element = object.get("extensions");
            if (element == null) {
                return Map.of();
            }
            String extensionsPath = field(path, "extensions");
            JsonObject extensions = object(element, extensionsPath);
            if (extensions == null) {
                return Map.of();
            }
            Map<String, Value> result = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : extensions.entrySet()) {
                Value value = value(entry.getValue(), field(extensionsPath, entry.getKey()), 0);
                if (value != null) {
                    result.put(entry.getKey(), value);
                }
            }
            return result;
        }

        private @Nullable Value args(JsonObject object, String path, Set<String> reserved) {
            Map<String, Value> members = new TreeMap<>(Values.codePointOrder());
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (reserved.contains(entry.getKey())) {
                    continue;
                }
                Value child = value(entry.getValue(), field(path, entry.getKey()), 0);
                if (child == null) {
                    return null;
                }
                members.put(entry.getKey(), child);
            }
            return new Value.ObjectValue(members, span(path));
        }

        private @Nullable Value value(@Nullable JsonElement element, String path, int depth) {
            if (element == null) {
                return null;
            }
            if (depth > maxValueDepth) {
                fail(Codes.documentField, path, path, "the value tree is deeper than " + maxValueDepth);
                return null;
            }
            SourceSpan span = span(path);
            if (element instanceof JsonObject object) {
                Map<String, Value> members = new TreeMap<>(Values.codePointOrder());
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    Value child = value(entry.getValue(), field(path, entry.getKey()), depth + 1);
                    if (child == null) {
                        return null;
                    }
                    members.put(entry.getKey(), child);
                }
                return withSpan(new Value.ObjectValue(members, null), span);
            }
            if (element instanceof JsonArray array) {
                List<Value> elements = new ArrayList<>(array.size());
                for (int index = 0; index < array.size(); index++) {
                    Value child = value(array.get(index), element(path, index), depth + 1);
                    if (child == null) {
                        return null;
                    }
                    elements.add(child);
                }
                return withSpan(new Value.ArrayValue(elements, null), span);
            }
            if (element instanceof JsonPrimitive primitive) {
                if (primitive.isBoolean()) {
                    return withSpan(new Value.BooleanValue(primitive.getAsBoolean(), null), span);
                }
                if (primitive.isString()) {
                    return withSpan(new Value.StringValue(primitive.getAsString(), null), span);
                }
                if (primitive.isNumber()) {
                    Long number = number(primitive.getAsString(), path);
                    return number == null ? null : withSpan(new Value.NumberValue(number, null), span);
                }
            }
            if (element.isJsonNull()) {
                return withSpan(new Value.NullValue(null), span);
            }
            fail(Codes.documentField, path, path, "unsupported JSON value");
            return null;
        }

        private static Value withSpan(Value value, @Nullable SourceSpan span) {
            return span == null ? value : Value.withSpan(value, span);
        }

        private @Nullable Long number(String text, String path) {
            try {
                return new BigDecimal(text).longValueExact();
            } catch (NumberFormatException | ArithmeticException rejected) {
                fail(
                    Codes.documentField,
                    path,
                    path,
                    "only 64-bit integers are representable in a document value: " + text
                );
                return null;
            }
        }

        private @Nullable SourceSpan span(String path) {
            return spans == null ? null : spans.value(path);
        }

        private @Nullable JsonElement required(JsonObject object, String name, String path) {
            @Nullable
            JsonElement element = object.get(name);
            if (element == null) {
                fail(Codes.documentField, field(path, name), path, "missing member '" + name + "'");
            }
            return element;
        }

        private @Nullable JsonArray requiredArray(JsonObject object, String name, String path) {
            return array(required(object, name, path), field(path, name));
        }

        private @Nullable JsonArray optionalArray(JsonObject object, String name, String path) {
            @Nullable
            JsonElement element = object.get(name);
            return element == null ? null : array(element, field(path, name));
        }

        private @Nullable JsonObject object(@Nullable JsonElement element, String path) {
            if (element == null) {
                return null;
            }
            if (element instanceof JsonObject object) {
                return object;
            }
            fail(Codes.documentField, path, path, "expected a JSON object");
            return null;
        }

        private @Nullable JsonArray array(@Nullable JsonElement element, String path) {
            if (element == null) {
                return null;
            }
            if (element instanceof JsonArray array) {
                return array;
            }
            fail(Codes.documentField, path, path, "expected a JSON array");
            return null;
        }

        private @Nullable NamespacedId id(JsonObject object, String name, String path) {
            return id(required(object, name, path), field(path, name));
        }

        private @Nullable NamespacedId optionalId(JsonObject object, String name, String path) {
            @Nullable
            JsonElement element = object.get(name);
            return element == null ? null : id(element, field(path, name));
        }

        private @Nullable NamespacedId id(@Nullable JsonElement element, String path) {
            String text = string(element, path);
            if (text == null) {
                return null;
            }
            NamespacedId id = NamespacedId.parse(text);
            if (id == null) {
                fail(Codes.documentField, path, path, "invalid id '" + text + "'");
            }
            return id;
        }

        private @Nullable String string(JsonObject object, String name, String path) {
            return string(required(object, name, path), field(path, name));
        }

        private @Nullable String optionalString(JsonObject object, String name, String path) {
            @Nullable
            JsonElement element = object.get(name);
            return element == null ? null : string(element, field(path, name));
        }

        private @Nullable String string(@Nullable JsonElement element, String path) {
            if (element == null) {
                return null;
            }
            if (element instanceof JsonPrimitive primitive && primitive.isString()) {
                return primitive.getAsString();
            }
            fail(Codes.documentField, path, path, "expected a string");
            return null;
        }

        private @Nullable Long integer(@Nullable JsonElement element, String path) {
            if (element == null) {
                return null;
            }
            if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
                return number(primitive.getAsString(), path);
            }
            fail(Codes.documentField, path, path, "expected an integer");
            return null;
        }

        private @Nullable Integer integer32(@Nullable JsonElement element, String path) {
            Long value = integer(element, path);
            if (value == null) {
                return null;
            }
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                fail(Codes.documentField, path, path, "expected a 32-bit integer");
                return null;
            }
            return value.intValue();
        }

        private @Nullable Integer requiredInteger(JsonObject object, String name, String path) {
            return integer32(required(object, name, path), field(path, name));
        }

        private @Nullable Boolean optionalBoolean(JsonObject object, String name, String path) {
            @Nullable
            JsonElement element = object.get(name);
            if (element == null) {
                return null;
            }
            if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            fail(Codes.documentField, field(path, name), path, "expected a boolean");
            return null;
        }

        private @Nullable Cuboid box(@Nullable JsonElement element, String path) {
            JsonArray array = array(element, path);
            if (array == null) {
                return null;
            }
            if (array.size() != 6) {
                fail(Codes.documentField, path, path, "a box is [x, y, z, sizeX, sizeY, sizeZ]");
                return null;
            }
            int[] values = new int[6];
            for (int index = 0; index < 6; index++) {
                Integer value = integer32(array.get(index), element(path, index));
                if (value == null) {
                    return null;
                }
                values[index] = value;
            }
            try {
                return Cuboid.ofSize(values[0], values[1], values[2], values[3], values[4], values[5]);
            } catch (IllegalArgumentException rejected) {
                fail(Codes.documentField, path, path, "invalid box: " + rejected.getMessage());
                return null;
            }
        }

        private @Nullable CuboidSet boxSet(@Nullable JsonElement element, String path) {
            JsonArray array = array(element, path);
            if (array == null) {
                return null;
            }
            List<Cuboid> boxes = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                Cuboid box = box(array.get(index), element(path, index));
                if (box == null) {
                    return null;
                }
                boxes.add(box);
            }
            return CuboidSet.of(boxes);
        }

        private @Nullable LatticePoint point(@Nullable JsonElement element, String path) {
            JsonArray array = array(element, path);
            if (array == null) {
                return null;
            }
            if (array.size() != 3) {
                fail(Codes.documentField, path, path, "a point is [x, y, z]");
                return null;
            }
            int[] values = new int[3];
            for (int index = 0; index < 3; index++) {
                Integer value = integer32(array.get(index), element(path, index));
                if (value == null) {
                    return null;
                }
                values[index] = value;
            }
            try {
                return LatticePoint.of(values[0], values[1], values[2]);
            } catch (IllegalArgumentException rejected) {
                fail(Codes.documentField, path, path, "invalid point: " + rejected.getMessage());
                return null;
            }
        }

        private Set<NamespacedId> ids(JsonObject object, String name, String path) {
            JsonArray array = optionalArray(object, name, path);
            if (array == null) {
                return Set.of();
            }
            String arrayPath = field(path, name);
            Set<NamespacedId> ids = new TreeSet<>();
            for (int index = 0; index < array.size(); index++) {
                NamespacedId id = id(array.get(index), element(arrayPath, index));
                if (id != null) {
                    ids.add(id);
                }
            }
            return ids;
        }

        private void rejectUnknown(JsonObject object, String path, Set<String> allowed) {
            for (String name : object.keySet()) {
                if (!allowed.contains(name)) {
                    fail(Codes.documentField, field(path, name), path, "unknown member '" + name + "'");
                }
            }
        }

        private void fail(CodeEntry entry, String path, @Nullable String fallback, String message) {
            failed = true;
            Finding.Builder builder = Finding.builder(entry).phase(Phase.compile).message(message);
            SourceSpan span = null;
            if (spans != null) {
                span = spans.name(path);
                if (span == null) {
                    span = spans.value(path);
                }
                if (span == null && fallback != null) {
                    span = spans.value(fallback);
                }
            }
            if (span != null) {
                builder.label(Label.primary(Locus.definition(span), message));
            }
            errors.add(builder.build());
        }

        private static String field(String path, String name) {
            return JsonSpans.member(path, name);
        }

        private static String element(String path, int index) {
            return JsonSpans.element(path, index);
        }
    }
}
