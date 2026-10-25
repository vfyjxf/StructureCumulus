package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sourceforge.pmd.lang.java.ast.ASTClassType;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTFieldAccess;
import net.sourceforge.pmd.lang.java.ast.ASTImportDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTInfixExpression;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTNullLiteral;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchLabel;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchLike;
import net.sourceforge.pmd.lang.java.ast.ASTTypeExpression;
import net.sourceforge.pmd.lang.java.ast.ASTVariableAccess;
import net.sourceforge.pmd.lang.java.ast.BinaryOp;

/**
 * The shapes a null check can take, shared by {@link RedundantNullCheckRule} and
 * {@link DuplicateNullCheckRule}.
 *
 * <p>A shape is the pair of the checked expression (the {@code expr} of {@code expr == null})
 * and the syntactic kind of the check. The kinds cover every form the two rules must see:
 * <ul>
 *   <li>{@code expr == null} and {@code expr != null}, including the yoda spellings
 *       {@code null == expr} / {@code null != expr};</li>
 *   <li>{@code Objects.isNull(expr)} / {@code Objects.nonNull(expr)};</li>
 *   <li>{@code Objects.requireNonNull(expr)} and {@code Checks.checkNotNull(expr)}, which assert
 *       non-nullness rather than branch on it;</li>
 *   <li>a {@code case null} label of a Java 21 switch, whose checked expression is the switch
 *       selector.</li>
 * </ul>
 *
 * <p>Checks nested in a larger expression — an {@code assert} condition, the condition of a
 * ternary, one conjunct of an {@code &&} — are not separate shapes: those contexts contain the
 * shape nodes themselves, so the rules match the shape wherever it sits.
 *
 * <p>The static-utility qualifiers are recognised by written name, the way the ruleset's
 * {@code BannedNullabilityAnnotation} and {@code IllegalImport} entries work: a bare
 * {@code isNull(x)} counts only when the compilation unit statically imports it from
 * {@code java.util.Objects}, and a qualified {@code Objects.isNull(x)} counts when the qualifier
 * is a type named {@code Objects} (spelled simple, or fully qualified as {@code java.util.Objects}).
 * The banned-import rule keeps any {@code Nullable}-named annotation JSpecify, so name matching
 * cannot be fooled by a same-named foreign annotation here either.
 */
final class NullCheckShapes {

    private NullCheckShapes() {}

    /** The syntactic kind of a null check. */
    enum Kind {
        EQ_NULL,
        NE_NULL,
        IS_NULL,
        NON_NULL,
        CASE_NULL,
        REQUIRE_NONNULL;

        /** Whether the check being true means the checked expression is null. */
        boolean trueMeansNull() {
            return this == EQ_NULL || this == IS_NULL || this == CASE_NULL;
        }
    }

    /** A null check: the checked expression plus its kind. */
    record NullCheck(ASTExpression checked, Kind kind) {}

    /** The static imports of a compilation unit, reduced to the member names the qualifiers need. */
    record Imports(Set<String> objectsMembers, Set<String> checksMembers, Set<String> stringMembers) {

        static final Imports EMPTY = new Imports(Set.of(), Set.of(), Set.of());
    }

    private static final String OBJECTS_TYPE = "java.util.Objects";
    private static final String CHECKS_TYPE = "dev.vfyjxf.cloudlib.util.Checks";
    private static final String STRING_TYPE = "java.lang.String";

    /** The shape of an equality against null, or null when the expression compares anything else. */
    static NullCheck of(ASTInfixExpression expression) {
        BinaryOp operator = expression.getOperator();
        if (operator != BinaryOp.EQ && operator != BinaryOp.NE) {
            return null;
        }
        if (expression.getLeftOperand() instanceof ASTNullLiteral) {
            return ofChecked(expression.getRightOperand(), operator);
        }
        if (expression.getRightOperand() instanceof ASTNullLiteral) {
            return ofChecked(expression.getLeftOperand(), operator);
        }
        return null;
    }

    private static NullCheck ofChecked(ASTExpression checked, BinaryOp operator) {
        if (checked instanceof ASTNullLiteral) {
            return null; // null == null is not a null check on a source
        }
        return new NullCheck(checked, operator == BinaryOp.EQ ? Kind.EQ_NULL : Kind.NE_NULL);
    }

    /** The shape of an isNull/nonNull/requireNonNull/checkNotNull call, or null for other calls. */
    static NullCheck of(ASTMethodCall call, Imports imports) {
        if (call.getArguments().getNumChildren() < 1) {
            return null;
        }
        switch (call.getMethodName()) {
            case "isNull":
                return isObjectsMember(call, imports) ? new NullCheck(argument(call), Kind.IS_NULL) : null;
            case "nonNull":
                return isObjectsMember(call, imports) ? new NullCheck(argument(call), Kind.NON_NULL) : null;
            case "requireNonNull":
                return isObjectsMember(call, imports) ? new NullCheck(argument(call), Kind.REQUIRE_NONNULL) : null;
            case "checkNotNull":
                return isChecksMember(call, imports) ? new NullCheck(argument(call), Kind.REQUIRE_NONNULL) : null;
            default:
                return null;
        }
    }

