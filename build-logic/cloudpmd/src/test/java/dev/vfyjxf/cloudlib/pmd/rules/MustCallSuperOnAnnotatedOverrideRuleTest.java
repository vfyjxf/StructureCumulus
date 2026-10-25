package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A-01: an override of a @MustBeInvokedByOverriders method must call super of it. The stub
 * hierarchy mirrors the real one: WidgetGroup extends CompositeWidget extends Widget, and
 * BasicScreen extends a Minecraft-like Screen, which is where the annotation sits and the
 * legal counterexample comes from.
 */
class MustCallSuperOnAnnotatedOverrideRuleTest {

    private static final String HIERARCHY = """
            class Widget {

                void tick() {}

                void collectInspectionInfo(Object collector) {}
            }

            class CompositeWidget<T extends Widget> extends Widget {

                @Override
                void tick() { super.tick(); }

                @Override
                void collectInspectionInfo(Object collector) { super.collectInspectionInfo(collector); }
            }

            class WidgetGroup<T extends Widget> extends CompositeWidget<T> {}
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new MustCallSuperOnAnnotatedOverrideRule(), "Test.java",
                "package com.example;\n\n" + HIERARCHY + body + "\n");
    }

    // region positives: the catalogue's real shapes

    @Test
    void reportsATickOverrideThatSkipsSuper() {
        // the DevToolsWindow.ToolBar shape: a WidgetGroup subclass, an early return, no super call
        List<RuleViolation> found = violations("""
                class ToolBar extends WidgetGroup<Widget> {

                    @Override
                    public void tick() {
                        if (width() <= 0) return;
                    }

                    int width() { return 0; }
                }
                """);
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("tick() overrides"), found.get(0).getDescription());
        assertTrue(found.get(0).getDescription().contains("CompositeWidget"), found.get(0).getDescription());
    }

    @Test
    void reportsCollectInspectionInfoThroughAnAncestorChain() {
        // WidgetGroup inherits the annotated method from Widget via CompositeWidget
        assertEquals(1, violations("""
                class Row extends WidgetGroup<Widget> {

                    @Override
                    public void collectInspectionInfo(Object collector) {}
                }
                """).size());
    }

    @Test
    void reportsAWidgetSubclassTickWithoutSuper() {
        assertEquals(1, violations("""
                class Leaf extends Widget {

                    @Override
                    public void tick() {}
                }
                """).size());
    }

    // endregion

    // region negatives: super called, or nothing annotated overridden

    @Test
    void acceptsATickOverrideThatCallsSuper() {
        assertEquals(0, violations("""
                class ToolBar extends WidgetGroup<Widget> {

                    @Override
                    public void tick() {
                        super.tick();
                        if (width() <= 0) return;
                    }

                    int width() { return 0; }
                }
                """).size());
    }

    @Test
    void acceptsASuperCallAnywhereInTheBody() {
        assertEquals(0, violations("""
                class ToolBar extends WidgetGroup<Widget> {

                    @Override
                    public void tick() {
                        if (width() <= 0) { super.tick(); return; }
                    }

                    int width() { return 0; }
                }
                """).size());
    }

    @Test
    void ignoresATickOfANonWidgetClass() {
        // InspectController in the catalogue: same name, no annotated superclass anywhere
        assertEquals(0, violations("""
                class InspectController extends Controller {

                    @Override
                    public void tick() {}
                }
                """ + """
                class Controller {

                    public void tick() {}
                }
                """).size());
    }

    @Test
    void ignoresAMethodWithoutOverride() {
        assertEquals(0, violations("""
                class ToolBar extends WidgetGroup<Widget> {

                    public void tick() {}
                }
                """).size());
    }

    @Test
    void ignoresAbstractOverrides() {
        assertEquals(0, violations("""
                abstract class HalfDone extends WidgetGroup<Widget> {

                    @Override
                    public abstract void tick();
                }
                """).size());
    }

    // endregion

    // region the BasicScreen pair and the property

    @Test
    void reportsInitOverrideOfBasicScreen() {
        assertEquals(1, violations("""
                class McScreen {

                    protected void init() {}
                }

                class BasicScreen extends McScreen {

                    @Override
                    protected void init() {}
                }

                class MyScreen extends BasicScreen {

                    @Override
                    protected void init() {}
                }
                """).size());
    }

    @Test
    void acceptsBasicScreenItsOwnInit() {
        // BasicScreen.init overrides Mc Screen.init, which carries no annotation — the
        // catalogue's own legal counterexample
        assertEquals(0, violations("""
                class McScreen {

                    protected void init() {}
                }

                class BasicScreen extends McScreen {

                    @Override
                    protected void init() {}
                }
                """).size());
    }

    @Test
    void initOfAPlainWidgetSubclassIsNotInTheTable() {
        assertEquals(0, violations("""
                class Leaf extends Widget {

                    public void init() {}
                }
                """).size());
    }

    @Test
    void thePairTableIsAProperty() {
        MustCallSuperOnAnnotatedOverrideRule rule = new MustCallSuperOnAnnotatedOverrideRule();
        PmdRuleTestSupport.setProperty(rule, "annotatedMethods", List.of("CustomBase.render"));
        List<RuleViolation> found = PmdRuleTestSupport.violationsOf(rule, "Test.java", """
                package com.example;

                class CustomBase {

                    public void render() {}
                }

                class Leaf extends CustomBase {

                    @Override
                    public void render() {}
                }
                """);
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("CustomBase"), found.get(0).getDescription());
    }

    // endregion
}
