package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import dev.vfyjxf.cumulus.mc.trait.BlockTraitContext;
import dev.vfyjxf.cumulus.mc.trait.EntityTraitContext;
import dev.vfyjxf.cumulus.mc.trait.TraitCaptureContext;
import dev.vfyjxf.cumulus.mc.trait.TraitHolders;
import dev.vfyjxf.cumulus.mc.trait.TraitLookup;
import dev.vfyjxf.cumulus.mc.trait.TraitProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The adapter's stable trait keys and the generic provider dispatch one capture policy uses. */
@NullMarked
public final class OccupantTraits {

    public static final TraitKey<IItemHandler> inventory = new TraitKey<>(id("inventory"), IItemHandler.class);

    public static final TraitKey<IEnergyStorage> energy = new TraitKey<>(id("energy"), IEnergyStorage.class);

    public static final TraitKey<IFluidHandler> fluid = new TraitKey<>(id("fluid"), IFluidHandler.class);

    private static final FindingCode unreadableTrait = FindingCode.of(FindingCode.Family.mc, 2);

    static List<TraitSlot> block(
        SnapshotPolicy policy,
        TraitLookup lookup,
        TraitCaptureContext capture,
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity
    ) {
        if (policy.traits().isEmpty()) {
            return List.of();
        }
        List<TraitSlot> slots = new ArrayList<>(policy.traits().size());
        for (NamespacedId id : policy.traits()) {
            slots.add(blockSlot(id, lookup, capture, level, pos, state, blockEntity));
        }
        return List.copyOf(slots);
    }

    static List<TraitSlot> entity(
        SnapshotPolicy policy,
        TraitLookup lookup,
        TraitCaptureContext capture,
        Entity entity
    ) {
        if (policy.traits().isEmpty()) {
            return List.of();
        }
        List<TraitSlot> slots = new ArrayList<>(policy.traits().size());
        for (NamespacedId id : policy.traits()) {
            slots.add(entitySlot(id, lookup, capture, entity));
        }
        return List.copyOf(slots);
    }

    private static TraitSlot blockSlot(
        NamespacedId id,
        TraitLookup lookup,
        TraitCaptureContext capture,
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity
    ) {
        TraitProvider<?, BlockTraitContext> provider = lookup.provider(id, TraitHolders.block);
        if (provider == null) {
            return absentOrDenied(lookup, id);
        }
        if (state.hasBlockEntity() && blockEntity == null) {
            return refused(id, ReadStatus.notCaptured);
        }
        return read(provider, id, new BlockTraitContext(level, pos, state, blockEntity), capture);
    }

    private static TraitSlot entitySlot(
        NamespacedId id,
        TraitLookup lookup,
        TraitCaptureContext capture,
        Entity entity
    ) {
        TraitProvider<?, EntityTraitContext> provider = lookup.provider(id, TraitHolders.entity);
        if (provider == null) {
            return absentOrDenied(lookup, id);
        }
        return read(provider, id, new EntityTraitContext(entity), capture);
    }

    private static <H> TraitSlot read(
        TraitProvider<?, H> provider,
        NamespacedId id,
        H actual,
        TraitCaptureContext capture
    ) {
        try {
            TraitSlot slot = provider.get(actual, capture);
            return slot == null || !slot.id().equals(id) ? refused(id, ReadStatus.failed) : slot;
        } catch (RuntimeException | LinkageError failure) {
            return refused(id, ReadStatus.failed);
        }
    }

    private static TraitSlot absentOrDenied(TraitLookup lookup, NamespacedId id) {
        return lookup.knows(id) ? new TraitSlot.Absent(id) : refused(id, ReadStatus.denied);
    }

    private static TraitSlot refused(NamespacedId id, ReadStatus status) {
        return new TraitSlot.Refused(id, new ReadRefusal(status, unreadableTrait));
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }

    private OccupantTraits() {}
}
