package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A-05: a registration-window handle must not survive the dispatch it belongs to. The
 * catalogue's four shapes plus the two let-outs: helper calls, and a class that implements a
 * *Register interface itself.
 */
class RegisterHandleMustNotEscapeRuleTest {

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new RegisterHandleMustNotEscapeRule(), "Test.java",
                "package com.example;\n\n"
                        + "import java.util.concurrent.CompletableFuture;\n\n"
                        + "interface ContentRegister {\n\n"
                        + "    void register(Object entry);\n"
                        + "}\n\n"
                        + body + "\n");
    }

    // region positives: the escape shapes

    @Test
    void reportsStoringTheHandleInAField() {
        List<RuleViolation> found = violations("""
                class Test {

                    Object saved;

                    void dispatch(ContentRegister register) {
                        this.saved = register;
                    }
                }
                """);
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("stored into a field"),
                found.get(0).getDescription());
    }

    @Test
    void reportsHandingTheHandleToTheAsyncMachinery() {
        assertEquals(1, violations("""
                class Test {

                    void dispatch(ContentRegister register) {
                        CompletableFuture.runAsync(() -> register.register(new Object()));
                    }
                }
                """).size());
    }

    @Test
    void reportsAFieldInitializedFromTheHandle() {
        // an anonymous class can see the enclosing parameter in its field initializer
        assertEquals(1, violations("""
                class Test {

                    void dispatch(ContentRegister register) {
                        Runnable r = new Runnable() {

                            final Object captured = register;

                            public void run() {}
                        };
                    }
                }
                """).size());
    }

    // endregion

    // region negatives: the let-outs

    @Test
    void acceptsCallingMethodsOnTheHandle() {
        assertEquals(0, violations("""
                class Test {

                    void dispatch(ContentRegister register) {
                        register.register(new Object());
                    }
                }
                """).size());
    }

    @Test
    void acceptsPassingTheHandleToAHelper() {
        assertEquals(0, violations("""
                class Test {

                    void dispatch(ContentRegister register) {
                        registerAll(register);
                    }

                    static void registerAll(ContentRegister register) {
                        register.register(new Object());
                    }
                }
                """).size());
    }

    @Test
    void acceptsAHelperCallInsideACallback() {
        // the catalogue's let-out 1: passing through to a helper inside a lambda is fine
        assertEquals(0, violations("""
                class Test {

                    void dispatch(ContentRegister register) {
                        CompletableFuture.runAsync(() -> registerAll(register));
                    }

                    static void registerAll(ContentRegister register) {
                        register.register(new Object());
                    }
                }
                """).size());
    }

    @Test
    void acceptsARegisterImplementationClass() {
        // the let-out 2: the machinery itself takes and returns handles legitimately
        assertEquals(0, violations("""
                class Dispatching implements ContentRegister {

                    Object entries;

                    public void register(Object entry) {}

                    void forward(ContentRegister register) {
                        entries = register;
                    }
                }
                """).size());
    }

    @Test
    void ignoresParametersThatAreNoHandles() {
        assertEquals(0, violations("""
                class Test {

                    Object saved;

                    void dispatch(String register) {
                        this.saved = register;
                    }
                }
                """).size());
    }

    // endregion
}
