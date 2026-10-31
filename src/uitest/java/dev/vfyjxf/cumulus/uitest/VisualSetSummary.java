package dev.vfyjxf.cumulus.uitest;

import java.util.List;
import java.util.Objects;

/** Dev-only set metadata for the HUD: the fixture's members, formed members and budget. */
public record VisualSetSummary(
    String id,
    int members,
    int formed,
    int claims,
    int budgetUsed,
    int budgetLimit,
    List<VisualSetLink> links
) {

    public VisualSetSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(links, "links");
        if (members < 0 || formed < 0 || claims < 0 || budgetUsed < 0 || budgetLimit < 0) {
            throw new IllegalArgumentException("set counts must be non-negative");
        }
        links = List.copyOf(links);
    }

    public int linkCount() {
        return links.size();
    }
}
