package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** The item-source registrar a session fills once. */
@NullMarked
final class DefaultItemSourceRegistrar implements ItemSourceRegistrar {

    private final Map<NamespacedId, ItemSourceProvider> providers = new LinkedHashMap<>();

    @Override
    public ItemSourceRegistrar itemSource(ItemSourceProvider provider) {
        Objects.requireNonNull(provider, "provider");
        NamespacedId id = Objects.requireNonNull(provider.id(), "provider id");
        ItemSourceProvider existing = providers.putIfAbsent(id, provider);
        if (existing != null && !existing.equals(provider)) {
            throw new IllegalArgumentException(
                "SC-SPI-0003: item source table already holds a different value for " + id
            );
        }
        return this;
    }

    @Override
    public ItemSources itemSources() {
        return new ItemSources(providers);
    }
}
