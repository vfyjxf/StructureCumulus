package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyProvider;
import dev.vfyjxf.cumulus.kernel.structure.ComputationFactory;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.kernel.structure.FocusFactory;

import java.util.List;

/** Collects catalog contributions over the built-in tables until {@link #catalogs()} freezes them. */
public interface CatalogRegistrar {

    CatalogRegistrar expectation(NamespacedId id, ExpectationFactory factory);

    CatalogRegistrar focus(NamespacedId id, FocusFactory factory);

    CatalogRegistrar computation(NamespacedId id, ComputationFactory factory);

    CatalogRegistrar deriver(NamespacedId id, Deriver<?> deriver);

    /** A code entry is keyed by its code text, the way every built-in code table keys it. */
    CatalogRegistrar code(CodeEntry entry);

    /** One remedy provider, under the same duplicate law as every other contribution (D-84). */
    CatalogRegistrar remedy(NamespacedId id, RemedyProvider provider);

    /** Attributes one register failure to the plugin that threw. */
    void fail(NamespacedId pluginId, RuntimeException failure);

    List<RegistrationFailure> failures();

    Catalogs catalogs();

    static CatalogRegistrar builtin() {
        return new DefaultCatalogRegistrar(Catalogs.builtin());
    }

    /** One register failure, attributed to the plugin that threw. */
    record RegistrationFailure(NamespacedId pluginId, RuntimeException cause) {}
}
