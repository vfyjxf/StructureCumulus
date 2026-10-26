package dev.vfyjxf.cumulus.kernel.core;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;
/** The only naming type in the kernel: {@code namespace:path}. */
public record NamespacedId(String namespace, String path) implements Comparable<NamespacedId> {

    public static final String DEFAULT_NAMESPACE = "structurecumulus";

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]{1,256}");

    public NamespacedId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("invalid namespace: " + namespace);
        }
        if (!PATH.matcher(path).matches()
                || path.startsWith("/") || path.endsWith("/") || path.contains("//")) {
            throw new IllegalArgumentException("invalid path: " + path);
        }
    }

    public static NamespacedId of(String namespace, String path) {
        return new NamespacedId(namespace, path);
    }

    public static NamespacedId of(String path) {
        return new NamespacedId(DEFAULT_NAMESPACE, path);
    }

    public static @Nullable NamespacedId parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        int colon = text.indexOf(':');
        if (colon < 0) {
            return isValidPath(text) ? new NamespacedId(DEFAULT_NAMESPACE, text) : null;
        }
        if (text.indexOf(':', colon + 1) >= 0) {
            return null;
        }
        String namespace = text.substring(0, colon);
        String path = text.substring(colon + 1);
        if (!NAMESPACE.matcher(namespace).matches() || !isValidPath(path)) {
            return null;
        }
        return new NamespacedId(namespace, path);
    }

    private static boolean isValidPath(String path) {
        return PATH.matcher(path).matches()
                && !path.startsWith("/") && !path.endsWith("/") && !path.contains("//");
    }

    public String text() {
        return namespace + ":" + path;
    }

    @Override
    public int compareTo(NamespacedId other) {
        int byNamespace = namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return text();
    }
}
