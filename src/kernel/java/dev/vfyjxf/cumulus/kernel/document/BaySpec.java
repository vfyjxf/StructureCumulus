package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The data form of a bay. */
public record BaySpec(
    NamespacedId id,
    CuboidSet cells,
    Coverage coverage,
    Footprint footprint,
    Sharing sharing,
    @Nullable String description,
    @Nullable String hint,
    @Nullable SourceSpan source
) {

    public BaySpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(sharing, "sharing");
    }
}
