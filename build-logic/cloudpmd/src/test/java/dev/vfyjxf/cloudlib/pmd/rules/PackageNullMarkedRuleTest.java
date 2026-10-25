package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

class PackageNullMarkedRuleTest {

    private static final String PACKAGE_DECLARATION = "package com.example.sub;\n";

    private static List<RuleViolation> violations(String source) {
        return PmdRuleTestSupport.violationsOf(new PackageNullMarkedRule(), "package-info.java", source);
    }

    @Test
    void defaultsMatchTheCatalogue() {
        PackageNullMarkedRule rule = new PackageNullMarkedRule();
        assertEquals("true", String.valueOf(rule.getProperty(rule.getPropertyDescriptor("acceptFullyQualified"))));
        assertEquals("true", String.valueOf(rule.getProperty(rule.getPropertyDescriptor("acceptImportedSimpleName"))));
    }

    @Test
    void reportsAPackageWithoutNullMarked() {
        List<RuleViolation> found = violations(PACKAGE_DECLARATION);

        assertEquals(1, found.size());
        assertEquals(1, found.get(0).getBeginLine());
        assertTrue(found.get(0).getDescription().contains("org.jspecify.annotations.NullMarked"),
                found.get(0).getDescription());
    }

    @Test
    void acceptsTheFullyQualifiedSpelling() {
        assertEquals(List.of(), violations("@org.jspecify.annotations.NullMarked\n" + PACKAGE_DECLARATION));
    }

    @Test
    void acceptsTheImportedSimpleNameSpelling() {
        assertEquals(List.of(), violations("@NullMarked\n" + PACKAGE_DECLARATION
                + "\nimport org.jspecify.annotations.NullMarked;\n"));
    }

    @Test
    void acceptsTheFullyQualifiedSpellingAfterAJavadoc() {
        assertEquals(List.of(), violations("/**\n * The panel runtime.\n */\n"
                + "@org.jspecify.annotations.NullMarked\n" + PACKAGE_DECLARATION));
    }

    @Test
    void acceptsTheSimpleNameNextToOtherAnnotations() {
        assertEquals(List.of(), violations("@Deprecated\n@org.jspecify.annotations.NullMarked\n"
                + PACKAGE_DECLARATION));
    }

    @Test
    void reportsTheSimpleNameWithoutItsImport() {
        assertEquals(1, violations("@NullMarked\n" + PACKAGE_DECLARATION).size());
    }

    @Test
    void reportsAPackageWithAnUnrelatedAnnotation() {
        assertEquals(1, violations("@Deprecated\n" + PACKAGE_DECLARATION).size());
    }

    @Test
    void reportsTheLineOfThePackageDeclaration() {
        List<RuleViolation> found = violations("/*\n * The panel runtime.\n */\n" + PACKAGE_DECLARATION);

        assertEquals(1, found.size());
        assertEquals(4, found.get(0).getBeginLine());
    }

    @Test
    void ignoresFilesThatDeclareAType() {
        // a type declaration makes this an ordinary compilation unit; class level @NullMarked is
        // neither required nor reported
        assertEquals(List.of(), violations("package com.example.sub;\n\nclass Thing {\n}\n"));
    }

    @Test
    void ignoresRedundantClassLevelNullMarked() {
        assertEquals(List.of(), violations("package com.example.sub;\n\n"
                + "@org.jspecify.annotations.NullMarked\n"
                + "class Thing {\n}\n"));
    }

    @Test
    void ignoresMemberLevelNullMarked() {
        assertEquals(List.of(), violations("package com.example.sub;\n\n"
                + "class Thing {\n"
                + "    @org.jspecify.annotations.NullMarked\n"
                + "    void run() {\n    }\n"
                + "}\n"));
    }

    @Test
    void fullyQualifiedSpellingCanBeTurnedOff() {
        PackageNullMarkedRule rule = new PackageNullMarkedRule();
        PmdRuleTestSupport.setProperty(rule, "acceptFullyQualified", false);

        List<RuleViolation> found = PmdRuleTestSupport.violationsOf(rule, "package-info.java",
                "@org.jspecify.annotations.NullMarked\n" + PACKAGE_DECLARATION);

        assertEquals(1, found.size());
    }

    @Test
    void importedSimpleNameCanBeTurnedOff() {
        PackageNullMarkedRule rule = new PackageNullMarkedRule();
        PmdRuleTestSupport.setProperty(rule, "acceptImportedSimpleName", false);

        List<RuleViolation> found = PmdRuleTestSupport.violationsOf(rule, "package-info.java",
                "@NullMarked\n" + PACKAGE_DECLARATION + "\nimport org.jspecify.annotations.NullMarked;\n");

        assertEquals(1, found.size());
    }

}
