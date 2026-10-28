package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import dev.vfyjxf.cumulus.mc.trait.BlockTraitContext;
import dev.vfyjxf.cumulus.mc.trait.CapabilityAccess;
import dev.vfyjxf.cumulus.mc.trait.CapabilityTraitProvider;
import dev.vfyjxf.cumulus.mc.trait.EntityTraitContext;
import dev.vfyjxf.cumulus.mc.trait.TraitCaptureContext;
import dev.vfyjxf.cumulus.mc.trait.TraitHolders;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

@SuppressWarnings("NullAway")
class CapabilityTraitProviderTest {

    private static final NamespacedId customId = NamespacedId.of("test", "capability_value");
    private static final TraitCaptureContext capture = TraitCaptureContext.empty();

    @Test
    void oneProviderClassCoversBlockAndEntity() {
        BaseCapability<IItemHandler, @Nullable Void> capability = new TestCapability<>(IItemHandler.class);
        IItemHandler handler = itemHandler();
        CapabilityTraitProvider<IItemHandler, BlockTraitContext, @Nullable Void> block = CapabilityTraitProvider
                .of(OccupantTraits.inventory, TraitHolders.block, capability, new FixedAccess<>(capability, handler));
        CapabilityTraitProvider<IItemHandler, EntityTraitContext, @Nullable Void> entity = CapabilityTraitProvider
                .of(OccupantTraits.inventory, TraitHolders.entity, capability, new FixedAccess<>(capability, handler));

        assertEquals(OccupantTraits.inventory, block.key());
        assertEquals(TraitHolders.block, block.holder());
        assertEquals(TraitHolders.entity, entity.holder());
        assertSame(handler, value(block.get(blockContext(), capture)));
        assertSame(handler, value(entity.get(entityContext(), capture)));
    }

    @Test
    void aNullCapabilityValueIsAbsent() {
        TraitKey<String> key = new TraitKey<>(customId, String.class);
        BaseCapability<String, @Nullable Void> capability = new TestCapability<>(String.class);
        CapabilityTraitProvider<String, BlockTraitContext, @Nullable Void> provider = CapabilityTraitProvider
                .of(key, TraitHolders.block, capability, new FixedAccess<>(capability, null));

        TraitSlot slot = provider.get(blockContext(), capture);
        assertInstanceOf(TraitSlot.Absent.class, slot);
        assertEquals(customId, slot.id());
    }

    @Test
    void theDirectCapabilityTypesAreTheValues() {
        IItemHandler items = itemHandler();
        IEnergyStorage energy = energyStorage();
        IFluidHandler fluid = fluidHandler();

        BaseCapability<IItemHandler, @Nullable Void> itemCapability = new TestCapability<>(IItemHandler.class);
        BaseCapability<IEnergyStorage, @Nullable Void> energyCapability = new TestCapability<>(IEnergyStorage.class);
        BaseCapability<IFluidHandler, @Nullable Void> fluidCapability = new TestCapability<>(IFluidHandler.class);

        assertSame(
            items,
            value(
                CapabilityTraitProvider.of(
                    OccupantTraits.inventory,
                    TraitHolders.block,
                    itemCapability,
                    new FixedAccess<>(itemCapability, items)
                ).get(blockContext(), capture)
            )
        );
        assertSame(
            energy,
            value(
                CapabilityTraitProvider.of(
                    OccupantTraits.energy,
                    TraitHolders.block,
                    energyCapability,
                    new FixedAccess<>(energyCapability, energy)
                ).get(blockContext(), capture)
            )
        );
        assertSame(
            fluid,
            value(
                CapabilityTraitProvider.of(
                    OccupantTraits.fluid,
                    TraitHolders.block,
                    fluidCapability,
                    new FixedAccess<>(fluidCapability, fluid)
                ).get(blockContext(), capture)
            )
        );
    }

    @Test
    void theAccessorReceivesTheActualHolderAndTheCapture() {
        BaseCapability<String, @Nullable Void> capability = new TestCapability<>(String.class);
        FixedAccess<BlockTraitContext> access = new FixedAccess<>(capability, "value");
        CapabilityTraitProvider<String, BlockTraitContext, @Nullable Void> provider = CapabilityTraitProvider
                .of(new TraitKey<>(customId, String.class), TraitHolders.block, capability, access);
        BlockTraitContext actual = blockContext();

        provider.get(actual, capture);

        assertSame(actual, access.seen);
        assertSame(capture, access.seenCapture);
    }

    private static BlockTraitContext blockContext() {
        return new BlockTraitContext(null, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null);
    }

    private static EntityTraitContext entityContext() {
        return new EntityTraitContext(new Marker(EntityType.MARKER, null));
    }

    private static @Nullable Object value(TraitSlot slot) {
        TraitSlot.Present present = assertInstanceOf(TraitSlot.Present.class, slot);
        return present.trait().value();
    }

    private static IItemHandler itemHandler() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 0;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 0;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return false;
            }
        };
    }

    private static IEnergyStorage energyStorage() {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
                return 0;
            }

            @Override
            public int extractEnergy(int amount, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return 0;
            }

            @Override
            public int getMaxEnergyStored() {
                return 0;
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return false;
            }
        };
    }

    private static IFluidHandler fluidHandler() {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 0;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int tank) {
                return 0;
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return false;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return 0;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return FluidStack.EMPTY;
            }
        };
    }

    private static final class TestCapability<T> extends BaseCapability<T, @Nullable Void> {

        private TestCapability(Class<T> type) {
            super(ResourceLocation.fromNamespaceAndPath("structurecumulus", "test"), type, void.class);
        }
    }

    private static final class FixedAccess<H> implements CapabilityAccess<H, @Nullable Void> {

        private final BaseCapability<?, @Nullable Void> capability;
        private final @Nullable Object value;
        private @Nullable H seen;
        private @Nullable TraitCaptureContext seenCapture;

        private FixedAccess(@Nullable BaseCapability<?, @Nullable Void> capability, @Nullable Object value) {
            this.capability = capability;
            this.value = value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> @Nullable T get(BaseCapability<T, @Nullable Void> requested, H actual, TraitCaptureContext context) {
            seen = actual;
            seenCapture = context;
            if (capability != null && requested != capability) {
                return null;
            }
            return (T) value;
        }
    }
}
