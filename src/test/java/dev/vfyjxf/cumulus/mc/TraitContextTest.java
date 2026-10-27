package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.mc.trait.TraitContext;
import dev.vfyjxf.cumulus.mc.trait.TraitContextKey;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraitContextTest {

    private static final TraitContextKey<Long> count = TraitContextKey.of(NamespacedId.of("test", "count"), Long.class);
    private static final TraitContextKey<String> label = TraitContextKey
            .of(NamespacedId.of("test", "label"), String.class);

    @Test
    void builderStoresTypedValues() {
        TraitContext context = TraitContext.builder().put(count, 42L).put(label, "kept").build();
        assertEquals(42L, context.get(count));
        assertEquals("kept", context.get(label));
        assertTrue(context.has(count));
        assertEquals(Set.of(count, label), context.keys());
    }

    @Test
    void anAbsentKeyReadsNull() {
        assertNull(TraitContext.empty().get(count));
        assertNull(TraitContext.builder().build().get(count));
    }

    @Test
    void duplicateIdIsRejectedEvenWithADifferentType() {
        TraitContextKey<String> sameId = TraitContextKey.of(count.id(), String.class);
        assertThrows(IllegalArgumentException.class, () -> TraitContext.builder().put(count, 1L).put(sameId, "x"));
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullKeyAndNullValueAreRejected() {
        assertThrows(NullPointerException.class, () -> TraitContext.builder().put(null, 1L));
        assertThrows(NullPointerException.class, () -> TraitContext.builder().put(count, null));
    }

    @Test
    void aWrongTypeReadThrows() {
        TraitContext context = TraitContext.builder().put(count, 1L).build();
        TraitContextKey<String> sameId = TraitContextKey.of(count.id(), String.class);
        assertThrows(IllegalArgumentException.class, () -> context.get(sameId));
    }
}
