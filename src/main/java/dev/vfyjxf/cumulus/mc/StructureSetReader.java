package dev.vfyjxf.cumulus.mc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.core.Values;
import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.PresenceSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.document.StructureSetSpec;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
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

/** Reads the v1 structure-set schema from a Gson tree and records the offsets Gson drops. */
@NullMarked
public final class StructureSetReader {

    private static final Set<String> setMembers = Set
            .of("schemaVersion", "kind", "id", "root", "satellites", "extensions");

    private static final Set<String> satelliteMembers = Set
            .of("id", "blueprint", "at", "orientation", "required", "arguments", "presence", "links");

    private static final Set<String> presenceMembers = Set.of("cell", "boxes");

    private static final Set<String> linkMembers = Set
            .of("mode", "parent_cell", "satellite_cell", "max_distance", "metric");

    private static final int maxValueDepth = 256;

    public @Nullable StructureSetDocument read(NamespacedId source, String text, List<Finding> errors) {
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
        JsonSpans spans = JsonSpans.scan(source, text);
        StructureSetDocument document = new Decoder(errors, spans).decode(element);
        if (document != null && spans == null) {
            errors.add(
                Finding.builder(Codes.noPositions).phase(Phase.compile)
                        .message("the reader produced no source spans for " + source.text()).build()
            );
        }
        return document;
    }

    private static @Nullable LinkMode linkMode(String name) {
        return switch (name) {
            case "face" -> LinkMode.face;
            case "range" -> LinkMode.range;
            case "none" -> LinkMode.none;
            default -> null;
        };
    }

    private static @Nullable DistanceMetric distanceMetric(String name) {
        return switch (name) {
            case "chebyshev" -> DistanceMetric.chebyshev;
            case "manhattan" -> DistanceMetric.manhattan;
            case "euclidean" -> DistanceMetric.euclidean;
            default -> null;
        };
    }

    private static final class Decoder {

        private final List<Finding> errors;
        private final @Nullable JsonSpans spans;
        private final Set<NamespacedId> satelliteIds = new TreeSet<>();
        private boolean failed;

        private Decoder(List<Finding> errors, @Nullable JsonSpans spans) {
            this.errors = errors;
            this.spans = spans;
        }

        private @Nullable StructureSetDocument decode(@Nullable JsonElement element) {
            JsonObject object = object(element, "$");
            if (object == null) {
                return null;
            }
            rejectUnknown(object, "$", setMembers);
            @Nullable
            JsonElement versionElement = object.get("schemaVersion");
            Long version = versionElement == null ? null : integer(versionElement, field("$", "schemaVersion"));
            if (versionElement == null) {
                fail(Codes.documentField, field("$", "schemaVersion"), "$", "missing member 'schemaVersion'");
            }
            if (version == null) {
                return null;
            }
            if (version != StructureSetDocument.currentVersion) {
                fail(
                    Codes.schemaVersion,
                    field("$", "schemaVersion"),
                    "$",
                    "unsupported schemaVersion " + version + ", expected " + StructureSetDocument.currentVersion
                );
                return null;
            }
            String kind = string(object, "kind", "$");
            if (kind == null) {
                return null;
            }
            if (!kind.equals("structure_set")) {
                fail(Codes.documentField, field("$", "kind"), "$", "kind must be 'structure_set', not '" + kind + "'");
                return null;
            }
            NamespacedId id = id(object, "id", "$");
            NamespacedId root = id(object, "root", "$");
            List<SatelliteSpec> satellites = satellites(object, "$");
            Map<String, Value> extensions = extensions(object, "$");
            if (id == null || root == null) {
                return null;
            }
            if (failed) {
                return null;
            }
            return new StructureSetDocument(
                version.intValue(),
                new StructureSetSpec(id, root, satellites, span("$")),
                extensions
            );
        }

        private List<SatelliteSpec> satellites(JsonObject object, String path) {
            JsonArray array = requiredArray(object, "satellites", path);
            if (array == null) {
                return List.of();
            }
            String arrayPath = field(path, "satellites");
            List<SatelliteSpec> satellites = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                SatelliteSpec satellite = satellite(array.get(index), element(arrayPath, index));
                if (satellite != null) {
                    satellites.add(satellite);
                }
            }
            return satellites;
        }

        private @Nullable SatelliteSpec satellite(@Nullable JsonElement element, String path) {
            JsonObject object = object(element, path);
            if (object == null) {
                return null;
            }
            rejectUnknown(object, path, satelliteMembers);
            NamespacedId id = id(object, "id", path);
            if (id != null && !satelliteIds.add(id)) {
                fail(Codes.documentDuplicate, field(path, "id"), path, "duplicate satellite id " + id.text());
            }
            NamespacedId blueprint = id(object, "blueprint", path);
            LatticePoint at = point(required(object, "at", path), field(path, "at"));
            Orientation orientation = orientation(object, path);
            Boolean required = optionalBoolean(object, "required", path);
            Value arguments = arguments(object, path);
            PresenceSpec presence = presence(object, path);
            List<LinkSpec> links = links(object, path);
            if (id == null || blueprint == null || at == null) {
                return null;
            }
            return new SatelliteSpec(
                id,
                blueprint,
                at,
                orientation,
                Boolean.TRUE.equals(required),
                arguments,
                presence,
                links,
                span(path)
            );
        }

