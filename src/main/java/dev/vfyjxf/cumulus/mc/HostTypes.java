package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.world.HostType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.NullMarked;

/** The adapter's typed host-read tokens. */
@NullMarked
public final class HostTypes {

    public static final HostType<BlockState> blockState = HostType.of(id("block_state"));
    public static final HostType<FluidState> fluidState = HostType.of(id("fluid_state"));
    public static final HostType<Entity> entity = HostType.of(id("entity"));
    public static final HostType<BlockEntity> blockEntity = HostType.of(id("block_entity"));

    private static NamespacedId id(String path) {
        return NamespacedId.of(Constants.modId, path);
    }

    private HostTypes() {}
}
