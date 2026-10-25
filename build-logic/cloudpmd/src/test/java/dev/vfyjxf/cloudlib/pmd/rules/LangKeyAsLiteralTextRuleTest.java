package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

/**
 * A-02: the raw exits of LangEntry — key() and string() — must land in the slot that wants
 * that kind of string. The whole compilation unit sits in LangEntry's real package, so the
 * type anchor resolves exactly as it does in production, and the same-file LangFile stub is
 * the real package's LangFile for the key-sink whitelist.
 */
class LangKeyAsLiteralTextRuleTest {

    private static final String STUBS = """
            record LangEntry(String key) {

                String string() { return ""; }
            }

            final class LangFile {

                LangFile add(String key, String value) { return this; }
            }

            final class KVRowWidget {

                static Object of(String label, Object value) { return null; }
            }

            final class Holder {

                Holder(String text) {}
            }
            """;

    /** The unit under test, in LangEntry's own package so every anchor is the real FQN. */
    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new LangKeyAsLiteralTextRule(), "Test.java",
                "package dev.vfyjxf.cloudlib.data.lang;\n\n"
                        + "import java.util.Map;\n\n"
                        + STUBS + "\n"
                        + "class Test {\n"
                        + "    final LangEntry entry = new LangEntry(\"nimbus.content.items\");\n"
                        + "    final Map<String, String> map = Map.of();\n"
                        + "    final LangFile file = new LangFile();\n\n"
                        + body
                        + "}\n");
    }

    // region positives: raw key into a text slot

    @Test
    void reportsAKeyIntoAStringLabelSlot() {
        // the StoresPresenter shape: KVRowWidget.of(String, RichText) paints the bare key
        List<RuleViolation> found = violations(
                "    void m() { Object row = KVRowWidget.of(entry.key(), 3); }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("bare translation key"),
                found.get(0).getDescription());
        assertTrue(found.get(0).getDescription().contains("KVRowWidget"), found.get(0).getDescription());
    }

    @Test
    void reportsAKeyIntoAConstructorTextSlot() {
        assertEquals(1, violations(
                "    void m() { new Holder(entry.key()); }").size());
    }

    @Test
    void reportsAKeyIntoSetText() {
        assertEquals(1, violations("    void m() { take(entry.key()); }\n\n"
                + "    void take(String text) {}").size());
    }

    // endregion

    // region positives: translated text into a key slot

    @Test
    void reportsAStringIntoALangFileKeySlot() {
        List<RuleViolation> found = violations(
                "    void m() { file.add(entry.string(), \"value\"); }");
        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("already-translated"),
                found.get(0).getDescription());
    }

    // endregion

    // region negatives: key sinks, other exits, same-name interference

    @Test
    void acceptsAKeyIntoTheLangFileKeySlot() {
        assertEquals(0, violations(
                "    void m() { file.add(entry.key(), \"value\"); }").size());
    }

    @Test
    void acceptsAKeyIntoMapGet() {
        assertEquals(0, violations(
                "    void m() { map.get(entry.key()); }").size());
    }

    /**
     * The RichText.of(LangEntry, args) shape: TranslatableNode's first parameter is the
     * translation key itself, so the raw exit lands in the right slot. Anchored through the
     * exact single-type import, the way production anchors it through the classpath.
     */
    @Test
    void acceptsAKeyIntoTheTranslatableNodeConstructor() {
        String source = """
                package dev.vfyjxf.cloudlib.data.lang;

                import dev.vfyjxf.cloudlib.api.text.TranslatableNode;

                record LangEntry(String key) {

                    String string() { return ""; }
                }

                final class KVRowWidget {

                    static Object of(String label, Object value) { return null; }
                }

                class Test {

                    final LangEntry entry = new LangEntry("nimbus.content.items");

                    void right() { Object node = new TranslatableNode(entry.key()); }

                    void control() { Object row = KVRowWidget.of(entry.key(), 3); }
                }
                """;
        List<RuleViolation> found = PmdRuleTestSupport.violationsOf(new LangKeyAsLiteralTextRule(),
                "Test.java", source);
        assertEquals(1, found.size(), "only the text-slot control may be reported");
        assertTrue(found.get(0).getDescription().contains("KVRowWidget"), found.get(0).getDescription());
    }

    @Test
    void acceptsAStringIntoATextSlot() {
        assertEquals(0, violations(
                "    void m() { KVRowWidget.of(entry.string(), 3); }").size());
    }

    @Test
    void ignoresArgumentsThatAreNoRawExits() {
        assertEquals(0, violations("    void m() {\n"
                + "        new Holder(entryToString());\n"
                + "    }\n\n"
                + "    String entryToString() { return \"\"; }").size());
    }

    @Test
    void ignoresALocalVariableKeyThatIsNoLangEntry() {
        // the 91 same-name .key() calls of the two projects: a receiver of another type
        assertEquals(0, violations("""
                    void m() {
                        Profile profile = new Profile();
                        KVRowWidget.of(profile.key(), 3);
                    }

                    record Profile(String name) {

                        String key() { return name; }
                    }
                """).size());
    }

    @Test
    void ignoresANonStringParameterSlot() {
        assertEquals(0, violations("    void m() { take(entry.key()); }\n\n"
                + "    void take(Object anything) {}").size());
    }

    // endregion
}
