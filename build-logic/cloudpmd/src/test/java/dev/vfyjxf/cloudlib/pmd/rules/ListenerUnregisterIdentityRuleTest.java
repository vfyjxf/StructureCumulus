package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A-09: the event layer identifies listeners by reference. Shape 1 — unregister of a freshly
 * created listener; shape 2 — unregister after registerManaged, which stores a wrapper. The
 * event types are anchored through their real canonical names.
 */
class ListenerUnregisterIdentityRuleTest {

    private static final String HEADER = """
            package com.example;

            import dev.vfyjxf.cloudlib.api.event.EventDefinition;

            class Bus {

                void unregister(Object listener) {}

                void registerManaged(Object listener, int lifetime) {}
            }
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new ListenerUnregisterIdentityRule(), "Test.java",
                HEADER + "\nclass Test {\n\n"
                        + "    final EventDefinition<Object> event = null;\n"
                        + "    final Object listener = new Object();\n"
                        + "    final Bus bus = new Bus();\n\n"
                        + body + "\n"
                        + "}\n");
    }

    // region shape 1: a fresh instance can never match

    @Test
    void reportsUnregisterOfAnInlineLambda() {
        List<RuleViolation> found = violations("    void m() { event.unregister(x -> {}); }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("no-op"), found.get(0).getDescription());
    }

    @Test
    void reportsUnregisterOfAMethodReference() {
        assertEquals(1, violations("    void m() { event.unregister(this::onClick); }").size());
    }

    @Test
    void reportsIsRegisteredOfANewObject() {
        assertEquals(1, violations("    void m() { event.isRegistered(new Object()); }").size());
    }

    // endregion

    // region shape 2: registerManaged wrapped the listener

    @Test
    void reportsUnregisterAfterRegisterManagedOfTheSameListener() {
        List<RuleViolation> found = violations("""
                    void m() {
                        event.registerManaged(this::onClick, 5);
                        event.unregister(this::onClick);
                    }
                """);
        // both shapes fire on the same call: the argument is a method reference, and its text
        // was registered managed
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(v -> v.getDescription().contains("registerManaged")),
                () -> found.toString());
    }

    @Test
    void reportsUnregisterAfterRegisterManagedOfAStableReference() {
        // the pure shape 2: a stable reference that registerManaged wrapped
        List<RuleViolation> found = violations("""
                    void m() {
                        event.registerManaged(listener, 5);
                        event.unregister(listener);
                    }
                """);
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("registerManaged"),
                found.get(0).getDescription());
    }

    // endregion

    // region negatives

    @Test
    void acceptsUnregisterOfAStableReference() {
        assertEquals(0, violations("    void m() { event.unregister(listener); }").size());
    }

    @Test
    void acceptsRegisterManagedOfAStableReference() {
        assertEquals(0, violations("    void m() { event.registerManaged(listener, 5); }").size());
    }

    @Test
    void ignoresAForeignUnregister() {
        // a Bus.unregister is not the event layer, whatever its argument is
        assertEquals(0, violations("    void m() { bus.unregister(x -> {}); }").size());
    }

    @Test
    void unregisterOfAnotherListenerTextIsNotTheManagedPairing() {
        assertEquals(1, violations("""
                    void m() {
                        event.registerManaged(listener, 5);
                        event.unregister(this::onClick);
                    }
                """).size());
    }

    // endregion
}
