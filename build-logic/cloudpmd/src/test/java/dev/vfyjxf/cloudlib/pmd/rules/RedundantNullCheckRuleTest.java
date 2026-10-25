package dev.vfyjxf.cloudlib.pmd.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.sourceforge.pmd.reporting.RuleViolation;

import org.junit.jupiter.api.Test;

class RedundantNullCheckRuleTest {

    private static final String HEADER = """
            package com.example;

            import java.util.List;
            import java.util.Objects;
            import java.util.Optional;

            import org.jspecify.annotations.Nullable;

            class Test {

                private final String widget = "w";

                @Nullable
                private String maybe;

                private final List<@Nullable String> nested = List.of();

                enum Color { RED }

                static class Checks {

                    static <T> T checkNotNull(@Nullable T value, String name) {
                        return Objects.requireNonNull(value, name);
                    }
                }

            """;

    private static final String FOOTER = "}\n";

    private static List<RuleViolation> violations(String body) {
        return PmdRuleTestSupport.violationsOf(new RedundantNullCheckRule(), "Test.java",
                HEADER + body + FOOTER);
    }

    // region check shapes: every form must be seen

    @Test
    void reportsEqualityAgainstNull() {
        assertEquals(1, violations("void m() { if (widget == null) { } }").size());
    }

    @Test
    void reportsNotEqualAgainstNull() {
        assertEquals(1, violations("void m() { if (widget != null) { } }").size());
    }

    @Test
    void reportsBothYodaSpellings() {
        assertEquals(2, violations("""
                void m() {
                    if (null == widget) { }
                    if (null != widget) { }
                }
                """).size());
    }

    @Test
    void reportsObjectsIsNullAndNonNull() {
        assertEquals(2, violations("""
                void m() {
                    if (Objects.isNull(widget)) { }
                    if (Objects.nonNull(widget)) { }
                }
                """).size());
    }

    @Test
    void ignoresAForeignIsNullUtility() {
        // a qualifier that is not java.util.Objects is not the standard utility
        assertEquals(0, violations("""
                static class Helper { static boolean isNull(Object o) { return o == null; } }

                void m(String p) { if (Helper.isNull(p)) { } }
                """).size());
    }

    @Test
    void ignoresABareIsNullWithoutItsStaticImport() {
        assertEquals(0, violations("void m(String p) { if (isNull(p)) { } }").size());
    }

    @Test
    void reportsAStaticallyImportedIsNull() {
        String source = "package com.example;\n\nimport static java.util.Objects.isNull;\n\n"
                + "class Test {\n"
                + "    private final String widget = \"w\";\n\n"
                + "    void m() { if (isNull(widget)) { } }\n"
                + "}\n";
        assertEquals(1, PmdRuleTestSupport.violationsOf(new RedundantNullCheckRule(), "Test.java", source).size());
    }

    @Test
    void reportsAssertConditions() {
        assertEquals(1, violations("void m() { assert widget != null; }").size());
    }

    @Test
    void reportsTernaryConditions() {
        assertEquals(1, violations("void m() { String s = widget != null ? widget : \"\"; }").size());
    }

    @Test
    void reportsChecksEmbeddedInCompoundConditions() {
        assertEquals(1, violations("void m() { if (widget != null && widget.isEmpty()) { } }").size());
    }

    @Test
    void reportsEachEmbeddedCheckOfACondition() {
        assertEquals(2, violations("""
                void m() {
                    String other = "o";
                    if (widget == null || other == null) { }
                }
                """).size());
    }

    @Test
    void reportsCaseNullInASwitchStatement() {
        assertEquals(1, violations("""
                void m() {
                    switch (widget) {
                        case null -> { throw new IllegalStateException(); }
                        default -> { }
                    }
                }
                """).size());
    }

    @Test
    void reportsCaseNullInASwitchExpression() {
        assertEquals(1, violations("""
                void m() {
                    String s = switch (widget) {
                        case null -> "n";
                        default -> "d";
                    };
                }
                """).size());
    }

    @Test
    void reportsRequireNonNullOfAProvableExpression() {
        assertEquals(1, violations("void m() { Objects.requireNonNull(widget); }").size());
    }

    @Test
    void reportsCheckNotNullOfAProvableExpression() {
        assertEquals(1, violations("void m() { Checks.checkNotNull(widget, \"widget\"); }").size());
    }

    // endregion

    // region S1: syntactically non-null sources

    @Test
    void provesThis() {
        assertEquals(1, violations("void m() { if (this == null) { } }").size());
    }

    @Test
    void provesInstanceCreation() {
        assertEquals(1, violations("void m() { if (new Object() == null) { } }").size());
    }

    @Test
    void provesArrayCreation() {
        assertEquals(1, violations("void m() { if (new String[1] == null) { } }").size());
    }

    @Test
    void provesLiterals() {
        assertEquals(1, violations("void m() { if (\"text\" == null) { } }").size());
    }

