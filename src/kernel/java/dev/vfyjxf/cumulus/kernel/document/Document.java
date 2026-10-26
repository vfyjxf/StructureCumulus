package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
/** The data form of one blueprint and its reusable definitions. */
public record Document(int schemaVersion, BlueprintSpec root,
                       Map<NamespacedId, BlueprintSpec> definitions,
                       Map<String, Value> extensions) {

    public static final int CURRENT_VERSION = 1;

    public Document {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(extensions, "extensions");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion >= 1");
        }
        TreeMap<NamespacedId, BlueprintSpec> sorted = new TreeMap<>();
        for (Map.Entry<NamespacedId, BlueprintSpec> entry : definitions.entrySet()) {
            sorted.put(Objects.requireNonNull(entry.getKey(), "definition id"),
                    Objects.requireNonNull(entry.getValue(), "definition"));
        }
        definitions = Collections.unmodifiableMap(sorted);
        extensions = Collections.unmodifiableMap(new TreeMap<>(extensions));
    }
}
