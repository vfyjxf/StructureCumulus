package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.Checks;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
/** What a human rendering may read. */
public record RenderContext(List<SourceText> sources, @Nullable NamespacedId document,
                            int width, ColorMode color, Locale locale) {

    private static final int DEFAULT_WIDTH = 100;

    public enum ColorMode { PLAIN, ANSI }

    public RenderContext {
        List<SourceText> ordered = new ArrayList<>(sources);
        ordered.sort((left, right) -> left.id().compareTo(right.id()));
        sources = List.copyOf(ordered);
        Checks.checkNotNull(color, "color");
        Checks.checkNotNull(locale, "locale");
    }

    public static RenderContext plain() {
        return new RenderContext(List.of(), null, DEFAULT_WIDTH, ColorMode.PLAIN, Locale.ROOT);
    }

    public static RenderContext of(SourceText source) {
        Checks.checkNotNull(source, "source");
        return new RenderContext(List.of(source), source.id(), DEFAULT_WIDTH,
                ColorMode.PLAIN, Locale.ROOT);
    }

    public @Nullable SourceText sourceFor(NamespacedId id) {
        for (SourceText source : sources) {
            if (source.id().equals(id)) {
                return source;
            }
        }
        return null;
    }

    public RenderContext withWidth(int width) {
        return new RenderContext(sources, document, width, color, locale);
    }
}
