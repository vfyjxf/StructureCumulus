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

    private static final InstanceId INSTANCE = new InstanceId(World.of(id("w")), 1);

    @Test
    void publishMergesAndBumpsTheRevision() {
        ContextKey<Long> a = ContextKeys.of(id("a"), Long.class, Retention.INSTANCE);
        ContextKey<Long> b = ContextKeys.of(id("b"), Long.class, Retention.INSTANCE);
        ContextStore store = ContextStore.inMemory();
        StructureContext first = store.publish(INSTANCE, Map.of(a, 1L), FindingBundle.empty(), new Spend(2, 1));
        assertEquals(1, first.revision());
        assertEquals(1L, first.get(a));
        assertEquals(new Spend(2, 1), first.spend());

        StructureContext second = store.publish(INSTANCE, Map.of(b, 2L), FindingBundle.empty(), new Spend(3, 2));
        assertEquals(2, second.revision());
        assertEquals(1L, second.get(a));
        assertEquals(2L, second.get(b));
        assertEquals(new Spend(5, 3), second.spend());
        assertEquals(second, store.context(INSTANCE));
    }

    @Test
    void matchValuesAreNeverPublished() {
        ContextKey<Long> match = ContextKeys.of(id("match"), Long.class, Retention.MATCH);
        ContextStore store = ContextStore.inMemory();
        assertThrows(
            IllegalArgumentException.class,
            () -> store.publish(INSTANCE, Map.of(match, 1L), FindingBundle.empty(), Spend.ZERO)
        );
        assertTrue(store.context(INSTANCE).keys().isEmpty());
    }

    @Test
    void absentAndMismatchedValuesStayAbsent() {
        ContextKey<Long> key = ContextKeys.of(id("key"), Long.class, Retention.INSTANCE);
        StructureContext context = StructureContext.empty(INSTANCE);
        assertNull(context.get(key));
        assertTrue(context.require(key).isPresent() == false);
        assertFalse(context.has(key));
        assertThrows(
            IllegalArgumentException.class,
            () -> new StructureContext(INSTANCE, 1, Map.of(key, "wrong"), FindingBundle.empty(), Spend.ZERO)
        );
    }
}
