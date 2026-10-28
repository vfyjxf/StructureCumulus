package dev.vfyjxf.cumulus.mc.trait;

import dev.vfyjxf.cumulus.mc.OccupantTraits;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.NullMarked;

/** The entity standard trait providers as capability declarations. */
@NullMarked
public final class EntityTraitProviders {

    public static final TraitProvider<IItemHandler, EntityTraitContext> inventory = CapabilityTraitProvider.of(
        OccupantTraits.inventory,
        TraitHolders.entity,
        Capabilities.ItemHandler.ENTITY,
        CapabilityAccesses.entityVoid()
    );

    public static final TraitProvider<IEnergyStorage, EntityTraitContext> energy = CapabilityTraitProvider.of(
        OccupantTraits.energy,
        TraitHolders.entity,
        Capabilities.EnergyStorage.ENTITY,
        CapabilityAccesses.entity()
    );

    public static final TraitProvider<IFluidHandler, EntityTraitContext> fluid = CapabilityTraitProvider.of(
        OccupantTraits.fluid,
        TraitHolders.entity,
        Capabilities.FluidHandler.ENTITY,
        CapabilityAccesses.entity()
    );

    private EntityTraitProviders() {}

    static void registerAll(TraitRegister register) {
        register.register(inventory);
        register.register(energy);
        register.register(fluid);
    }
}
