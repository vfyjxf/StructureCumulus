package dev.vfyjxf.cumulus.kernel.space;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
/** Where a finding points. */
public record Locus(LocusFrame frame, CuboidSet cells, @Nullable KeyLocus key,
                    @Nullable SourceSpan source) {

    public Locus {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(cells, "cells");
        switch (frame) {
            case DEFINITION -> {
                if (source == null) {
                    throw new IllegalArgumentException("a DEFINITION locus requires a source span");
                }
                if (key != null) {
                    throw new IllegalArgumentException("a DEFINITION locus carries no key");
                }
            }
            case LOCAL, WORLD -> {
                if (source != null) {
                    throw new IllegalArgumentException("a cell locus carries no source span");
                }
                if (key != null) {
                    throw new IllegalArgumentException("a cell locus carries no key");
                }
                if (cells.isEmpty()) {
                    throw new IllegalArgumentException("a cell locus requires cells");
                }
            }
            case KEY -> {
                if (key == null) {
                    throw new IllegalArgumentException("a KEY locus requires a key");
                }
                if (source != null) {
                    throw new IllegalArgumentException("a KEY locus carries no source span");
                }
                if (!cells.isEmpty()) {
                    throw new IllegalArgumentException("a KEY locus carries no cells");
                }
            }
        }
    }

    public static Locus definition(SourceSpan source) {
        return new Locus(LocusFrame.DEFINITION, CuboidSet.empty(), null, source);
    }

    public static Locus definition(CuboidSet cells, SourceSpan source) {
        return new Locus(LocusFrame.DEFINITION, cells, null, source);
    }

    public static Locus local(CuboidSet cells) {
        return new Locus(LocusFrame.LOCAL, cells, null, null);
    }

    public static Locus world(CuboidSet cells) {
        return new Locus(LocusFrame.WORLD, cells, null, null);
    }

    public static Locus key(InstanceId instance, NamespacedId key) {
        return new Locus(LocusFrame.KEY, CuboidSet.empty(), new KeyLocus(instance, key), null);
    }

    public boolean hasCells() {
        return !cells.isEmpty();
    }

    @Override
    public String toString() {
        return frame + (key != null ? "(" + key + ")" : "") + (source != null ? "@" + source : "")
                + (cells.isEmpty() ? "" : cells.toString());
    }
}
