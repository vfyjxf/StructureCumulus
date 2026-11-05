package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The remaining main-bus listeners: entities live outside {@code LevelChunk#setBlockState}, so
 * their join and leave stay evented; block observation moved to the funnel (D-47).
 */
@NullMarked
public final class WatchEvents {

    private static final AtomicLong dispatches = new AtomicLong();

    private WatchEvents() {}

    /** Registers the server-side mark listeners once, from the mod constructor. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, WatchEvents::entityJoined);
        NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, WatchEvents::entityLeft);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, WatchEvents::levelLoaded);
    }

    static long dispatchCount() {
        return dispatches.get();
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

    static void dispatch(StructureManager manager, StructureEvent event) {
        try {
            manager.onEvent(event);
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("structure lifecycle event {} was rejected", event, failure);
        }
    }

    private static boolean active(LevelAccessor level) {
        return LevelWatch.active() && level instanceof ServerLevel;
    }
}
