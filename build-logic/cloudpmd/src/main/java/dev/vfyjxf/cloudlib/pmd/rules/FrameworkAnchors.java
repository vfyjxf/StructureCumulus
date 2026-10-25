package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sourceforge.pmd.lang.java.ast.ASTClassType;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTFieldAccess;
import net.sourceforge.pmd.lang.java.ast.ASTImportDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTType;
import net.sourceforge.pmd.lang.java.ast.ASTVariableAccess;
import net.sourceforge.pmd.lang.java.ast.ASTVariableId;
import net.sourceforge.pmd.lang.java.symbols.JExecutableSymbol;
import net.sourceforge.pmd.lang.java.symbols.JMethodSymbol;
import net.sourceforge.pmd.lang.java.symbols.JVariableSymbol;
import net.sourceforge.pmd.lang.java.types.OverloadSelectionResult;
import net.sourceforge.pmd.lang.java.types.TypeTestUtil;

/**
 * Anchors a call or receiver to a framework type by canonical name, for the framework rules
 * A-01 … A-11 of the framework catalogue.
 *
 * <p>Resolution is the primary anchor: {@link TypeTestUtil} against the type mirror, which works
 * when the type is declared in the analysed compilation unit or present on the analysis
 * classpath. When that says nothing — a type that is neither — the anchor falls back to the
 * written imports, the same conservative trick {@code RedundantNullCheckRule} uses for
 * {@code Objects}/{@code Checks}: a receiver whose declared type name is spelled in this file and
 * whose single-type import is exactly the wanted canonical name is that type, because a
 * single-type import names one type. On-demand imports are deliberately not trusted. The fallback
 * can therefore only miss, never invent.
 */
final class FrameworkAnchors {

    private FrameworkAnchors() {}

    /** The single-type imports of a compilation unit. */
    record Imports(Set<String> types) {

        static final Imports EMPTY = new Imports(Set.of());

        boolean importsExactly(String canonicalName) {
            return types.contains(canonicalName);
        }
    }

    static Imports importsOf(ASTCompilationUnit root) {
        Set<String> types = new HashSet<>();
        for (ASTImportDeclaration declaration : root.children(ASTImportDeclaration.class)) {
            if (!declaration.isStatic() && !declaration.isImportOnDemand()) {
                types.add(declaration.getImportedName());
            }
        }
        return new Imports(Set.copyOf(types));
    }

    /** Whether {@code expression} has exactly the wanted type, resolution or import fallback. */
    static boolean isExactly(ASTExpression expression, String canonicalName, Imports imports) {
        if (expression == null) {
            return false;
        }
        try {
            if (TypeTestUtil.isExactlyA(canonicalName, expression)) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // an unresolved type says nothing either way
        }
        return byWrittenImport(expression, canonicalName, imports);
    }

    /** Whether {@code expression} has the wanted type or a subtype of it. */
    static boolean isA(ASTExpression expression, String canonicalName, Imports imports) {
        if (expression == null) {
            return false;
        }
        try {
            if (TypeTestUtil.isA(canonicalName, expression)) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // an unresolved type says nothing either way
        }
        return byWrittenImport(expression, canonicalName, imports);
    }

    /**
     * The canonical name of the class whose method {@code call} resolved to, or {@code null} when
     * the overload selection failed or the symbol carries no class. Never throws.
     */
    static String resolvedOwner(ASTMethodCall call) {
        try {
            OverloadSelectionResult resolution = call.getOverloadSelectionInfo();
            if (resolution.isFailed()) {
                return null;
            }
            JExecutableSymbol symbol = resolution.getMethodType().getSymbol();
            return symbol == null ? null : symbol.getEnclosingClass().getCanonicalName();
        } catch (RuntimeException | AssertionError error) {
            return null; // type resolution is a bonus, never a crash
        }
    }

    /**
     * Whether {@code call} resolved to a method of {@code ownerCanonicalName}; an unresolvable
     * static call counts when its qualifier is the owner's simple name and this file imports the
     * owner exactly.
     */
    static boolean resolvesTo(ASTMethodCall call, String ownerCanonicalName, Imports imports) {
        String owner = resolvedOwner(call);
        if (owner != null) {
            return owner.equals(ownerCanonicalName);
        }
        if (!(call.getQualifier() instanceof ASTExpression qualifier)) {
            return false;
        }
        String qualifierName = qualifierName(qualifier);
        return qualifierName != null
                && qualifierName.equals(simpleName(ownerCanonicalName))
                && imports.importsExactly(ownerCanonicalName);
    }

