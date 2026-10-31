package dev.vfyjxf.cumulus.uitest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The HUD's structured text model: summary, detail, groups, hierarchy, flow and legend sections. */
public record VisualHudModel(
    String title,
    List<String> summaryLines,
    List<String> detailLines,
    List<String> groupLines,
    List<String> hierarchyLines,
    List<String> inspectLines,
    List<String> flowLines,
    List<String> legendLines,
    boolean compact
) {

    public VisualHudModel {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summaryLines, "summaryLines");
        Objects.requireNonNull(detailLines, "detailLines");
        Objects.requireNonNull(groupLines, "groupLines");
        Objects.requireNonNull(hierarchyLines, "hierarchyLines");
        Objects.requireNonNull(inspectLines, "inspectLines");
        Objects.requireNonNull(flowLines, "flowLines");
        Objects.requireNonNull(legendLines, "legendLines");
        summaryLines = List.copyOf(summaryLines);
        detailLines = List.copyOf(detailLines);
        groupLines = List.copyOf(groupLines);
        hierarchyLines = List.copyOf(hierarchyLines);
        inspectLines = List.copyOf(inspectLines);
        flowLines = List.copyOf(flowLines);
        legendLines = List.copyOf(legendLines);
    }

    public static VisualHudModel empty() {
        return new VisualHudModel(
            "StructureCumulus 可视化",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            false
        );
    }

    /** The flat text the scenarios and captures assert on, honouring the compact toggle. */
    public List<String> flattened() {
        List<String> lines = new ArrayList<>();
        lines.add(title);
        lines.addAll(summaryLines);
        if (compact) {
            lines.addAll(flowLines);
            lines.addAll(inspectLines);
            if (!detailLines.isEmpty()) {
                lines.add(detailLines.get(0));
            }
        } else {
            lines.addAll(groupLines);
            lines.addAll(hierarchyLines);
            lines.addAll(detailLines);
            lines.addAll(flowLines);
            lines.addAll(inspectLines);
            lines.addAll(legendLines);
        }
        return List.copyOf(lines);
    }
}
