package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.Checks;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;
/** An optional repair hint. */
public record Suggestion(String message, Applicability applicability,
                         @Nullable NamespacedId remedy) {

    public Suggestion {
        Checks.checkNotNull(message, "message");
        Checks.checkNotNull(applicability, "applicability");
    }
}
