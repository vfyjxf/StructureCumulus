package dev.vfyjxf.cumulus.mc.plugin;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiPluginLookupTest {

    public static final class HealthySpiProvider implements SpiTestPlugin {

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "healthy-spi");
        }
    }

    public static final class BrokenSpiProvider implements SpiTestPlugin {

        static {
            failOnInit();
        }

        private static void failOnInit() {
            throw new IllegalStateException("broken static initializer");
        }

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "broken-spi");
        }
    }

    @Test
    void aProviderWithAFailingInitializerIsSkipped() {
        Collection<SpiTestPlugin> plugins = SpiPluginLookup.of(SpiTestPlugin.class).findPlugins();
        assertEquals(1, plugins.size());
        assertInstanceOf(HealthySpiProvider.class, plugins.iterator().next());
    }

    @Test
    void theLogNamesTheSkippedProvider() {
        try (LogCapture capture = LogCapture.attach(SpiPluginLookup.class)) {
            SpiPluginLookup.of(SpiTestPlugin.class).findPlugins();
            String className = BrokenSpiProvider.class.getName();
            assertTrue(
                capture.messages().stream().anyMatch(message -> message.contains(className)),
                "the error log must name the skipped provider " + className
            );
        }
    }
}
