package dev.vfyjxf.cumulus.kernel.finding;

import dev.vfyjxf.cumulus.kernel.core.Checks;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.KeyLocus;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.LocusFrame;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
/** Pure projections of a bundle. */
public final class FindingRenderer {

    private FindingRenderer() {
    }

    public static String human(FindingBundle bundle, RenderContext context) {
        Checks.checkNotNull(bundle, "bundle");
        Checks.checkNotNull(context, "context");
        StringBuilder out = new StringBuilder();
        List<Finding> findings = bundle.findings();
        for (int index = 0; index < findings.size(); index++) {
            appendFinding(out, findings.get(index), context);
            if (index + 1 < findings.size()) {
                out.append('\n');
            }
        }
        if (bundle.elided() + bundle.filtered() > 0) {
            if (!findings.isEmpty()) {
                out.append('\n');
            }
            out.append("= ").append(bundle.elided()).append(" elided, ")
                    .append(bundle.filtered()).append(" filtered\n");
        }
        return out.toString();
    }

    public static String json(FindingBundle bundle) {
        Checks.checkNotNull(bundle, "bundle");
        StringBuilder out = new StringBuilder();
        out.append("{\"schema\":1,\"findings\":[");
        List<Finding> findings = bundle.findings();
        for (int index = 0; index < findings.size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            appendJsonFinding(out, findings.get(index));
        }
        out.append("],\"elided\":").append(bundle.elided())
                .append(",\"filtered\":").append(bundle.filtered()).append('}');
        return out.toString();
    }

    private static void appendFinding(StringBuilder out, Finding finding, RenderContext context) {
        String severity = finding.severity().name().toLowerCase(Locale.ROOT);
        if (context.color() == RenderContext.ColorMode.ANSI) {
            out.append(ansi(finding.severity())).append(severity).append("\u001B[0m");
        } else {
            out.append(severity);
        }
        out.append('[').append(finding.code().text()).append("]: ")
                .append(finding.message()).append('\n');

        int primaryIndex = -1;
        for (int index = 0; index < finding.labels().size(); index++) {
            if (finding.labels().get(index).role() == LabelRole.PRIMARY) {
                primaryIndex = index;
                break;
            }
        }
        if (primaryIndex >= 0) {
            appendPrimary(out, finding.labels().get(primaryIndex), context);
        }
        for (int index = 0; index < finding.labels().size(); index++) {
            if (index == primaryIndex) {
                continue;
            }
            Label label = finding.labels().get(index);
            out.append("  = ").append(label.locus().frame().name().toLowerCase(Locale.ROOT))
                    .append(' ').append(locusText(label.locus())).append(": ")
                    .append(label.message()).append('\n');
        }
        for (Note note : finding.notes()) {
            out.append("  = note: ").append(note.message());
            if (note.locus() != null) {
                out.append(" at ").append(locusText(note.locus()));
            }
            out.append('\n');
        }
        Suggestion suggestion = finding.suggestion();
        if (suggestion != null) {
            out.append("  = help: ").append(suggestion.message());
            if (suggestion.remedy() != null) {
                out.append(" [remedy: ").append(suggestion.remedy().text()).append(']');
            }
            out.append('\n');
        }
    }

    private static void appendPrimary(StringBuilder out, Label label, RenderContext context) {
        Locus locus = label.locus();
        if (locus.frame() == LocusFrame.DEFINITION) {
            SourceSpan span = locus.source();
            if (span == null) {
                return;
            }
            SourceText source = context.sourceFor(span.document());
            if (source != null && source.lineAt(span.start()) != null) {
                out.append(" --> ").append(span.document().text()).append(':')
                        .append(source.lineNumber(span.start())).append(':')
                        .append(source.column(span.start())).append('\n');
                appendSourceFrame(out, source, span, label.message(), context.width());
            } else {
                out.append(" --> ").append(span.document().text()).append(':')
                        .append(span.start()).append("..").append(span.end())
                        .append(": ").append(label.message()).append('\n');
            }
            return;
        }
        out.append(" --> ").append(locus.frame().name().toLowerCase(Locale.ROOT))
                .append(' ').append(locusText(locus)).append(": ")
                .append(label.message()).append('\n');
    }

    private static void appendSourceFrame(StringBuilder out, SourceText source, SourceSpan span,
                                          String message, int width) {
        String line = source.lineAt(span.start());
        if (line == null) {
            return;
        }
        int lineNumber = source.lineNumber(span.start());
        int column = source.column(span.start());
        if (width > 0 && line.length() > width) {
            line = line.substring(0, width);
        }
        int caretStart = Math.min(Math.max(column - 1, 0), line.length());
        int requested = Math.max(1, span.end() - span.start());
        int caretEnd = Math.min(line.length(), caretStart + requested);
        int carets = Math.max(1, caretEnd - caretStart);
        String gutter = " ".repeat(Integer.toString(lineNumber).length());
        out.append(' ').append(gutter).append(" |\n");
        out.append(lineNumber).append(" | ").append(line).append('\n');
        out.append(' ').append(gutter).append(" | ").append(" ".repeat(caretStart))
                .append("^".repeat(carets));
        if (!message.isBlank()) {
            out.append(' ').append(message);
        }
        out.append('\n');
        out.append(' ').append(gutter).append(" |\n");
    }

