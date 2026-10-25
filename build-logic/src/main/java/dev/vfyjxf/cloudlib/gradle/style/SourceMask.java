package dev.vfyjxf.cloudlib.gradle.style;

/**
 * Text helpers shared by the source-reading lints.
 */
final class SourceMask {

    private SourceMask() {
    }

    /**
     * Replaces comments and literals with spaces, preserving newlines so
     * offsets and line numbers stay stable. Handles {@code //}, {@code /* *\/},
     * {@code "..."}, {@code '...'} and text blocks.
     */
    static String mask(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        int n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            if (c == '/' && i + 1 < n && raw.charAt(i + 1) == '/') {
                while (i < n && raw.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && raw.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(raw.charAt(i) == '*' && i + 1 < n && raw.charAt(i + 1) == '/')) {
                    out.append(raw.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
            } else if (c == '"' && i + 2 < n && raw.charAt(i + 1) == '"' && raw.charAt(i + 2) == '"') {
                out.append("   ");
                i += 3;
                while (i < n && !(raw.charAt(i) == '"' && i + 2 < n && raw.charAt(i + 1) == '"'
                        && raw.charAt(i + 2) == '"')) {
                    out.append(raw.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("   ");
                    i += 3;
                }
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out.append(' ');
                i++;
                while (i < n && raw.charAt(i) != quote) {
                    if (raw.charAt(i) == '\\' && i + 1 < n) {
                        out.append("  ");
                        i += 2;
                    } else {
                        out.append(raw.charAt(i) == '\n' ? '\n' : ' ');
                        i++;
                    }
                }
                if (i < n) {
                    out.append(' ');
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** The 1-based line of {@code offset} in text whose newlines are intact. */
    static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
