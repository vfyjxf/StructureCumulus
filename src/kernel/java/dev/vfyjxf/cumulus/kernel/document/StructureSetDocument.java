package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.Value;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** The data form of one structure-set document. */
public record StructureSetDocument(int schemaVersion, StructureSetSpec spec, Map<String, Value> extensions) {

    public static final int currentVersion = 1;

    public StructureSetDocument {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(extensions, "extensions");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion >= 1");
        }
        extensions = Collections.unmodifiableMap(new TreeMap<>(extensions));
    }

    public StructureSetDocument withoutSpans() {
        StructureSetSpec stripped = spec.withoutSpans();
        return stripped == spec ? this : new StructureSetDocument(schemaVersion, stripped, extensions);
    }
}
