package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * Types are referenced by their simple name, not by a dotted path.
 *
 * <p>This is the PMD port of the Spotless lint {@code dev.vfyjxf.cloudlib.gradle.style.FqnLint},
 * and it keeps that lint's lexical route on purpose: the rule masks comments and string literals
 * and then scans the remaining text for {@code pkg.segments.Type}, which stays stable no matter
 * how the parser shapes a dotted name. The judgement is the lint's, unchanged:
 * <ul>
 *   <li>files without a type declaration are skipped, which covers {@code package-info.java} —
 *       its {@code @org.jspecify.annotations.NullMarked} is the one legitimate dotted reference,
 *       and there are 86 of them in CloudLib alone;</li>
 *   <li>{@code import} and {@code package} lines are skipped;</li>
 *   <li>{@code this.}/{@code super.} member chains are not package paths;</li>
 *   <li>a dotted name is allowed when it disambiguates: the simple name belongs to a <em>different</em>
 *       imported type, or to a type declared in the same file;</li>
 *   <li>a line carrying {@code // fqn-ok} is suppressed, for the rare qualification the collision
 *       heuristic cannot see.</li>
 * </ul>
 *
 * <p>Violations carry the whole message themselves, so the {@code message} attribute of the
 * ruleset reference is informational only. Messages therefore stay free of apostrophes and braces,
 * which {@code java.text.MessageFormat} would otherwise reinterpret.
 */
public class FqnRule extends AbstractJavaRulechainRule {

    /** {@code pkg.segments.Type[.Nested]} — needs at least two package-looking segments. */
    private static final String defaultFqnPattern = "\\b(?:[a-z_]\\w*\\.){2,}[A-Z]\\w*(?:\\.[A-Z]\\w*)*";

    private static final Pattern importDeclaration =
            Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+)\\s*;", Pattern.MULTILINE);

    private static final Pattern typeDeclaration =
            Pattern.compile("\\b(?:class|interface|enum|record|@interface)\\s+([A-Z]\\w*)");

    /** The line-level escape hatch, spelled exactly as the Spotless lint spells it. */
    private static final String suppressionMarker = "fqn-ok";

    private final PropertyDescriptor<Pattern> fqnPattern = PropertyFactory
            .regexProperty("fqnPattern")
            .desc("Regex matching a dotted type reference in code, with comments and literals masked out")
            .defaultValue(defaultFqnPattern)
            .build();

    public FqnRule() {
        super(ASTCompilationUnit.class);
        definePropertyDescriptor(fqnPattern);
        setMessage("Reference types by their simple name and keep the optional qualification to an import");
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        RuleContext ctx = asCtx(data);
        String raw = node.getTextDocument().getText().toString();
        String code = mask(raw);
        if (!typeDeclaration.matcher(code).find()) {
            // no type declared: package-info, module-info, or a comment-only file
            return data;
        }
        Map<String, String> imported = importedTypes(code);
        Set<String> declared = declaredTypes(code);
        int[] lineStarts = lineStarts(code);

        Pattern pattern = getProperty(fqnPattern);
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            String fqn = matcher.group();
            if (fqn.startsWith("this.") || fqn.startsWith("super.")) {
                continue; // member access chain, not a package path
            }
            int lineNumber = lineOf(lineStarts, matcher.start());
            String line = rawLine(raw, lineNumber);
            String trimmed = line.strip();
            if (trimmed.startsWith("import ") || trimmed.startsWith("package ")) {
                continue;
            }
            if (line.contains(suppressionMarker)) {
                continue;
            }
            String[] parts = fqn.split("\\.");
            int capitalized = firstCapitalized(parts);
            String outer = String.join(".", List.of(parts).subList(0, capitalized + 1));
            String simpleName = parts[capitalized];
            String bound = imported.get(simpleName);
            if (bound != null && !bound.equals(outer)) {
                continue; // a different type already owns the simple name
            }
            if (bound == null && declared.contains(simpleName)) {
                continue; // the qualification disambiguates against a type declared here
            }
            String reference = String.join(".", List.of(parts).subList(capitalized, parts.length));
            ctx.addViolationWithPosition(node, lineNumber, lineNumber, "Fully qualified type reference " + fqn
                    + ": import " + outer + " and use " + reference
                    + ", or add a // " + suppressionMarker + " comment when the qualification is deliberate");
        }
        return data;
    }

    /** The first capitalized segment — the outer class of a possibly nested type name. */
    private static int firstCapitalized(String[] parts) {
        for (int i = 0; i < parts.length; i++) {
            if (Character.isUpperCase(parts[i].charAt(0))) {
                return i;
            }
        }
        return parts.length - 1;
    }

    /** Non-static imports bind a type's simple name; static imports do not. */
    private static Map<String, String> importedTypes(String code) {
        Map<String, String> imported = new HashMap<>();
        Matcher matcher = importDeclaration.matcher(code);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                continue;
            }
            String fqn = matcher.group(2);
            int dot = fqn.lastIndexOf('.');
            if (dot > 0 && Character.isUpperCase(fqn.charAt(dot + 1))) {
                imported.put(fqn.substring(dot + 1), fqn);
            }
        }
        return imported;
    }

    private static Set<String> declaredTypes(String code) {
        Set<String> declared = new HashSet<>();
        Matcher matcher = typeDeclaration.matcher(code);
        while (matcher.find()) {
            declared.add(matcher.group(1));
        }
        return declared;
    }

    /**
     * Replaces comments and literals with spaces, preserving newlines so that offsets and line
     * numbers stay stable. Handles {@code //}, block comments, {@code "..."}, {@code '...'} and
     * text blocks.
     */
    private static String mask(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        int length = raw.length();
        while (i < length) {
            char c = raw.charAt(i);
            if (c == '/' && i + 1 < length && raw.charAt(i + 1) == '/') {
                while (i < length && raw.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < length && raw.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < length && !(raw.charAt(i) == '*' && i + 1 < length && raw.charAt(i + 1) == '/')) {
                    out.append(raw.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < length) {
                    out.append("  ");
                    i += 2;
                }
            } else if (c == '"' && i + 2 < length && raw.charAt(i + 1) == '"' && raw.charAt(i + 2) == '"') {
                out.append("   ");
                i += 3;
                while (i < length
                        && !(raw.charAt(i) == '"' && i + 2 < length && raw.charAt(i + 1) == '"'
                                && raw.charAt(i + 2) == '"')) {
                    out.append(raw.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < length) {
                    out.append("   ");
                    i += 3;
                }
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out.append(' ');
                i++;
                while (i < length && raw.charAt(i) != quote) {
                    if (raw.charAt(i) == '\\' && i + 1 < length) {
                        out.append("  ");
                        i += 2;
                    } else {
                        out.append(raw.charAt(i) == '\n' ? '\n' : ' ');
                        i++;
                    }
                }
                if (i < length) {
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

    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int lineOf(int[] lineStarts, int offset) {
        int low = 0;
        int high = lineStarts.length - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (lineStarts[middle] <= offset) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low + 1;
    }

    private static String rawLine(String raw, int lineNumber) {
        int start = 0;
        for (int line = 1; line < lineNumber; line++) {
            int newline = raw.indexOf('\n', start);
            if (newline < 0) {
                return "";
            }
            start = newline + 1;
        }
        int end = raw.indexOf('\n', start);
        return end < 0 ? raw.substring(start) : raw.substring(start, end);
    }

}