    @Test
    void provesEnumConstants() {
        assertEquals(1, violations("void m() { if (Color.RED == null) { } }").size());
    }

    @Test
    void provesStringConcatenation() {
        assertEquals(1, violations("void m(String p) { if ((\"a\" + p) == null) { } }").size());
    }

    @Test
    void provesRequireNonNullResult() {
        assertEquals(1, violations("void m(String p) { if (Objects.requireNonNull(p) == null) { } }").size());
    }

    @Test
    void provesCheckNotNullResult() {
        assertEquals(1, violations("void m(String p) { if (Checks.checkNotNull(p, \"p\") == null) { } }").size());
    }

    @Test
    void provesStringValueOf() {
        assertEquals(1, violations("void m(String p) { if (String.valueOf(p) == null) { } }").size());
    }

    @Test
    void provesGetClass() {
        assertEquals(1, violations("void m(String p) { if (p.getClass() == null) { } }").size());
    }

    @Test
    void provesOptionalOrElseThrow() {
        assertEquals(1, violations("void m(Optional<String> o) { if (o.orElseThrow() == null) { } }").size());
    }

    @Test
    void provesOptionalOrElseOfAProvableArgument() {
        assertEquals(1, violations("void m(Optional<String> o) { if (o.orElse(\"d\") == null) { } }").size());
    }

    @Test
    void doesNotProveOptionalOrElseOfAnUnknownArgument() {
        // orElse returns its argument when empty, and this argument may be null
        assertEquals(0, violations("void m(Optional<String> o, String p) { if (o.orElse(p) == null) { } }").size());
    }

    @Test
    void doesNotProveParameters() {
        assertEquals(0, violations("void m(String p) { if (p == null) { } }").size());
    }

    @Test
    void doesNotProveTheNullLiteral() {
        assertEquals(0, violations("void m() { if (null == null) { } }").size());
    }

    // endregion

    // region S2: local variables

    @Test
    void provesALocalAssignedOnlyProvableSources() {
        assertEquals(1, violations("""
                void m(String p) {
                    String a = "x";
                    if (a == null) { }
                }
                """).size());
    }

    @Test
    void provesALocalWithSeveralProvableAssignments() {
        assertEquals(1, violations("""
                void m(String p) {
                    String b;
                    if (p.isEmpty()) {
                        b = "x";
                    } else {
                        b = String.valueOf(p);
                    }
                    if (b == null) { }
                }
                """).size());
    }

    @Test
    void provesALocalAssignedFromAField() {
        assertEquals(1, violations("""
                void m() {
                    String c = widget;
                    if (c == null) { }
                }
                """).size());
    }

    @Test
    void doesNotProveALocalAssignedFromAParameter() {
        assertEquals(0, violations("""
                void m(String p) {
                    String s = p;
                    if (s == null) { }
                }
                """).size());
    }

    @Test
    void doesNotProveALocalWithOneUnknownAssignment() {
        assertEquals(0, violations("""
                void m(String p) {
                    String t = "x";
                    if (p.isEmpty()) {
                        t = p;
                    }
                    if (t == null) { }
                }
                """).size());
    }

    @Test
    void doesNotProveANullableLocal() {
        assertEquals(0, violations("""
                void m() {
                    @Nullable String u = "x";
                    if (u == null) { }
                }
                """).size());
    }

    @Test
    void doesNotProveAForeachVariable() {
        assertEquals(0, violations("""
                void m(List<String> list) {
                    for (String v : list) {
                        if (v == null) { }
                    }
                }
                """).size());
    }

    // endregion

    // region S3: fields of this file

    @Test
    void provesAFieldBySimpleName() {
        assertEquals(1, violations("void m() { if (widget == null) { } }").size());
    }

    @Test
    void provesAFieldThroughThis() {
        assertEquals(1, violations("void m() { if (this.widget == null) { } }").size());
    }

    @Test
    void provesAFieldWhoseTypeArgumentCarriesNullable() {
        // the annotation sits on the element type, not on the field itself
        assertEquals(1, violations("void m() { if (nested == null) { } }").size());
    }

    @Test
    void doesNotProveANullableField() {
        assertEquals(0, violations("void m() { if (maybe == null) { } }").size());
    }

    @Test
    void doesNotProveAFieldThroughAnotherReceiver() {
        assertEquals(0, violations("void m(Test other) { if (other.widget == null) { } }").size());
    }

    // endregion

    // region S4: methods declared in this file

    @Test
    void provesAPrivateMethod() {
        assertEquals(1, violations("""
                private String helper() { return "h"; }

                void m() { if (helper() == null) { } }
                """).size());
    }

    @Test
    void provesAStaticMethod() {
        assertEquals(1, violations("""
                static String shelp() { return "s"; }

                void m() { if (shelp() == null) { } }
                """).size());
    }

