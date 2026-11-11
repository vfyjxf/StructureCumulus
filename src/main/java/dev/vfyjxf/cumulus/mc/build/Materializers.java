package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.Edit;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The materializer chain in registration order: the first entry that answers non-null lowers the
 * edit, and the built-in vanilla materializer is always last.
 */
public final class Materializers {

    private final List<Materializer> chain;
    private final Map<NamespacedId, Materializer> byId;

    Materializers(List<Materializer> chain) {
        this.chain = List.copyOf(chain);
        Map<NamespacedId, Materializer> values = new LinkedHashMap<>();
        for (Materializer materializer : this.chain) {
            values.put(materializer.id(), materializer);
        }
        this.byId = Map.copyOf(values);
    }

    /** The built-in chain: the vanilla materializer alone. */
    public static Materializers builtin() {
        return new Materializers(List.of(new VanillaMaterializer()));
    }

    /** Every contribution, in chain order. */
    public List<NamespacedId> ids() {
        List<NamespacedId> ids = new ArrayList<>(chain.size());
        for (Materializer materializer : chain) {
            ids.add(materializer.id());
        }
        return List.copyOf(ids);
    }

    public @Nullable Materializer get(NamespacedId id) {
        Objects.requireNonNull(id, "id");
        return byId.get(id);
    }

    /**
     * The payload of the first materializer that lowers the edit. A throwing materializer fails the
     * step — the chain never falls back past it — and the message names the materializer's id.
     */
    public @Nullable MaterializedEdit materialize(Edit edit, Orientation orientation, MaterializeContext context) {
        Objects.requireNonNull(edit, "edit");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(context, "context");
        for (Materializer materializer : chain) {
            MaterializedEdit materialized;
            try {
                materialized = materializer.materialize(edit, orientation, context);
            } catch (RuntimeException | LinkageError failure) {
                throw new IllegalStateException(
                    "materializer " + materializer.id().text() + " failed: " + failure,
                    failure
                );
            }
            if (materialized != null) {
                return materialized;
            }
        }
        return null;
    }
}
