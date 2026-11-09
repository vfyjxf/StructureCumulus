package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * K08-D01: the derive path looks a context key up by its slot, and a hash container keys by the
 * full record, so the lookup silently misses; the only legal container is the TreeMap in
 * ContextKeys.order().
 */
class ContextKeyMapsUseSlotOrderRuleTest {

    private static final String HEADER = """
            package com.example;

            import java.util.HashMap;
            import java.util.LinkedHashMap;
            import java.util.Map;
            import java.util.TreeMap;

            class ContextKey<T> {}
            class NamespacedId {}
            class OccupantRef {}
            class ContextKeys {

                static Object order() { return null; }
            }
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new ContextKeyMapsUseSlotOrderRule(),
                "Test.java", HEADER + "\nclass Test {\n\n" + body + "\n}\n");
    }

    // region positives: the original defect shapes

    @Test
    void reportsAFieldOfLinkedHashMapKeyedByContextKey() {
        List<RuleViolation> found = violations(
                "    private final Map<ContextKey<?>, Object> values = new LinkedHashMap<>();");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("ContextKeys.order()"), found.get(0).getDescription());
    }

    @Test
    void reportsALocalPublishedMapOfTheDerivePath() {
        assertEquals(1, violations(
                "    Object publish() {\n        Map<ContextKey<?>, Object> published = new LinkedHashMap<>();\n"
                        + "        return published;\n    }").size());
    }

    @Test
    void reportsAnExplicitHashMapAllocation() {
        assertEquals(1, violations(
                "    private final Map<ContextKey<?>, Object> values = new HashMap<ContextKey<?>, Object>();")
                .size());
    }

    @Test
    void reportsADiamondAllocationAssignedInAConstructor() {
        assertEquals(1, violations("""
                private final Map<ContextKey<?>, Object> other;

                Test() { other = new HashMap<>(); }
                """).size());
    }

    // endregion

    // region negatives: the legal forms and the same-name interference

    @Test
    void acceptsTheTreeMapInSlotOrder() {
        assertEquals(0, violations(
                "    private final TreeMap<ContextKey<?>, Object> values = new TreeMap<>(ContextKeys.order());")
                .size());
    }

    @Test
    void acceptsASubjectsMapKeyedByBay() {
        assertEquals(0, violations(
                "    private final Map<NamespacedId, LinkedHashMap<OccupantRef, Object>> subjects = new LinkedHashMap<>();")
                .size());
    }

    @Test
    void acceptsAContextKeyInTheValuePosition() {
        assertEquals(0, violations(
                "    private final Map<NamespacedId, ContextKey<?>> keys = new LinkedHashMap<>();").size());
    }

    @Test
    void acceptsATraitContextKey() {
        assertEquals(0, violations("""
            private final Map<TraitContextKey<?>, Object> traits = new HashMap<>();

            static class TraitContextKey<T> {}
            """).size());
    }

    @Test
    void acceptsAnOrdinaryHashMap() {
        assertEquals(0, violations(
                "    private final Map<NamespacedId, Object> byId = new HashMap<>();").size());
    }

    // endregion
}
