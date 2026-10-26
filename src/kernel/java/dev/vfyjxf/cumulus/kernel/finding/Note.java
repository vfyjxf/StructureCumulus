package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.Checks;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import org.jspecify.annotations.Nullable;
/** A fact with no cell of its own. */
public record Note(String message, @Nullable Locus locus) {

    public Note {
        Checks.checkNotNull(message, "message");
        Checks.checkArgument(!message.isBlank(), "a note message must not be blank");
    }

    public static Note of(String message) {
        return new Note(message, null);
    }

    public static Note at(String message, Locus locus) {
        return new Note(message, Checks.checkNotNull(locus, "locus"));
    }
}
