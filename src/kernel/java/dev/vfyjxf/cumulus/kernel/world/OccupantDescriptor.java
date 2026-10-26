package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
/** A version-independent description of an occupant. */
public record OccupantDescriptor(NamespacedId type, OccupantKind kind,
                                 Map<String, String> properties, List<NamespacedId> tags) {

    public OccupantDescriptor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(tags, "tags");
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            sorted.put(Objects.requireNonNull(entry.getKey(), "key"),
                    Objects.requireNonNull(entry.getValue(), "value"));
        }
        properties = Collections.unmodifiableMap(sorted);
        tags = List.copyOf(new TreeSet<>(tags));
    }

    public @Nullable String property(String key) {
        return properties.get(key);
    }
}
