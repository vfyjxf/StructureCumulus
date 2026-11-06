package dev.vfyjxf.cumulus.kernel.spi;

import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.structure.FactoryContext;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusFactory;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogsTest {

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static NamespacedId kind(String path) {
        return NamespacedId.of(path);
    }

    @Test
    void builtinShipsTheLeafFactoriesAndEveryCode() {
        Catalogs catalogs = Catalogs.builtin();
        for (String focus : new String[]{"occupant", "bay", "all_cells", "entity", "nested", "door", "context"}) {
            assertTrue(catalogs.focuses().has(kind(focus)), focus);
        }
        for (String expectation : new String[]{"block", "air", "trait", "any_occupant"}) {
            assertTrue(catalogs.expectations().has(kind(expectation)), expectation);
        }
        for (String computation : new String[]{"count", "volume", "air"}) {
            assertTrue(catalogs.computations().has(kind(computation)), computation);
        }
        assertTrue(catalogs.derivers().isEmpty());
        assertTrue(catalogs.remedies().isEmpty());
        assertEquals(Codes.all.size(), catalogs.codes().size());
    }

    @Test
    void aBuilderAddsFactoriesWithoutReplacingTheBuiltins() {
        FocusFactory custom = new FocusFactory() {
            @Override
            public NamespacedId id() {
                return CatalogsTest.id("custom");
            }

            @Override
            public @Nullable Focus<?> create(Value args, FactoryContext context) {
                return Focuses.bay();
            }
        };
        Catalog<FocusFactory> extra = Catalog.<FocusFactory>builder(id("focuses")).put(custom.id(), custom).build();
        Catalogs merged = Catalogs.builder().focuses(extra).build();
        assertTrue(merged.focuses().has(kind("occupant")));
        assertNotSame(custom, merged.focuses().require(kind("occupant")));
        assertSame(custom, merged.focuses().require(id("custom")));
        assertEquals(Catalogs.builtin().focuses().size() + 1, merged.focuses().size());
    }

    @Test
    void aDifferentValueUnderOneIdThrowsAtTheFamilyCall() {
        FocusFactory replacement = new FocusFactory() {
            @Override
            public NamespacedId id() {
                return CatalogsTest.kind("occupant");
            }

            @Override
            public @Nullable Focus<?> create(Value args, FactoryContext context) {
                return null;
            }
        };
        Catalog<FocusFactory> clash = Catalog.<FocusFactory>builder(id("focuses")).put(replacement.id(), replacement)
                .build();
        IllegalArgumentException duplicate = assertThrows(
            IllegalArgumentException.class,
            () -> Catalogs.builder().focuses(clash)
        );
        assertTrue(Objects.requireNonNull(duplicate.getMessage(), "message").startsWith("SC-SPI-0003: "));
    }

    @Test
    void aBuiltCatalogIgnoresLaterBuilderCalls() {
        Catalogs.Builder builder = Catalogs.builder();
        Catalogs first = builder.build();
        FocusFactory custom = new FocusFactory() {
            @Override
            public NamespacedId id() {
                return CatalogsTest.id("custom");
            }

            @Override
            public @Nullable Focus<?> create(Value args, FactoryContext context) {
                return Focuses.bay();
            }
        };
        builder.focuses(Catalog.<FocusFactory>builder(id("focuses")).put(custom.id(), custom).build());
        assertTrue(first.focuses().has(kind("occupant")));
        assertFalse(first.focuses().has(id("custom")));
    }

    @Test
    void theCodeCatalogIsKeyedByLowercaseCodeText() {
        Catalogs catalogs = Catalogs.builtin();
        for (CodeEntry entry : Codes.all) {
            CodeEntry resolved = catalogs.codes()
                    .require(NamespacedId.of(entry.code().text().toLowerCase(Locale.ROOT)));
            assertSame(entry, resolved);
            assertEquals(entry.code(), resolved.explanation().code());
        }
    }
}
