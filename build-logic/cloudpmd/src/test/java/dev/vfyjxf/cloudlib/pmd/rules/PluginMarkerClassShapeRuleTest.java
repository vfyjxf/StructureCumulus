package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A-03: a @PluginMarker class must be instantiable by AnnotationPluginLookup, and an
 * unmarked class implementing a plugin interface is invisible to it. The stubs sit in the
 * annotation's real package, and the known-interface list is the framework's own plugin
 * hierarchy.
 */
class PluginMarkerClassShapeRuleTest {

    private static final String STUBS = """
            @interface PluginMarker {}

            interface ModPlugin {}

            interface CloudLibPlugin extends ModPlugin {}

            interface ClientNimbusPlugin extends ModPlugin {}

            interface LocalPlugin extends ModPlugin {}
            """;

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new PluginMarkerClassShapeRule(), "Test.java",
                "package dev.vfyjxf.cloudlib.api.plugin;\n\n" + STUBS + "\n" + body + "\n");
    }

    // region shape 1: the marked class

    @Test
    void reportsAMarkedClassWithoutPluginInterfaceAndConstructor() {
        List<RuleViolation> found = violations("""
                @PluginMarker
                public class Marked {

                    Marked(String argument) {}
                }
                """);
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(v -> v.getDescription().contains("no public no-arg")),
                () -> found.toString());
        assertTrue(found.stream().anyMatch(v -> v.getDescription().contains("no ModPlugin")),
                () -> found.toString());
    }

    @Test
    void reportsAMarkedClassWithOnlyAParameterizedConstructor() {
        assertEquals(1, violations("""
                @PluginMarker
                public class Marked implements CloudLibPlugin {

                    Marked(String argument) {}
                }
                """).size());
    }

    @Test
    void reportsAnAbstractMarkedClass() {
        List<RuleViolation> found = violations("""
                @PluginMarker
                public abstract class Marked implements CloudLibPlugin {}
                """);
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("abstract"), found.get(0).getDescription());
    }

    @Test
    void reportsANonStaticNestedMarkedClass() {
        assertEquals(1, violations("""
                class Outer {

                    @PluginMarker
                    public class Inner implements CloudLibPlugin {}
                }
                """).size());
    }

    @Test
    void acceptsAWellFormedMarkedClass() {
        assertEquals(0, violations("""
                @PluginMarker
                public class Marked implements CloudLibPlugin {}
                """).size());
    }

    @Test
    void aPackagePrivateClassIsDroppedToo() {
        assertEquals(1, violations("""
                @PluginMarker
                class Hidden implements CloudLibPlugin {}
                """).size());
    }

    @Test
    void aPackagePrivateConstructorIsNotPublic() {
        assertEquals(1, violations("""
                @PluginMarker
                public class Marked implements CloudLibPlugin {

                    Marked() {}
                }
                """).size());
    }

    @Test
    void acceptsAMarkedClassImplementingASameFilePluginInterface() {
        assertEquals(0, violations("""
                @PluginMarker
                public class Marked implements LocalPlugin {}
                """).size());
    }

    // endregion

    // region shape 2: the unmarked plugin implementation

    @Test
    void reportsAnUnmarkedPluginImplementation() {
        assertEquals(1, violations("""
                class Quiet implements CloudLibPlugin {}
                """).size());
    }

    @Test
    void theExemptPropertyCarriesTheHandInjectedPlugin() {
        assertEquals(0, violations("""
                class NimbusEnginePlugin implements ClientNimbusPlugin {}
                """).size());
    }

    @Test
    void anAbstractPluginBaseIsSkipped() {
        assertEquals(0, violations("""
                abstract class PluginBase implements CloudLibPlugin {}
                """).size());
    }

    @Test
    void anInterfaceIsNeverAPluginImplementation() {
        assertEquals(0, violations("""
                interface SubPlugin extends CloudLibPlugin {}
                """).size());
    }

    @Test
    void anUnrelatedClassIsNotAPlugin() {
        assertEquals(0, violations("""
                class Bystander {}
                """).size());
    }

    // endregion
}
