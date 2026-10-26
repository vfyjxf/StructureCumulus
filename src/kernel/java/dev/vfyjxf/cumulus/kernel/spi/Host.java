package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
/** The four host ports a kernel holds. */
public record Host(DocumentSource documents, @Nullable ClaimLedger ledger,
                   ContextStore contexts, Map<NamespacedId, Class<?>> types) {

    public Host {
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(contexts, "contexts");
        Objects.requireNonNull(types, "types");
        types = Collections.unmodifiableMap(new TreeMap<>(types));
    }

    public static Host empty() {
        return new Host(DocumentSource.empty(), null, ContextStore.inMemory(), Map.of());
    }
}
