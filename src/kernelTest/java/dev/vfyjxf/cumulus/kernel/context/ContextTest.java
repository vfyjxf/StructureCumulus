package dev.vfyjxf.cumulus.kernel.context;

import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Spend;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextTest {

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static final InstanceId instance = new InstanceId(World.of(id("w")), 1);

    @Test
    void publishMergesAndBumpsTheRevision() {
        ContextKey<Long> a = ContextKeys.of(id("a"), Long.class, Retention.instance);
        ContextKey<Long> b = ContextKeys.of(id("b"), Long.class, Retention.instance);
        ContextStore store = ContextStore.inMemory();
        StructureContext first = store.publish(instance, Map.of(a, 1L), FindingBundle.empty(), new Spend(2, 1));
        assertEquals(1, first.revision());
        assertEquals(1L, first.get(a));
        assertEquals(new Spend(2, 1), first.spend());

        StructureContext second = store.publish(instance, Map.of(b, 2L), FindingBundle.empty(), new Spend(3, 2));
        assertEquals(2, second.revision());
        assertEquals(1L, second.get(a));
        assertEquals(2L, second.get(b));
        assertEquals(new Spend(5, 3), second.spend());
        assertEquals(second, store.context(instance));
    }

    @Test
    void removeReturnsTheStoredContextAndLeavesAnEmptyInstance() {
        ContextKey<Long> key = ContextKeys.of(id("removed"), Long.class, Retention.instance);
        ContextStore store = ContextStore.inMemory();
        assertNull(store.remove(instance), "removing an absent instance answers null");

        StructureContext published = store.publish(instance, Map.of(key, 1L), FindingBundle.empty(), new Spend(1, 1));
        assertEquals(published, store.remove(instance), "remove returns the stored context");
        assertNull(store.remove(instance), "a second remove finds nothing");

        StructureContext after = store.context(instance);
        assertEquals(0, after.revision(), "the removed revision does not survive");
        assertTrue(after.keys().isEmpty(), "the removed values are gone");
        assertEquals(Spend.zero, after.spend(), "the removed spend is gone");
    }

    @Test
    void matchValuesAreNeverPublished() {
        ContextKey<Long> match = ContextKeys.of(id("match"), Long.class, Retention.match);
        ContextStore store = ContextStore.inMemory();
        assertThrows(
            IllegalArgumentException.class,
            () -> store.publish(instance, Map.of(match, 1L), FindingBundle.empty(), Spend.zero)
        );
        assertTrue(store.context(instance).keys().isEmpty());
    }

    @Test
    void absentAndMismatchedValuesStayAbsent() {
        ContextKey<Long> key = ContextKeys.of(id("key"), Long.class, Retention.instance);
        StructureContext context = StructureContext.empty(instance);
        assertNull(context.get(key));
        assertTrue(context.require(key).isPresent() == false);
        assertFalse(context.has(key));
        assertThrows(
            IllegalArgumentException.class,
            () -> new StructureContext(instance, 1, Map.of(key, "wrong"), FindingBundle.empty(), Spend.zero)
        );
    }
}
