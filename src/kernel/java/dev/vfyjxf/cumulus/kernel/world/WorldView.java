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

    @Nullable WorldView parent();

    int cellSize();

    SectionKey sectionOf(LatticePoint cell);

    SectionSlot slot(SectionKey key);

    @Nullable OccupantSnapshot occupant(LatticePoint cell);

    @Nullable OccupantSnapshot occupant(OccupantRef ref);

    List<OccupantSnapshot> occupants(Cuboid box, OccupantFilter filter);

    @Nullable WorldView nested(LatticePoint cell);

    long contentDigest(SectionKey key);

    default ReadStatus statusOf(LatticePoint cell) {
        return slot(sectionOf(cell)).status();
    }
}
