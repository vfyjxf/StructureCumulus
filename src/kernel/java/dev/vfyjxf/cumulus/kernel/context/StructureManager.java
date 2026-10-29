package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The host-driven registry of formed structures and their contexts. */
@NullMarked
public interface StructureManager extends ContextStore {

    static StructureManager create() {
        return new InMemoryStructureManager();
    }

    /** Reserves a new instance and its draft context; the host settles through the returned attempt. */
    StructureAttempt begin(World world, StructureSpec spec);

    @Nullable
    StructureInstance find(InstanceId id);

    @Nullable
    StructureInstance find(OwnerId owner, Siting siting);

    /** Every formed instance whose bounds contain the world cell, in id order. */
    List<StructureInstance> findAt(LatticePoint worldCell);

    /** Every formed instance, in id order. */
    List<StructureInstance> instances();

    void onEvent(StructureEvent event);

    /** The manager's world-frame lookup helper; the host may use it for lifecycle detection. */
    default List<StructureInstance> findIn(CuboidSet worldCells) {
        List<StructureInstance> found = new ArrayList<>();
        for (StructureInstance instance : instances()) {
            if (worldCells.intersects(instance.bounds())) {
                found.add(instance);
            }
        }
        return List.copyOf(found);
    }
}
