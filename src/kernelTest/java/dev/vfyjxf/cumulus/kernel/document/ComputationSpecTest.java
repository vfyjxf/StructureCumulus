package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComputationSpecTest {

    @Test
    void aKeySetIteratesInCanonicalOrder() {
        Set<NamespacedId> ids = new HashSet<>(
            List.of(
                NamespacedId.of("test", "delta"),
                NamespacedId.of("test", "alpha"),
                NamespacedId.of("other", "zulu"),
                NamespacedId.of("test", "charlie")
            )
        );
        ComputationSpec first = spec(ids);
        ComputationSpec second = spec(ids);
        List<NamespacedId> expected = List.of(
            NamespacedId.of("other", "zulu"),
            NamespacedId.of("test", "alpha"),
            NamespacedId.of("test", "charlie"),
            NamespacedId.of("test", "delta")
        );
        assertEquals(expected, List.copyOf(first.inputs()));
        assertEquals(List.copyOf(first.inputs()), List.copyOf(second.inputs()));
    }

    private static ComputationSpec spec(Set<NamespacedId> inputs) {
        NamespacedId id = NamespacedId.of("test", "out");
        return new ComputationSpec(
            id,
            NamespacedId.of("test", "kind"),
            Value.object(Map.of()),
            null,
            null,
            id,
            inputs,
            null
        );
    }
}
