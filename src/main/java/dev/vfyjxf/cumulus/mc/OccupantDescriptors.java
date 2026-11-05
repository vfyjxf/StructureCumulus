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
import org.jspecify.annotations.NullMarked;

import java.util.List;
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
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.block);
        OccupantRef ref = new OccupantRef(snapshot, frame, type, OccupantKind.block, cell);
        return new OccupantSnapshot(ref, descriptor, Cuboid.of(cell), traits);
    }

    public static OccupantSnapshot blockEntity(SnapshotId snapshot, int frame, LatticePoint cell, BlockEntity entity) {
        return blockEntity(snapshot, frame, cell, entity, List.of());
    }

    public static OccupantSnapshot blockEntity(
        SnapshotId snapshot,
        int frame,
        LatticePoint cell,
        BlockEntity entity,
        List<TraitSlot> traits
    ) {
        NamespacedId type = id(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()));
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.blockEntity);
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
        NamespacedId body = body(type, uuid);
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.entity);
        OccupantRef ref = new OccupantRef(snapshot, frame, type, OccupantKind.entity, anchor, body);
        return new OccupantSnapshot(ref, descriptor, footprint, traits);
    }

    /** The one body formula: capture and the body-read index must derive the same id. */
    static NamespacedId body(NamespacedId type, UUID uuid) {
        return NamespacedId.of(type.namespace(), uuid.toString());
    }

    public static OccupantSnapshot nested(
        SnapshotId snapshot,
        int frame,
        LatticePoint anchor,
        NamespacedId type,
        Cuboid footprint
    ) {
        OccupantDescriptor descriptor = new OccupantDescriptor(type, OccupantKind.nested);
        OccupantRef ref = new OccupantRef(snapshot, frame, type, OccupantKind.nested, anchor);
        return new OccupantSnapshot(ref, descriptor, footprint, List.of());
    }

    public static OccupantSnapshot air(SnapshotId snapshot, int frame, LatticePoint cell) {
        return OccupantSnapshot.air(snapshot, frame, cell);
    }

    private static NamespacedId id(ResourceLocation id) {
        return NamespacedId.of(id.getNamespace(), id.getPath());
    }

    private OccupantDescriptors() {}
}
