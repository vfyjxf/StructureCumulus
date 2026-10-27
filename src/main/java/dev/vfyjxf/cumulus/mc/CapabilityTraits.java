package dev.vfyjxf.cumulus.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The production trait lookup: five-argument block queries and context-null entity queries. */
@NullMarked
enum CapabilityTraits implements TraitLookup {
    instance;

    @Override
    public @Nullable IItemHandler itemBlock(
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity
    ) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, null);
    }

    @Override
    public @Nullable IEnergyStorage energyBlock(
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity
    ) {
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, state, blockEntity, null);
    }

    @Override
    public @Nullable IFluidHandler fluidBlock(
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity
    ) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, blockEntity, null);
    }

    @Override
    public @Nullable IItemHandler itemEntity(Entity entity) {
        return entity.getCapability(Capabilities.ItemHandler.ENTITY, null);
    }

    @Override
    public @Nullable IEnergyStorage energyEntity(Entity entity) {
        return entity.getCapability(Capabilities.EnergyStorage.ENTITY, null);
    }

    @Override
    public @Nullable IFluidHandler fluidEntity(Entity entity) {
        return entity.getCapability(Capabilities.FluidHandler.ENTITY, null);
    }
}
