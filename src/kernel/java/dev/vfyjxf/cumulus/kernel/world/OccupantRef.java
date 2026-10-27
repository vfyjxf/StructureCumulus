package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** A snapshot-scoped handle minted in one frame. */
public record OccupantRef(
    SnapshotId snapshot,
    int frame,
    NamespacedId type,
    OccupantKind kind,
    LatticePoint anchor,
    @Nullable NamespacedId body
) implements Comparable<OccupantRef> {

    public OccupantRef {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(anchor, "anchor");
        if (frame < 0) {
            throw new IllegalArgumentException("frame >= 0");
        }
    }

    public OccupantRef(SnapshotId snapshot, int frame, NamespacedId type, OccupantKind kind, LatticePoint anchor) {
        this(snapshot, frame, type, kind, anchor, null);
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
        if (byType != 0) {
            return byType;
        }
        int byAnchor = anchor.compareTo(other.anchor);
        if (byAnchor != 0) {
            return byAnchor;
        }
        if (body == null) {
            return other.body == null ? 0 : -1;
        }
        return other.body == null ? 1 : body.compareTo(other.body);
    }
}
