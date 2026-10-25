package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A-07: the receive hook may only apply. The Handle stub sits in its real package so the
 * argument anchor — the receiver of ::set / ::load must be a Handle — is the production one,
 * which is what keeps AtomicReference::set and ModConfigSpec setters legal.
 */
class ReceiveHookUsesApplyRuleTest {

    private static final String STUBS = """
            import java.util.function.Consumer;
            import java.util.concurrent.atomic.AtomicReference;

            interface Handle<T> {

                void set(T value);

                void apply(T value);

                void load(T value);

                void whenReceive(Consumer<T> consumer);

                void whenReceiveFromClient(Consumer<T> consumer);
            }
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new ReceiveHookUsesApplyRule(), "Test.java",
                "package dev.vfyjxf.cloudlib.api.data.handle;\n\n"
                        + STUBS + "\n"
                        + "class Test<T> {\n\n"
                        + "    final Handle<T> handle = null;\n"
                        + "    final AtomicReference<Object> atomic = new AtomicReference<>();\n\n"
                        + body + "\n"
                        + "}\n");
    }

    // region positives: the wrong write modes

    @Test
    void reportsASetMethodReference() {
        List<RuleViolation> found = violations("    void m() { handle.whenReceive(handle::set); }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("echoes"), found.get(0).getDescription());
    }

    @Test
    void reportsALoadMethodReference() {
        List<RuleViolation> found = violations("    void m() { handle.whenReceive(handle::load); }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("never fires the listeners"),
                found.get(0).getDescription());
    }

    @Test
    void reportsALambdaWritingWithSet() {
        assertEquals(1, violations("    void m() { handle.whenReceive(v -> handle.set(v)); }").size());
    }

    @Test
    void reportsTheClientVariantToo() {
        assertEquals(1, violations(
                "    void m() { handle.whenReceiveFromClient(handle::set); }").size());
    }

    // endregion

    // region negatives

    @Test
    void acceptsApply() {
        assertEquals(0, violations("    void m() { handle.whenReceive(handle::apply); }").size());
    }

    @Test
    void acceptsALambdaApplying() {
        assertEquals(0, violations("    void m() { handle.whenReceive(v -> handle.apply(v)); }").size());
    }

    @Test
    void ignoresASetOnAnotherType() {
        // AtomicReference::set as the receive hook — same method name, other receiver
        assertEquals(0, violations("    void m() { handle.whenReceive(atomic::set); }").size());
    }

    // endregion
}
