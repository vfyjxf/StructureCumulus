package dev.vfyjxf.cumulus.mc;

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

/** The package-private trait-capability seam: fake in tests, capabilities in production. */
@NullMarked
interface TraitLookup {

    @Nullable
    IItemHandler itemBlock(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity);

    @Nullable
    IEnergyStorage energyBlock(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity);

    @Nullable
    IFluidHandler fluidBlock(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity);

    @Nullable
    IItemHandler itemEntity(Entity entity);

    @Nullable
    IEnergyStorage energyEntity(Entity entity);

    @Nullable
    IFluidHandler fluidEntity(Entity entity);

    static TraitLookup capabilities() {
        return CapabilityTraits.instance;
    }
}
