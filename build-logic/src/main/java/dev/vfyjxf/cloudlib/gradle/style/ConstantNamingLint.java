package dev.vfyjxf.cloudlib.gradle.style;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces the project naming rule for {@code static final} fields: lowerCamelCase.
 * Violations are reported, never auto-fixed — renaming is a semantic change a formatter must
 * not make. Enum constants are handled by {@link EnumConstantNamingLint}.
 */
final class ConstantNamingLint {

    /** {@code static final <type> NAME} with an UPPER_SNAKE name, single letters included. */
    private static final Pattern FIELD =
            Pattern.compile("\\bstatic\\s+final\\s+(?:[\\w<>\\[\\],.?]+\\s+)+([A-Z][A-Z0-9_]*)\\b");

    private ConstantNamingLint() {
    }

    /**
     * Throws {@link IllegalArgumentException} with every violation found;
     * returns {@code raw} untouched when clean.
     */
    static String check(String raw, String fileName) {
        List<String> violations = new ArrayList<>();
        Matcher m = FIELD.matcher(raw);
        while (m.find()) {
            violations.add(fileName + ": field '" + m.group(1) + "' at line " + lineOf(raw, m.start()));
        }
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    "static final fields must be lowerCamelCase:\n  " + String.join("\n  ", violations));
        }
        return raw;
    }

    private static int lineOf(String raw, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < raw.length(); i++) {
            if (raw.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
