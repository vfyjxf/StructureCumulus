package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyProvider;
import dev.vfyjxf.cumulus.kernel.structure.ComputationFactory;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.kernel.structure.FocusFactory;
/** The six frozen extension families of one kernel. */
public interface Catalogs {

    Catalog<ExpectationFactory> expectations();

    Catalog<FocusFactory> focuses();

    Catalog<ComputationFactory> computations();

    Catalog<Deriver<?>> derivers();

    Catalog<CodeEntry> codes();

    Catalog<RemedyProvider> remedies();

    static Catalogs builtin() {
        return DefaultCatalogs.builtin();
    }

    static Builder builder() {
        return new DefaultCatalogs.BuilderImpl();
    }

    interface Builder {
        Builder expectations(Catalog<ExpectationFactory> catalog);

        Builder focuses(Catalog<FocusFactory> catalog);

        Builder computations(Catalog<ComputationFactory> catalog);

        Builder derivers(Catalog<Deriver<?>> catalog);

        Builder codes(Catalog<CodeEntry> catalog);

        Builder remedies(Catalog<RemedyProvider> catalog);

        Catalogs build();
    }
}
