package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.Trait;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import dev.vfyjxf.cumulus.mc.OccupantTraits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/** The five standard trait providers over NeoForge capabilities and saved NBT. */
@NullMarked
public final class StandardTraitProviders {

    public static final TraitProvider<List<ItemStack>> inventory = new InventoryProvider();
    public static final TraitProvider<Integer> energy = new EnergyProvider();
    public static final TraitProvider<List<FluidStack>> fluid = new FluidProvider();
    public static final TraitProvider<CompoundTag> blockEntityNbt = new BlockEntityNbtProvider();
    public static final TraitProvider<CompoundTag> entityNbt = new EntityNbtProvider();

    private static final FindingCode unreadableTrait = FindingCode.of(FindingCode.Family.mc, 2);

    private StandardTraitProviders() {}

    static void registerAll(TraitRegister register) {
        register.register(inventory);
        register.register(energy);
        register.register(fluid);
        register.register(blockEntityNbt);
        register.register(entityNbt);
    }

    private static final class InventoryProvider implements TraitProvider<List<ItemStack>> {

        @Override
        public TraitKey<List<ItemStack>> key() {
            return OccupantTraits.inventory;
        }

        @Override
        public TraitSlot readBlock(BlockTraitContext context) {
            if (context.state().hasBlockEntity() && context.blockEntity() == null) {
                return refused(key().id(), ReadStatus.notCaptured);
            }
            IItemHandler handler = context.level().getCapability(
                Capabilities.ItemHandler.BLOCK,
                context.pos(),
                context.state(),
                context.blockEntity(),
                context.capture().capabilityContext()
            );
            return handler == null ? new TraitSlot.Absent(key().id()) : present(key(), inventoryValue(handler));
        }

        @Override
        public TraitSlot readEntity(EntityTraitContext context) {
            IItemHandler handler = context.entity().getCapability(Capabilities.ItemHandler.ENTITY, null);
            return handler == null ? new TraitSlot.Absent(key().id()) : present(key(), inventoryValue(handler));
        }
    }

    private static final class EnergyProvider implements TraitProvider<Integer> {

        @Override
        public TraitKey<Integer> key() {
            return OccupantTraits.energy;
        }

        @Override
        public TraitSlot readBlock(BlockTraitContext context) {
            if (context.state().hasBlockEntity() && context.blockEntity() == null) {
                return refused(key().id(), ReadStatus.notCaptured);
            }
            IEnergyStorage storage = context.level().getCapability(
                Capabilities.EnergyStorage.BLOCK,
                context.pos(),
                context.state(),
                context.blockEntity(),
                context.capture().capabilityContext()
            );
            return storage == null ? new TraitSlot.Absent(key().id()) : present(key(), storage.getEnergyStored());
        }

        @Override
        public TraitSlot readEntity(EntityTraitContext context) {
            IEnergyStorage storage = context.entity().getCapability(Capabilities.EnergyStorage.ENTITY, null);
            return storage == null ? new TraitSlot.Absent(key().id()) : present(key(), storage.getEnergyStored());
        }
    }

    private static final class FluidProvider implements TraitProvider<List<FluidStack>> {

        @Override
        public TraitKey<List<FluidStack>> key() {
            return OccupantTraits.fluid;
        }

        @Override
        public TraitSlot readBlock(BlockTraitContext context) {
            if (context.state().hasBlockEntity() && context.blockEntity() == null) {
                return refused(key().id(), ReadStatus.notCaptured);
            }
            IFluidHandler handler = context.level().getCapability(
                Capabilities.FluidHandler.BLOCK,
                context.pos(),
                context.state(),
                context.blockEntity(),
                context.capture().capabilityContext()
            );
            return handler == null ? new TraitSlot.Absent(key().id()) : present(key(), fluidValue(handler));
        }

        @Override
        public TraitSlot readEntity(EntityTraitContext context) {
            IFluidHandler handler = context.entity().getCapability(Capabilities.FluidHandler.ENTITY, null);
            return handler == null ? new TraitSlot.Absent(key().id()) : present(key(), fluidValue(handler));
        }
    }

    private static final class BlockEntityNbtProvider implements TraitProvider<CompoundTag> {

        @Override
        public TraitKey<CompoundTag> key() {
            return OccupantTraits.blockEntityNbt;
        }

        @Override
        public TraitSlot readBlock(BlockTraitContext context) {
            BlockEntity blockEntity = context.blockEntity();
            if (blockEntity != null) {
                return present(key(), blockEntity.saveWithoutMetadata(context.level().registryAccess()).copy());
            }
            return context.state().hasBlockEntity()
                    ? refused(key().id(), ReadStatus.notCaptured)
                    : new TraitSlot.Absent(key().id());
        }
    }

    private static final class EntityNbtProvider implements TraitProvider<CompoundTag> {

        @Override
        public TraitKey<CompoundTag> key() {
            return OccupantTraits.entityNbt;
        }

        @Override
        public TraitSlot readEntity(EntityTraitContext context) {
            return present(key(), context.entity().saveWithoutId(new CompoundTag()).copy());
        }
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
}
