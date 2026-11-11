package dev.vfyjxf.cumulus.mc.build;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** A target block state plus the block-entity data to load into it, if any. */
public record BlockPlacement(BlockState state, @Nullable CompoundTag blockEntityData) {

    public BlockPlacement {
        Objects.requireNonNull(state, "state");
    }

    public boolean isAir() {
        return state.isAir();
    }
}
