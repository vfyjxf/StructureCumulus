package dev.vfyjxf.cumulus.kernel.predicate;

import dev.vfyjxf.cumulus.kernel.base.FindingCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingsTest {

    private static final BindingKey<Integer> COUNT = BindingKey.of("test:count", Integer.class);
    private static final BindingKey<String> NAME = BindingKey.of("test:name", String.class);

    @Test
    void disjointKeysMergeAndKeepBothValues() {
        Reply merged = Bindings.of(COUNT, 1).merge(Bindings.of(NAME, "oak"));

        assertTrue(merged instanceof Reply.Match);
        Bindings bindings = ((Reply.Match) merged).bindings();
        assertEquals(Integer.valueOf(1), bindings.get(COUNT));
        assertEquals("oak", bindings.get(NAME));
        assertEquals(2, bindings.size());
    }

    @Test
    void equalValuesUnderOneKeyMerge() {
        Reply merged = Bindings.of(COUNT, 7).merge(Bindings.of(COUNT, 7));

        assertTrue(merged instanceof Reply.Match);
        assertEquals(Integer.valueOf(7), ((Reply.Match) merged).bindings().get(COUNT));
    }

    @Test
    void twoValuesUnderOneKeyAreRefusedWithAFindingAndNoBindings() {
        Reply refused = Bindings.of(COUNT, 1).merge(Bindings.of(COUNT, 2));

        assertTrue(refused instanceof Reply.Mismatch);
        Reply.Mismatch mismatch = (Reply.Mismatch) refused;
        assertEquals(1, mismatch.findings().size());
        assertEquals(FindingCode.of("SC-EXP-0003"), mismatch.findings().findings().get(0).code());
        assertTrue(mismatch.findings().findings().get(0).message().contains("test:count"));
        assertFalse(Bindings.of(COUNT, 1).merge(Bindings.of(COUNT, 2)) instanceof Reply.Match);
    }

    @Test
    void oneIdUnderTwoTypesIsRefusedBecauseTheIdSpaceIsFlat() {
        BindingKey<String> sameId = BindingKey.of("test:count", String.class);

        Reply refused = Bindings.of(COUNT, 1).merge(Bindings.of(sameId, "one"));

        assertTrue(refused instanceof Reply.Mismatch);
        assertEquals(FindingCode.of("SC-EXP-0003"),
                ((Reply.Mismatch) refused).findings().findings().get(0).code());
    }

    @Test
    void aBindingMapIsImmutableAndTyped() {
        Bindings bindings = Bindings.of(COUNT, 3);

        assertTrue(bindings.has(COUNT));
        assertEquals(Integer.valueOf(3), bindings.get(COUNT));
        assertFalse(bindings.has(NAME));
        assertThrows(IllegalArgumentException.class,
                () -> Bindings.of(new Bindings.Binding<>(COUNT, 1), new Bindings.Binding<>(COUNT, 2)));
        assertTrue(Bindings.empty().isEmpty());
    }
}
