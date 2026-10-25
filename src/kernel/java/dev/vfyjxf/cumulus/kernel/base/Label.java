package dev.vfyjxf.cumulus.kernel.base;

import java.util.Objects;

public record Label(Locus locus, String message, LabelRole role) {

    public Label {
        Objects.requireNonNull(locus, "locus");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(role, "role");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    public static Label primary(Locus locus, String message) {
        return new Label(locus, message, LabelRole.PRIMARY);
    }

    public static Label secondary(Locus locus, String message) {
        return new Label(locus, message, LabelRole.SECONDARY);
    }

    public static Label context(Locus locus, String message) {
        return new Label(locus, message, LabelRole.CONTEXT);
    }
}
