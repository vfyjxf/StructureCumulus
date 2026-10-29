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
import java.util.Objects;

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

    /** The world-scoped lookup; two worlds may form the same owner/siting. */
    @Nullable
    StructureInstance find(World world, OwnerId owner, Siting siting);

    /**
     * The unambiguous convenience lookup: answers only when exactly one world holds the tuple.
     *
     * @throws IllegalArgumentException when more than one world holds it; use
     *     {@link #find(World, OwnerId, Siting)} instead
     */
    default @Nullable StructureInstance find(OwnerId owner, Siting siting) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(siting, "siting");
        StructureInstance found = null;
        for (StructureInstance instance : instances()) {
            if (instance.owner().equals(owner) && instance.siting().equals(siting)) {
                if (found != null) {
                    throw new IllegalArgumentException(
                        "the (owner, siting) pair is ambiguous across worlds; use find(world, owner, siting)"
                    );
                }
                found = instance;
            }
        }
        return found;
    }

    /** Every formed instance whose bounds contain the world cell, in id order. */
    List<StructureInstance> findAt(LatticePoint worldCell);

    /** Every formed instance, in id order. */
    List<StructureInstance> instances();

    void onEvent(StructureEvent event);

    /** The manager's world-frame lookup helper; the host may use it for lifecycle detection. */
    default List<StructureInstance> findIn(CuboidSet worldCells) {
        Objects.requireNonNull(worldCells, "worldCells");
        List<StructureInstance> found = new ArrayList<>();
        for (StructureInstance instance : instances()) {
            if (worldCells.intersects(instance.bounds())) {
                found.add(instance);
            }
        }
        return List.copyOf(found);
    }
}
