package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A-06: the two unimplemented public API stubs must not be called. Each stub lives in its
 * owner's real package, so the receiver anchor is the production one; setDragging and any
 * foreign grid stay legal.
 */
class NoCallToUnimplementedStubRuleTest {

    private static List<RuleViolation> widgetViolations(String body) {
        return PmdRuleTestSupport.violationsOf(new NoCallToUnimplementedStubRule(), "Test.java",
                "package dev.vfyjxf.cloudlib.api.ui.base;\n\n"
                        + "class Widget {\n\n"
                        + "    Widget setDraggable(boolean draggable) { throw new UnsupportedOperationException(); }\n\n"
                        + "    Widget setDragging(boolean dragging) { return this; }\n"
                        + "}\n\n"
                        + "class Test {\n\n"
                        + "    final Widget widget = new Widget();\n\n"
                        + body + "\n"
                        + "}\n");
    }

    private static List<RuleViolation> widgetsViolations(String body) {
        return PmdRuleTestSupport.violationsOf(new NoCallToUnimplementedStubRule(), "Test.java",
                "package dev.vfyjxf.cloudlib.api.ui;\n\n"
                        + "class Widgets {\n\n"
                        + "    static Object grid() { throw new UnsupportedOperationException(); }\n"
                        + "}\n\n"
                        + "class Other {\n\n"
                        + "    Object grid() { return null; }\n"
                        + "}\n\n"
                        + "class Test {\n\n"
                        + "    final Other other = new Other();\n\n"
                        + body + "\n"
                        + "}\n");
    }

    // region positives

    @Test
    void reportsSetDraggable() {
        List<RuleViolation> found = widgetViolations("    void m() { widget.setDraggable(true); }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("setDraggable"), found.get(0).getDescription());
    }

    @Test
    void reportsWidgetsGrid() {
        List<RuleViolation> found = widgetsViolations("    void m() { Widgets.grid(); }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("Widgets"), found.get(0).getDescription());
    }

    // endregion

    // region negatives

    @Test
    void acceptsSetDragging() {
        assertEquals(0, widgetViolations("    void m() { widget.setDragging(true); }").size());
    }

    @Test
    void acceptsAForeignGrid() {
        assertEquals(0, widgetsViolations("    void m() { other.grid(); }").size());
    }

    @Test
    void theStubListIsAProperty() {
        NoCallToUnimplementedStubRule rule = new NoCallToUnimplementedStubRule();
        PmdRuleTestSupport.setProperty(rule, "stubs", List.of("com.example.Widget#grid"));
        List<RuleViolation> found = PmdRuleTestSupport.violationsOf(rule, "Test.java", """
                package com.example;

                class Widget {

                    Object grid() { return null; }
                }

                class Test {

                    final Widget widget = new Widget();

                    void m() { widget.grid(); }
                }
                """);
        assertEquals(1, found.size());
    }

    // endregion
}
