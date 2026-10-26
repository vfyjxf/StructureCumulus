package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.space.Locus;

import java.util.Objects;

/** A labelled point of interest in an overlay. */
public record Marker(Locus locus, String label, MarkerKind kind) {

    public Marker {
        Objects.requireNonNull(locus, "locus");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(kind, "kind");
    }
}
