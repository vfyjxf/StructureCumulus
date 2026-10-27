package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.Trait;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The adapter's stable trait keys and the four-state slots one capture policy requests. */
@NullMarked
public final class OccupantTraits {

    public static final TraitKey<List<ItemStack>> inventory = new TraitKey<>(id("inventory"), listClass());
    public static final TraitKey<Integer> energy = new TraitKey<>(id("energy"), Integer.class);
    public static final TraitKey<List<FluidStack>> fluid = new TraitKey<>(id("fluid"), listClass());
    public static final TraitKey<CompoundTag> blockEntityNbt = new TraitKey<>(
        id("block_entity_nbt"),
        CompoundTag.class
    );
    public static final TraitKey<CompoundTag> entityNbt = new TraitKey<>(id("entity_nbt"), CompoundTag.class);

    private static final FindingCode unreadableTrait = FindingCode.of(FindingCode.Family.mc, 2);

    static List<TraitSlot> block(
        SnapshotPolicy policy,
        TraitLookup lookup,
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
            slots.add(blockSlot(id, lookup, level, pos, state, blockEntity));
        }
        return List.copyOf(slots);
    }

    static List<TraitSlot> entity(SnapshotPolicy policy, TraitLookup lookup, Entity entity) {
        if (policy.traits().isEmpty()) {
            return List.of();
        }
        List<TraitSlot> slots = new ArrayList<>(policy.traits().size());
        for (NamespacedId id : policy.traits()) {
            slots.add(entitySlot(id, lookup, entity));
        }
        return List.copyOf(slots);
    }

    private static TraitSlot blockSlot(
        NamespacedId id,
        TraitLookup lookup,
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity
    ) {
        try {
            if (entityNbt.id().equals(id)) {
                return new TraitSlot.Absent(id);
            }
            if (blockEntityNbt.id().equals(id)) {
                return blockEntitySlot(id, level, state, blockEntity);
            }
            if (!isCapabilityTrait(id)) {
                return refused(id, ReadStatus.denied);
            }
            if (state.hasBlockEntity() && blockEntity == null) {
                return refused(id, ReadStatus.notCaptured);
            }
            if (inventory.id().equals(id)) {
                IItemHandler handler = lookup.itemBlock(level, pos, state, blockEntity);
                return handler == null ? new TraitSlot.Absent(id) : present(inventory, inventoryValue(handler));
            }
            if (energy.id().equals(id)) {
                IEnergyStorage storage = lookup.energyBlock(level, pos, state, blockEntity);
                return storage == null ? new TraitSlot.Absent(id) : present(energy, storage.getEnergyStored());
            }
            IFluidHandler handler = lookup.fluidBlock(level, pos, state, blockEntity);
            return handler == null ? new TraitSlot.Absent(id) : present(fluid, fluidValue(handler));
        } catch (RuntimeException failure) {
            return refused(id, ReadStatus.failed);
        }
    }

    private static TraitSlot entitySlot(NamespacedId id, TraitLookup lookup, Entity entity) {
        try {
            if (entityNbt.id().equals(id)) {
                return present(entityNbt, entity.saveWithoutId(new CompoundTag()).copy());
            }
            if (blockEntityNbt.id().equals(id)) {
                return new TraitSlot.Absent(id);
            }
            if (inventory.id().equals(id)) {
                IItemHandler handler = lookup.itemEntity(entity);
                return handler == null ? new TraitSlot.Absent(id) : present(inventory, inventoryValue(handler));
            }
            if (energy.id().equals(id)) {
                IEnergyStorage storage = lookup.energyEntity(entity);
                return storage == null ? new TraitSlot.Absent(id) : present(energy, storage.getEnergyStored());
            }
            if (fluid.id().equals(id)) {
                IFluidHandler handler = lookup.fluidEntity(entity);
                return handler == null ? new TraitSlot.Absent(id) : present(fluid, fluidValue(handler));
            }
            return refused(id, ReadStatus.denied);
        } catch (RuntimeException failure) {
            return refused(id, ReadStatus.failed);
        }
    }

    private static TraitSlot blockEntitySlot(
        NamespacedId id,
        ServerLevel level,
        BlockState state,
        @Nullable BlockEntity blockEntity
    ) {
        if (blockEntity != null) {
            return present(blockEntityNbt, blockEntity.saveWithoutMetadata(level.registryAccess()).copy());
        }
        return state.hasBlockEntity() ? refused(id, ReadStatus.notCaptured) : new TraitSlot.Absent(id);
    }

    private static boolean isCapabilityTrait(NamespacedId id) {
        return inventory.id().equals(id) || energy.id().equals(id) || fluid.id().equals(id);
    }

    private static List<ItemStack> inventoryValue(IItemHandler handler) {
        int slots = handler.getSlots();
        if (slots < 0) {
            throw new IllegalStateException("negative slot count " + slots);
        }
        List<ItemStack> stacks = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack == null) {
                throw new IllegalStateException("null stack in slot " + slot);
            }
            stacks.add(stack.copy());
        }
        return List.copyOf(stacks);
    }

    private static List<FluidStack> fluidValue(IFluidHandler handler) {
        int tanks = handler.getTanks();
        if (tanks < 0) {
            throw new IllegalStateException("negative tank count " + tanks);
        }
        List<FluidStack> stacks = new ArrayList<>(tanks);
        for (int tank = 0; tank < tanks; tank++) {
            FluidStack stack = handler.getFluidInTank(tank);
            if (stack == null) {
                throw new IllegalStateException("null fluid in tank " + tank);
            }
            stacks.add(stack.copy());
        }
        return List.copyOf(stacks);
    }

    private static <T> TraitSlot present(TraitKey<T> key, T value) {
        return new TraitSlot.Present(Trait.of(key, value));
    }

    private static TraitSlot refused(NamespacedId id, ReadStatus status) {
        return new TraitSlot.Refused(id, new ReadRefusal(status, unreadableTrait));
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<List<T>> listClass() {
        return (Class<List<T>>) (Class<?>) List.class;
    }

    private OccupantTraits() {}
}
