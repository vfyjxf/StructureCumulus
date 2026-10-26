package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;

import java.util.Objects;

/** The three answers of a capture. */
public sealed interface CaptureResult {
    record Captured(WorldView view, SnapshotLease lease) implements CaptureResult {
        public Captured {
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(lease, "lease");
        }
    }

    record TooLarge(Cuboid bounds, int elements) implements CaptureResult {
        public TooLarge {
            Objects.requireNonNull(bounds, "bounds");
            if (elements < 0) {
                throw new IllegalArgumentException("elements >= 0");
            }
        }
    }

    record Unavailable(ReadStatus status, FindingCode code) implements CaptureResult {
        public Unavailable {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(code, "code");
        }
    }
}
