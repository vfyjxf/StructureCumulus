package dev.vfyjxf.cumulus.kernel.base;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public record NamespacedId(String namespace, String path) implements Comparable<NamespacedId> {

    public static final String DEFAULT_NAMESPACE = "structurecumulus";

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH_PATTERN = Pattern.compile("[a-z0-9_./-]+");

    public NamespacedId {
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("namespace: " + namespace);
        }
        if (!validPath(path)) {
            throw new IllegalArgumentException("path: " + path);
        }
    }

    public static NamespacedId of(String namespace, String path) {
        return new NamespacedId(namespace, path);
    }

    public static @Nullable NamespacedId parse(String text) {
        int colon = text.indexOf(':');
        if (colon <= 0 || colon == text.length() - 1 || text.indexOf(':', colon + 1) >= 0) {
            return null;
        }
        String namespace = text.substring(0, colon);
        String path = text.substring(colon + 1);
        if (!NAMESPACE_PATTERN.matcher(namespace).matches() || !validPath(path)) {
            return null;
        }
        return new NamespacedId(namespace, path);
    }

    private static boolean validPath(String path) {
        return PATH_PATTERN.matcher(path).matches()
                && !path.startsWith("/")
                && !path.endsWith("/")
                && !path.contains("//");
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
