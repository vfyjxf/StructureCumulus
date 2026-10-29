package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
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
                dispatch(state.structures(level), new StructureEvent.Loaded(state.world(level), List.of()));
            }
        }
    }

    static void levelUnloaded(LevelEvent.Unload event) {
        if (active(event.getLevel())) {
            dispatches.incrementAndGet();
            LevelWatch.levelUnloaded(event.getLevel());
        }
        if (event.getLevel() instanceof ServerLevel level) {
            ServerState state = ServerState.current();
            if (state != null) {
                dispatch(state.structures(level), new StructureEvent.Unloaded(state.world(level)));
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
            boolean hasClaim = false;
            boolean allMoved = true;
            Cuboid claimBounds = instance.bounds().bounds();
            if (claimBounds == null) {
                dispatch(manager, new StructureEvent.Invalidated(instance.id(), "the structure has no bounds"));
                release(level, instance);
                continue;
            }
            ClaimView claims = state.claims(level).read(claimBounds);
            for (ClaimRow row : claims.rows()) {
                if (!row.owner().equals(instance.owner()) || !row.siting().equals(instance.siting())) {
                    continue;
                }
                hasClaim = true;
                LatticePoint cell = row.cell().point();
                BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
                if (!level.getBlockState(pos).isAir() && !pushed.contains(cell)) {
                    allMoved = false;
                    break;
                }
            }
            CuboidSet movedBounds = instance.bounds().translate(dx, dy, dz);
            LatticePoint movedOrigin = instance.siting().origin().offset(dx, dy, dz);
            if (!hasClaim || !allMoved || movedBounds == null || movedOrigin == null) {
                dispatch(
                    manager,
                    new StructureEvent.Invalidated(instance.id(), "a piston moved only part of the structure")
                );
                release(level, instance);
                continue;
            }
            Siting movedSiting = instance.siting().withOrigin(movedOrigin);
            StructureEvent moved = new StructureEvent.Moved(instance.id(), instance.siting(), movedSiting, movedBounds);
            if (!dispatchChecked(manager, moved)) {
                continue;
            }
            if (!moveClaims(level, instance, movedSiting, dx, dy, dz)) {
                dispatch(manager, new StructureEvent.Invalidated(instance.id(), "the moved claims conflict"));
                release(level, instance);
                release(level, instance.owner(), movedSiting);
                continue;
            }
        }
    }

    private static boolean moveClaims(
        ServerLevel level,
        StructureInstance instance,
        Siting to,
        int dx,
        int dy,
        int dz
    ) {
        ServerState state = ServerState.current();
        if (state == null) {
            return false;
        }
        Cuboid oldBounds = instance.bounds().bounds();
        if (oldBounds == null) {
            return true;
        }
        SavedClaims ledger = state.claims(level);
        ClaimView old = ledger.read(oldBounds);
        List<ClaimRow> moved = new ArrayList<>();
        for (ClaimRow row : old.rows()) {
            if (!row.owner().equals(instance.owner()) || !row.siting().equals(instance.siting())) {
                continue;
            }
            LatticePoint cell = row.cell().point();
            LatticePoint next = cell.offset(dx, dy, dz);
            if (next == null) {
                return false;
            }
            moved.add(new ClaimRow(LatticeKey.of(next), row.sharing(), row.owner(), to, row.bay()));
        }
        if (moved.isEmpty()) {
            return true;
        }
        ClaimVerdict verdict = ledger.settle(new SettleRequest(instance.owner(), to, moved, ledger.revision()));
        if (!(verdict instanceof ClaimVerdict.Settled)) {
            return false;
        }
        try {
            ledger.release(instance.owner(), instance.siting());
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("claim release for moved structure {} failed", instance.id(), failure);
        }
        return true;
    }

    private static void release(ServerLevel level, StructureInstance instance) {
        release(level, instance.owner(), instance.siting());
    }

    private static void release(ServerLevel level, OwnerId owner, Siting siting) {
        ServerState state = ServerState.current();
        if (state == null) {
            return;
        }
        try {
            state.claims(level).release(owner, siting);
        } catch (RuntimeException failure) {
            StructureCumulus.logger.warn("claim release for {} at {} failed", owner, siting, failure);
        }
    }

    private static void dispatch(StructureManager manager, StructureEvent event) {
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
