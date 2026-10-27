package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Block, block-entity and entity descriptor mapping. */
@NullMarked
public final class OccupantDescriptors {

    public static OccupantSnapshot block(SnapshotId snapshot, int frame, LatticePoint cell, BlockState state) {
        return block(snapshot, frame, cell, state, List.of());
    }

    public static OccupantSnapshot block(
        SnapshotId snapshot,
        int frame,
        LatticePoint cell,
        BlockState state,
        List<TraitSlot> traits
    ) {
        NamespacedId type = id(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.block, properties(state), List.of());
        OccupantRef ref = new OccupantRef(snapshot, frame, type, OccupantKind.block, cell);
        return new OccupantSnapshot(ref, descriptor, Cuboid.of(cell), traits);
    }

    public static OccupantSnapshot blockEntity(
        SnapshotId snapshot,
        int frame,
        LatticePoint cell,
        BlockState state,
        BlockEntity entity
    ) {
        return blockEntity(snapshot, frame, cell, state, entity, List.of());
    }

    public static OccupantSnapshot blockEntity(
        SnapshotId snapshot,
        int frame,
        LatticePoint cell,
        BlockState state,
        BlockEntity entity,
        List<TraitSlot> traits
    ) {
        NamespacedId type = id(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()));
        OccupantDescriptor descriptor = new OccupantDescriptor(
            type,
            OccupantKind.blockEntity,
            properties(state),
            List.of()
        );
        OccupantRef ref = new OccupantRef(snapshot, frame, type, OccupantKind.blockEntity, cell);
        return new OccupantSnapshot(ref, descriptor, Cuboid.of(cell), traits);
    }

    public static OccupantSnapshot entity(
        SnapshotId snapshot,
        int frame,
        LatticePoint anchor,
        NamespacedId type,
        UUID uuid,
        Cuboid footprint
    ) {
        return entity(snapshot, frame, anchor, type, uuid, footprint, List.of());
    }

    public static OccupantSnapshot entity(
        SnapshotId snapshot,
        int frame,
        LatticePoint anchor,
        NamespacedId type,
        UUID uuid,
        Cuboid footprint,
        List<TraitSlot> traits
    ) {
        NamespacedId body = NamespacedId.of(type.namespace(), uuid.toString());
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.entity, Map.of(), List.of());
        OccupantRef ref = new OccupantRef(snapshot, frame, type, OccupantKind.entity, anchor, body);
        return new OccupantSnapshot(ref, descriptor, footprint, traits);
    }

    public static OccupantSnapshot nested(
        SnapshotId snapshot,
        int frame,
        LatticePoint anchor,
        NamespacedId type,
        Cuboid footprint
    ) {
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.nested, Map.of(), List.of());
        OccupantRef ref = new OccupantRef(snapshot, frame, type, OccupantKind.nested, anchor);
        return new OccupantSnapshot(ref, descriptor, footprint, List.of());
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