    private static String locusText(Locus locus) {
        return switch (locus.frame()) {
            case DEFINITION -> "definition " + spanText(locus.source());
            case LOCAL -> "local " + boxesText(locus.cells());
            case WORLD -> "world " + boxesText(locus.cells());
            case KEY -> "key " + keyText(locus.key());
        };
    }

    private static String spanText(@Nullable SourceSpan span) {
        if (span == null) {
            return "?";
        }
        return span.document().text() + ":" + span.start() + ".." + span.end();
    }

    private static String keyText(@Nullable KeyLocus key) {
        if (key == null) {
            return "?";
        }
        return key.key().text() + " @ " + key.instance().world().id().text()
                + "#" + key.instance().serial();
    }

    private static String boxesText(CuboidSet cells) {
        if (cells.isEmpty()) {
            return "empty";
        }
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < cells.boxCount(); index++) {
            if (index > 0) {
                out.append(", ");
            }
            Cuboid box = cells.boxes().get(index);
            out.append('[').append(pointText(box.min().x(), box.min().y(), box.min().z()))
                    .append("..").append(pointText(box.max().x(), box.max().y(), box.max().z()))
                    .append(')');
        }
        return out.toString();
    }

    private static String pointText(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static String ansi(Severity severity) {
        return switch (severity) {
            case ERROR -> "\u001B[31m";
            case WARNING -> "\u001B[33m";
            case INFO -> "\u001B[36m";
        };
    }

    private static void appendJsonFinding(StringBuilder out, Finding finding) {
        out.append("{\"code\":");
        quote(out, finding.code().text());
        out.append(",\"severity\":");
        quote(out, finding.severity().name());
        out.append(",\"phase\":");
        quote(out, finding.phase().name());
        out.append(",\"message\":");
        quote(out, finding.message());
        out.append(",\"labels\":[");
        for (int index = 0; index < finding.labels().size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            appendJsonLabel(out, finding.labels().get(index));
        }
        out.append("],\"notes\":[");
        for (int index = 0; index < finding.notes().size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            Note note = finding.notes().get(index);
            out.append("{\"message\":");
            quote(out, note.message());
            out.append(",\"locus\":");
            appendJsonLocus(out, note.locus());
            out.append('}');
        }
        out.append("],\"suggestion\":");
        Suggestion suggestion = finding.suggestion();
        if (suggestion == null) {
            out.append("null");
        } else {
            out.append("{\"message\":");
            quote(out, suggestion.message());
            out.append(",\"applicability\":");
            quote(out, suggestion.applicability().name());
            out.append(",\"remedy\":");
            appendJsonId(out, suggestion.remedy());
            out.append('}');
        }
        out.append('}');
    }

    private static void appendJsonLabel(StringBuilder out, Label label) {
        out.append("{\"role\":");
        quote(out, label.role().name());
        out.append(",\"frame\":");
        quote(out, label.locus().frame().name());
        out.append(",\"cells\":");
        appendJsonCells(out, label.locus().cells());
        out.append(",\"source\":");
        appendJsonSpan(out, label.locus().source());
        if (label.locus().key() != null) {
            out.append(",\"key\":");
            appendJsonKey(out, label.locus().key());
        }
        out.append(",\"message\":");
        quote(out, label.message());
        out.append('}');
    }

    private static void appendJsonLocus(StringBuilder out, @Nullable Locus locus) {
        if (locus == null) {
            out.append("null");
            return;
        }
        out.append("{\"frame\":");
        quote(out, locus.frame().name());
        out.append(",\"cells\":");
        appendJsonCells(out, locus.cells());
        out.append(",\"source\":");
        appendJsonSpan(out, locus.source());
        out.append(",\"key\":");
        appendJsonKey(out, locus.key());
        out.append('}');
    }

    private static void appendJsonCells(StringBuilder out, CuboidSet cells) {
        out.append('[');
        for (int index = 0; index < cells.boxCount(); index++) {
            if (index > 0) {
                out.append(',');
            }
            Cuboid box = cells.boxes().get(index);
            out.append('[').append(box.min().x()).append(',').append(box.min().y()).append(',')
                    .append(box.min().z()).append(',').append(box.max().x()).append(',')
                    .append(box.max().y()).append(',').append(box.max().z()).append(']');
        }
        out.append(']');
    }

    private static void appendJsonSpan(StringBuilder out, @Nullable SourceSpan span) {
        if (span == null) {
            out.append("null");
            return;
        }
        out.append("{\"document\":");
        quote(out, span.document().text());
        out.append(",\"start\":").append(span.start())
                .append(",\"end\":").append(span.end()).append('}');
    }

    private static void appendJsonKey(StringBuilder out, @Nullable KeyLocus key) {
        if (key == null) {
            out.append("null");
            return;
        }
        out.append("{\"world\":");
        quote(out, key.instance().world().id().text());
        out.append(",\"serial\":").append(key.instance().serial()).append(",\"key\":");
        quote(out, key.key().text());
        out.append('}');
    }

    private static void appendJsonId(StringBuilder out, @Nullable NamespacedId id) {
        if (id == null) {
            out.append("null");
        } else {
            quote(out, id.text());
        }
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (current < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) current));
                    } else {
                        out.append(current);
                    }
                }
            }
        }
        out.append('"');
    }
}
