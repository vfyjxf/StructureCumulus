package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyProvider;
import dev.vfyjxf.cumulus.kernel.structure.ComputationFactory;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.kernel.structure.FocusFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class DefaultCatalogRegistrar implements CatalogRegistrar {

    private final Catalog.Builder<ExpectationFactory> expectations;
    private final Catalog.Builder<FocusFactory> focuses;
    private final Catalog.Builder<ComputationFactory> computations;
    private final Catalog.Builder<Deriver<?>> derivers;
    private final Catalog.Builder<CodeEntry> codes;
    private final Catalog.Builder<RemedyProvider> remedies;
    private final List<RegistrationFailure> failures = new ArrayList<>();

    DefaultCatalogRegistrar(Catalogs builtin) {
        expectations = seeded(builtin.expectations());
        focuses = seeded(builtin.focuses());
        computations = seeded(builtin.computations());
        derivers = seeded(builtin.derivers());
        codes = seeded(builtin.codes());
        remedies = seeded(builtin.remedies());
    }

    private static <T> Catalog.Builder<T> seeded(Catalog<T> catalog) {
        Catalog.Builder<T> builder = Catalog.builder(catalog.family());
        builder.putAll(catalog);
        return builder;
    }

    @Override
    public CatalogRegistrar expectation(NamespacedId id, ExpectationFactory factory) {
        expectations.put(id, factory);
        return this;
    }

    @Override
    public CatalogRegistrar focus(NamespacedId id, FocusFactory factory) {
        focuses.put(id, factory);
        return this;
    }

    @Override
    public CatalogRegistrar computation(NamespacedId id, ComputationFactory factory) {
        computations.put(id, factory);
        return this;
    }

    @Override
    public CatalogRegistrar deriver(NamespacedId id, Deriver<?> deriver) {
        derivers.put(id, deriver);
        return this;
    }

    @Override
    public CatalogRegistrar code(CodeEntry entry) {
        Objects.requireNonNull(entry, "entry");
        codes.put(NamespacedId.of("structurecumulus", entry.code().text().toLowerCase(Locale.ROOT)), entry);
        return this;
    }

    @Override
    public CatalogRegistrar remedy(NamespacedId id, RemedyProvider provider) {
        remedies.put(id, provider);
        return this;
    }

    @Override
    public void fail(NamespacedId pluginId, RuntimeException failure) {
        failures.add(new RegistrationFailure(pluginId, failure));
    }

    @Override
    public List<RegistrationFailure> failures() {
        return List.copyOf(failures);
    }

    @Override
    public Catalogs catalogs() {
        return Catalogs.builder().expectations(expectations.build()).focuses(focuses.build())
                .computations(computations.build()).derivers(derivers.build()).codes(codes.build())
                .remedies(remedies.build()).build();
    }
}
