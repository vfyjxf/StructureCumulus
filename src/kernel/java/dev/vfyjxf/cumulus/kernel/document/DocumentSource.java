package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** id to document, for cross-document definitions. */
public interface DocumentSource {

    @Nullable
    Document find(NamespacedId id);

    static DocumentSource of(Map<NamespacedId, Document> documents) {
        Objects.requireNonNull(documents, "documents");
        Map<NamespacedId, Document> copy = Collections.unmodifiableMap(new TreeMap<>(documents));
        return copy::get;
    }

    static DocumentSource empty() {
        return id -> null;
    }
}
