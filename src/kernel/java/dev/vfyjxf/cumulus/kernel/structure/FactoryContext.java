package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** What a document factory may see while building a unit. */
public record FactoryContext(
    NamespacedId document,
    @Nullable SourceSpan source,
    Function<NamespacedId, @Nullable ContextKey<?>> keys,
    Consumer<Finding> report
) {

    public FactoryContext {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(report, "report");
    }
}
