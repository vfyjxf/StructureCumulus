package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The item-source kinds a session's plugins contributed, keyed by provider id. */
public final class ItemSources {

    private final Map<NamespacedId, ItemSourceProvider> providers;

    ItemSources(Map<NamespacedId, ItemSourceProvider> providers) {
        this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(providers));
    }

    /** A table with no contributions; the creative, player and container forms are command literals. */
    public static ItemSources empty() {
        return new ItemSources(Map.of());
    }

    public List<NamespacedId> ids() {
        return List.copyOf(new ArrayList<>(providers.keySet()));
    }

    public boolean has(NamespacedId id) {
        Objects.requireNonNull(id, "id");
        return providers.containsKey(id);
    }

    /** Builds the named kind for this request, or answers null when the id is unknown or declined. */
    public @Nullable ItemSource create(NamespacedId id, ServerPlayer player, ServerLevel level) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(level, "level");
        ItemSourceProvider provider = providers.get(id);
        return provider == null ? null : provider.create(player, level);
    }
}
