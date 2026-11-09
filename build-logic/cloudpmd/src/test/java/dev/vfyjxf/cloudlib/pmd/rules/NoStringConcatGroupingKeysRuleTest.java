package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * X02-D01: a map key joined with a separator literal collides the moment a key part itself
 * contains the separator ({@code bay + "|" + instance} against an id holding {@code "|"}); a
 * record key keeps the parts separate and cannot collide. Only the key position is checked.
 */
class NoStringConcatGroupingKeysRuleTest {

    private static final String HEADER = """
            package com.example;

            import java.util.ArrayList;
            import java.util.HashMap;
            import java.util.List;
            import java.util.Map;

            class InstanceId {}
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new NoStringConcatGroupingKeysRule(),
                "Test.java", HEADER + "\nclass Test {\n\n"
                        + "    private final Map<String, InstanceId> bySiting = new HashMap<>();\n"
                        + "    private final Map<String, List<Integer>> byCell = new HashMap<>();\n"
                        + "    private final Map<String, InstanceId> owners = new HashMap<>();\n\n"
                        + body + "\n}\n");
    }

    // region positives: the original defect shapes

    @Test
    void reportsAPipeJoinedGetKey() {
        List<RuleViolation> found = violations(
                "    InstanceId existing(String bay, String instance) {\n"
                        + "        return bySiting.get(bay + \"|\" + instance);\n    }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("\"|\""), found.get(0).getDescription());
    }

    @Test
    void reportsAColonJoinedComputeIfAbsentKey() {
        assertEquals(1, violations(
                "    void index(long x, long y) {\n"
                        + "        byCell.computeIfAbsent(x + \":\" + y, ignored -> new ArrayList<>());\n    }").size());
    }

    @Test
    void reportsADotJoinedPutKey() {
        assertEquals(1, violations(
                "    void register(String owner, String bay, InstanceId handle) {\n"
                        + "        owners.put(owner + \".\" + bay, handle);\n    }").size());
    }

    @Test
    void reportsASlashJoinedKey() {
        assertEquals(1, violations(
                "    InstanceId at(String dir, String name) {\n        return bySiting.get(dir + \"/\" + name);\n    }")
                .size());
    }

    // endregion

    // region negatives: concats outside the key position and non-map receivers

    @Test
    void acceptsALogMessageThatJoinsTheSameParts() {
        assertEquals(0, violations(
                "    void note(String bay, String instance) {\n"
                        + "        System.out.println(bay + \"|\" + instance);\n    }").size());
    }

    @Test
    void acceptsAPathBuiltByConcatenation() {
        assertEquals(0, violations(
                "    String pathOf(String dir, String name) {\n        return dir + \"/\" + name;\n    }").size());
    }

    @Test
    void acceptsAKeyJoinedWithoutALiteralSeparator() {
        assertEquals(0, violations(
                "    InstanceId existing(String bay, String instance) {\n"
                        + "        return bySiting.get(bay + instance);\n    }").size());
    }

    @Test
    void acceptsAGetOrDefaultWithAJoinedKey() {
        assertEquals(0, violations(
                "    InstanceId existing(String bay, String instance) {\n"
                        + "        return bySiting.getOrDefault(bay + \"|\" + instance, null);\n    }").size());
    }

    @Test
    void acceptsAConcatInsideACallOperandOfTheKey() {
        assertEquals(0, violations("""
                InstanceId existing(String bay, String instance) {
                    return bySiting.get(label(bay + "|") + instance);
                }

                String label(String part) { return part; }
                """).size());
    }

    @Test
    void acceptsAListGetWithANumericIndex() {
        assertEquals(0, violations("""
                Integer at(List<Integer> xs, int i) {
                    return xs.get(i + 1);
                }
                """).size());
    }

    // endregion
}
