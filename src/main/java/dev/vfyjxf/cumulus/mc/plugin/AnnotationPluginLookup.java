package dev.vfyjxf.cumulus.mc.plugin;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Discovers plugins through NeoForge's type annotation scan. */
@NullMarked
public final class AnnotationPluginLookup<T extends ModPlugin> implements PluginLookup<T> {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationPluginLookup.class);

    private final Class<T> pluginClass;
    private final Class<? extends Annotation> annotation;

    private AnnotationPluginLookup(Class<T> pluginClass, Class<? extends Annotation> annotation) {
        this.pluginClass = pluginClass;
        this.annotation = annotation;
    }

    public static <T extends ModPlugin> AnnotationPluginLookup<T> of(Class<T> pluginClass) {
        return of(pluginClass, PluginMarker.class);
    }

    public static <T extends ModPlugin> AnnotationPluginLookup<T> of(
        Class<T> pluginClass,
        Class<? extends Annotation> annotation
    ) {
        Objects.requireNonNull(pluginClass, "pluginClass");
        Objects.requireNonNull(annotation, "annotation");
        return new AnnotationPluginLookup<>(pluginClass, annotation);
    }

    @Override
    public Collection<T> findPlugins() {
        List<T> result = new ArrayList<>();
        for (ModFileScanData scanData : ModList.get().getAllScanData()) {
            scanData.getAnnotatedBy(annotation, ElementType.TYPE).forEach(annotationData -> {
                String className = annotationData.memberName();
                try {
                    Class<?> clazz = Class.forName(className);
                    if (!pluginClass.isAssignableFrom(clazz)) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    T instance = (T) clazz.getConstructor().newInstance();
                    result.add(instance);
                } catch (ClassNotFoundException exception) {
                    logger.error("Plugin class not found: {}", className, exception);
                } catch (NoSuchMethodException exception) {
                    logger.error("Plugin class {} must have a public no-arg constructor", className, exception);
                } catch (ReflectiveOperationException exception) {
                    logger.error("Failed to instantiate plugin: {}", className, exception);
                }
            });
        }
        return result;
    }
}
