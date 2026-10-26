package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.Objects;
/** A snapshot-scoped handle minted in one frame. */
public record OccupantRef(SnapshotId snapshot, int frame, NamespacedId type, OccupantKind kind,
                          LatticePoint anchor) implements Comparable<OccupantRef> {

    public OccupantRef {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(anchor, "anchor");
        if (frame < 0) {
            throw new IllegalArgumentException("frame >= 0");
        }
    }

    public boolean from(SnapshotId snapshot) {
        return this.snapshot.equals(snapshot);
    }

    @Override
    public int compareTo(OccupantRef other) {
        int bySnapshot = snapshot.compareTo(other.snapshot);
        if (bySnapshot != 0) {
            return bySnapshot;
        }
        int byFrame = Integer.compare(frame, other.frame);
        if (byFrame != 0) {
            return byFrame;
        }
        int byKind = kind.compareTo(other.kind);
        if (byKind != 0) {
            return byKind;
        }
        int byType = type.compareTo(other.type);
        return byType != 0 ? byType : anchor.compareTo(other.anchor);
    }
}
