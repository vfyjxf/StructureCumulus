package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A-11: a declared expose channel or kind must be registered in the same file. The channel
 * stubs sit in ExposeChannel's real package, the kind stubs in ExposeKind's, so both registry
 * chains are anchored the way production anchors them.
 */
class FrameworkIdMustBeRegisteredRuleTest {

    private static List<RuleViolation> channelViolations(String body) {
        return PmdRuleTestSupport.violationsOf(new FrameworkIdMustBeRegisteredRule(), "Test.java",
                "package dev.vfyjxf.cloudlib.api.data.expose;\n\n"
                        + "record ExposeChannel<T>(Object id, Object codec) {\n\n"
                        + "    static <T> ExposeChannel<T> of(Object id, Object codec) { return null; }\n"
                        + "}\n\n"
                        + "final class ExposeChannelRegistry {\n\n"
                        + "    static <C> C register(C channel) { return channel; }\n"
                        + "}\n\n"
                        + "class Test {\n\n"
                        + body + "\n"
                        + "}\n");
    }

    private static List<RuleViolation> kindViolations(String body) {
        return PmdRuleTestSupport.violationsOf(new FrameworkIdMustBeRegisteredRule(), "Test.java",
                "package dev.vfyjxf.cloudlib.api.network.expose.subscription;\n\n"
                        + "record ExposeKind(Object id) {\n\n"
                        + "    static ExposeKind of(Object id) { return null; }\n"
                        + "}\n\n"
                        + "final class ExposeKindRegistry {\n\n"
                        + "    static <C> C register(C kind) { return kind; }\n"
                        + "}\n\n"
                        + "class Test {\n\n"
                        + body + "\n"
                        + "}\n");
    }

    // region positives

    @Test
    void reportsAChannelDeclaredWithoutAnyRegistration() {
        List<RuleViolation> found = channelViolations(
                "    static final ExposeChannel<Object> CHANNEL = ExposeChannel.of(new Object(), new Object());");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("ExposeChannelRegistry"),
                found.get(0).getDescription());
    }

    @Test
    void reportsAKindDeclaredWithoutAnyRegistration() {
        List<RuleViolation> found = kindViolations(
                "    static final ExposeKind KIND = ExposeKind.of(new Object());");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("ExposeKindRegistry"),
                found.get(0).getDescription());
    }

    @Test
    void theMarkerLineIsTheEscapeHatch() {
        assertEquals(0, channelViolations(
                "    static final ExposeChannel<Object> CHANNEL = ExposeChannel.of(id(), codec()); // framework-id-unregistered-ok\n"
                        + "    static Object id() { return new Object(); }\n"
                        + "    static Object codec() { return new Object(); }").size());
    }

    // endregion

    // region negatives

    @Test
    void acceptsAChannelRegisteredDirectly() {
        assertEquals(0, channelViolations("""
                    static final ExposeChannel<Object> CHANNEL =
                            ExposeChannelRegistry.register(ExposeChannel.of(new Object(), new Object()));
                """).size());
    }

    @Test
    void acceptsAChannelWhenTheFileRegistersAnotherOne() {
        assertEquals(0, channelViolations("""
                    static final ExposeChannel<Object> DECLARED = ExposeChannel.of(new Object(), new Object());
                    static final ExposeChannel<Object> REGISTERED =
                            ExposeChannelRegistry.register(ExposeChannel.of(new Object(), new Object()));
                """).size());
    }

    @Test
    void acceptsARegisteredKind() {
        assertEquals(0, kindViolations(
                "    static final ExposeKind KIND = ExposeKindRegistry.register(ExposeKind.of(new Object()));").size());
    }

    @Test
    void ignoresAForeignOf() {
        assertEquals(0, channelViolations("""
                    static final Object VALUE = java.util.Optional.of(new Object());
                """).size());
    }

    // endregion
}
