package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One world's per-instance contexts, persisted as that level's SavedData; writes run on the server thread. */
@NullMarked
public final class SavedContexts extends SavedData implements ContextStore {

    /** The saved-data name under the level's data folder. */
    public static final String dataName = Constants.modId + "_contexts";

    /** The NBT schema version written by {@link #save}. */
    public static final int dataVersion = 1;

    private static final String versionKey = "version";
    private static final String instancesKey = "instances";
    private static final String serialKey = "serial";
    private static final String revisionKey = "revision";
    private static final String valuesKey = "values";
    private static final String idKey = "id";
    private static final String typeKey = "type";
    private static final String valueKey = "value";
    private static final String spendKey = "spend";
    private static final String cellsKey = "cells";
    private static final String sectionsKey = "sections";

    private final World world;
    private final Map<String, ContextCodecs.Codec<?>> codecs;
    private final TreeMap<InstanceId, StructureContext> contexts = new TreeMap<>();
    private long nextSerial = 1;

    public SavedContexts(World world) {
        this(world, ContextCodecs.builtin());
    }

    SavedContexts(World world, Map<String, ContextCodecs.Codec<?>> codecs) {
        this.world = Objects.requireNonNull(world, "world");
        this.codecs = Map.copyOf(Objects.requireNonNull(codecs, "codecs"));
    }

    /** Builds the SavedData factory for a world. */
    public static SavedData.Factory<SavedContexts> factory(World world) {
        Objects.requireNonNull(world, "world");
        return new SavedData.Factory<>(() -> new SavedContexts(world), (tag, registries) -> load(world, tag));
    }

    public World world() {
        return world;
    }

    /** Returns the next unused instance serial for this world. */
    long allocateSerial() {
        long serial = nextSerial;
        nextSerial = serial + 1;
        return serial;
    }

    @Override
    public StructureContext context(InstanceId instance) {
        Objects.requireNonNull(instance, "instance");
        requireWorld(instance);
        StructureContext context = contexts.get(instance);
        return context == null ? StructureContext.empty(instance) : context;
    }

