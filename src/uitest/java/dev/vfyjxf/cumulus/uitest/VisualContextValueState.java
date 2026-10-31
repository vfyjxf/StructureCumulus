package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dev-only scalar context values mirrored from the fixture staging for the HUD and inspector. */
public final class VisualContextValueState {

    private static volatile Map<InstanceId, Map<String, String>> values = Map.of();

    private VisualContextValueState() {}

    static synchronized void put(InstanceId id, Map<String, String> next) {
        Map<InstanceId, Map<String, String>> copy = new LinkedHashMap<>(values);
        copy.put(id, Map.copyOf(next));
        values = Map.copyOf(copy);
    }

    static synchronized void clear() {
        values = Map.of();
    }

    public static Map<String, String> values(InstanceId id) {
        return values.getOrDefault(id, Map.of());
    }
}
