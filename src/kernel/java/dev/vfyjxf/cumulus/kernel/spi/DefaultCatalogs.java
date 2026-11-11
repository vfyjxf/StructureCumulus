package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyProvider;
import dev.vfyjxf.cumulus.kernel.remedy.StandardPreviewProvider;
import dev.vfyjxf.cumulus.kernel.remedy.StandardRepairProvider;
import dev.vfyjxf.cumulus.kernel.structure.ComputationFactory;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.kernel.structure.FocusFactory;

import java.util.Objects;

final class DefaultCatalogs implements Catalogs {

    private final Catalog<ExpectationFactory> expectations;
    private final Catalog<FocusFactory> focuses;
    private final Catalog<ComputationFactory> computations;
    private final Catalog<Deriver<?>> derivers;
    private final Catalog<CodeEntry> codes;
    private final Catalog<RemedyProvider> remedies;

    DefaultCatalogs(
        Catalog<ExpectationFactory> expectations,
        Catalog<FocusFactory> focuses,
        Catalog<ComputationFactory> computations,
        Catalog<Deriver<?>> derivers,
        Catalog<CodeEntry> codes,
        Catalog<RemedyProvider> remedies
    ) {
        this.expectations = Objects.requireNonNull(expectations, "expectations");
        this.focuses = Objects.requireNonNull(focuses, "focuses");
        this.computations = Objects.requireNonNull(computations, "computations");
        this.derivers = Objects.requireNonNull(derivers, "derivers");
        this.codes = Objects.requireNonNull(codes, "codes");
        this.remedies = Objects.requireNonNull(remedies, "remedies");
    }

    @Override
    public Catalog<ExpectationFactory> expectations() {
        return expectations;
    }

    @Override
    public Catalog<FocusFactory> focuses() {
        return focuses;
    }

    @Override
    public Catalog<ComputationFactory> computations() {
        return computations;
    }

    @Override
    public Catalog<Deriver<?>> derivers() {
        return derivers;
    }

    @Override
    public Catalog<CodeEntry> codes() {
        return codes;
    }

    @Override
    public Catalog<RemedyProvider> remedies() {
        return remedies;
    }

    static Catalogs builtin() {
        return new DefaultCatalogs(
            BuiltinExpectations.catalog(),
            BuiltinFocuses.catalog(),
            BuiltinComputations.catalog(),
            Catalog.empty(family("derivers")),
            BuiltinCodes.catalog(),
            builtinRemedies()
        );
    }

    /** The two built-in remedy providers of D-85. */
    static Catalog<RemedyProvider> builtinRemedies() {
        return Catalog.<RemedyProvider>builder(family("remedies"))
                .put(NamespacedId.of("structurecumulus", "cell_repair"), new StandardRepairProvider())
                .put(NamespacedId.of("structurecumulus", "preview"), new StandardPreviewProvider()).build();
    }

    private static NamespacedId family(String name) {
        return NamespacedId.of("structurecumulus", name);
    }

    static final class BuilderImpl implements Catalogs.Builder {

        private Catalog<ExpectationFactory> expectations = BuiltinExpectations.catalog();
        private Catalog<FocusFactory> focuses = BuiltinFocuses.catalog();
        private Catalog<ComputationFactory> computations = BuiltinComputations.catalog();
        private Catalog<Deriver<?>> derivers = Catalog.empty(family("derivers"));
        private Catalog<CodeEntry> codes = BuiltinCodes.catalog();
        private Catalog<RemedyProvider> remedies = builtinRemedies();

        @Override
        public Catalogs.Builder expectations(Catalog<ExpectationFactory> catalog) {
            expectations = merge(expectations, catalog);
            return this;
        }

        @Override
        public Catalogs.Builder focuses(Catalog<FocusFactory> catalog) {
            focuses = merge(focuses, catalog);
            return this;
        }

        @Override
        public Catalogs.Builder computations(Catalog<ComputationFactory> catalog) {
            computations = merge(computations, catalog);
            return this;
        }

        @Override
        public Catalogs.Builder derivers(Catalog<Deriver<?>> catalog) {
            derivers = merge(derivers, catalog);
            return this;
        }

        @Override
        public Catalogs.Builder codes(Catalog<CodeEntry> catalog) {
            codes = merge(codes, catalog);
            return this;
        }

        @Override
        public Catalogs.Builder remedies(Catalog<RemedyProvider> catalog) {
            remedies = merge(remedies, catalog);
            return this;
        }

        @Override
        public Catalogs build() {
            return new DefaultCatalogs(expectations, focuses, computations, derivers, codes, remedies);
        }

        private static <T> Catalog<T> merge(Catalog<T> base, Catalog<T> extra) {
            Catalog.Builder<T> builder = Catalog.builder(base.family());
            builder.putAll(base);
            builder.putAll(extra);
            return builder.build();
        }
    }
}