    /**
     * The written name of a call qualifier, whatever shape it takes: a variable or field read,
     * or the type name of a static call (an {@code ASTTypeExpression} around the class name).
     */
    private static String qualifierName(ASTExpression qualifier) {
        if (qualifier instanceof ASTVariableAccess access) {
            return access.getName();
        }
        if (qualifier instanceof ASTFieldAccess field) {
            return field.getName();
        }
        if (qualifier instanceof net.sourceforge.pmd.lang.java.ast.ASTTypeExpression type) {
            ASTClassType named = type.firstChild(ASTClassType.class);
            return named != null ? named.getSimpleName() : null;
        }
        return null;
    }

    private static boolean byWrittenImport(ASTExpression expression, String canonicalName, Imports imports) {
        String written = writtenTypeName(expression);
        return written != null
                && written.equals(simpleName(canonicalName))
                && imports.importsExactly(canonicalName);
    }

    /**
     * The simple name of the type a receiver is declared with, when that declaration is written in
     * this file (a variable, a field, or a same-file method's return type). Receiver shapes whose
     * type this file cannot name — reads of foreign fields and calls of foreign methods — return
     * {@code null}.
     */
    private static String writtenTypeName(ASTExpression expression) {
        if (expression instanceof ASTVariableAccess access) {
            return declaredTypeOf(access.getReferencedSym());
        }
        if (expression instanceof ASTFieldAccess field) {
            return declaredTypeOf(field.getReferencedSym());
        }
        if (expression instanceof ASTMethodCall call) {
            try {
                OverloadSelectionResult resolution = call.getOverloadSelectionInfo();
                if (resolution.isFailed()) {
                    return null;
                }
                if (!(resolution.getMethodType().getSymbol() instanceof JMethodSymbol method)) {
                    return null;
                }
                ASTMethodDeclaration declaration = method.tryGetNode();
                return declaration == null ? null : typeNameOf(declaration.getResultTypeNode());
            } catch (RuntimeException | AssertionError error) {
                return null;
            }
        }
        return null;
    }

    private static String declaredTypeOf(JVariableSymbol symbol) {
        if (symbol == null || !(symbol.tryGetNode() instanceof ASTVariableId id)) {
            return null;
        }
        return typeNameOf(id.getTypeNode());
    }

    private static String typeNameOf(ASTType type) {
        if (type == null) {
            return null;
        }
        ASTClassType named = type.firstChild(ASTClassType.class);
        return named != null ? named.getSimpleName() : null;
    }

    private static String simpleName(String canonicalName) {
        int dot = canonicalName.lastIndexOf('.');
        return dot < 0 ? canonicalName : canonicalName.substring(dot + 1);
    }

    /** The source text of a node, for the text-based pairings the catalogue prescribes. */
    static String sourceTextOf(net.sourceforge.pmd.lang.java.ast.JavaNode node) {
        try {
            return node.getRoot().getTextDocument().sliceOriginalText(node.getTextRegion()).toString();
        } catch (RuntimeException error) {
            return "";
        }
    }

    /** The block body or the expression body of a lambda, whichever it has. */
    static net.sourceforge.pmd.lang.ast.Node lambdaBodyOf(
            net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression lambda) {
        return lambda.getBlockBody() != null ? lambda.getBlockBody() : lambda.getExpressionBody();
    }

    /**
     * The method calls in a body, the body itself included when it is one — a lambda's
     * expression body is the call, which {@code descendants} alone would skip.
     */
    static List<net.sourceforge.pmd.lang.java.ast.ASTMethodCall> callsOf(
            net.sourceforge.pmd.lang.ast.Node body) {
        List<net.sourceforge.pmd.lang.java.ast.ASTMethodCall> calls = new ArrayList<>();
        if (body instanceof net.sourceforge.pmd.lang.java.ast.ASTMethodCall self) {
            calls.add(self);
        }
        for (net.sourceforge.pmd.lang.java.ast.ASTMethodCall call
                : body.descendants(net.sourceforge.pmd.lang.java.ast.ASTMethodCall.class)
                        .crossFindBoundaries()) {
            calls.add(call);
        }
        return calls;
    }
}
