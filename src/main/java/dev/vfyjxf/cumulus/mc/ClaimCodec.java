package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Placement;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.NullMarked;

/** NBT codec for one claim row and the siting it carries. */
@NullMarked
final class ClaimCodec {

    private static final String cellKey = "cell";
    private static final String sharingKey = "sharing";
    private static final String ownerKey = "owner";
    private static final String sitingKey = "siting";
    private static final String bayKey = "bay";
    private static final String originKey = "origin";
    private static final String orientationKey = "orientation";
    private static final String offsetKey = "offset";

    static CompoundTag encode(ClaimRow row) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(cellKey, row.cell().packed());
        tag.putString(sharingKey, row.sharing().name());
        tag.putString(ownerKey, row.owner().id().text());
        tag.putString(bayKey, row.bay().text());
        tag.put(sitingKey, encodeSiting(row.siting()));
        return tag;
    }

    static ClaimRow decode(CompoundTag tag) {
        LatticeKey cell = new LatticeKey(requiredLong(tag, cellKey));
        Sharing sharing = Sharing.valueOf(requiredString(tag, sharingKey));
        OwnerId owner = OwnerId.of(requiredId(tag, ownerKey));
        NamespacedId bay = requiredId(tag, bayKey);
        Siting siting = decodeSiting(requiredCompound(tag, sitingKey));
        return new ClaimRow(cell, sharing, owner, siting, bay);
    }

    static CompoundTag encodeSiting(Siting siting) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(originKey, point(siting.origin()));
        tag.putInt(orientationKey, siting.placement().orientation().ordinal());
        tag.putIntArray(offsetKey, point(siting.placement().offset()));
        return tag;
    }

    static Siting decodeSiting(CompoundTag tag) {
        LatticePoint origin = point(tag, originKey);
        int ordinal = requiredInt(tag, orientationKey);
        if (ordinal < 0 || ordinal >= Orientation.all().size()) {
            throw new IllegalArgumentException("orientation outside 0..47: " + ordinal);
        }
        LatticePoint offset = point(tag, offsetKey);
        return new Siting(origin, new Placement(new Orientation(ordinal), offset));
    }

    private static int[] point(LatticePoint point) {
        return new int[]{point.x(), point.y(), point.z()};
    }

    private static LatticePoint point(CompoundTag tag, String key) {
        int[] values = tag.getIntArray(key);
        if (values.length != 3) {
            throw new IllegalArgumentException(key + " must hold three ints");
        }
        return LatticePoint.of(values[0], values[1], values[2]);
    }

    private static long requiredLong(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("missing long " + key);
        }
        return tag.getLong(key);
    }

    private static int requiredInt(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("missing int " + key);
        }
        return tag.getInt(key);
    }

    private static String requiredString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("missing string " + key);
        }
        return tag.getString(key);
    }

    private static NamespacedId requiredId(CompoundTag tag, String key) {
        NamespacedId id = NamespacedId.parse(requiredString(tag, key));
        if (id == null) {
            throw new IllegalArgumentException("invalid id in " + key);
        }
        return id;
    }

    private static CompoundTag requiredCompound(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("missing compound " + key);
        }
        return tag.getCompound(key);
    }

    private ClaimCodec() {}
}
