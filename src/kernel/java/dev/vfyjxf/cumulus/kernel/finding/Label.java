package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.Checks;
import dev.vfyjxf.cumulus.kernel.space.Locus;

/** One located message. */
public record Label(Locus locus, String message, LabelRole role) {

    public Label {
        Checks.checkNotNull(locus, "locus");
        Checks.checkNotNull(message, "message");
        Checks.checkNotNull(role, "role");
        Checks.checkArgument(!message.isBlank(), "a label message must not be blank");
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
