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
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedContextsLifecycleTest {

    private static final World world = World.of(NamespacedId.of("test", "context_lifecycle"));
    private static final InstanceId instance = new InstanceId(world, 1);
    private static final InstanceId second = new InstanceId(world, 2);

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static ContextKey<Long> longKey(String path) {
        return ContextKeys.of(id(path), Long.class, Retention.instance);
    }

    @Test
    void allocateSerialIsMonotonicAndPublishingAdvancesIt() {
        SavedContexts contexts = new SavedContexts(world);
        assertEquals(1, contexts.allocateSerial(), "the first serial is one");
        assertEquals(2, contexts.allocateSerial(), "the next serial is two");

        InstanceId late = new InstanceId(world, 10);
        contexts.publish(late, Map.of(longKey("count"), 1L), FindingBundle.empty(), Spend.zero);
        assertEquals(11, contexts.allocateSerial(), "publishing advances the next serial past the instance");

        SavedContexts reloaded = SavedContexts.load(world, contexts.save(new CompoundTag(), RegistryAccess.EMPTY));
        assertEquals(11, reloaded.allocateSerial(), "reload restores the next unused serial");
    }

    @Test
    void publishMergesAndReloadsTheWholeContext() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> count = longKey("count");
        ContextKey<String> label = ContextKeys.of(id("label"), String.class, Retention.instance);

        StructureContext first = contexts.publish(instance, Map.of(count, 3L), FindingBundle.empty(), new Spend(4, 2));
        assertEquals(1, first.revision(), "the first publish is revision one");
        Finding finding = Finding.builder(Codes.keyAbsent).phase(Phase.settle).message("absent").build();
        StructureContext second = contexts
                .publish(instance, Map.of(label, "kept"), FindingBundle.of(List.of(finding)), new Spend(1, 1));
        assertEquals(2, second.revision(), "the merge bumps the revision");
        assertEquals(3L, second.get(count), "the first value survives the merge");
        assertEquals("kept", second.get(label), "the second value is added");
        assertEquals(new Spend(5, 3), second.spend(), "spend accumulates");
        assertEquals(second, contexts.context(instance), "the store keeps the merged context");

        SavedContexts reloaded = SavedContexts.load(world, contexts.save(new CompoundTag(), RegistryAccess.EMPTY));
        StructureContext restored = reloaded.context(instance);
        assertEquals(second.revision(), restored.revision(), "the merged revision survives reload");
        assertEquals(second.values(), restored.values(), "the merged values survive reload");
        assertEquals(second.keys(), restored.keys(), "the merged keys survive reload");
        assertEquals(second.spend(), restored.spend(), "the merged spend survives reload");
        assertEquals(List.of(finding), restored.findings().findings(), "findings survive reload");
    }

    @Test
    void instancesStayIsolatedAcrossReload() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> count = longKey("count");
        ContextKey<String> label = ContextKeys.of(id("label"), String.class, Retention.instance);
        contexts.publish(instance, Map.of(count, 1L), FindingBundle.empty(), Spend.zero);
        contexts.publish(second, Map.of(label, "second"), FindingBundle.empty(), Spend.zero);

        SavedContexts reloaded = SavedContexts.load(world, contexts.save(new CompoundTag(), RegistryAccess.EMPTY));
        assertEquals(1L, reloaded.context(instance).get(count), "the first instance keeps its count");
        assertNull(reloaded.context(instance).get(label), "the first instance has no second value");
        assertEquals("second", reloaded.context(second).get(label), "the second instance keeps its label");
        assertNull(reloaded.context(second).get(count), "the second instance has no first value");
        assertEquals(3, reloaded.allocateSerial(), "both instances advance the next serial");
    }

    @Test
    @DisplayName("known gap D5: SavedContexts grows one entry per instance without eviction")
    void growthWithoutEvictionKeepsEveryInstance() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> count = longKey("count");
        int instances = 64;
        for (int serial = 1; serial <= instances; serial++) {
            contexts.publish(
                new InstanceId(world, serial),
                Map.of(count, (long) serial),
                FindingBundle.empty(),
                Spend.zero
            );
        }

        CompoundTag saved = contexts.save(new CompoundTag(), RegistryAccess.EMPTY);
        assertEquals(instances, saved.getList("instances", Tag.TAG_COMPOUND).size(), "all instances are written");
        SavedContexts reloaded = SavedContexts.load(world, saved);
        assertEquals(
            instances,
            reloaded.save(new CompoundTag(), RegistryAccess.EMPTY).getList("instances", Tag.TAG_COMPOUND).size(),
            "all instances survive the reload"
        );
        for (int serial = 1; serial <= instances; serial++) {
            assertEquals(
                (long) serial,
                reloaded.context(new InstanceId(world, serial)).get(count),
                "instance " + serial + " keeps its value"
            );
        }
        assertEquals(instances + 1, reloaded.allocateSerial(), "the next serial follows the highest stored instance");
    }

    @Test
    void anUnknownTypeBecomesAFindingAndKeepsTheOtherValues() {
        SavedContexts contexts = new SavedContexts(world);
        ContextKey<Long> count = longKey("count");
        ContextKey<String> label = ContextKeys.of(id("label"), String.class, Retention.instance);
        contexts.publish(instance, Map.of(count, 11L, label, "kept"), FindingBundle.empty(), new Spend(2, 1));

        CompoundTag tag = contexts.save(new CompoundTag(), RegistryAccess.EMPTY);
        ListTag values = tag.getList("instances", Tag.TAG_COMPOUND).getCompound(0).getList("values", Tag.TAG_COMPOUND);
        assertTrue(mutateType(values, count.id(), "com.example.Unknown"), "the stored type is replaced");
        SavedContexts reloaded = SavedContexts.load(world, tag);
        StructureContext restored = reloaded.context(instance);

        assertEquals(1, restored.revision(), "the instance revision survives");
        assertEquals(new Spend(2, 1), restored.spend(), "the spend survives");
        assertNull(restored.get(count), "the unknown type is dropped");
        assertEquals("kept", restored.get(label), "the known value survives");
        assertEquals(1, restored.findings().size(), "the drop is reported");
        Finding primary = Objects.requireNonNull(restored.findings().primary(), "primary");
        assertEquals(Codes.keyType.code(), primary.code(), "the finding is SC-CTX for a bad key type");
        assertTrue(primary.message().contains("com.example.Unknown"), "the finding names the unknown type");
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
}
