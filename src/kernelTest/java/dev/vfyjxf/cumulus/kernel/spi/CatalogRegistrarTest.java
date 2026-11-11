package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.context.DeriveContext;
import dev.vfyjxf.cumulus.kernel.context.Deriver;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Outcome;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Explanation;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.remedy.Remedy;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyContext;
import dev.vfyjxf.cumulus.kernel.remedy.RemedyProvider;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.structure.Computation;
import dev.vfyjxf.cumulus.kernel.structure.ComputationFactory;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.kernel.structure.FactoryContext;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogRegistrarTest {

    private static final NamespacedId reactor = NamespacedId.of("mymod", "reactor");

    @Test
    void anEmptyRegistrarFreezesToTheBuiltinCatalogs() {
        Catalogs catalogs = CatalogRegistrar.builtin().catalogs();
        Catalogs builtin = Catalogs.builtin();
        assertSameEntries(builtin.expectations(), catalogs.expectations());
        assertSameEntries(builtin.focuses(), catalogs.focuses());
        assertSameEntries(builtin.computations(), catalogs.computations());
        assertSameEntries(builtin.derivers(), catalogs.derivers());
        assertSameEntries(builtin.codes(), catalogs.codes());
        assertSameEntries(builtin.remedies(), catalogs.remedies());
    }

    @Test
    void contributionsEnterTheFrozenCatalogs() {
        ContextKey<Long> capacity = ContextKeys.of(id("capacity"), Long.class, Retention.instance);
        ExpectationFactory expectation = expectationFactory(reactor);
        FocusFactory focus = focusFactory(id("core"));
        ComputationFactory computation = computationFactory(id("gather"));
        Deriver<Long> deriver = deriver(capacity);
        CodeEntry entry = codeEntry();
        Catalogs catalogs = CatalogRegistrar.builtin().expectation(reactor, expectation).focus(id("core"), focus)
                .computation(id("gather"), computation).deriver(id("capacity"), deriver).code(entry).catalogs();
        assertEquals(expectation, catalogs.expectations().require(reactor));
        assertEquals(focus, catalogs.focuses().require(id("core")));
        assertEquals(computation, catalogs.computations().require(id("gather")));
        assertEquals(deriver, catalogs.derivers().require(id("capacity")));
        assertEquals(entry, catalogs.codes().require(codeId(entry)));
        assertTrue(catalogs.expectations().has(NamespacedId.of("structurecumulus", "block")));
        assertEquals(2, catalogs.remedies().size());
        assertTrue(catalogs.remedies().has(NamespacedId.of("structurecumulus", "cell_repair")));
    }

    @Test
    void aRemedyContributionFollowsTheDuplicateLaw() {
        RemedyProvider provider = remedyProvider(id("custom_repair"));
        Catalogs catalogs = CatalogRegistrar.builtin().remedy(id("custom_repair"), provider)
                .remedy(id("custom_repair"), provider).catalogs();
        assertSame(provider, catalogs.remedies().require(id("custom_repair")));
        assertTrue(catalogs.remedies().has(NamespacedId.of("structurecumulus", "preview")));

        IllegalArgumentException duplicate = assertThrows(
            IllegalArgumentException.class,
            () -> CatalogRegistrar.builtin().remedy(id("custom_repair"), provider)
                    .remedy(id("custom_repair"), remedyProvider(id("custom_repair")))
        );
        assertTrue(Objects.requireNonNull(duplicate.getMessage()).contains("SC-SPI-0003"));

        IllegalArgumentException builtin = assertThrows(
            IllegalArgumentException.class,
            () -> CatalogRegistrar.builtin().remedy(NamespacedId.of("structurecumulus", "cell_repair"), provider)
        );
        assertTrue(Objects.requireNonNull(builtin.getMessage()).contains("SC-SPI-0003"));
    }

    @Test
    void aRemedyRegisterFailureIsAttributedToItsPlugin() {
        IllegalStateException boom = new IllegalStateException("boom");
        CatalogRegistrar registrar = CatalogRegistrar.builtin();
        try {
            registrar.remedy(id("exploding"), remedyProvider(id("exploding")));
            registrar.remedy(id("exploding"), remedyProvider(id("other")));
        } catch (IllegalArgumentException failure) {
            registrar.fail(id("plugin"), boom);
        }
        assertEquals(1, registrar.failures().size());
        assertSame(boom, registrar.failures().get(0).cause());
        assertEquals(id("plugin"), registrar.failures().get(0).pluginId());
    }

    @Test
    void aBuiltinIdCannotBeReplaced() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> CatalogRegistrar.builtin().expectation(
                NamespacedId.of("structurecumulus", "block"),
                expectationFactory(NamespacedId.of("structurecumulus", "block"))
            )
        );
        assertTrue(Objects.requireNonNull(exception.getMessage()).contains("SC-SPI-0003"));
    }

    @Test
    void aDuplicateIdWithADifferentValueIsRejected() {
        CatalogRegistrar registrar = CatalogRegistrar.builtin().expectation(reactor, expectationFactory(reactor));
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> registrar.expectation(reactor, expectationFactory(reactor))
        );
        assertTrue(Objects.requireNonNull(exception.getMessage()).contains("SC-SPI-0003"));
    }

    @Test
    void anEqualContributionCollapses() {
        ExpectationFactory expectation = expectationFactory(reactor);
        Catalogs catalogs = CatalogRegistrar.builtin().expectation(reactor, expectation)
                .expectation(reactor, expectation).catalogs();
        assertEquals(expectation, catalogs.expectations().require(reactor));
    }

    @Test
    void failuresAreAttributedToTheirPlugin() {
        IllegalStateException boom = new IllegalStateException("boom");
        CatalogRegistrar.RegistrationFailure failure = new CatalogRegistrar.RegistrationFailure(id("plugin"), boom);
        CatalogRegistrar registrar = CatalogRegistrar.builtin();
        registrar.fail(id("plugin"), boom);
        assertEquals(List.of(failure), registrar.failures());
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static NamespacedId codeId(CodeEntry entry) {
        return NamespacedId.of("structurecumulus", entry.code().text().toLowerCase(Locale.ROOT));
    }

    private static CodeEntry codeEntry() {
        FindingCode code = FindingCode.of(FindingCode.Family.doc, 901);
        return new CodeEntry(code, Severity.warning, new Explanation(code, "summary", "body", List.of()));
    }

    private static RemedyProvider remedyProvider(NamespacedId id) {
        return new RemedyProvider() {
            @Override
            public NamespacedId id() {
                return id;
            }

            @Override
            public List<Remedy> solve(Finding finding, RemedyContext context) {
                return List.of();
            }
        };
    }

    private static <T> void assertSameEntries(Catalog<T> expected, Catalog<T> actual) {
        assertEquals(expected.ids(), actual.ids());
        expected.ids().forEach(id -> assertEquals(expected.require(id), actual.require(id)));
    }

    private static ExpectationFactory expectationFactory(NamespacedId kind) {
        return new ExpectationFactory() {
            @Override
            public NamespacedId id() {
                return kind;
            }

            @Override
            public @Nullable Expectation<?> create(Value args, FactoryContext context) {
                return null;
            }
        };
    }

    private static FocusFactory focusFactory(NamespacedId kind) {
        return new FocusFactory() {
            @Override
            public NamespacedId id() {
                return kind;
            }

            @Override
            public @Nullable Focus<?> create(Value args, FactoryContext context) {
                return null;
            }
        };
    }

    private static ComputationFactory computationFactory(NamespacedId kind) {
        return new ComputationFactory() {
            @Override
            public NamespacedId id() {
                return kind;
            }

            @Override
            public @Nullable Computation<?> create(
                Value args,
                ContextKey<?> output,
                CuboidSet region,
                FactoryContext context
            ) {
                return null;
            }
        };
    }

    private static Deriver<Long> deriver(ContextKey<Long> output) {
        return new Deriver<>() {
            @Override
            public ContextKey<Long> output() {
                return output;
            }

            @Override
            public Set<ContextKey<?>> requires() {
                return Set.of();
            }

            @Override
            public Outcome<Long> derive(DeriveContext ctx) {
                return Outcome.missing();
            }
        };
    }
}
