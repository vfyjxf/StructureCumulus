package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

class FqnRuleTest {

    private static String classSource(String body) {
        return "package com.example;\n\nclass Thing {\n" + body + "\n}\n";
    }

    private static List<RuleViolation> violations(String source) {
        return PmdRuleTestSupport.violationsOf(new FqnRule(), "Thing.java", source);
    }

    private static List<RuleViolation> violations(String fileName, String source) {
        return PmdRuleTestSupport.violationsOf(new FqnRule(), fileName, source);
    }

    @Test
    void defaultPatternIsTheLintsPattern() {
        FqnRule rule = new FqnRule();
        assertEquals("\\b(?:[a-z_]\\w*\\.){2,}[A-Z]\\w*(?:\\.[A-Z]\\w*)*",
                String.valueOf(rule.getProperty(rule.getPropertyDescriptor("fqnPattern"))));
    }

    @Test
    void reportsQualifiedTypeReference() {
        List<RuleViolation> found = violations(classSource("    private java.util.Map<String, Integer> m;"));

        assertEquals(1, found.size());
        assertEquals(4, found.get(0).getBeginLine());
        assertTrue(found.get(0).getDescription().contains("java.util.Map"), found.get(0).getDescription());
    }

    @Test
    void reportsRedundantQualificationOfImportedType() {
        List<RuleViolation> found = violations("package com.example;\n\nimport java.util.Map;\n\nclass Thing {\n"
                + "    private java.util.Map<String, Integer> m;\n}\n");

        assertEquals(1, found.size());
        assertEquals(6, found.get(0).getBeginLine());
        assertTrue(found.get(0).getDescription().contains("java.util.Map"), found.get(0).getDescription());
    }

    @Test
    void allowsQualificationForcedByImportCollision() {
        // java.awt.List is imported, so java.util.List has to stay qualified
        assertEquals(List.of(), violations("package com.example;\n\nimport java.awt.List;\n\nclass Thing {\n"
                + "    java.util.List<String> a;\n    List<String> b;\n}\n"));
    }

    @Test
    void allowsQualificationAgainstDeclaredType() {
        assertEquals(List.of(), violations("package com.example;\n\nclass Timer {\n"
                + "    java.util.Timer other;\n}\n"));
    }

    @Test
    void allowsNestedTypeViaOuterCollision() {
        assertEquals(List.of(), violations("package com.example;\n\nimport other.pkg.Map;\n\nclass Thing {\n"
                + "    java.util.Map.Entry<String, Integer> e;\n}\n"));
    }

    @Test
    void ignoresLiteralsAndComments() {
        assertEquals(List.of(), violations(classSource("    String s = \"java.util.Map\";\n"
                + "    // java.util.List is mentioned here\n"
                + "    /* java.util.Set in a block comment */\n"
                + "    String t = \"\"\"\n"
                + "        java.util.HashMap in a text block\n"
                + "        \"\"\";\n"
                + "    char c = 'x';")));
    }

    @Test
    void ignoresImportAndPackageLines() {
        assertEquals(List.of(), violations("package com.example.sub;\n\n"
                + "import java.util.Map;\nimport javax.swing.JFrame;\n\n"
                + "import static java.util.Collections.emptyList;\n\n"
                + "class Thing {\n    Map<String, Integer> m;\n}\n"));
    }

    @Test
    void ignoresMemberAccessChains() {
        assertEquals(List.of(), violations(classSource("    Object o = this.inner.Widget.create();\n"
                + "    Object p = super.inner.Widget.create();")));
    }

    @Test
    void skipsPackageInfo() {
        // the fully qualified @NullMarked of a package-info is the one legitimate dotted reference
        assertEquals(List.of(), violations("package-info.java", "/** docs with {@link java.util.Map} */\n"
                + "@org.jspecify.annotations.NullMarked\n"
                + "package com.example.sub;\n"));
    }

    @Test
    void honoursTheSuppressionMarker() {
        assertEquals(List.of(), violations(
                classSource("    private java.util.Map<String, Integer> m; // fqn-ok: field name chain")));
    }

    @Test
    void reportsTheNestedTypeWithItsOuterImport() {
        List<RuleViolation> found = violations(classSource("    java.util.Map.Entry<String, Integer> e;"));

        assertEquals(1, found.size());
        String description = found.get(0).getDescription();
        assertTrue(description.contains("java.util.Map.Entry"), description);
        assertTrue(description.contains("import java.util.Map"), description);
        assertTrue(description.contains("use Map.Entry"), description);
    }

    @Test
    void reportsEveryReferenceOnItsOwnLine() {
        List<RuleViolation> found = violations(classSource("    java.util.Map<String, Integer> m;\n"
                + "    java.util.List<String> l;"));

        assertEquals(2, found.size());
        assertEquals(List.of(4, 5), found.stream().map(RuleViolation::getBeginLine).toList());
    }

    @Test
    void fqnPatternIsConfigurable() {
        FqnRule rule = new FqnRule();
        PmdRuleTestSupport.setProperty(rule, "fqnPattern", Pattern.compile("\\bjava\\.util\\.List\\b"));

        List<RuleViolation> found = PmdRuleTestSupport.violationsOf(rule, "Thing.java",
                classSource("    java.util.Map<String, Integer> m;\n    java.util.List<String> l;"));

        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("java.util.List"), found.get(0).getDescription());
    }

}
