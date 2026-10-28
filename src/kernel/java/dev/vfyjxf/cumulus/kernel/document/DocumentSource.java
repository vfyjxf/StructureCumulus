package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** id to document, for cross-document definitions and structure-set resolution. */
public interface DocumentSource {

    @Nullable
    Document find(NamespacedId id);

    /** The structure-set document under this id, when the source loads both document kinds. */
    default @Nullable StructureSetDocument findSet(NamespacedId id) {
        return null;
    }

    static DocumentSource of(Map<NamespacedId, Document> documents) {
        Objects.requireNonNull(documents, "documents");
        Map<NamespacedId, Document> copy = Collections.unmodifiableMap(new TreeMap<>(documents));
        return copy::get;
    }

    static DocumentSource of(Map<NamespacedId, Document> documents, Map<NamespacedId, StructureSetDocument> sets) {
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(sets, "sets");
        Map<NamespacedId, Document> blueprintCopy = Collections.unmodifiableMap(new TreeMap<>(documents));
        Map<NamespacedId, StructureSetDocument> setCopy = Collections.unmodifiableMap(new TreeMap<>(sets));
        return new DocumentSource() {
            @Override
            public @Nullable Document find(NamespacedId id) {
                return blueprintCopy.get(id);
            }

            @Override
            public @Nullable StructureSetDocument findSet(NamespacedId id) {
                return setCopy.get(id);
            }
        };
    }

    static DocumentSource empty() {
        return id -> null;
    }
}
