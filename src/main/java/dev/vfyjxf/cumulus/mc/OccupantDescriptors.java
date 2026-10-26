package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** M1 block/block-entity descriptor mapping; entities and traits follow in M2. */
@NullMarked
public final class OccupantDescriptors {

    public static OccupantSnapshot block(SnapshotId snapshot, int frame, LatticePoint cell, BlockState state) {
        NamespacedId type = id(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.BLOCK, properties(state), List.of());
        OccupantRef ref = new OccupantRef(snapshot, frame, type, OccupantKind.BLOCK, cell);
        return new OccupantSnapshot(ref, descriptor, Cuboid.of(cell), List.of());
    }

    public static OccupantSnapshot blockEntity(
        SnapshotId snapshot,
        int frame,
        LatticePoint cell,
        BlockState state,
        BlockEntity entity
    ) {
        NamespacedId type = id(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()));
        OccupantDescriptor descriptor = new OccupantDescriptor(
            type,
            OccupantKind.BLOCK_ENTITY,
            properties(state),
            List.of()
        );
        OccupantRef ref = new OccupantRef(snapshot, frame, type, OccupantKind.BLOCK_ENTITY, cell);
        return new OccupantSnapshot(ref, descriptor, Cuboid.of(cell), List.of());
    }

    public static OccupantSnapshot air(SnapshotId snapshot, int frame, LatticePoint cell) {
        return OccupantSnapshot.air(snapshot, frame, cell);
    }

    private static Map<String, String> properties(BlockState state) {
        Map<String, String> result = new TreeMap<>();
        for (Property<?> property : state.getProperties()) {
            result.put(property.getName(), propertyName(state, property));
        }
        return result;
    }

    private static String propertyName(BlockState state, Property<?> property) {
        return propertyNameTyped(state, property);
    }

    private static <T extends Comparable<T>> String propertyNameTyped(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static NamespacedId id(ResourceLocation id) {
        return NamespacedId.of(id.getNamespace(), id.getPath());
    }

    private OccupantDescriptors() {}
}
