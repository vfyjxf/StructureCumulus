package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.Edit;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.mc.OrientationMapping;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The built-in materializer: a type and its properties resolve through the block registry and the
 * block's state definition, then the siting orientation is applied forward.
 */
public final class VanillaMaterializer implements Materializer {

    private static final NamespacedId id = NamespacedId.of("structurecumulus", "vanilla");

    @Override
    public NamespacedId id() {
        return id;
    }

    @Override
    public @Nullable MaterializedEdit materialize(Edit edit, Orientation orientation, MaterializeContext context) {
        return lower(edit, orientation);
    }

    /** The pure lowering; the context only matters to materializers that read the world. */
    @Nullable
    MaterializedEdit lower(Edit edit, Orientation orientation) {
        return switch (edit) {
            case Edit.Place place -> newEdit(place.cells(), place.type(), place.properties(), orientation);
            case Edit.Replace replace -> newEdit(replace.cells(), replace.type(), replace.properties(), orientation);
            case Edit.Remove remove -> air(remove.cells());
            case Edit.SetTrait ignored -> null;
        };
    }

    private static @Nullable MaterializedEdit newEdit(
        CuboidSet cells,
        NamespacedId type,
        Map<String, String> properties,
        Orientation orientation
    ) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(type.namespace(), type.path());
        // The block registry is defaulted: get answers air for an unknown key, so presence is asked first.
        if (!BuiltInRegistries.BLOCK.containsKey(location)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(location);
        BlockState state = block.defaultBlockState();
        StateDefinition<Block, BlockState> definition = block.getStateDefinition();
        for (Map.Entry<String, String> declared : properties.entrySet()) {
            Property<?> property = definition.getProperty(declared.getKey());
            if (property == null) {
                return null;
            }
            state = withValue(state, property, declared.getValue());
            if (state == null) {
                return null;
            }
        }
        if (!(OrientationMapping
                .transform(orientation, state) instanceof OrientationMapping.MappingResult.Mapped placed)) {
            return null;
        }
        Map<LatticePoint, BlockPlacement> placements = new TreeMap<>();
        for (LatticePoint cell : Cells.of(cells)) {
            placements.put(cell, new BlockPlacement(placed.state(), null));
        }
        return new MaterializedEdit(placements, items(block, placements.size()), List.of());
    }

    private static MaterializedEdit air(CuboidSet cells) {
        BlockState air = Blocks.AIR.defaultBlockState();
        Map<LatticePoint, BlockPlacement> placements = new TreeMap<>();
        for (LatticePoint cell : Cells.of(cells)) {
            placements.put(cell, new BlockPlacement(air, null));
        }
        return new MaterializedEdit(placements, List.of(), List.of());
    }

    private static List<ItemRequirement> items(Block block, int count) {
        Item item = block.asItem();
        if (item == Items.AIR || count < 1) {
            return List.of();
        }
        List<ItemRequirement> requirements = new ArrayList<>(1);
        requirements.add(new ItemRequirement(new ItemStack(item), count));
        return requirements;
    }

    private static @Nullable BlockState withValue(BlockState state, Property<?> property, String value) {
        return withValueTyped(state, property, value);
    }

    private static <T extends Comparable<T>> @Nullable BlockState withValueTyped(
        BlockState state,
        Property<T> property,
        String value
    ) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(parsedValue -> state.setValue(property, parsedValue)).orElse(null);
    }
}
