package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.plugin.PluginLoadingException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraitLookupLoadingTest {

    private static final Logger logger = LoggerFactory.getLogger(TraitLookupLoadingTest.class);

    @Test
    void aDuplicateRegistrationIsAFatalFailureNamingThePlugin() {
        TraitPlugin duplicate = new TraitPlugin() {

            @Override
            public NamespacedId pluginId() {
                return NamespacedId.of("test", "duplicate");
            }

            @Override
            public void registerTraits(TraitRegister register) {
                register.register(BlockTraitProviders.inventory);
            }
        };
        PluginLoadingException exception = assertThrows(
            PluginLoadingException.class,
            () -> TraitLookup.load(logger, () -> List.of(duplicate))
        );
        assertEquals(NamespacedId.of("test", "duplicate"), exception.failures().get(0).pluginId());
        assertTrue(
            Objects.requireNonNull(exception.getMessage()).contains("duplicate trait provider"),
            "the summary keeps the duplicate cause: " + exception.getMessage()
        );
    }

    @Test
    void aLinkageErrorInRegisterTraitsIsAFatalFailureNamingThePlugin() {
        TraitPlugin broken = new TraitPlugin() {

            @Override
            public NamespacedId pluginId() {
                return NamespacedId.of("test", "broken");
            }

            @Override
            public void registerTraits(TraitRegister register) {
                throw new NoClassDefFoundError("the optional mod is absent");
            }
        };
        PluginLoadingException exception = assertThrows(
            PluginLoadingException.class,
            () -> TraitLookup.load(logger, () -> List.of(broken))
        );
        assertEquals(NamespacedId.of("test", "broken"), exception.failures().get(0).pluginId());
        assertInstanceOf(NoClassDefFoundError.class, exception.failures().get(0).cause());
        assertTrue(
            Objects.requireNonNull(exception.getMessage()).contains("the optional mod is absent"),
            "the summary keeps the cause: " + exception.getMessage()
        );
    }
}
