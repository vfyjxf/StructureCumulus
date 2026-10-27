package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** The offsets Gson does not expose, recovered from the source text by a small structural scan. */
@NullMarked
final class JsonSpans {

    private static final int maxDepth = 256;

    private final Map<String, SourceSpan> values = new HashMap<>();
    private final Map<String, SourceSpan> names = new HashMap<>();

    static @Nullable JsonSpans scan(NamespacedId document, String text) {
        try {
            return new Scanner(document, text).scan();
        } catch (RuntimeException rejected) {
            return null;
        }
    }

    static String member(String path, String name) {
        return path + '\u0000' + name;
    }

    static String element(String path, int index) {
        return path + '\u0001' + index;
    }

    @Nullable
    SourceSpan value(String path) {
        return values.get(path);
    }

    @Nullable
    SourceSpan name(String path) {
        return names.get(path);
    }

    private static final class Scanner {

        private final NamespacedId document;
        private final String text;
        private final JsonSpans spans = new JsonSpans();
        private int pos;

        private Scanner(NamespacedId document, String text) {
            this.document = document;
            this.text = text;
        }

        private JsonSpans scan() {
            value("$", 0);
            skipWhitespace();
            if (pos != text.length()) {
                throw new IllegalArgumentException("trailing text");
            }
            return spans;
        }

        private void value(String path, int depth) {
            if (depth > maxDepth) {
                throw new IllegalArgumentException("too deep");
            }
            skipWhitespace();
            int start = pos;
            char current = peek();
            switch (current) {
                case '{' -> object(path, depth);
                case '[' -> array(path, depth);
                case '"' -> string();
                case 't' -> literal("true");
                case 'f' -> literal("false");
                case 'n' -> literal("null");
                default -> number();
            }
            spans.values.put(path, new SourceSpan(document, start, pos));
        }

        private void object(String path, int depth) {
            pos++;
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return;
            }
            while (true) {
                skipWhitespace();
                int nameStart = pos;
                if (peek() != '"') {
                    throw new IllegalArgumentException("object key");
                }
                String name = string();
                String member = member(path, name);
                spans.names.put(member, new SourceSpan(document, nameStart, pos));
                skipWhitespace();
                expect(':');
                value(member, depth + 1);
                skipWhitespace();
                if (peek() == '}') {
                    pos++;
                    return;
                }
                expect(',');
            }
        }

        private void array(String path, int depth) {
            pos++;
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return;
            }
            int index = 0;
            while (true) {
                value(element(path, index), depth + 1);
                index++;
                skipWhitespace();
                if (peek() == ']') {
                    pos++;
                    return;
                }
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char current = text.charAt(pos++);
                if (current == '"') {
                    return out.toString();
                }
                if (current != '\\') {
                    out.append(current);
                    continue;
                }
                char escaped = text.charAt(pos++);
                switch (escaped) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw new IllegalArgumentException("short unicode escape");
                        }
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape");
                }
            }
        }

        private void literal(String literal) {
            if (!text.startsWith(literal, pos)) {
                throw new IllegalArgumentException("literal");
            }
            pos += literal.length();
        }

        private void number() {
            if (peek() == '-') {
                pos++;
            }
            if (!digit(peek())) {
                throw new IllegalArgumentException("number");
            }
            while (digit(peek())) {
                pos++;
            }
            if (peek() == '.') {
                pos++;
                if (!digit(peek())) {
                    throw new IllegalArgumentException("number");
                }
                while (digit(peek())) {
                    pos++;
                }
            }
            if (peek() == 'e' || peek() == 'E') {
                pos++;
                if (peek() == '+' || peek() == '-') {
                    pos++;
                }
                if (!digit(peek())) {
                    throw new IllegalArgumentException("number");
                }
                while (digit(peek())) {
                    pos++;
                }
            }
        }

        private void expect(char expected) {
            if (peek() != expected) {
                throw new IllegalArgumentException("expected " + expected);
            }
            pos++;
        }

        private char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        private void skipWhitespace() {
            while (pos < text.length()) {
                char current = text.charAt(pos);
                if (current != ' ' && current != '\t' && current != '\n' && current != '\r') {
                    return;
                }
                pos++;
            }
        }

        private static boolean digit(char value) {
            return value >= '0' && value <= '9';
        }
    }

    private JsonSpans() {}
}
