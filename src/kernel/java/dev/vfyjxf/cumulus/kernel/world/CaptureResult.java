package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;

import java.util.Objects;

/** The three answers of a capture, each carrying the host's own capture report. */
public sealed interface CaptureResult {
    record Captured(WorldView view, SnapshotLease lease, CaptureReport report) implements CaptureResult {
        public Captured {
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(report, "report");
        }

        public Captured(WorldView view, SnapshotLease lease) {
            this(view, lease, CaptureReport.empty());
        }
    }

    record TooLarge(Cuboid bounds, int elements, CaptureReport report) implements CaptureResult {
        public TooLarge {
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(report, "report");
            if (elements < 0) {
                throw new IllegalArgumentException("elements >= 0");
            }
        }

        public TooLarge(Cuboid bounds, int elements) {
            this(bounds, elements, CaptureReport.empty());
        }
    }

    record Unavailable(ReadStatus status, FindingCode code, CaptureReport report) implements CaptureResult {
        public Unavailable {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(report, "report");
        }

        public Unavailable(ReadStatus status, FindingCode code) {
            this(status, code, CaptureReport.empty());
        }
    }
}
