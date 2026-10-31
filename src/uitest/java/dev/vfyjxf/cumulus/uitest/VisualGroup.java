package dev.vfyjxf.cumulus.uitest;

import java.util.Objects;

/** One visible system group: its owner/system id, display name, palette color and counts. */
public record VisualGroup(String id, String displayName, int color, int instanceCount, int claimCount) {

    public VisualGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        if (instanceCount < 0 || claimCount < 0) {
            throw new IllegalArgumentException("group counts must be non-negative");
        }
    }
}
