package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.Checks;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import org.jspecify.annotations.Nullable;
/** One document text plus offset-to-line and column conversion. */
public record SourceText(NamespacedId id, String text) {

    public SourceText {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(text, "text");
    }

    public static SourceText of(NamespacedId id, String text) {
        return new SourceText(id, text);
    }

    /** Returns the line containing {@code offset}, without its line break. */
    public @Nullable String lineAt(int offset) {
        Line line = locate(offset);
        return line == null ? null : text.substring(line.start(), line.end());
    }

    /** Lines are one-based. */
    public int lineNumber(int offset) {
        Line line = requireLine(offset);
        return line.number();
    }

    /** Columns are one-based. */
    public int column(int offset) {
        Line line = requireLine(offset);
        return offset - line.start() + 1;
    }

    private Line requireLine(int offset) {
        Line line = locate(offset);
        if (line == null) {
            throw new IllegalArgumentException("offset outside the text: " + offset);
        }
        return line;
    }

    private @Nullable Line locate(int offset) {
        if (offset < 0 || offset > text.length()) {
            return null;
        }
        int lineStart = 0;
        int number = 1;
        while (true) {
            int newline = -1;
            int breakLength = 0;
            for (int index = lineStart; index < text.length(); index++) {
                char current = text.charAt(index);
                if (current == '\n') {
                    newline = index;
                    breakLength = 1;
                    break;
                }
                if (current == '\r') {
                    newline = index;
                    breakLength = index + 1 < text.length() && text.charAt(index + 1) == '\n'
                            ? 2 : 1;
                    break;
                }
            }
            if (newline < 0) {
                if (offset <= text.length()) {
                    return new Line(lineStart, text.length(), number);
                }
                return null;
            }
            if (offset < newline + breakLength) {
                return new Line(lineStart, newline, number);
            }
            lineStart = newline + breakLength;
            number++;
        }
    }

    private record Line(int start, int end, int number) { }
}
