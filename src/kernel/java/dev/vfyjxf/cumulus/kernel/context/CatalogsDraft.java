package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.base.NamespacedId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** The catalog's key family while it is being built; a second declaration of one id is refused. */
public interface CatalogsDraft {

    <T> ContextKey<T> registerKey(ContextKey<T> key);

    <T> ContextKey<T> putKey(ContextKey<T> key);

    static CatalogsDraft create() {
        return new MapDraft();
    }
}

final class MapDraft implements CatalogsDraft {

    private final Map<NamespacedId, ContextKey<?>> keys = new LinkedHashMap<>();

    @Override
    public <T> ContextKey<T> registerKey(ContextKey<T> key) {
        return putKey(key);
    }

    @Override
    public <T> ContextKey<T> putKey(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        ContextKey<?> existing = keys.putIfAbsent(key.id(), key);
        if (existing != null) {
            throw new IllegalStateException("SC-CTX-0102: context key " + key.id() + " is already declared");
        }
        return key;
    }
}