    @Test
    void provesAFinalMethod() {
        assertEquals(1, violations("""
                final String fin() { return "f"; }

                void m() { if (fin() == null) { } }
                """).size());
    }

    @Test
    void provesAMethodOfAFinalClass() {
        assertEquals(1, violations("""
                static final class Box { String value() { return "v"; } }

                void m() { if (new Box().value() == null) { } }
                """).size());
    }

    @Test
    void doesNotProveANullableReturn() {
        assertEquals(0, violations("""
                @Nullable String nullableReturn() { return null; }

                void m() { if (nullableReturn() == null) { } }
                """).size());
    }

    @Test
    void doesNotProveANullableArrayReturn() {
        // the "int @Nullable []" spelling puts the annotation on the array dimension
        assertEquals(0, violations("""
                private int @Nullable [] dims(String s) { return null; }

                void m(String p) {
                    int[] r = dims(p);
                    if (r == null) { return; }
                }
                """).size());
    }

    @Test
    void doesNotProveANullableArrayField() {
        assertEquals(0, violations("""
                private final int @Nullable [] dims = null;

                void m() { if (dims == null) { } }
                """).size());
    }

    @Test
    void doesNotProveAnOverridableMethod() {
        // a subclass in another file could return null, so the declaration proves nothing
        assertEquals(0, violations("""
                public String overridable() { return "o"; }

                void m() { if (overridable() == null) { } }
                """).size());
    }

    @Test
    void doesNotProveLibraryCalls() {
        assertEquals(0, violations("""
                void m(String p) {
                    if (Objects.toString(p) == null) { }
                    if (p.concat("x").trim() == null) { }
                }
                """).size());
    }

    // endregion

    // region S5: parentheses, casts, array accesses

    @Test
    void seesThroughParentheses() {
        assertEquals(1, violations("void m() { if (((widget)) == null) { } }").size());
    }

    @Test
    void aCastKeepsAProvableJudgement() {
        assertEquals(1, violations("void m() { if ((Object) widget == null) { } }").size());
    }

    @Test
    void aCastKeepsAnUnknownJudgement() {
        assertEquals(0, violations("void m(String p) { if ((String) p == null) { } }").size());
    }

    @Test
    void doesNotProveArrayAccess() {
        assertEquals(0, violations("void m(String[] array) { if (array[0] == null) { } }").size());
    }

    // endregion

    // region the report itself

    @Test
    void messageNamesTheCheckedExpressionAndTheBasis() {
        List<RuleViolation> found = violations("""
                void m() {
                    String local = "x";
                    if (local == null) { }
                }
                """);

        assertEquals(1, found.size());
        String message = found.get(0).getDescription();
        assertTrue(message.contains("local"), message);
        assertTrue(message.contains("non-null"), message);
    }

    @Test
    void messageNamesAFieldBasis() {
        List<RuleViolation> found = violations("void m() { if (widget == null) { } }");

        assertEquals(1, found.size());
        String message = found.get(0).getDescription();
        assertTrue(message.contains("widget"), message);
        assertTrue(message.contains("field"), message);
        assertTrue(message.contains("this file"), message);
    }

    @Test
    void reportsOnTheLineOfTheCheck() {
        String source = HEADER + "void m() {\n    if (widget == null) { }\n}" + FOOTER;
        int checkLine = source.substring(0, source.indexOf("if (widget")).split("\n", -1).length;

        List<RuleViolation> found = PmdRuleTestSupport.violationsOf(new RedundantNullCheckRule(), "Test.java",
                source);

        assertEquals(1, found.size());
        assertEquals(checkLine, found.get(0).getBeginLine());
    }

    // endregion

    // region suppression: the escape hatches promised by the warnings ruleset

    /**
     * A {@code // NOPMD} on the check's line silences that finding only — PMD's default
     * suppression marker, applied by the reporting layer to every rule, including
     * {@code DuplicateNullCheck}. This is the documented way out when a nullness declaration
     * the rule relied on is simply wrong.
     */
    @Test
    void nopmdOnTheLineSuppressesOnlyThatFinding() {
        assertEquals(1, violations(
                "void m() {\n    if (widget == null) { } // NOPMD\n    if (widget == null) { }\n}").size());
    }

    /** The same escape hatch on the enclosing declaration, for a whole method's worth of findings. */
    @Test
    void suppressWarningsAnnotationSuppressesTheDeclaration() {
        RedundantNullCheckRule rule = new RedundantNullCheckRule();
        rule.setName("RedundantNullCheck");
        String source = HEADER
                + "@SuppressWarnings(\"PMD.RedundantNullCheck\")\n"
                + "void m() { if (widget == null) { } }\n"
                + "void n() { if (widget == null) { } }\n"
                + FOOTER;

        List<RuleViolation> found = PmdRuleTestSupport.violationsOf(rule, "Test.java", source);

        assertEquals(1, found.size(), "only the un-annotated method may be reported");
    }

    // endregion
}
