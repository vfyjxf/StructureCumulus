package dev.vfyjxf.cumulus.mc.plugin;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationPluginLookupTest {

    interface ProbePlugin extends ModPlugin {}

    public static final class HealthyPlugin implements ProbePlugin {

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "healthy");
        }
    }

    public static final class BrokenInitPlugin implements ProbePlugin {

        static {
            failOnInit();
        }

        private static void failOnInit() {
            throw new IllegalStateException("broken static initializer");
        }

        @Override
        public NamespacedId pluginId() {
            return NamespacedId.of("test", "broken");
        }
    }

    @Test
    void aClassWithAFailingInitializerIsSkipped() {
        AnnotationPluginLookup<ProbePlugin> lookup = AnnotationPluginLookup.of(ProbePlugin.class);
        assertNull(lookup.instantiate(BrokenInitPlugin.class.getName()));
    }

    @Test
    void theLogNamesTheSkippedClass() {
        try (LogCapture capture = LogCapture.attach(AnnotationPluginLookup.class)) {
            AnnotationPluginLookup.of(ProbePlugin.class).instantiate(BrokenInitPlugin.class.getName());
            String className = BrokenInitPlugin.class.getName();
            assertTrue(
                capture.messages().stream().anyMatch(message -> message.contains(className)),
                "the error log must name the skipped class " + className
            );
        }
    }

    @Test
    void otherPluginsStillLoad() {
        AnnotationPluginLookup<ProbePlugin> lookup = AnnotationPluginLookup.of(ProbePlugin.class);
        List<ProbePlugin> result = new ArrayList<>();
        for (String candidate : List.of(BrokenInitPlugin.class.getName(), HealthyPlugin.class.getName())) {
            ProbePlugin plugin = lookup.instantiate(candidate);
            if (plugin != null) {
                result.add(plugin);
            }
        }
        assertEquals(1, result.size());
        assertInstanceOf(HealthyPlugin.class, result.get(0));
    }
}
