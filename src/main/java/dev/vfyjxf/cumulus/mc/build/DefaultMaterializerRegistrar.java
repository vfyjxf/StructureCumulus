package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The materializer registrar a session fills once; the vanilla entry stays last in the chain. */
@NullMarked
final class DefaultMaterializerRegistrar implements MaterializerRegistrar {

    private final Map<NamespacedId, Materializer> entries = new LinkedHashMap<>();
    private final NamespacedId vanillaId;

    DefaultMaterializerRegistrar() {
        Materializer vanilla = new VanillaMaterializer();
        this.vanillaId = vanilla.id();
        entries.put(vanillaId, vanilla);
    }

    @Override
    public MaterializerRegistrar materializer(Materializer materializer) {
        Objects.requireNonNull(materializer, "materializer");
        NamespacedId id = Objects.requireNonNull(materializer.id(), "materializer id");
        Materializer existing = entries.putIfAbsent(id, materializer);
        if (existing != null && !existing.equals(materializer)) {
            throw new IllegalArgumentException(
                "SC-SPI-0003: materializer table already holds a different value for " + id
            );
        }
        return this;
    }

    @Override
    public Materializers materializers() {
        Materializer vanilla = entries.get(vanillaId);
        List<Materializer> chain = new ArrayList<>(entries.size());
        for (Map.Entry<NamespacedId, Materializer> entry : entries.entrySet()) {
            if (!vanillaId.equals(entry.getKey())) {
                chain.add(entry.getValue());
            }
        }
        chain.add(vanilla);
        return new Materializers(chain);
    }
}
