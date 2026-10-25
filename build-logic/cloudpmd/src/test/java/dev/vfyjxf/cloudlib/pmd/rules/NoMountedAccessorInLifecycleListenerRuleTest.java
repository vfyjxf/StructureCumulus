package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A-04: a lifecycle listener must not read its own widget's mounted-gate accessors. The stub
 * Widget sits in its real package, so the owner resolution that separates the gates from
 * BasicScreen.scene() and SceneHandle.scene() is the production one.
 */
class NoMountedAccessorInLifecycleListenerRuleTest {

    private static final String STUBS = """
            class Scene { int x() { return 0; } }

            class SceneContext {}

            class SceneHandle {

                Scene scene() { return new Scene(); }
            }

            class NodeId {}

            @FunctionalInterface
            interface MountListener {

                void onMount(Scene scene, SceneContext context, SceneHandle handle);
            }

            class Widget {

                Widget onInit(Runnable listener) { return this; }

                Widget onMount(MountListener listener) { return this; }

                Widget onUnmount(Runnable listener) { return this; }

                Widget onDestroy(Runnable listener) { return this; }

                Scene scene() { return null; }

                SceneContext context() { return null; }

                Object path() { return null; }

                Object layout() { return null; }

                void applyLayout() {}

                boolean needsLayout() { return false; }

                NodeId nodeId() { return null; }
            }
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new NoMountedAccessorInLifecycleListenerRule(),
                "Test.java",
                "package dev.vfyjxf.cloudlib.api.ui.base;\n\n" + STUBS + "\n"
                        + "class Test extends Widget {\n"
                        + "    final Widget widget = new Widget();\n\n"
                        + body
                        + "}\n");
    }

    // region positives: the gates inside a listener

    @Test
    void reportsAnUnqualifiedSceneInsideOnMount() {
        List<RuleViolation> found = violations(
                "    void m() { widget.onMount((scene, context, handle) -> scene().x()); }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("scene()"), found.get(0).getDescription());
        assertTrue(found.get(0).getDescription().contains("onMount"), found.get(0).getDescription());
    }

    @Test
    void reportsAnUnqualifiedContextInsideOnUnmount() {
        // unmount clears scene and context before the listener runs
        assertEquals(1, violations(
                "    void m() { widget.onUnmount(() -> context().hashCode()); }").size());
    }

    @Test
    void reportsGatesInABlockBodiedListener() {
        assertEquals(1, violations("""
                    void m() {
                        widget.onUnmount(() -> {
                            needsLayout();
                        });
                    }
                """).size());
    }

    // endregion

    // region negatives: the catalogue's exemptions

    @Test
    void acceptsReadingTheGateThroughTheListenerParameter() {
        assertEquals(0, violations(
                "    void m() { widget.onMount((scene, context, handle) -> handle.scene().x()); }").size());
    }

    @Test
    void acceptsNodeIdInsideOnMount() {
        // nodeId is assigned before onMount dispatch and javadoc-permitted there
        assertEquals(0, violations(
                "    void m() { widget.onMount((scene, context, handle) -> nodeId()); }\n\n"
                + "    NodeId nodeId() { return new NodeId(); }").size());
    }

    @Test
    void acceptsAQualifiedCallOnAnotherWidget() {
        assertEquals(0, violations("""
                    void m(Widget other) {
                        widget.onMount((scene, context, handle) -> other.scene().x());
                    }
                """).size());
    }

    @Test
    void ignoresASameNameMethodOfTheEnclosingClass() {
        // a subclass that redeclares scene() has its own contract, not the mounted gate
        assertEquals(0, violations("""
                    class Host extends Widget {

                        @Override
                        Scene scene() { return new Scene(); }

                        void m() {
                            widget.onMount((s, c, h) -> scene().x());
                        }
                    }
                """).size());
    }

    @Test
    void ignoresGatesOutsideAnyListener() {
        assertEquals(0, violations("    void m() { scene().x(); }\n\n"
                + "    Scene scene() { return null; }").size());
    }

    // endregion
}
