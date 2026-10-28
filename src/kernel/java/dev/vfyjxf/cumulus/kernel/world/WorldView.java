package dev.vfyjxf.cumulus.kernel.world;

import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** The immutable, recursive, section-based read face. */
public interface WorldView {

    SnapshotId snapshotId();

    int frame();

    int depth();

    LatticePoint origin();

    @Nullable
    WorldView parent();

    int cellSize();

    Cuboid bounds();

    SnapshotPolicy policy();

    SectionKey sectionOf(LatticePoint cell);

    SectionSlot slot(SectionKey key);

    CellRead read(LatticePoint cell);

    default @Nullable OccupantSnapshot occupant(LatticePoint cell) {
        return read(cell) instanceof CellRead.Found found ? found.occupant() : null;
    }

    default @Nullable ReadRefusal refusalAt(LatticePoint cell) {
        return read(cell) instanceof CellRead.Unreadable unreadable ? unreadable.refusal() : null;
    }

    default ReadStatus statusOf(LatticePoint cell) {
        return read(cell) instanceof CellRead.Unreadable unreadable
                ? unreadable.refusal().status()
                : ReadStatus.readable;
    }

    @Nullable
    OccupantSnapshot occupant(OccupantRef ref);

    default <H> @Nullable H get(LatticePoint pos, HostType<H> type) {
        return null;
    }

    default <H> @Nullable H get(OccupantRef ref, HostType<H> type) {
        return get(ref.anchor(), type);
    }

    List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);

    @Nullable
    WorldView nested(LatticePoint cell);

    long contentDigest(SectionKey key);
}
