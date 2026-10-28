package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** The document form of one link between a root cell and a satellite cell. */
public record LinkSpec(
    LinkMode mode,
    @Nullable LatticePoint parentCell,
    @Nullable LatticePoint satelliteCell,
    int maxDistance,
    DistanceMetric metric,
    @Nullable SourceSpan source
) {

    public LinkSpec {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(metric, "metric");
        if (maxDistance < 0) {
            throw new IllegalArgumentException("maxDistance >= 0");
        }
        switch (mode) {
            case none -> {
                if (parentCell != null || satelliteCell != null) {
                    throw new IllegalArgumentException("a none link carries no cells");
                }
                if (maxDistance != 0) {
                    throw new IllegalArgumentException("a none link carries no distance");
                }
            }
            case face -> {
                if (parentCell == null || satelliteCell == null) {
                    throw new IllegalArgumentException("a face link needs both cells");
                }
                if (maxDistance != 0) {
                    throw new IllegalArgumentException("a face link carries no distance");
                }
                if (metric != DistanceMetric.chebyshev) {
                    throw new IllegalArgumentException("a face link carries no metric");
                }
            }
            case range -> {
                if (parentCell == null || satelliteCell == null) {
                    throw new IllegalArgumentException("a range link needs both cells");
                }
            }
        }
    }

    public static LinkSpec face(LatticePoint parentCell, LatticePoint satelliteCell) {
        return new LinkSpec(LinkMode.face, parentCell, satelliteCell, 0, DistanceMetric.chebyshev, null);
    }

    public static LinkSpec range(
        LatticePoint parentCell,
        LatticePoint satelliteCell,
        int maxDistance,
        DistanceMetric metric
    ) {
        return new LinkSpec(LinkMode.range, parentCell, satelliteCell, maxDistance, metric, null);
    }

    public static LinkSpec range(LatticePoint parentCell, LatticePoint satelliteCell, int maxDistance) {
        return range(parentCell, satelliteCell, maxDistance, DistanceMetric.chebyshev);
    }

    public static LinkSpec none() {
        return new LinkSpec(LinkMode.none, null, null, 0, DistanceMetric.chebyshev, null);
    }

    public LinkSpec withoutSpans() {
        return this.source == null ? this : new LinkSpec(mode, parentCell, satelliteCell, maxDistance, metric, null);
    }
}
