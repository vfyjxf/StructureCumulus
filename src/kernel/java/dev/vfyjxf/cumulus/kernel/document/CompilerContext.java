package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.structure.ComputationFactory;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.kernel.structure.FocusFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** The catalogs, type registry and document source a compiler needs. */
public record CompilerContext(
    Catalog<FocusFactory> focuses,
    Catalog<ExpectationFactory> expectations,
    Catalog<ComputationFactory> computations,
    Map<NamespacedId, Class<?>> types,
    DocumentSource documents
) {

    public CompilerContext {
        Objects.requireNonNull(focuses, "focuses");
        Objects.requireNonNull(expectations, "expectations");
        Objects.requireNonNull(computations, "computations");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(documents, "documents");
        types = Collections.unmodifiableMap(new TreeMap<>(types));
    }
}
