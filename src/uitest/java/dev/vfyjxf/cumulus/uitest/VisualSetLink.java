package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.Objects;

/** One root-to-satellite connector the set fixture exposes to the HUD and the highlight layer. */
public record VisualSetLink(LatticePoint from, LatticePoint to) {

    public VisualSetLink {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
