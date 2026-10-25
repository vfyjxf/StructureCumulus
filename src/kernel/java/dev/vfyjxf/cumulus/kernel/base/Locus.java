package dev.vfyjxf.cumulus.kernel.base;

import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.jspecify.annotations.Nullable;

public record Locus(LocusFrame frame, CuboidSet cells, @Nullable KeyLocus key, @Nullable SourceSpan source) {

    public Locus {
        if (frame == LocusFrame.KEY) {
            if (key == null) {
                throw new IllegalArgumentException("a KEY locus carries a key");
            }
            if (!cells.isEmpty()) {
                throw new IllegalArgumentException("a KEY locus carries no cells");
            }
            if (source != null) {
                throw new IllegalArgumentException("a KEY locus carries no source span");
            }
        } else {
            if (key != null) {
                throw new IllegalArgumentException("only a KEY locus carries a key: " + frame);
            }
            if (frame == LocusFrame.DEFINITION && source == null) {
                throw new IllegalArgumentException("a DEFINITION locus needs a source span");
            }
        }
    }

    public static Locus definition(SourceSpan source) {
        return new Locus(LocusFrame.DEFINITION, CuboidSet.empty(), null, source);
    }

    public static Locus local(CuboidSet cells) {
        return new Locus(LocusFrame.LOCAL, cells, null, null);
    }

    public static Locus local(CuboidSet cells, SourceSpan source) {
        return new Locus(LocusFrame.LOCAL, cells, null, source);
    }

    public static Locus world(CuboidSet cells) {
        return new Locus(LocusFrame.WORLD, cells, null, null);
    }

    public static Locus world(CuboidSet cells, SourceSpan source) {
        return new Locus(LocusFrame.WORLD, cells, null, source);
    }

    public static Locus key(InstanceId instance, NamespacedId key) {
        return new Locus(LocusFrame.KEY, CuboidSet.empty(), KeyLocus.of(instance, key), null);
    }

    public Locus withSource(SourceSpan source) {
        return new Locus(frame, cells, key, source);
    }
}
