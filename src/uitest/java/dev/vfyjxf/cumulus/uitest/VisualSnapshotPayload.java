package dev.vfyjxf.cumulus.uitest;

import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Placement;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.visual.VisualClaim;
import dev.vfyjxf.cumulus.mc.visual.VisualInstance;
import dev.vfyjxf.cumulus.mc.visual.VisualSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Server-to-client carrier of one read-only visual snapshot for the HUD and the test probe. */
public record VisualSnapshotPayload(VisualSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<VisualSnapshotPayload> type = new Type<>(
        ResourceLocation.fromNamespaceAndPath(UiTestMod.modId, "visual_snapshot")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VisualSnapshotPayload> streamCodec = StreamCodec
            .of(VisualSnapshotPayload::encode, VisualSnapshotPayload::decode);

    public VisualSnapshotPayload {
        Objects.requireNonNull(snapshot, "snapshot");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VisualSnapshotPayload payload) {
        VisualSnapshot snapshot = payload.snapshot();
        writeId(buffer, snapshot.level());
        buffer.writeVarLong(snapshot.capturedTick());
        buffer.writeVarLong(snapshot.claimRevision());
        buffer.writeVarInt(snapshot.instances().size());
        for (VisualInstance instance : snapshot.instances()) {
            writeId(buffer, instance.id().world().id());
            buffer.writeVarLong(instance.id().serial());
            writeId(buffer, instance.owner().id());
            writeSiting(buffer, instance.siting());
            buffer.writeVarInt(instance.bounds().boxes().size());
            for (Cuboid box : instance.bounds().boxes()) {
                writePoint(buffer, box.min());
                writePoint(buffer, box.max());
            }
            buffer.writeVarLong(instance.contextRevision());
            buffer.writeVarInt(instance.spend().cells());
            buffer.writeVarInt(instance.spend().sections());
            buffer.writeVarInt(instance.findings().size());
            for (String finding : instance.findings()) {
                buffer.writeUtf(finding);
            }
            buffer.writeVarInt(instance.contextKeys().size());
            for (String key : instance.contextKeys()) {
                buffer.writeUtf(key);
            }
            buffer.writeVarInt(instance.contextValueCount());
        }
        buffer.writeVarInt(snapshot.claims().size());
        for (VisualClaim claim : snapshot.claims()) {
            buffer.writeLong(claim.cell().packed());
            buffer.writeBoolean(claim.sharing() == Sharing.exclusive);
            writeId(buffer, claim.owner().id());
            writeSiting(buffer, claim.siting());
            writeId(buffer, claim.bay());
        }
    }

    private static VisualSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        NamespacedId level = readId(buffer);
        long capturedTick = buffer.readVarLong();
        long claimRevision = buffer.readVarLong();
        int instanceCount = buffer.readVarInt();
        List<VisualInstance> instances = new ArrayList<>(instanceCount);
        for (int index = 0; index < instanceCount; index++) {
            NamespacedId world = readId(buffer);
            long serial = buffer.readVarLong();
            OwnerId owner = OwnerId.of(readId(buffer));
            Siting siting = readSiting(buffer);
            int boxCount = buffer.readVarInt();
            List<Cuboid> boxes = new ArrayList<>(boxCount);
            for (int box = 0; box < boxCount; box++) {
                boxes.add(Cuboid.of(readPoint(buffer), readPoint(buffer)));
            }
            long contextRevision = buffer.readVarLong();
            Spend spend = new Spend(buffer.readVarInt(), buffer.readVarInt());
            int findingCount = buffer.readVarInt();
            List<String> findings = new ArrayList<>(findingCount);
            for (int finding = 0; finding < findingCount; finding++) {
                findings.add(buffer.readUtf());
            }
            int keyCount = buffer.readVarInt();
            List<String> contextKeys = new ArrayList<>(keyCount);
            for (int key = 0; key < keyCount; key++) {
                contextKeys.add(buffer.readUtf());
            }
            int contextValueCount = buffer.readVarInt();
            instances.add(
                new VisualInstance(
                    new InstanceId(World.of(world), serial),
                    owner,
                    siting,
                    CuboidSet.of(boxes),
                    contextRevision,
                    spend,
                    findings,
                    contextKeys,
                    contextValueCount
                )
            );
        }
        int claimCount = buffer.readVarInt();
        List<VisualClaim> claims = new ArrayList<>(claimCount);
        for (int index = 0; index < claimCount; index++) {
            LatticeKey cell = new LatticeKey(buffer.readLong());
            Sharing sharing = buffer.readBoolean() ? Sharing.exclusive : Sharing.shared;
            OwnerId owner = OwnerId.of(readId(buffer));
            Siting siting = readSiting(buffer);
            claims.add(new VisualClaim(cell, sharing, owner, siting, readId(buffer)));
        }
        return new VisualSnapshotPayload(new VisualSnapshot(level, capturedTick, claimRevision, instances, claims));
    }

    private static void writeId(RegistryFriendlyByteBuf buffer, NamespacedId id) {
        buffer.writeUtf(id.namespace());
        buffer.writeUtf(id.path());
    }

    private static NamespacedId readId(RegistryFriendlyByteBuf buffer) {
        return NamespacedId.of(buffer.readUtf(), buffer.readUtf());
    }

    private static void writePoint(RegistryFriendlyByteBuf buffer, LatticePoint point) {
        buffer.writeVarInt(point.x());
        buffer.writeVarInt(point.y());
        buffer.writeVarInt(point.z());
    }

    private static LatticePoint readPoint(RegistryFriendlyByteBuf buffer) {
        return LatticePoint.of(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    private static void writeSiting(RegistryFriendlyByteBuf buffer, Siting siting) {
        writePoint(buffer, siting.origin());
        buffer.writeByte(siting.placement().orientation().ordinal());
        writePoint(buffer, siting.placement().offset());
    }

    private static Siting readSiting(RegistryFriendlyByteBuf buffer) {
        LatticePoint origin = readPoint(buffer);
        Orientation orientation = new Orientation(buffer.readByte());
        LatticePoint offset = readPoint(buffer);
        return new Siting(origin, new Placement(orientation, offset));
    }
}
