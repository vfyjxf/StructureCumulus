package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Set;

/**
 * The observation funnel (D-47): every server-side block write passes through {@link #blockChanged}
 * and every block-entity data change through {@link #blockEntityChanged}, both called from mixins on
 * the write methods rather than from intent events.
 */
@NullMarked
public final class CumulusHooks {

    private CumulusHooks() {}

    /**
     * A write actually changed this cell ({@code previous} is the state it replaced). The first
     * three checks are constant-time and shed every write that touches no formed structure; the
     * dispatch runs inside a guard because an exception inside a mixin would kill the tick (D10).
     */
    public static void blockChanged(
        ServerLevel level,
        BlockPos pos,
        BlockState previous,
        BlockState state,
        boolean moving
    ) {
        LevelWatch.blockChanged(level, pos);
        ServerState current = ServerState.current();
        if (current == null) {
            return;
        }
        InstanceIndex index = current.index(level);
        if (index == null) {
            return;
        }
        Set<InstanceId> hits = index.at(pos);
        if (hits == null) {
            return;
        }
        try {
            dispatch(current, level, pos, hits, state.isAir());
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("block change at {} was not handled", pos, failure);
        }
    }

    /** Feeds the watch only: content changes never invalidate the instance itself (D-41). */
    public static void blockEntityChanged(ServerLevel level, BlockPos pos) {
        try {
            LevelWatch.blockChanged(level, pos);
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("block entity change at {} was not handled", pos, failure);
        }
    }

    /**
     * The index is chunk-grained, so a hit is filtered by the instance's real bounds; a dispatched
     * instance leaves the index, which merges the rest of one tick's writes into the first hit.
     */
    private static void dispatch(
        ServerState state,
        ServerLevel level,
        BlockPos pos,
        Set<InstanceId> hits,
        boolean becameAir
    ) {
        StructureManager manager = state.structures(level);
        LatticePoint cell = LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
        for (InstanceId id : List.copyOf(hits)) {
            StructureInstance instance = manager.find(id);
            if (instance == null || !instance.bounds().contains(cell)) {
                continue;
            }
            StructureEvent event = becameAir
                    ? new StructureEvent.Broken(id)
                    : new StructureEvent.Invalidated(id, "a block inside the structure changed");
            WatchEvents.dispatch(manager, event);
            release(state, level, instance);
        }
    }

    private static void release(ServerState state, ServerLevel level, StructureInstance instance) {
        try {
            state.claims(level).release(instance.id());
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("claim release for {} failed", instance.id(), failure);
        }
    }
}
