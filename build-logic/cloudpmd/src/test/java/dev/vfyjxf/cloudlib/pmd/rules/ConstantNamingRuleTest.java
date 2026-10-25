package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.regex.Pattern;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

class ConstantNamingRuleTest {

    private static String classSource(String body) {
        return "package com.example;\n\nclass Thing {\n" + body + "\n}\n";
    }

    private static List<RuleViolation> violations(String source) {
        return PmdRuleTestSupport.violationsOf(new ConstantNamingRule(), "Thing.java", source);
    }

    @Test
    void defaultsMatchTheCatalogue() {
        ConstantNamingRule rule = new ConstantNamingRule();
        assertEquals("[a-z][a-zA-Z0-9]*",
                String.valueOf(rule.getProperty(rule.getPropertyDescriptor("constantPattern"))));
        assertEquals("[a-z][a-zA-Z0-9_]*",
                String.valueOf(rule.getProperty(rule.getPropertyDescriptor("enumConstantPattern"))));
    }

    @Test
    void reportsSingleLetterConstant() {
        // the old lint's {1,} quantifier missed exactly this
        List<RuleViolation> found = violations(classSource("    private static final int H = 12;"));

        assertEquals(1, found.size());
        assertEquals(4, found.get(0).getBeginLine());
        assertTrue(found.get(0).getDescription().contains("H"), found.get(0).getDescription());
    }

    @Test
    void reportsUpperSnakeConstant() {
        List<RuleViolation> found = violations(classSource("    private static final int MAX_SIZE = 3;"));

        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("MAX_SIZE"), found.get(0).getDescription());
    }

    @Test
    void reportsEveryDeclaratorOfOneField() {
        List<RuleViolation> found = violations(classSource("    static final int ok = 1, BAD = 2;"));

        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("BAD"), found.get(0).getDescription());
    }

    @Test
    void acceptsLowerCamelConstants() {
        assertEquals(List.of(), violations(classSource("    private static final int height = 12;\n"
                + "    public static final String log = \"x\";\n"
                + "    static final int minW = 26;")));
    }

    @Test
    void ignoresInstanceFields() {
        // only an explicit static final is a constant
        assertEquals(List.of(), violations(classSource("    private final int WIDTH = 1;")));
    }

    @Test
    void ignoresInterfaceConstants() {
        // Easing.QUAD_IN and friends are implicitly public static final; renaming them is not intended
        assertEquals(List.of(), violations("package com.example;\n\ninterface Easing {\n"
                + "    double LINEAR = 0;\n"
                + "    double QUAD_IN = 1;\n"
                + "    double COLOR = 2;\n"
                + "}\n"));
    }

    @Test
    void ignoresAnnotationTypeConstants() {
        assertEquals(List.of(), violations("package com.example;\n\n@interface Marker {\n"
                + "    int LIMIT = 3;\n"
                + "}\n"));
    }

    @Test
    void reportsConstantsOfATypeNestedInAnInterface() {
        // a nested class is a class again, so its explicit static final is a real constant
        List<RuleViolation> found = violations("package com.example;\n\ninterface Outer {\n"
                + "    class Inner {\n"
                + "        static final int MAX = 1;\n"
                + "    }\n"
                + "}\n");

        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("MAX"), found.get(0).getDescription());
    }

    @Test
    void reportsUpperCamelEnumConstant() {
        List<RuleViolation> found = violations("package com.example;\n\nenum TextAlign {\n"
                + "    left, CENTER, right\n"
                + "}\n");

        assertEquals(1, found.size());
        assertEquals(4, found.get(0).getBeginLine());
        assertTrue(found.get(0).getDescription().contains("CENTER"), found.get(0).getDescription());
    }

    @Test
    void acceptsLowerCamelEnumConstants() {
        assertEquals(List.of(), violations("package com.example;\n\nenum DetailLevel {\n"
                + "    off, status, detail\n"
                + "}\n"));
    }

    @Test
    void acceptsSnakeCaseEnumConstants() {
        // TargetDockAnchor.top_left: the enum pattern allows underscores on purpose
        assertEquals(List.of(), violations("package com.example;\n\nenum FixedAnchor {\n"
                + "    top_left, top_right\n"
                + "}\n"));
    }

    @Test
    void reportsEnumConstantWithBody() {
        List<RuleViolation> found = violations("package com.example;\n\nenum Kind {\n"
                + "    UNIT {\n"
                + "        void accept() {}\n"
                + "    };\n\n"
                + "    abstract void accept();\n"
                + "}\n");

        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("UNIT"), found.get(0).getDescription());
    }

    @Test
    void constantPatternIsConfigurable() {
        ConstantNamingRule rule = new ConstantNamingRule();
        PmdRuleTestSupport.setProperty(rule, "constantPattern", Pattern.compile("[A-Z][A-Z0-9_]*"));

        List<RuleViolation> found = PmdRuleTestSupport.violationsOf(rule, "Thing.java",
                classSource("    private static final int height = 1;\n    private static final int H = 2;"));

        assertEquals(1, found.size());
        assertTrue(found.get(0).getDescription().contains("height"), found.get(0).getDescription());
    }

    @Test
    void descriptionsHaveNoMessageFormatSyntax() {
        for (RuleViolation violation : violations(classSource("    private static final int MAX_SIZE = 3;"))) {
            assertNotNull(violation.getDescription());
            assertTrue(violation.getDescription().indexOf('{') < 0, violation.getDescription());
            assertTrue(violation.getDescription().indexOf('\'') < 0, violation.getDescription());
        }
    }

}
