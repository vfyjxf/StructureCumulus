package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.Objects;

/** A parent-to-child hierarchy link drawn in the owning group's palette color. */
record VisualGroupLink(LatticePoint from, LatticePoint to, int color) {

    VisualGroupLink {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
