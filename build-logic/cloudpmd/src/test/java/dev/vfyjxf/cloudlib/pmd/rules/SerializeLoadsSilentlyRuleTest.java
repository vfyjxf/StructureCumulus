package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A-08: Serialize.create's setter position must not be a Handle setter. Handle and Serialize
 * are not on the test classpath, so the anchors go through the written-import fallback —
 * which is the same conservativeness a consumer project without CloudLib on the analysis
 * classpath gets.
 */
class SerializeLoadsSilentlyRuleTest {

    private static final String HEADER = """
            package com.example;

            import dev.vfyjxf.cloudlib.api.data.handle.Handle;
            import dev.vfyjxf.cloudlib.api.data.serialize.Serialize;

            class Box {

                String value;

                String get() { return value; }

                void set(String v) { value = v; }
            }
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new SerializeLoadsSilentlyRule(), "Test.java",
                HEADER + "\nclass Test {\n\n"
                        + "    final Handle<String> handle = null;\n"
                        + "    final Box box = new Box();\n\n"
                        + body + "\n"
                        + "}\n");
    }

    // region positives: a Handle written on load

    @Test
    void reportsAHandleSetterMethodReference() {
        List<RuleViolation> found = violations(
                "    void m(Object codec) { Serialize.create(\"name\", handle::get, handle::set, codec); }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("loads from disk"),
                found.get(0).getDescription());
    }

    @Test
    void reportsAHandleApplyMethodReference() {
        assertEquals(1, violations(
                "    void m(Object codec) { Serialize.create(\"name\", handle::get, handle::apply, codec); }").size());
    }

    @Test
    void reportsALambdaSettersToo() {
        assertEquals(1, violations(
                "    void m(Object codec) { Serialize.create(\"name\", () -> null, v -> handle.set(v), codec); }").size());
    }

    // endregion

    // region negatives

    @Test
    void acceptsTheHandleOverload() {
        assertEquals(0, violations(
                "    void m(Object codec) { Serialize.create(\"name\", handle, codec); }").size());
    }

    @Test
    void acceptsAPlainFieldSetter() {
        assertEquals(0, violations(
                "    void m(Object codec) { Serialize.create(\"name\", box::get, box::set, codec); }").size());
    }

    @Test
    void ignoresAForeignCreate() {
        // TypeDefinition.create and friends: same name, other owner
        assertEquals(0, violations("""
                    void m() {
                        Types.create("name", box::get, box::set, this);
                    }

                    static final class Types {

                        static Object create(String a, Object b, Object c, Object d) { return null; }
                    }
                """).size());
    }

    // endregion
}
