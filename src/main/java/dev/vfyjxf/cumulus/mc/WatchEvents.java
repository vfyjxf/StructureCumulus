package dev.vfyjxf.cumulus.mc;

import net.minecraft.core.BlockPos;
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
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, WatchEvents::levelUnloaded);
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
        if (!active(event.getLevel())) {
            return;
        }
        dispatches.incrementAndGet();
        LevelWatch.blockChanged(event.getLevel(), event.getPos());
    }

    static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!active(event.getLevel())) {
            return;
        }
        dispatches.incrementAndGet();
        LevelWatch.blockChanged(event.getLevel(), event.getPos());
    }

    static void explosionDetonated(ExplosionEvent.Detonate event) {
        if (!active(event.getLevel())) {
            return;
        }
        dispatches.incrementAndGet();
        for (BlockPos pos : event.getAffectedBlocks()) {
            LevelWatch.blockChanged(event.getLevel(), pos);
        }
    }

    static void pistonMoved(PistonEvent.Pre event) {
        if (!active(event.getLevel())) {
            return;
        }
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

    static void levelUnloaded(LevelEvent.Unload event) {
        if (!active(event.getLevel())) {
            return;
        }
        dispatches.incrementAndGet();
        LevelWatch.levelUnloaded(event.getLevel());
    }

    private static boolean active(LevelAccessor level) {
        return LevelWatch.active() && level instanceof ServerLevel;
    }
}
