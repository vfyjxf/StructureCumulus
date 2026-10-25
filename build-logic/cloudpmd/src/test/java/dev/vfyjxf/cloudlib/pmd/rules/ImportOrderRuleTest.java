package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

class ImportOrderRuleTest {

    private static List<RuleViolation> violations(String source) {
        return PmdRuleTestSupport.violationsOf(new ImportOrderRule(), "Imports.java", source);
    }

    private static String source(String imports) {
        return "package com.example;\n\n" + imports + "\nclass Imports {\n}\n";
    }

    @Test
    void acceptsGroupedAndSortedImports() {
        assertEquals(List.of(), violations(source("import com.other.Thing;\n"
                + "import com.other.Zed;\n"
                + "\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import javax.swing.JFrame;\n"
                + "\n"
                + "import static java.util.Collections.emptyList;\n")));
    }

    @Test
    void acceptsOnDemandStaticImportInTheStaticGroup() {
        // import static X.* is a rank 3 import like any other
        assertEquals(List.of(), violations(source("import com.other.Thing;\n"
                + "import java.util.List;\n"
                + "import static com.other.Thing.*;\n")));
    }

    @Test
    void ignoresBlankLinesBetweenGroups() {
        // blank lines are Spotless business, not this rule's
        assertEquals(List.of(), violations(source("import com.other.Thing;\n"
                + "\n\n"
                + "import java.util.List;\n"
                + "\n"
                + "import static java.util.Collections.emptyList;\n")));
    }

    @Test
    void acceptsAFileWithoutImports() {
        assertEquals(List.of(), violations("package com.example;\n\nclass Imports {\n}\n"));
    }

    @Test
    void reportsAStaticImportBeforeTheJavaGroup() {
        // line 1 package, 2 blank, 3 static, 4 blank, 5 java
        List<RuleViolation> found = violations(source("import static java.util.Collections.emptyList;\n"
                + "\n"
                + "import java.util.List;\n"));

        assertEquals(1, found.size());
        assertEquals(5, found.get(0).getBeginLine());
        assertTrue(found.get(0).getDescription().contains("java.util.List"), found.get(0).getDescription());
        assertTrue(found.get(0).getDescription().contains("expected position: before java.util.Collections.emptyList"),
                found.get(0).getDescription());
    }

    @Test
    void reportsUnsortedNamesInsideAGroup() {
        List<RuleViolation> found = violations(source("import java.util.Map;\n"
                + "import java.util.List;\n"));

        assertEquals(1, found.size());
        assertEquals(4, found.get(0).getBeginLine());
        assertTrue(found.get(0).getDescription().contains("java.util.List"), found.get(0).getDescription());
        assertTrue(found.get(0).getDescription().contains("must come before java.util.Map at line 3"),
                found.get(0).getDescription());
    }

    @Test
    void reportsProjectImportAfterTheJavaGroup() {
        List<RuleViolation> found = violations(source("import java.util.List;\n"
                + "import com.other.Thing;\n"));

        assertEquals(1, found.size());
        assertEquals(4, found.get(0).getBeginLine());
        assertTrue(found.get(0).getDescription().contains("com.other.Thing"), found.get(0).getDescription());
    }

    @Test
    void reportsDuplicateImport() {
        // names increase strictly, so a repeated import is a violation too
        List<RuleViolation> found = violations(source("import java.util.List;\n"
                + "import java.util.List;\n"));

        assertEquals(1, found.size());
        assertEquals(4, found.get(0).getBeginLine());
    }

    @Test
    void reportsEveryImportThatBreaksTheOrder() {
        List<RuleViolation> found = violations(source("import static java.util.Collections.emptyList;\n"
                + "import com.other.Thing;\n"
                + "import java.util.Map;\n"
                + "import org.example.Thing;\n"));

        // the static import is fine, then each import that drops a group is reported
        assertEquals(2, found.size());
        assertEquals(4, found.get(0).getBeginLine());
        assertEquals(6, found.get(1).getBeginLine());
    }

    @Test
    void acceptsJavaxImportsOnly() {
        assertEquals(List.of(), violations(source("import javax.swing.JFrame;\n"
                + "import javax.swing.JPanel;\n")));
    }

}
