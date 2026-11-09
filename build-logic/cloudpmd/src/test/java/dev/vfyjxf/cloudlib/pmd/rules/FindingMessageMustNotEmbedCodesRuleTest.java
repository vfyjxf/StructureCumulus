package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * K06-D06: a Finding message that embeds its own code renders with the code twice, because the
 * renderer prefixes the code. The rejection texts (BlueprintBuilder's fail, the IAE of Check and
 * Catalog.put) are not Finding messages and must stay legal.
 */
class FindingMessageMustNotEmbedCodesRuleTest {

    private static final String HEADER = """
            package com.example;

            class Finding {

                static Builder builder(Object entry) { return new Builder(); }

                static final class Builder {

                    Builder phase(Object phase) { return this; }

                    Builder message(String message) { return this; }

                    Builder note(String note) { return this; }

                    Object build() { return null; }
                }
            }

            class Phase {}
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new FindingMessageMustNotEmbedCodesRule(),
                "Test.java", HEADER + "\nclass Test {\n\n" + body + "\n}\n");
    }

    // region positives: the original defect shapes

    @Test
    void reportsACodeEmbeddedInABuilderChainMessage() {
        List<RuleViolation> found = violations(
                "    Object finding = Finding.builder(entry).phase(new Phase())"
                        + ".message(\"SC-OCC-0004 the required read was refused\").build();");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("SC-OCC-0004"), found.get(0).getDescription());
    }

    @Test
    void reportsACodeEmbeddedMidSentence() {
        assertEquals(1, violations(
                "    Object finding = Finding.builder(entry).phase(new Phase())"
                        + ".message(\"the read failed and stays SC-OCC-0004\").build();").size());
    }

    @Test
    void reportsACodeEmbeddedThroughASplitBuilderLocal() {
        assertEquals(1, violations("""
                void report(Object entry) {
                    Finding.Builder builder = Finding.builder(entry).phase(new Phase());
                    builder.message("SC-SET-0007 the cleanup failed");
                }
                """).size());
    }

    @Test
    void reportsACodeEmbeddedInACodeHelperMessage() {
        List<RuleViolation> found = violations("""
                void report(Object entry) {
                    Object finding = code(entry, new Phase(), "SC-SPI-0001 the deriver threw", null);
                }

                static Finding code(Object entry, Phase phase, String message, Object locus) {
                    return Finding.builder(entry).phase(phase).message(message).build();
                }
                """);
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("code(...) helper"), found.get(0).getDescription());
    }

    // endregion

    // region negatives: the legal messages and the deliberate code carriers

    @Test
    void acceptsAMessageWithoutACode() {
        assertEquals(0, violations(
                "    Object finding = Finding.builder(entry).phase(new Phase())"
                        + ".message(\"the required read was refused\").build();").size());
    }

    @Test
    void acceptsAVariableMessageAndTheEntryAccessor() {
        assertEquals(0, violations("""
                void report(Object entry) {
                    String message = entry.toString();
                    Object finding = Finding.builder(entry).phase(new Phase()).message(message).build();
                }
                """).size());
    }

    @Test
    void acceptsANoteThatNamesAnotherCode() {
        assertEquals(0, violations("""
                void report(Object entry) {
                    Finding.builder(entry).phase(new Phase()).message("relayed")
                            .note("reported as SC-OCC-0004").build();
                }
                """).size());
    }

    @Test
    void acceptsTheBlueprintRejectionHelperThatTakesTheCode() {
        assertEquals(0, violations("""
                void build() {
                    fail("SC-BPT-0001", "a blueprint needs bounds");
                }

                static void fail(String code, String message) {}
                """).size());
    }

    @Test
    void acceptsAnIaeMessageThatCarriesTheCode() {
        assertEquals(0, violations("""
                void check() {
                    throw new IllegalArgumentException("SC-CHK-0001: focus and expectation subject differ");
                }
                """).size());
    }

    @Test
    void ignoresAForeignMessageCall() {
        assertEquals(0, violations("""
                void relay(Object entry) {
                    String text = relay(entry, "SC-OCC-0004 relayed");
                }

                String relay(Object entry, String message) { return message; }
                """).size());
    }

    @Test
    void ignoresACodeHelperThatReturnsAFindingCode() {
        assertEquals(0, violations("""
                void register() {
                    Object code = code(1);
                }

                static Object code(int number) { return null; }
                """).size());
    }

    // endregion
}
