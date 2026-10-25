package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A-10: an event definition must be evaluated exactly once, in a field initializer. The
 * anchors keep ModConfigSpec.Builder#define — same name, other owner — out.
 */
class EventDefinitionMustBeEvaluatedOnceRuleTest {

    private static final String HEADER = """
            package com.example;

            import java.util.function.Supplier;

            import dev.vfyjxf.cloudlib.api.event.Event;
            import dev.vfyjxf.cloudlib.api.event.Events;

            class ModConfigSpec {

                static class Builder {

                    Builder define(String key) { return this; }
                }
            }
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new EventDefinitionMustBeEvaluatedOnceRule(),
                "Test.java", HEADER + "\nclass Test {\n\n"
                        + "    final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();\n\n"
                        + body + "\n"
                        + "}\n");
    }

    // region positives: re-evaluated identities

    @Test
    void reportsADefineInsideAMethod() {
        List<RuleViolation> found = violations(
                "    void m() { Object d = Events.define(Object.class, null); }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("fresh identity"),
                found.get(0).getDescription());
    }

    @Test
    void reportsADefineInsideALambdaEvenInAFieldInitializer() {
        assertEquals(1, violations(
                "    final Supplier<Object> supplied = () -> Events.define(Object.class, null);").size());
    }

    @Test
    void reportsACreateGenericInsideAMethod() {
        assertEquals(1, violations("""
                    void m() {
                        Object d = Events.createGeneric(null, null);
                    }
                """).size());
    }

    @Test
    void reportsAnEventCreateInsideAConstructor() {
        assertEquals(1, violations("""
                    Test() {
                        Object e = Event.create(null);
                    }
                """).size());
    }

    // endregion

    // region negatives: the legal shapes and the same-name interference

    @Test
    void acceptsADefineInAFieldInitializer() {
        assertEquals(0, violations(
                "    static final Object DEFINED = Events.define(Object.class, null);").size());
    }

    @Test
    void acceptsADefineAsAnInterfaceConstant() {
        // an interface constant is a field initializer with an implicit static final
        String source = HEADER + """
                interface EventsHolder {

                    Object DEFINED = Events.define(Object.class, null);
                }
                """;
        assertEquals(0, PmdRuleTestSupport.violationsOf(new EventDefinitionMustBeEvaluatedOnceRule(),
                "Test.java", source).size());
    }

    @Test
    void acceptsADefineInAStaticInitializerBlock() {
        assertEquals(0, violations("""
                    static final Object DEFINED;
                    static {
                        DEFINED = Events.define(Object.class, null);
                    }
                """).size());
    }

    @Test
    void ignoresAModConfigSpecBuilderDefine() {
        assertEquals(0, violations("""
                    void m() {
                        Object d = builder.define("some.key");
                    }
                """).size());
    }

    @Test
    void ignoresAForeignCreate() {
        assertEquals(0, violations("""
                    void m() {
                        Object d = create();
                    }

                    Object create() { return null; }
                """).size());
    }

    // endregion
}
