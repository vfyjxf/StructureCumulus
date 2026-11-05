package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

class NoLevelAsCollectionKeyRuleTest {

    private static String classSource(String body) {
        return "package com.example;\n\nclass Thing {\n" + body + "\n}\n";
    }

    private static List<RuleViolation> violations(String source) {
        return PmdRuleTestSupport.violationsOf(new NoLevelAsCollectionKeyRule(), "Thing.java", source);
    }

    @Test
    void reportsAFieldKeyedByServerLevel() {
        List<RuleViolation> found = violations(
            classSource("    private final Map<ServerLevel, LevelStore> levels = new HashMap<>();")
        );

        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("Map<ServerLevel, LevelStore>"),
                found.get(0).getDescription());
    }

    @Test
    void reportsALocalKeyedByLevelAccessor() {
        List<RuleViolation> found = violations(
            classSource("    void run() {\n        Map<LevelAccessor, LevelWatch> watches = new HashMap<>();\n    }")
        );

        assertEquals(1, found.size());
    }

    @Test
    void reportsAParameterKeyedByLevel() {
        List<RuleViolation> found = violations(classSource("    void take(Map<Level, Integer> byLevel) {}"));

        assertEquals(1, found.size());
    }

    @Test
    void reportsAReturnTypeKeyedByClientLevel() {
        List<RuleViolation> found = violations(
            classSource("    Map<ClientLevel, Integer> snapshot() {\n        return null;\n    }")
        );

        assertEquals(1, found.size());
    }

    @Test
    void reportsAFullyQualifiedDeclaration() {
        List<RuleViolation> found = violations(
            classSource("    java.util.Map<net.minecraft.server.level.ServerLevel, Integer> pinned;")
        );

        assertEquals(1, found.size());
    }

    @Test
    void allowsALevelAsAValue() {
        List<RuleViolation> found = violations(
            classSource("    private final Map<String, Level> byName = new HashMap<>();")
        );

        assertEquals(0, found.size(), found.toString());
    }

    @Test
    void allowsTheResourceKey() {
        List<RuleViolation> found = violations(
            classSource("    private final Map<ResourceKey<Level>, LevelStore> levels = new HashMap<>();")
        );

        assertEquals(0, found.size(), found.toString());
    }

    @Test
    void allowsASetOfResourceKeys() {
        List<RuleViolation> found = violations(
            classSource("    private final Set<ResourceKey<Level>> loaded = new HashSet<>();")
        );

        assertEquals(0, found.size(), found.toString());
    }

    @Test
    void allowsAListOfLevels() {
        List<RuleViolation> found = violations(
            classSource("    private final List<Level> ordered = new ArrayList<>();")
        );

        assertEquals(0, found.size(), found.toString());
    }
}
