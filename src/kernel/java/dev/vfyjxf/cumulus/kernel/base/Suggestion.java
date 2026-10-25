package dev.vfyjxf.cumulus.kernel.base;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record Suggestion(String message, Applicability applicability, @Nullable NamespacedId remedy) {

    public Suggestion {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(applicability, "applicability");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
