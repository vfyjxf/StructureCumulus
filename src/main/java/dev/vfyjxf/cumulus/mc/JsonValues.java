package dev.vfyjxf.cumulus.mc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/** The JSON encoding shared by the blueprint and structure-set writers. */
@NullMarked
final class JsonValues {

    static JsonElement value(Value value) {
        return switch (value) {
            case Value.ObjectValue object -> {
                JsonObject json = new JsonObject();
                for (Map.Entry<String, Value> entry : object.members().entrySet()) {
                    json.add(entry.getKey(), value(entry.getValue()));
                }
                yield json;
            }
            case Value.ArrayValue array -> {
                JsonArray json = new JsonArray();
                for (Value element : array.elements()) {
                    json.add(value(element));
                }
                yield json;
            }
            case Value.StringValue string -> new JsonPrimitive(string.value());
            case Value.NumberValue number -> new JsonPrimitive(number.value());
            case Value.BooleanValue bool -> new JsonPrimitive(bool.value());
            case Value.NullValue ignored -> JsonNull.INSTANCE;
        };
    }

    static void addArguments(JsonObject json, Value arguments) {
        if (!(arguments instanceof Value.ObjectValue object)) {
            throw new IllegalStateException("arguments must be a JSON object");
        }
        if (!object.members().isEmpty()) {
            json.add("arguments", value(object));
        }
    }

    static void addExtensions(JsonObject json, Map<String, Value> extensions) {
        if (extensions.isEmpty()) {
            return;
        }
        JsonObject encoded = new JsonObject();
        for (Map.Entry<String, Value> entry : extensions.entrySet()) {
            encoded.add(entry.getKey(), value(entry.getValue()));
        }
        json.add("extensions", encoded);
    }

    static JsonArray box(Cuboid box) {
        JsonArray array = new JsonArray();
        array.add(box.min().x());
        array.add(box.min().y());
        array.add(box.min().z());
        array.add(box.sizeX());
        array.add(box.sizeY());
        array.add(box.sizeZ());
        return array;
    }

    static JsonArray boxSet(CuboidSet cells) {
        JsonArray array = new JsonArray();
        for (Cuboid box : cells.decomposition()) {
            array.add(box(box));
        }
        return array;
    }

    static JsonArray point(LatticePoint point) {
        JsonArray array = new JsonArray();
        array.add(point.x());
        array.add(point.y());
        array.add(point.z());
        return array;
    }

    private JsonValues() {}
}
