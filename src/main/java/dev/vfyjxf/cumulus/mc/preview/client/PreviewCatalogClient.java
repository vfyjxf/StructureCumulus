package dev.vfyjxf.cumulus.mc.preview.client;

import dev.vfyjxf.cumulus.mc.preview.net.PreviewCatalogPayload;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The last catalog the server pushed, in the order the server built it. The JEI category reads this
 * snapshot; nothing here parses a document or recomputes a kernel decision.
 */
public final class PreviewCatalogClient {

    private static final Set<Runnable> listeners = new CopyOnWriteArraySet<>();

    private static volatile List<PreviewCatalogPayload.Entry> entries = List.of();

    private PreviewCatalogClient() {}

    public static void accept(PreviewCatalogPayload payload) {
        entries = payload.entries();
        listeners.forEach(Runnable::run);
    }

    public static List<PreviewCatalogPayload.Entry> entries() {
        return entries;
    }

    public static void listen(Runnable listener) {
        listeners.add(listener);
    }

    public static void unlisten(Runnable listener) {
        listeners.remove(listener);
    }

    public static void clear() {
        entries = List.of();
        listeners.clear();
    }
}
