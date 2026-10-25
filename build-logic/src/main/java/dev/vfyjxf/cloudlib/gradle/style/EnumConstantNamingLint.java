package dev.vfyjxf.cloudlib.gradle.style;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces the project naming rule for enum constants: lowerCamelCase, without underscores.
 * Violations are reported, never auto-fixed — renaming a constant is a semantic change a
 * formatter must not make.
 *
 * <p>The constants are read as the comma-separated list they are, not line by line: enums in
 * these projects routinely declare a whole list on one line ({@code down, up, north, south}),
 * so a line-oriented check would see only the first name.
 */
final class EnumConstantNamingLint {

    /** lowerCamelCase; this is stricter than the PMD rule, which also allows underscores. */
    private static final Pattern LOWER_CAMEL = Pattern.compile("[a-z][a-zA-Z0-9]*");

    private static final Pattern ENUM_HEAD = Pattern.compile("\\benum\\s+[\\w$]+[^{]*\\{");

    /** An annotation, with its optional parenthesised arguments. */
    private static final Pattern ANNOTATION = Pattern.compile("^@[\\w$.]+(?:\\s*\\([^)]*\\))?\\s*");

    private static final Pattern IDENTIFIER = Pattern.compile("^([A-Za-z_$][\\w$]*)");

    private EnumConstantNamingLint() {
    }

    /**
     * Throws {@link IllegalArgumentException} with every violation found;
     * returns {@code raw} untouched when clean.
     */
    static String check(String raw, String fileName) {
        String code = SourceMask.mask(raw);
        List<String> violations = new ArrayList<>();
        Matcher head = ENUM_HEAD.matcher(code);
        while (head.find()) {
            int start = head.end();
            int end = constantRegionEnd(code, start);
            collectViolations(code, start, end, fileName, violations);
        }
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    "enum constants must be lowerCamelCase:\n  " + String.join("\n  ", violations));
        }
        return raw;
    }

    /** The constant region of an enum body: from '{' to the depth-1 ';' or matching '}'. */
    private static int constantRegionEnd(String code, int start) {
        int depth = 1;
        for (int i = start; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            } else if (c == ';' && depth == 1) {
                return i;
            }
        }
        return code.length();
    }

    /** Splits the region at its top-level commas and checks the name each segment declares. */
    private static void collectViolations(String code, int start, int end, String fileName,
            List<String> violations) {
        int segmentStart = start;
        int depth = 0;
        for (int i = start; i <= end; i++) {
            char c = i == end ? ',' : code.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                checkSegment(code, segmentStart, i, fileName, violations);
                segmentStart = i + 1;
            }
        }
    }

    private static void checkSegment(String code, int start, int end, String fileName,
            List<String> violations) {
        String segment = code.substring(start, end);
        int offset = skipWhitespace(segment, 0);
        while (true) {
            Matcher annotation = ANNOTATION.matcher(segment.substring(offset));
            if (!annotation.find()) {
                break;
            }
            offset = skipWhitespace(segment, offset + annotation.end());
        }
        Matcher identifier = IDENTIFIER.matcher(segment.substring(offset));
        if (!identifier.find()) {
            return;
        }
        String name = identifier.group(1);
        if (!LOWER_CAMEL.matcher(name).matches()) {
            int position = start + offset + identifier.start(1);
            violations.add(fileName + ": enum constant '" + name + "' at line "
                    + SourceMask.lineOf(code, position));
        }
    }

    private static int skipWhitespace(String text, int offset) {
        int i = offset;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }
}
