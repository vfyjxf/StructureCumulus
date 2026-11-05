package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** The main-bus listeners that edge the mark counter; no listener does work unless a watch is active. */
@NullMarked
public final class WatchEvents {

    private static final AtomicLong dispatches = new AtomicLong();

    private WatchEvents() {}

    /** Registers the server-side mark listeners once, from the mod constructor. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(true, BlockEvent.NeighborNotifyEvent.class, WatchEvents::neighborNotified);
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, WatchEvents::blockBroken);
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, WatchEvents::blockPlaced);
        NeoForge.EVENT_BUS.addListener(ExplosionEvent.Detonate.class, WatchEvents::explosionDetonated);
        NeoForge.EVENT_BUS.addListener(PistonEvent.Pre.class, WatchEvents::pistonMoved);
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, WatchEvents::chunkUnloaded);
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, WatchEvents::entityJoined);
        NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, WatchEvents::entityLeft);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, WatchEvents::levelLoaded);
    }

    static long dispatchCount() {
        return dispatches.get();
    }

    static void neighborNotified(BlockEvent.NeighborNotifyEvent event) {
        if (!active(event.getLevel())) {
            return;
        }
        dispatches.incrementAndGet();
        LevelWatch.blockChanged(event.getLevel(), event.getPos());
    }

    static void blockBroken(BlockEvent.BreakEvent event) {
        if (active(event.getLevel())) {
            dispatches.incrementAndGet();
            LevelWatch.blockChanged(event.getLevel(), event.getPos());
        }
        if (event.getLevel() instanceof ServerLevel level) {
            broken(level, List.of(event.getPos()));
        }
    }

    static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (active(event.getLevel())) {
            dispatches.incrementAndGet();
            LevelWatch.blockChanged(event.getLevel(), event.getPos());
        }
        if (event.getLevel() instanceof ServerLevel level) {
            invalidated(level, List.of(event.getPos()), "a block was placed inside the structure");
        }
    }

    static void explosionDetonated(ExplosionEvent.Detonate event) {
        if (active(event.getLevel())) {
            dispatches.incrementAndGet();
            for (BlockPos pos : event.getAffectedBlocks()) {
                LevelWatch.blockChanged(event.getLevel(), pos);
            }
        }
        if (event.getLevel() instanceof ServerLevel level) {
            broken(level, event.getAffectedBlocks());
        }
    }

    static void pistonMoved(PistonEvent.Pre event) {
        if (active(event.getLevel())) {
            dispatches.incrementAndGet();
            LevelWatch.blockChanged(event.getLevel(), event.getPos());
            PistonStructureResolver resolver = event.getStructureHelper();
            if (resolver != null && resolver.resolve()) {
                for (BlockPos pos : resolver.getToPush()) {
                    LevelWatch.blockChanged(event.getLevel(), pos);
                }
                for (BlockPos pos : resolver.getToDestroy()) {
                    LevelWatch.blockChanged(event.getLevel(), pos);
                }
            }
        }
        if (event.getLevel() instanceof ServerLevel level) {
            pistonStructures(level, event);
        }
    }

    static void chunkUnloaded(ChunkEvent.Unload event) {
        if (!active(event.getLevel())) {
            return;
        }
        dispatches.incrementAndGet();
        LevelWatch.chunkUnloaded(event.getLevel(), event.getChunk().getPos());
    }

    static void entityJoined(EntityJoinLevelEvent event) {
        if (!active(event.getLevel())) {
            return;
        }
        dispatches.incrementAndGet();
        LevelWatch.entityChanged(event.getLevel(), event.getEntity(), true);
    }

    static void entityLeft(EntityLeaveLevelEvent event) {
        if (!active(event.getLevel())) {
            return;
        }
        dispatches.incrementAndGet();
        LevelWatch.entityChanged(event.getLevel(), event.getEntity(), false);
    }

    static void levelLoaded(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ServerState state = ServerState.current();
            if (state != null) {
                state.openLevel(level);
                dispatch(state.structures(level), new StructureEvent.Loaded(state.world(level), List.of()));
            }
        }
    }

    private static void broken(ServerLevel level, List<BlockPos> positions) {
        ServerState state = ServerState.current();
        if (state == null) {
            return;
        }
        StructureManager manager = state.structures(level);
        Set<InstanceId> seen = new HashSet<>();
        for (BlockPos pos : positions) {
            LatticePoint cell = LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
            for (StructureInstance instance : manager.findAt(cell)) {
                if (seen.add(instance.id())) {
                    dispatch(manager, new StructureEvent.Broken(instance.id()));
                    release(level, instance);
                }
            }
        }
    }

    private static void invalidated(ServerLevel level, List<BlockPos> positions, String reason) {
        ServerState state = ServerState.current();
        if (state == null) {
            return;
        }
        StructureManager manager = state.structures(level);
        Set<InstanceId> seen = new HashSet<>();
        for (BlockPos pos : positions) {
            LatticePoint cell = LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
            for (StructureInstance instance : manager.findAt(cell)) {
                if (seen.add(instance.id())) {
                    dispatch(manager, new StructureEvent.Invalidated(instance.id(), reason));
                    release(level, instance);
                }
            }
        }
    }

    private static void pistonStructures(ServerLevel level, PistonEvent.Pre event) {
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        ServerState state = ServerState.current();
        if (state == null) {
            return;
        }
        StructureManager manager = state.structures(level);
        Set<LatticePoint> pushed = new HashSet<>();
        for (BlockPos pos : resolver.getToPush()) {
            pushed.add(LatticePoint.of(pos.getX(), pos.getY(), pos.getZ()));
        }
        Set<LatticePoint> destroyed = new HashSet<>();
        for (BlockPos pos : resolver.getToDestroy()) {
            destroyed.add(LatticePoint.of(pos.getX(), pos.getY(), pos.getZ()));
        }
        List<Cuboid> affected = new ArrayList<>();
        for (LatticePoint cell : pushed) {
            affected.add(Cuboid.of(cell));
        }
        for (LatticePoint cell : destroyed) {
            affected.add(Cuboid.of(cell));
        }
        if (affected.isEmpty()) {
            return;
        }
        Direction direction = event.getDirection();
        int dx = direction.getStepX();
        int dy = direction.getStepY();
        int dz = direction.getStepZ();
        for (StructureInstance instance : manager.findIn(CuboidSet.of(affected))) {
            boolean destroyedPart = false;
            for (Cuboid box : instance.bounds().boxes()) {
                for (LatticePoint cell : destroyed) {
                    if (box.contains(cell)) {
                        destroyedPart = true;
                        break;
                    }
                }
                if (destroyedPart) {
                    break;
                }
            }
            if (destroyedPart) {
                dispatch(
                    manager,
                    new StructureEvent.Invalidated(instance.id(), "a piston destroyed part of the structure")
                );
                release(level, instance);
                continue;
            }
            dispatch(
                manager,
                new StructureEvent.Invalidated(instance.id(), "a piston moved the structure")
            );
            release(level, instance);
        }
    }

    private static void release(ServerLevel level, StructureInstance instance) {
        ServerState state = ServerState.current();
        if (state == null) {
            return;
        }
        try {
            state.claims(level).release(instance.id());
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("claim release for {} failed", instance.id(), failure);
        }
    }

    static void dispatch(StructureManager manager, StructureEvent event) {
        dispatchChecked(manager, event);
    }

    private static boolean dispatchChecked(StructureManager manager, StructureEvent event) {
        try {
            manager.onEvent(event);
            return true;
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("structure lifecycle event {} was rejected", event, failure);
            return false;
        }
    }

    private static boolean active(LevelAccessor level) {
        return LevelWatch.active() && level instanceof ServerLevel;
    }
}