    /** The shape of a {@code case null} label, or null for every other label. */
    static NullCheck of(ASTSwitchLabel label) {
        if (!label.isCaseNull()) {
            return null;
        }
        ASTSwitchLike switchNode = label.ancestors(ASTSwitchLike.class).first();
        return switchNode == null ? null : new NullCheck(switchNode.getTestedExpression(), Kind.CASE_NULL);
    }

    /** The first argument of a call, known to exist because the shape matched. */
    static ASTExpression argument(ASTMethodCall call) {
        return (ASTExpression) call.getArguments().getChild(0);
    }

    /** Whether the call invokes a static {@code java.util.Objects} member, qualified or imported. */
    static boolean isObjectsMember(ASTMethodCall call, Imports imports) {
        return hasTypeQualifier(call, "Objects", OBJECTS_TYPE)
                || call.getQualifier() == null && imports.objectsMembers().contains(call.getMethodName());
    }

    /** Whether the call invokes {@code Checks.checkNotNull}, qualified or imported. */
    static boolean isChecksMember(ASTMethodCall call, Imports imports) {
        return hasTypeQualifier(call, "Checks", CHECKS_TYPE)
                || call.getQualifier() == null && imports.checksMembers().contains(call.getMethodName());
    }

    /** Whether the call invokes a static {@code java.lang.String} member, qualified or imported. */
    static boolean isStringMember(ASTMethodCall call, Imports imports) {
        return hasTypeQualifier(call, "String", STRING_TYPE)
                || call.getQualifier() == null && imports.stringMembers().contains(call.getMethodName());
    }

    /**
     * Whether the call's qualifier is a type named {@code simpleName}: spelled simple (an import
     * binds it, and a same-named class in the analysed file would own the name), or fully
     * qualified with exactly the expected package.
     */
    private static boolean hasTypeQualifier(ASTMethodCall call, String simpleName, String fqn) {
        if (!(call.getQualifier() instanceof ASTTypeExpression typeExpression)) {
            return false; // no qualifier, or a value receiver rather than the utility type
        }
        if (!(typeExpression.getTypeNode() instanceof ASTClassType classType)) {
            return false;
        }
        if (!simpleName.equals(classType.getSimpleName())) {
            return false;
        }
        if (!classType.isFullyQualified()) {
            return true;
        }
        String packageQualifier = classType.getPackageQualifier();
        return packageQualifier != null && fqn.equals(packageQualifier + "." + simpleName);
    }

    /** The static imports of the compilation unit, as the member-name sets the qualifiers need. */
    static Imports importsOf(ASTCompilationUnit root) {
        Set<String> objectsMembers = new HashSet<>();
        Set<String> checksMembers = new HashSet<>();
        Set<String> stringMembers = new HashSet<>();
        for (ASTImportDeclaration declaration : root.children(ASTImportDeclaration.class)) {
            if (!declaration.isStatic()) {
                continue;
            }
            String name = declaration.getImportedName();
            if (OBJECTS_TYPE.equals(name)) {
                objectsMembers.addAll(List.of("isNull", "nonNull", "requireNonNull"));
            } else if (CHECKS_TYPE.equals(name)) {
                checksMembers.add("checkNotNull");
            } else if (STRING_TYPE.equals(name)) {
                stringMembers.add("valueOf");
            } else if (name.startsWith(OBJECTS_TYPE + ".")) {
                objectsMembers.add(name.substring(OBJECTS_TYPE.length() + 1));
            } else if (name.startsWith(CHECKS_TYPE + ".")) {
                checksMembers.add(name.substring(CHECKS_TYPE.length() + 1));
            } else if (name.startsWith(STRING_TYPE + ".")) {
                stringMembers.add(name.substring(STRING_TYPE.length() + 1));
            }
        }
        return new Imports(Set.copyOf(objectsMembers), Set.copyOf(checksMembers), Set.copyOf(stringMembers));
    }

    /**
     * A short human-readable form of the checked expression. Messages must stay free of
     * apostrophes and braces, which {@code java.text.MessageFormat} would reinterpret, so those
     * characters are dropped from the sliced source text.
     */
    static String describe(ASTExpression expression) {
        if (expression instanceof ASTVariableAccess variable) {
            return variable.getName();
        }
        if (expression instanceof ASTFieldAccess field) {
            return field.getName();
        }
        String text = expression.getTextDocument()
                .sliceOriginalText(expression.getTextRegion())
                .toString()
                .replaceAll("\\s+", " ");
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character != '\'' && character != '{' && character != '}') {
                out.append(character);
            }
        }
        return out.length() > 48 ? out.substring(0, 48) + "..." : out.toString();
    }
}
