package dev.vfyjxf.cumulus.kernel.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One publication. A key the publisher could not produce is stored as a marker, so this frame
 * shadows an older value instead of letting the reader fall back to a stale one.
 */
public record ContextFrame(ContextTier tier, long revision, Map<ContextKey<?>, Object> values) {

    private static final Object ABSENT = new Object();

    public ContextFrame {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(values, "values");
        List<ContextKey<?>> keys = new ArrayList<>(values.keySet());
        Collections.sort(keys);
        Map<ContextKey<?>, Object> ordered = new LinkedHashMap<>();
        for (ContextKey<?> key : keys) {
            ordered.put(key, Objects.requireNonNull(values.get(key), "value"));
        }
        values = Collections.unmodifiableMap(ordered);
    }

    static Object absent() {
        return ABSENT;
    }

    static boolean isAbsent(Object value) {
        return value == ABSENT;
    }

    public boolean publishes(ContextKey<?> key) {
        return values.containsKey(key);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int size() {
        int size = 0;
        for (Object value : values.values()) {
            if (value != ABSENT) {
                size++;
            }
        }
        return size;
    }
}
