package dev.vfyjxf.cumulus.mc.preview.scene;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Rebuilds a payload's block state on the client. The payload is the only source: no level read, no
 * block-entity data and no lookup the server did not ship.
 */
public final class CellStates {

    private CellStates() {}

    public static @Nullable BlockState of(NamespacedId block, Map<String, String> properties) {
        Block found = BuiltInRegistries.BLOCK
                .getOptional(ResourceLocation.fromNamespaceAndPath(block.namespace(), block.path())).orElse(null);
        if (found == null) {
            return null;
        }
        BlockState state = found.defaultBlockState();
        for (Map.Entry<String, String> property : properties.entrySet()) {
            state = with(state, found, property.getKey(), property.getValue());
        }
        return state;
    }

    private static BlockState with(BlockState state, Block block, String name, String value) {
        Property<?> property = block.getStateDefinition().getProperty(name);
        if (property == null) {
            return state;
        }
        return withTyped(state, property, value);
    }

    private static <T extends Comparable<T>> BlockState withTyped(
        BlockState state,
        Property<T> property,
        String value
    ) {
        return property.getValue(value).map(found -> state.setValue(property, found)).orElse(state);
    }
}