    @Override
    public StructureContext publish(
        InstanceId instance,
        Map<ContextKey<?>, Object> values,
        FindingBundle findings,
        Spend spend
    ) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(spend, "spend");
        requireWorld(instance);
        for (Map.Entry<ContextKey<?>, Object> entry : values.entrySet()) {
            ContextKey<?> key = Objects.requireNonNull(entry.getKey(), "key");
            Objects.requireNonNull(entry.getValue(), "value");
            if (key.retention() != Retention.instance) {
                throw new IllegalArgumentException("only INSTANCE values are published: " + key.id());
            }
            if (!codecs.containsKey(key.type().getName())) {
                throw new IllegalArgumentException("no codec for context value type " + key.type().getName());
            }
        }
        StructureContext current = contexts.get(instance);
        if (current == null) {
            current = StructureContext.empty(instance);
        }
        Map<ContextKey<?>, Object> merged = new LinkedHashMap<>(current.values());
        merged.putAll(values);
        StructureContext next = new StructureContext(
            instance,
            current.revision() + 1,
            merged,
            findings,
            current.spend().plus(spend)
        );
        contexts.put(instance, next);
        nextSerial = Math.max(nextSerial, instance.serial() + 1);
        setDirty();
        return next;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Objects.requireNonNull(tag, "tag");
        tag.putInt(versionKey, dataVersion);
        ListTag encoded = new ListTag();
        for (StructureContext context : contexts.values()) {
            encoded.add(write(context));
        }
        tag.put(instancesKey, encoded);
        return tag;
    }

    /** Reads one context store from its NBT form. */
    public static SavedContexts load(World world, CompoundTag tag) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(tag, "tag");
        int version = tag.getInt(versionKey);
        if (version != dataVersion) {
            throw new IllegalArgumentException("unsupported context data version " + version);
        }
        SavedContexts store = new SavedContexts(world);
        ListTag encoded = tag.getList(instancesKey, Tag.TAG_COMPOUND);
        long maxSerial = 0;
        for (int index = 0; index < encoded.size(); index++) {
            try {
                StructureContext context = store.read(encoded.getCompound(index), world);
                store.contexts.put(context.instance(), context);
                maxSerial = Math.max(maxSerial, context.instance().serial());
            } catch (RuntimeException ignored) {
                // A stored instance that cannot be decoded is dropped.
            }
        }
        store.nextSerial = maxSerial + 1;
        return store;
    }

    private CompoundTag write(StructureContext context) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(serialKey, context.instance().serial());
        tag.putLong(revisionKey, context.revision());
        CompoundTag spend = new CompoundTag();
        spend.putInt(cellsKey, context.spend().cells());
        spend.putInt(sectionsKey, context.spend().sections());
        tag.put(spendKey, spend);
        ListTag values = new ListTag();
        for (Map.Entry<ContextKey<?>, Object> entry : context.values().entrySet()) {
            values.add(write(entry));
        }
        tag.put(valuesKey, values);
        FindingCodec.write(tag, context.findings());
        return tag;
    }

    private CompoundTag write(Map.Entry<ContextKey<?>, Object> entry) {
        ContextKey<?> key = entry.getKey();
        Object value = entry.getValue();
        ContextCodecs.Codec<?> codec = codecs.get(key.type().getName());
        if (codec == null) {
            throw new IllegalArgumentException("no codec for context value type " + key.type().getName());
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(idKey, key.id().text());
        tag.putString(typeKey, key.type().getName());
        CompoundTag encoded = new CompoundTag();
        writeValue(codec, encoded, value);
        tag.put(valueKey, encoded);
        return tag;
    }

    @SuppressWarnings("unchecked")
    private static <T> void writeValue(ContextCodecs.Codec<T> codec, CompoundTag tag, Object value) {
        codec.write(tag, (T) value);
    }

    private StructureContext read(CompoundTag tag, World world) {
        long serial = requiredLong(tag, serialKey);
        InstanceId instance = new InstanceId(world, serial);
        long revision = requiredLong(tag, revisionKey);
        Spend spend = readSpend(tag);
        List<Finding> findings = new ArrayList<>(FindingCodec.read(tag));
        Map<ContextKey<?>, Object> values = new LinkedHashMap<>();
        ListTag encoded = tag.getList(valuesKey, Tag.TAG_COMPOUND);
        for (int index = 0; index < encoded.size(); index++) {
            readValue(encoded.getCompound(index), values, findings);
        }
        FindingBundle bundle = findings.isEmpty() ? FindingBundle.empty() : FindingBundle.of(findings);
        return new StructureContext(instance, revision, values, bundle, spend);
    }

    private void readValue(CompoundTag tag, Map<ContextKey<?>, Object> values, List<Finding> findings) {
        String idText = tag.getString(idKey);
        NamespacedId id = NamespacedId.parse(idText);
        if (id == null) {
            findings.add(undecodable(idText, "invalid key id"));
            return;
        }
        String typeText = tag.getString(typeKey);
        ContextCodecs.Codec<?> codec = codecs.get(typeText);
        if (codec == null) {
            findings.add(undecodable(idText, typeText));
            return;
        }
        try {
            Object value = codec.read(tag.getCompound(valueKey));
            if (!codec.type().isInstance(value)) {
                throw new IllegalArgumentException("decoded value is not a " + codec.type().getName());
            }
            values.put(key(id, codec.type()), value);
        } catch (RuntimeException ignored) {
            findings.add(undecodable(idText, typeText));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ContextKey<?> key(NamespacedId id, Class<?> type) {
        return ContextKeys.of(id, (Class) type, Retention.instance);
    }

    private static Finding undecodable(String id, String type) {
        return Finding.builder(Codes.keyType).phase(Phase.settle)
                .message("context value " + id + " cannot use type " + type).note("the stored value was dropped")
                .build();
    }

    private static Spend readSpend(CompoundTag tag) {
        CompoundTag spend = tag.getCompound(spendKey);
        return new Spend(Math.max(0, spend.getInt(cellsKey)), Math.max(0, spend.getInt(sectionsKey)));
    }

    private void requireWorld(InstanceId instance) {
        if (!instance.world().equals(world)) {
            throw new IllegalArgumentException("instance belongs to " + instance.world().id() + ", not " + world.id());
        }
    }

    private static long requiredLong(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("missing long " + key);
        }
        return tag.getLong(key);
    }
}
