package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A06-D03 and K08-D03: a read accessor that fills a field map on miss resurrects deleted state
 * (the store an unload had removed) or silently creates a context no derive produced. The
 * creation points whose names say creation — ServerState.openLevel among them — stay legal.
 */
class ReadAccessorsMustNotCreateStateRuleTest {

    private static final String HEADER = """
            package com.example;

            import java.util.HashMap;
            import java.util.HashSet;
            import java.util.Map;
            import java.util.Set;
            import java.util.function.Function;

            class InstanceId {}
            class StructureContext {

                StructureContext(InstanceId id) {}
            }
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new ReadAccessorsMustNotCreateStateRule(),
                "Test.java", HEADER + "\nclass Test {\n\n"
                        + "    private final Map<InstanceId, StructureContext> contexts = new HashMap<>();\n"
                        + "    private final Map<Long, Set<InstanceId>> byChunk = new HashMap<>();\n\n"
                        + body + "\n}\n");
    }

    // region positives: the original defect shapes

    @Test
    void reportsAContextReadThatFallsIntoTheTable() {
        List<RuleViolation> found = violations(
                "    StructureContext context(InstanceId id) {\n"
                        + "        return contexts.computeIfAbsent(id, key -> new StructureContext(key));\n    }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("context"), found.get(0).getDescription());
    }

    @Test
    void reportsAStoreGetterThatResurrectsDeletedState() {
        assertEquals(1, violations(
                "    Object getStore(Object level) {\n"
                        + "        return contexts.computeIfAbsent((InstanceId) level, key -> new StructureContext(key));\n    }")
                .size());
    }

    @Test
    void reportsAReadPrefixMethodOnAQualifiedFieldReceiver() {
        assertEquals(1, violations(
                "    Set<InstanceId> findHits(long chunk) {\n"
                        + "        return this.byChunk.computeIfAbsent(chunk, ignored -> new HashSet<>());\n    }")
                .size());
    }

    @Test
    void reportsCreationHiddenInALambdaInsideAReadAccessor() {
        assertEquals(1, violations("""
                Set<InstanceId> indexHits(long chunk) {
                    Runnable fill = () -> byChunk.computeIfAbsent(chunk, ignored -> new HashSet<>());
                    return byChunk.get(chunk);
                }
                """).size());
    }

    @Test
    void reportsEveryReaderPrefix() {
        for (String reader : new String[] {"read", "peek", "lookup"}) {
            assertEquals(1, violations(
                    "    Object " + reader + "Next(InstanceId id) {\n"
                            + "        return contexts.computeIfAbsent(id, key -> new StructureContext(key));\n    }")
                    .size(), reader);
        }
    }

    // endregion

    // region negatives: the legal creation points and non-creation reads

    @Test
    void acceptsTheLevelLifecycleCreationPoint() {
        assertEquals(0, violations(
                "    void openLevel(InstanceId id) {\n"
                        + "        contexts.computeIfAbsent(id, key -> new StructureContext(key));\n    }").size());
    }

    @Test
    void acceptsCreationFromANonReaderNamedMethod() {
        assertEquals(0, violations(
                "    void contribute(InstanceId id) {\n"
                        + "        contexts.computeIfAbsent(id, key -> new StructureContext(key));\n    }").size());
    }

    @Test
    void acceptsComputeIfAbsentOnALocalMap() {
        assertEquals(0, violations("""
                Object readDigest(InstanceId id) {
                    Map<InstanceId, Object> digests = new HashMap<>();
                    return digests.computeIfAbsent(id, key -> new Object());
                }
                """).size());
    }

    @Test
    void acceptsAGetOrDefaultRead() {
        assertEquals(0, violations(
                "    Object lookupOrDefault(InstanceId id) {\n        return contexts.getOrDefault(id, null);\n    }")
                .size());
    }

    @Test
    void acceptsAPureFieldRead() {
        assertEquals(0, violations(
                "    Object getContext(InstanceId id) {\n        return contexts.get(id);\n    }").size());
    }

    // endregion
}
