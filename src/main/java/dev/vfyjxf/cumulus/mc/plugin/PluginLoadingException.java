package dev.vfyjxf.cumulus.mc.plugin;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Thrown when plugin loading fails fatally; individual failures are also suppressed. */
@NullMarked
public class PluginLoadingException extends RuntimeException {

    private final List<Failure> failures;

    public PluginLoadingException(List<Failure> failures) {
        super(formatMessage(failures));
        this.failures = List.copyOf(failures);
        for (Failure failure : failures) {
            addSuppressed(failure.cause());
        }
    }

    public List<Failure> failures() {
        return failures;
    }

    private static String formatMessage(List<Failure> failures) {
        return "Plugin loading failed: "
                + failures.stream().map(failure -> failure.pluginId() + ": " + failure.cause().getMessage())
                        .collect(Collectors.joining(", "));
    }

    /** One plugin that could not be loaded, with the cause used for the summary. */
    public record Failure(NamespacedId pluginId, Throwable cause) {

        public Failure {
            Objects.requireNonNull(pluginId, "pluginId");
            Objects.requireNonNull(cause, "cause");
        }
    }
}
