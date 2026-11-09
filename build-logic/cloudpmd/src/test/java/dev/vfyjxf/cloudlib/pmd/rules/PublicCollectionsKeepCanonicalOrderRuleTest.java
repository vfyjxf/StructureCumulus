package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * X03-D06: Set.copyOf and Map.copyOf freeze the source's iteration order, and a hash source's
 * order carries the JVM's hash salt, so a public surface built on them leaks identity-hash order
 * across JVMs (D-75: public collections keep canonical order).
 */
class PublicCollectionsKeepCanonicalOrderRuleTest {

    private static final String HEADER = """
            package com.example;

            import java.util.List;
            import java.util.Map;
            import java.util.Set;

            class NamespacedId {}
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new PublicCollectionsKeepCanonicalOrderRule(),
                "Test.java", HEADER + "\nclass Test {\n\n"
                        + "    private final Map<String, NamespacedId> byName = new java.util.HashMap<>();\n\n"
                        + body + "\n}\n");
    }

    // region positives: the original defect shapes

    @Test
    void reportsAPublicMethodReturningSetCopyOf() {
        List<RuleViolation> found = violations(
                "    public Set<NamespacedId> ids() {\n        return Set.copyOf(byName.values());\n    }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("Set.copyOf"), found.get(0).getDescription());
    }

    @Test
    void reportsAPublicMethodReturningMapCopyOf() {
        assertEquals(1, violations(
                "    public Map<String, NamespacedId> snapshot() {\n        return Map.copyOf(byName);\n    }").size());
    }

    @Test
    void reportsAPublicRecordComponentInitialisedWithSetCopyOf() {
        String source = """
                package com.example;

                import java.util.Set;

                public record Holder(Set<Long> chunks) {

                    public Holder {
                        this.chunks = Set.copyOf(chunks);
                    }
                }
                """;
        List<RuleViolation> found = PmdRuleTestSupport.violationsOf(new PublicCollectionsKeepCanonicalOrderRule(),
                "Holder.java", source);
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("chunks"), found.get(0).getDescription());
    }

    // endregion

    // region negatives: private, local, wrapped and ordered shapes

    @Test
    void acceptsAPrivateMethodReturningSetCopyOf() {
        assertEquals(0, violations(
                "    private Set<NamespacedId> idsPrivately() {\n        return Set.copyOf(byName.values());\n    }")
                .size());
    }

    @Test
    void acceptsAPackagePrivateRecordComponent() {
        String source = """
                package com.example;

                import java.util.Set;

                record PackageHolder(Set<Long> chunks) {

                    PackageHolder {
                        this.chunks = Set.copyOf(chunks);
                    }
                }
                """;
        assertEquals(0, PmdRuleTestSupport.violationsOf(new PublicCollectionsKeepCanonicalOrderRule(),
                "PackageHolder.java", source).size());
    }

    @Test
    void acceptsAClassFieldInitialisedWithMapCopyOf() {
        assertEquals(0, violations(
                "    private final Map<String, NamespacedId> copy = Map.copyOf(byName);").size());
    }

    @Test
    void acceptsListCopyOf() {
        assertEquals(0, violations(
                "    public List<NamespacedId> ordered() {\n        return List.copyOf(byName.values());\n    }")
                .size());
    }

    @Test
    void acceptsAWrappedCopyOf() {
        assertEquals(0, violations(
                "    public Set<NamespacedId> canonical() {\n        return canonicalised(Set.copyOf(byName.values()));\n    }\n\n"
                        + "    private Set<NamespacedId> canonicalised(Set<NamespacedId> given) {\n"
                        + "        return new java.util.TreeSet<>(given);\n    }").size());
    }

    // endregion
}
