package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedContextsTest {

    private static final World world = World.of(NamespacedId.of("test", "contexts"));
    private static final InstanceId instance = new InstanceId(world, 1);
    private static final InstanceId second = new InstanceId(world, 2);

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static ContextKey<Long> longKey(String path) {
        return ContextKeys.of(id(path), Long.class, Retention.instance);
    }

    @Test
    void publishMergesBumpsAndAccumulatesSpend() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> cells = longKey("cells");
        ContextKey<String> label = ContextKeys.of(id("label"), String.class, Retention.instance);

        StructureContext first = contexts
                .publish(instance, Map.of(cells, 3L, label, "x"), FindingBundle.empty(), new Spend(4, 2));
        assertEquals(1, first.revision());
        assertEquals(3L, first.get(cells));
        assertEquals("x", first.get(label));
        assertEquals(new Spend(4, 2), first.spend());

        Finding finding = Finding.builder(Codes.keyAbsent).phase(Phase.settle).message("absent").build();
        StructureContext next = contexts
                .publish(instance, Map.of(cells, 5L), FindingBundle.of(List.of(finding)), new Spend(1, 1));
        assertEquals(2, next.revision());
        assertEquals(5L, next.get(cells));
        assertEquals("x", next.get(label));
        assertEquals(new Spend(5, 3), next.spend());
        assertEquals(List.of(finding), next.findings().findings());
        assertEquals(next, contexts.context(instance));
    }

    @Test
    void matchValuesAreRejectedButNotWritten() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> match = ContextKeys.of(id("match"), Long.class, Retention.match);
        assertThrows(
            IllegalArgumentException.class,
            () -> contexts.publish(instance, Map.of(match, 1L), FindingBundle.empty(), Spend.zero)
        );
        assertEquals(0, contexts.context(instance).revision());
    }

    @Test
    void publishRejectsATypeWithoutACodec() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Object> unknown = ContextKeys.of(id("unknown"), Object.class, Retention.instance);
        assertThrows(
            IllegalArgumentException.class,
            () -> contexts.publish(instance, Map.of(unknown, new Object()), FindingBundle.empty(), Spend.zero)
        );
        assertEquals(0, contexts.context(instance).revision());
    }

    @Test
    void nbtRoundTripKeepsEveryValueTypeFindingsAndSpend() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> count = longKey("count");
        ContextKey<Integer> capacity = ContextKeys.of(id("capacity"), Integer.class, Retention.instance);
        ContextKey<Boolean> lit = ContextKeys.of(id("lit"), Boolean.class, Retention.instance);
        ContextKey<String> name = ContextKeys.of(id("name"), String.class, Retention.instance);
        ContextKey<NamespacedId> type = ContextKeys.of(id("type"), NamespacedId.class, Retention.instance);
        ContextKey<Cuboid> box = ContextKeys.of(id("box"), Cuboid.class, Retention.instance);
        ContextKey<CuboidSet> cells = ContextKeys.of(id("cells"), CuboidSet.class, Retention.instance);
        CuboidSet set = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1), Cuboid.of(2, 0, 0, 3, 1, 1));

        Finding finding = Finding.builder(Codes.keyAbsent).phase(Phase.settle).message("the value is absent")
                .label(Label.primary(Locus.key(instance, count.id()), "no count"))
                .note("checked after settle", Locus.world(set))
                .suggest("publish the value", Applicability.maybeIncorrect, id("remedy")).build();
        StructureContext published = contexts.publish(
            instance,
            Map.of(
                count,
                7L,
                capacity,
                64,
                lit,
                true,
                name,
                "furnace",
                type,
                id("furnace"),
                box,
                Cuboid.of(0, 0, 0, 2, 3, 2),
                cells,
                set
            ),
            FindingBundle.of(List.of(finding)),
            new Spend(12, 4)
        );

        CompoundTag tag = contexts.save(new CompoundTag(), RegistryAccess.EMPTY);
        SavedContexts reloaded = SavedContexts.load(world, tag);
        StructureContext restored = reloaded.context(instance);

        assertEquals(published.revision(), restored.revision());
        assertEquals(published.spend(), restored.spend());
        assertEquals(published.values(), restored.values());
        assertEquals(published.keys(), restored.keys());
        assertEquals(published.findings().findings(), restored.findings().findings());
        assertEquals(7L, restored.get(count));
        assertEquals(64, restored.get(capacity));
        assertEquals(true, restored.get(lit));
        assertEquals("furnace", restored.get(name));
        assertEquals(id("furnace"), restored.get(type));
        assertEquals(Cuboid.of(0, 0, 0, 2, 3, 2), restored.get(box));
        assertEquals(set, restored.get(cells));
        assertNotNull(restored.findings().primary());
        Finding primary = Objects.requireNonNull(restored.findings().primary(), "primary");
        assertEquals(Codes.keyAbsent.code(), primary.code());
    }

    @Test
    void anEmptyFindingBundleKeepsContextEqualityAcrossReload() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> count = longKey("count");
        StructureContext published = contexts
                .publish(instance, Map.of(count, 3L), FindingBundle.empty(), new Spend(2, 1));

        SavedContexts reloaded = SavedContexts.load(world, contexts.save(new CompoundTag(), RegistryAccess.EMPTY));

        assertEquals(published, reloaded.context(instance));
    }

    @Test
    void theFactoryDeserializesTwoInstancesSeparately() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> count = longKey("count");
        ContextKey<String> label = ContextKeys.of(id("label"), String.class, Retention.instance);
        contexts.publish(instance, Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        contexts.publish(second, Map.of(label, "second"), FindingBundle.empty(), Spend.zero);

        CompoundTag tag = contexts.save(new CompoundTag(), RegistryAccess.EMPTY);
        SavedData.Factory<SavedContexts> factory = SavedContexts.factory(world);
        SavedContexts reloaded = factory.deserializer().apply(tag, RegistryAccess.EMPTY);

        assertEquals(1L, reloaded.context(instance).get(count));
        assertNull(reloaded.context(instance).get(label));
        assertEquals("second", reloaded.context(second).get(label));
        assertNull(reloaded.context(second).get(count));
        assertEquals(3, reloaded.allocateSerial());
    }

    @Test
    void anUnknownTypeBecomesAFindingAndDoesNotCrashTheLoad() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> count = longKey("count");
        ContextKey<String> label = ContextKeys.of(id("label"), String.class, Retention.instance);
        contexts.publish(instance, Map.of(count, 11L, label, "kept"), FindingBundle.empty(), new Spend(2, 1));

        CompoundTag tag = contexts.save(new CompoundTag(), RegistryAccess.EMPTY);
        ListTag values = tag.getList("instances", Tag.TAG_COMPOUND).getCompound(0).getList("values", Tag.TAG_COMPOUND);
        assertTrue(mutateType(values, count.id(), "com.example.Unknown"));
        SavedContexts reloaded = SavedContexts.load(world, tag);
        StructureContext restored = reloaded.context(instance);

        assertEquals(1, restored.revision());
        assertEquals(new Spend(2, 1), restored.spend());
        assertNull(restored.get(count));
        assertEquals("kept", restored.get(label));
        assertEquals(1, restored.findings().size());
        Finding primary = Objects.requireNonNull(restored.findings().primary(), "primary");
        assertEquals(Codes.keyType.code(), primary.code());
        assertTrue(primary.message().contains("com.example.Unknown"));
    }

    @Test
    void loadDropsCorruptInstancesButKeepsTheRest() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> count = longKey("count");
        contexts.publish(instance, Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        contexts.publish(second, Map.of(count, 2L), FindingBundle.empty(), Spend.zero);

        CompoundTag tag = contexts.save(new CompoundTag(), RegistryAccess.EMPTY);
        ListTag instances = tag.getList("instances", Tag.TAG_COMPOUND);
        instances.getCompound(0).putLong("serial", 0L);
        SavedContexts reloaded = SavedContexts.load(world, tag);

        assertNull(reloaded.context(instance).get(count));
        assertEquals(0, reloaded.context(instance).revision());
        assertEquals(2L, reloaded.context(second).get(count));
        assertEquals(3, reloaded.allocateSerial());
    }

    @Test
    void dimensionDataStorageReadsThePersistedContextFile(@TempDir Path temp) throws IOException {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> count = longKey("count");
        contexts.publish(instance, Map.of(count, 42L), FindingBundle.empty(), new Spend(1, 2));

        CompoundTag wrapper = new CompoundTag();
        wrapper.put("data", contexts.save(new CompoundTag(), RegistryAccess.EMPTY));
        NbtUtils.addCurrentDataVersion(wrapper);
        NbtIo.writeCompressed(wrapper, temp.resolve(SavedContexts.dataName + ".dat"));

        DimensionDataStorage storage = new DimensionDataStorage(temp.toFile(), null, RegistryAccess.EMPTY);
        SavedContexts reloaded = storage.computeIfAbsent(SavedContexts.factory(world), SavedContexts.dataName);

        assertEquals(1, reloaded.context(instance).revision());
        assertEquals(42L, reloaded.context(instance).get(count));
        assertEquals(new Spend(1, 2), reloaded.context(instance).spend());
    }

    private static boolean mutateType(ListTag values, NamespacedId id, String type) {
        for (int index = 0; index < values.size(); index++) {
            CompoundTag entry = values.getCompound(index);
            if (id.text().equals(entry.getString("id"))) {
                entry.putString("type", type);
                return true;
            }
        }
        return false;
    }

    @Test
    void anAbsentInstanceIsEmptyAndStartsAtRevisionZero() {
        SavedContexts contexts = new SavedContexts(world);
        StructureContext empty = contexts.context(second);
        assertEquals(0, empty.revision());
        assertTrue(empty.keys().isEmpty());
        assertFalse(empty.has(longKey("count")));
    }
}
