package dev.vfyjxf.cumulus.kernel.base;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record Note(String message, @Nullable Locus locus) {

    public Note {
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    public static Note of(String message) {
        return new Note(message, null);
    }
}
