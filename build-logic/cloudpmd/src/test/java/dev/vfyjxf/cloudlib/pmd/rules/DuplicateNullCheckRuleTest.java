package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

class DuplicateNullCheckRuleTest {

    private static final String HEADER = """
            package com.example;

            import java.util.Objects;

            import org.jspecify.annotations.Nullable;

            class Test {

                @Nullable
                private String field;

                static class Checks {

                    static <T> T checkNotNull(@Nullable T value, String name) {
                        return Objects.requireNonNull(value, name);
                    }
                }

                private String maybe() { return null; }

            """;

    private static final String FOOTER = "}\n";

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new DuplicateNullCheckRule(), "Test.java",
                HEADER + body + FOOTER);
    }

    // region (a) after requireNonNull / checkNotNull

    @Test
    void reportsANullCheckAfterRequireNonNull() {
        assertEquals(1, violations("""
                void m(String p) {
                    Objects.requireNonNull(p);
                    if (p == null) { return; }
                }
                """).size());
    }

    @Test
    void reportsANullCheckAfterCheckNotNull() {
        assertEquals(1, violations("""
                void m(String p) {
                    Checks.checkNotNull(p, "p");
                    if (Objects.isNull(p)) { return; }
                }
                """).size());
    }

    @Test
    void reportsANullCheckOnTheVariableRequireNonNullInitialised() {
        assertEquals(1, violations("""
                void m(String p) {
                    String q = Objects.requireNonNull(p);
                    if (q != null) { q.hashCode(); }
                }
                """).size());
    }

    @Test
    void reportsANullCheckOnTheVariableRequireNonNullAssigned() {
        assertEquals(1, violations("""
                void m(String p) {
                    String q = "";
                    q = Objects.requireNonNull(p);
                    if (q != null) { q.hashCode(); }
                }
                """).size());
    }

    @Test
    void reportsANullCheckInsideACallAfterRequireNonNull() {
        assertEquals(1, violations("""
                void m(String p) {
                    Objects.requireNonNull(p);
                    String s = String.valueOf(p == null ? 1 : 2);
                }
                """).size());
    }

    @Test
    void anAssignmentBetweenTheCallsClearsTheKnowledge() {
        assertEquals(0, violations("""
                void m(String p) {
                    Objects.requireNonNull(p);
                    p = maybe();
                    if (p == null) { return; }
                }
                """).size());
    }

    // endregion

    // region (b) after a guard that leaves

    @Test
    void reportsAfterAReturnGuard() {
        assertEquals(1, violations("""
                void m(String p) {
                    if (p == null) { return; }
                    if (p != null) { p.hashCode(); }
                }
                """).size());
    }

    @Test
    void reportsAfterAThrowGuard() {
        assertEquals(1, violations("""
                void m(String p) {
                    if (p == null) { throw new IllegalArgumentException("p"); }
                    if (p == null) { return; }
                }
                """).size());
    }

    @Test
    void reportsAfterABreakGuardInsideALoop() {
        assertEquals(1, violations("""
                void m(String p) {
                    for (int i = 0; i < 3; i++) {
                        if (p == null) { break; }
                        if (p != null) { p.hashCode(); }
                    }
                }
                """).size());
    }

    @Test
    void reportsAfterAContinueGuardInsideALoop() {
        assertEquals(1, violations("""
                void m(java.util.List<String> all, String p) {
                    for (String s : all) {
                        if (p == null) { continue; }
                        if (p != null) { p.hashCode(); }
                    }
                }
                """).size());
    }

    @Test
    void reportsAfterAnElseBranchThatLeaves() {
        assertEquals(1, violations("""
                void m(String p) {
                    if (p != null) {
                        p.hashCode();
                    } else {
                        throw new IllegalArgumentException("p");
                    }
                    if (p == null) { return; }
                }
                """).size());
    }

    @Test
    void reportsInsideTheBranchOfAGuard() {
        assertEquals(1, violations("""
                void m(String p) {
                    if (p != null) {
                        if (p != null) { p.hashCode(); }
                    }
                }
                """).size());
    }

    // endregion

    // region (c) nested repeats

    @Test
    void reportsANestedRepeatAfterACall() {
        assertEquals(1, violations("""
                void m(String p) {
                    if (p != null) {
                        p.hashCode();
                        if (p != null) { return; }
                    }
                }
                """).size());
    }

    @Test
    void ignoresANestedCheckOfAnotherVariable() {
        assertEquals(0, violations("""
                void m(String p, String q) {
                    if (p != null) {
                        if (q == null) { return; }
                    }
                }
                """).size());
    }

    // endregion

    // region (d) within one condition

    @Test
    void reportsADuplicateInsideAnOrCondition() {
        assertEquals(1, violations("void m(String p) { if (p == null || p == null) { return; } }").size());
    }

    @Test
    void reportsADuplicateInsideAnAndCondition() {
        assertEquals(1, violations("void m(String p) { if (p != null && p != null) { p.hashCode(); } }").size());
    }

    @Test
    void reportsAMixedPolarityDuplicateInsideOneCondition() {
        assertEquals(1, violations("void m(String p) { if (p != null && p == null) { return; } }").size());
    }

    @Test
    void reportsADuplicateUnderANegation() {
        assertEquals(1, violations("""
                void m(String p) {
                    if (!(p != null) || Objects.isNull(p)) { return; }
                }
                """).size());
    }

    @Test
    void ignoresDistinctVariablesInsideOneCondition() {
        assertEquals(0, violations("void m(String p, String q) { if (p != null || q == null) { return; } }").size());
    }

    // endregion

    // region knowledge that must not survive

    @Test
    void aGuardDoesNotSurviveAnIfWithFallingThroughBranches() {
        assertEquals(0, violations("""
                void m(String p) {
                    if (p != null) {
                        p.hashCode();
                    } else {
                        p = "";
                    }
                    if (p == null) { return; }
                }
                """).size());
    }

    @Test
    void aGuardWithoutElseDoesNotSurviveTheIf() {
        assertEquals(0, violations("""
                void m(String p) {
                    if (p != null) {
                        p.hashCode();
                    }
                    if (p == null) { return; }
                }
                """).size());
    }

    @Test
    void anAssignmentInsideTheBranchClearsTheKnowledge() {
        assertEquals(0, violations("""
                void m(String p) {
                    if (p != null) {
                        p = maybe();
                        if (p == null) { return; }
                    }
                }
                """).size());
    }

    @Test
    void aRecheckedLoopConditionIsLegitimate() {
        assertEquals(0, violations("""
                private String read() { return null; }

                void m() {
                    String line = read();
                    while (line != null) {
                        line.hashCode();
                        line = read();
                    }
                }
                """).size());
    }

    @Test
    void anAssertPinsNothing() {
        assertEquals(0, violations("""
                void m(String p) {
                    assert p != null;
                    if (p == null) { return; }
                }
                """).size());
    }

    @Test
    void aRequireNonNullInsideAnIfConditionPinsNothing() {
        // the condition may be false without the call... it runs, but the branch join keeps it out
        assertEquals(0, violations("""
                void m(String p) {
                    if (Objects.requireNonNull(p) != null) {
                        if (p == null) { return; }
                    }
                }
                """).size());
    }

    @Test
    void aTernaryConditionDoesNotLeakOutwards() {
        assertEquals(0, violations("""
                void m(String p) {
                    String s = p != null ? p : "d";
                    if (p == null) { return; }
                }
                """).size());
    }

    @Test
    void fieldsAreNotTracked() {
        // a field can be reassigned from anywhere, so the rule only tracks locals and parameters
        assertEquals(0, violations("""
                void m() {
                    Objects.requireNonNull(field);
                    if (field == null) { return; }
                }
                """).size());
    }

    @Test
    void lambdaBodiesAreNotAnalysed() {
        // deferred execution: the rule deliberately misses checks inside lambdas
        assertEquals(0, violations("""
                void m(String p) {
                    Objects.requireNonNull(p);
                    Runnable later = () -> {
                        if (p == null) { return; }
                    };
                }
                """).size());
    }

    // endregion

    // region the report itself

    @Test
    void messageNamesTheVariableAndTheOrigin() {
        List<RuleViolation> found = violations("""
                void m(String p) {
                    Objects.requireNonNull(p);
                    if (p != null) { p.hashCode(); }
                }
                """);

        assertEquals(1, found.size());
        String message = found.get(0).getDescription();
        assertTrue(message.contains("p"), message);
        assertTrue(message.contains("non-null"), message);
        assertTrue(message.contains("requireNonNull"), message);
    }

    @Test
    void reportsOnTheLineOfTheSecondCheck() {
        String source = HEADER + """
                void m(String p) {
                    if (p == null) { return; }
                    if (p != null) { p.hashCode(); }
                }
                """ + FOOTER;
        int secondCheckLine = source.substring(0, source.indexOf("p != null")).split("\n", -1).length;

        List<RuleViolation> found = PmdRuleTestSupport.violationsOf(new DuplicateNullCheckRule(), "Test.java",
                source);

        assertEquals(1, found.size());
        assertEquals(secondCheckLine, found.get(0).getBeginLine());
    }

    @Test
    void reportsInAConstructorToo() {
        assertEquals(1, violations("""
                Test(String p) {
                    Objects.requireNonNull(p);
                    if (p == null) { throw new IllegalArgumentException("p"); }
                }
                """).size());
    }

    // endregion
}
