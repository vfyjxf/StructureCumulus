package dev.vfyjxf.cumulus.mc.preview.jei;

import com.mojang.logging.LogUtils;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.plugin.AnnotationPluginLookup;
import dev.vfyjxf.cumulus.mc.plugin.ModPlugin;
import dev.vfyjxf.cumulus.mc.plugin.PluginLoader;
import dev.vfyjxf.cumulus.mc.plugin.PluginLoadingException;
import mezz.jei.api.JeiPlugin;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * The loader pass that reads the built-in GUI plugin through the adapter's own plugin policy, so its
 * id, dependencies and ordering failures are attributed the way every other plugin's are. JEI absent
 * ends here, before any JEI type is resolved (D-90).
 */
final class JeiPreviewPlugins {

    private static final Logger logger = LogUtils.getLogger();

    private static boolean loaded;

    private JeiPreviewPlugins() {}

    static void load() {
        if (loaded || !ModList.get().isLoaded("jei")) {
            return;
        }
        loaded = true;
        PluginLoader.LoadingResult<ModPlugin> result;
        try {
            result = PluginLoader.load(AnnotationPluginLookup.of(ModPlugin.class, JeiPlugin.class));
        } catch (PluginLoadingException failure) {
            logger.warn("gui plugin loading failed: {}", failure.getMessage());
            return;
        }
        for (PluginLoader.LoadingFailure<ModPlugin> failure : result.failures()) {
            if (failure.type() == PluginLoader.FailureType.warning) {
                logger.warn("gui plugin {}: {}", failure.instance().pluginId().text(), failure.reason());
            } else {
                logger.error("gui plugin {}: {}", failure.instance().pluginId().text(), failure.reason());
            }
        }
        for (ModPlugin plugin : result.plugins()) {
            logger.info("gui plugin {} loaded", plugin.pluginId().text());
        }
    }

    static boolean isBuiltin(NamespacedId pluginId) {
        return loaded && JeiPreviewPlugin.id.equals(pluginId);
    }
}