        private Orientation orientation(JsonObject object, String path) {
            String name = optionalString(object, "orientation", path);
            if (name == null) {
                return Orientation.identity;
            }
            Orientation orientation = JsonVocabulary.orientation(name);
            if (orientation == null) {
                fail(Codes.documentField, field(path, "orientation"), path, "unknown orientation '" + name + "'");
                return Orientation.identity;
            }
            return orientation;
        }

        private @Nullable PresenceSpec presence(JsonObject object, String path) {
            @Nullable
            JsonElement element = object.get("presence");
            if (element == null) {
                return null;
            }
            String presencePath = field(path, "presence");
            JsonObject presence = object(element, presencePath);
            if (presence == null) {
                return null;
            }
            rejectUnknown(presence, presencePath, presenceMembers);
            @Nullable
            JsonElement cellElement = presence.get("cell");
            @Nullable
            JsonElement boxesElement = presence.get("boxes");
            if ((cellElement == null) == (boxesElement == null)) {
                fail(Codes.documentField, presencePath, path, "a presence needs exactly one of 'cell' and 'boxes'");
                return null;
            }
            CuboidSet cells;
            if (cellElement != null) {
                LatticePoint cell = point(cellElement, field(presencePath, "cell"));
                if (cell == null) {
                    return null;
                }
                Cuboid box = cellBox(cell, field(presencePath, "cell"), presencePath);
                if (box == null) {
                    return null;
                }
                cells = CuboidSet.of(box);
            } else {
                cells = boxSet(boxesElement, field(presencePath, "boxes"));
                if (cells == null) {
                    return null;
                }
                if (cells.isEmpty()) {
                    fail(
                        Codes.documentField,
                        field(presencePath, "boxes"),
                        presencePath,
                        "a presence region needs at least one cell"
                    );
                    return null;
                }
            }
            return new PresenceSpec(cells, span(presencePath));
        }

        private List<LinkSpec> links(JsonObject object, String path) {
            JsonArray array = optionalArray(object, "links", path);
            if (array == null) {
                return List.of();
            }
            String arrayPath = field(path, "links");
            List<LinkSpec> links = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                LinkSpec link = link(array.get(index), element(arrayPath, index));
                if (link != null) {
                    links.add(link);
                }
            }
            return links;
        }

        private @Nullable LinkSpec link(@Nullable JsonElement element, String path) {
            JsonObject object = object(element, path);
            if (object == null) {
                return null;
            }
            rejectUnknown(object, path, linkMembers);
            String modeName = string(object, "mode", path);
            if (modeName == null) {
                return null;
            }
            LinkMode mode = linkMode(modeName);
            if (mode == null) {
                fail(Codes.documentField, field(path, "mode"), path, "unknown link mode '" + modeName + "'");
                return null;
            }
            SourceSpan source = span(path);
            return switch (mode) {
                case none -> {
                    boolean extra = false;
                    for (String member : List.of("parent_cell", "satellite_cell", "max_distance", "metric")) {
                        if (object.has(member)) {
                            fail(
                                Codes.documentField,
                                field(path, member),
                                path,
                                "a none link carries no '" + member + "'"
                            );
                            extra = true;
                        }
                    }
                    yield extra ? null : new LinkSpec(LinkMode.none, null, null, 0, DistanceMetric.chebyshev, source);
                }
                case face -> {
                    LatticePoint parent = point(required(object, "parent_cell", path), field(path, "parent_cell"));
                    LatticePoint satellite = point(
                        required(object, "satellite_cell", path),
                        field(path, "satellite_cell")
                    );
                    boolean extra = false;
                    for (String member : List.of("max_distance", "metric")) {
                        if (object.has(member)) {
                            fail(
                                Codes.documentField,
                                field(path, member),
                                path,
                                "a face link carries no '" + member + "'"
                            );
                            extra = true;
                        }
                    }
                    yield parent == null || satellite == null || extra
                            ? null
                            : new LinkSpec(LinkMode.face, parent, satellite, 0, DistanceMetric.chebyshev, source);
                }
                case range -> {
                    LatticePoint parent = point(required(object, "parent_cell", path), field(path, "parent_cell"));
                    LatticePoint satellite = point(
                        required(object, "satellite_cell", path),
                        field(path, "satellite_cell")
                    );
                    Integer maxDistance = requiredInteger(object, "max_distance", path);
                    String metricName = optionalString(object, "metric", path);
                    DistanceMetric metric = DistanceMetric.chebyshev;
                    if (metricName != null) {
                        metric = distanceMetric(metricName);
                        if (metric == null) {
                            fail(
                                Codes.documentField,
                                field(path, "metric"),
                                path,
                                "unknown distance metric '" + metricName + "'"
                            );
                        }
                    }
                    if (maxDistance != null && maxDistance < 0) {
                        fail(
                            Codes.documentField,
                            field(path, "max_distance"),
                            path,
                            "max_distance must be non-negative"
                        );
                        maxDistance = null;
                    }
                    yield parent == null || satellite == null || maxDistance == null || metric == null
                            ? null
                            : new LinkSpec(LinkMode.range, parent, satellite, maxDistance, metric, source);
                }
            };
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

        private @Nullable Cuboid cellBox(LatticePoint cell, String path, String fallback) {
            try {
                return Cuboid.of(cell);
            } catch (IllegalArgumentException rejected) {
                fail(Codes.documentField, path, fallback, "invalid cell: " + rejected.getMessage());
                return null;
            }
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
