package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.mc.OccupantTraits;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.NullMarked;

/** The block standard trait providers as capability declarations. */
@NullMarked
public final class BlockTraitProviders {

    public static final TraitProvider<IItemHandler, BlockTraitContext> inventory = CapabilityTraitProvider.of(
        OccupantTraits.inventory,
        TraitHolders.block,
        Capabilities.ItemHandler.BLOCK,
        CapabilityAccesses.block()
    );

    public static final TraitProvider<IEnergyStorage, BlockTraitContext> energy = CapabilityTraitProvider.of(
        OccupantTraits.energy,
        TraitHolders.block,
        Capabilities.EnergyStorage.BLOCK,
        CapabilityAccesses.block()
    );

    public static final TraitProvider<IFluidHandler, BlockTraitContext> fluid = CapabilityTraitProvider
            .of(OccupantTraits.fluid, TraitHolders.block, Capabilities.FluidHandler.BLOCK, CapabilityAccesses.block());

    private BlockTraitProviders() {}

    static void registerAll(TraitRegister register) {
        register.register(inventory);
        register.register(energy);
        register.register(fluid);
    }
}
