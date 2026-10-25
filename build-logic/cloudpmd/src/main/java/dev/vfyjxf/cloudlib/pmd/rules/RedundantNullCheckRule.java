package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.ArrayList;
import java.util.List;

import net.sourceforge.pmd.lang.java.ast.ASTAnnotation;
import net.sourceforge.pmd.lang.java.ast.ASTArrayAllocation;
import net.sourceforge.pmd.lang.java.ast.ASTAssignmentExpression;
import net.sourceforge.pmd.lang.java.ast.ASTBlock;
import net.sourceforge.pmd.lang.java.ast.ASTBooleanLiteral;
import net.sourceforge.pmd.lang.java.ast.ASTCastExpression;
import net.sourceforge.pmd.lang.java.ast.ASTCharLiteral;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorCall;
import net.sourceforge.pmd.lang.java.ast.ASTExecutableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTFieldAccess;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTInfixExpression;
import net.sourceforge.pmd.lang.java.ast.ASTInitializer;
import net.sourceforge.pmd.lang.java.ast.ASTLocalVariableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTModifierList;
import net.sourceforge.pmd.lang.java.ast.ASTNumericLiteral;
import net.sourceforge.pmd.lang.java.ast.ASTStringLiteral;
import net.sourceforge.pmd.lang.java.ast.ASTSuperExpression;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchLabel;
import net.sourceforge.pmd.lang.java.ast.ASTThisExpression;
import net.sourceforge.pmd.lang.java.ast.ASTType;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTUnaryExpression;
import net.sourceforge.pmd.lang.java.ast.ASTVariableAccess;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclarator;
import net.sourceforge.pmd.lang.java.ast.ASTVariableId;
import net.sourceforge.pmd.lang.java.ast.AssignmentOp;
import net.sourceforge.pmd.lang.java.ast.BinaryOp;
import net.sourceforge.pmd.lang.java.ast.JavaNode;
import net.sourceforge.pmd.lang.java.ast.ModifierOwner;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.symbols.JFieldSymbol;
import net.sourceforge.pmd.lang.java.symbols.JMethodSymbol;
import net.sourceforge.pmd.lang.java.symbols.JVariableSymbol;
import net.sourceforge.pmd.lang.java.types.OverloadSelectionResult;
import net.sourceforge.pmd.lang.java.types.TypeTestUtil;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A null check on an expression whose source is provably non-null is redundant.
 *
 * <p>Every shape of null check is matched — equality against null both ways round,
 * {@code Objects.isNull}/{@code nonNull}, an {@code assert} condition, a ternary condition, a
 * conjunct embedded in a larger condition, a {@code case null} switch label, and
 * {@code Objects.requireNonNull}/{@code Checks.checkNotNull} applied to an already-provable
 * expression — and the checked expression is judged against a deliberate order of proofs:
 * <ol>
 *   <li><b>Syntactic constants</b>: {@code this}, {@code super}, instance and array creation,
 *       literals, enum constants, string concatenation, the return of
 *       {@code requireNonNull}/{@code checkNotNull}/{@code String.valueOf}/{@code getClass()},
 *       and {@code Optional.orElseThrow} (and {@code Optional.orElse}, when its argument is
 *       itself provable, because {@code orElse(null)} can yield null). These are non-null by the
 *       language; annotations are never consulted.</li>
 *   <li><b>Local variables</b>: the declaration and every assignment to the variable in its
 *       method must each be provable (there is no control-flow sensitivity: one unknown
 *       assignment anywhere in the method disqualifies). A {@code @Nullable} declaration
 *       disqualifies.</li>
 *   <li><b>Fields declared in this file</b>, by simple name or through {@code this.}: no
 *       {@code @Nullable} on the declaration or its outermost type means non-null. Inherited and
 *       cross-file fields, and reads through any other receiver, are never judged.</li>
 *   <li><b>Method calls that resolve to a declaration in this file</b> whose result type carries
 *       no {@code @Nullable} — and only when the declaration cannot be overridden from another
 *       file (private, static, final, or in a final/record/enum/anonymous type).</li>
 *   <li>Parentheses are transparent (PMD 7 keeps no node for them); a cast keeps the judgement
 *       of its operand; array accesses and unresolvable chains are left alone.</li>
 * </ol>
 *
 * <p>The rule is deliberately miss-before-error: anything the list above cannot prove is passed
 * over in silence, so parameters, array elements, resource and foreach variables, lambdas,
 * method references, catch parameters, cross-file fields and library calls never produce a report.
 *
 * <p>Violations carry the whole message themselves, so the {@code message} attribute of the
 * ruleset reference is informational only. Messages therefore stay free of apostrophes and braces,
 * which {@code java.text.MessageFormat} would otherwise reinterpret.
 */
public class RedundantNullCheckRule extends AbstractJavaRulechainRule {

    private ASTCompilationUnit analysedUnit;
    private NullCheckShapes.Imports imports = NullCheckShapes.Imports.EMPTY;

    public RedundantNullCheckRule() {
        super(ASTInfixExpression.class, ASTMethodCall.class, ASTSwitchLabel.class);
        setMessage("Do not check an expression for null whose source is provably non-null");
    }

    @Override
    public Object visit(ASTInfixExpression node, Object data) {
        reportIfProvable(NullCheckShapes.of(node), node, asCtx(data));
        return data;
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        reportIfProvable(NullCheckShapes.of(node, imports(node)), node, asCtx(data));
        return data;
    }

    @Override
    public Object visit(ASTSwitchLabel node, Object data) {
        reportIfProvable(NullCheckShapes.of(node), node, asCtx(data));
        return data;
    }

    private void reportIfProvable(NullCheckShapes.NullCheck check, JavaNode node, RuleContext ctx) {
        if (check == null) {
            return;
        }
        String basis = prove(check.checked());
        if (basis != null) {
            ctx.addViolationWithMessage(node,
                    "null check on " + NullCheckShapes.describe(check.checked()) + " is redundant: " + basis);
        }
    }

    // region the proof, in the order the class documentation lists

    private String prove(ASTExpression expression) {
        if (expression instanceof ASTThisExpression) {
            return "this is never null";
        }
        if (expression instanceof ASTSuperExpression) {
            return "super is never null";
        }
        if (expression instanceof ASTConstructorCall) {
            return "a class instance creation always returns a new object";
        }
        if (expression instanceof ASTArrayAllocation) {
            return "an array creation always returns a new array";
        }
        if (expression instanceof ASTStringLiteral || expression instanceof ASTNumericLiteral
                || expression instanceof ASTBooleanLiteral || expression instanceof ASTCharLiteral) {
            return "a literal is never null";
        }
        if (expression instanceof ASTInfixExpression infix && infix.getOperator() == BinaryOp.ADD) {
            return "string concatenation always produces a non-null String";
        }
        if (expression instanceof ASTCastExpression cast) {
            return prove((ASTExpression) cast.getChild(1)); // a cast keeps its operand's judgement
        }
        if (expression instanceof ASTMethodCall call) {
            return proveMethodCall(call);
        }
        if (expression instanceof ASTVariableAccess || expression instanceof ASTFieldAccess) {
            return proveAccess(expression);
        }
        return null;
    }

    private String proveMethodCall(ASTMethodCall call) {
        NullCheckShapes.Imports unitImports = imports(call);
        String name = call.getMethodName();
        if ("requireNonNull".equals(name) && NullCheckShapes.isObjectsMember(call, unitImports)) {
            return "Objects.requireNonNull returns its argument or throws";
        }
        if ("checkNotNull".equals(name) && NullCheckShapes.isChecksMember(call, unitImports)) {
            return "Checks.checkNotNull returns its argument or throws";
        }
        if ("getClass".equals(name) && call.getArguments().getNumChildren() == 0) {
            return "getClass always returns a Class object";
        }
        if ("valueOf".equals(name) && NullCheckShapes.isStringMember(call, unitImports)) {
            return "String.valueOf never returns null";
        }
        if ("orElseThrow".equals(name) && hasOptionalReceiver(call)) {
            return "Optional.orElseThrow returns a present value or throws";
        }
        if ("orElse".equals(name) && call.getArguments().getNumChildren() == 1 && hasOptionalReceiver(call)) {
            // orElse returns its argument when empty, so it is only provable when the argument is
            if (prove(NullCheckShapes.argument(call)) != null) {
                return "Optional.orElse of a provably non-null argument never returns null";
            }
            return null;
        }
        return proveByDeclaration(call);
    }

    private static boolean hasOptionalReceiver(ASTMethodCall call) {
        try {
            return call.getQualifier() != null
                    && TypeTestUtil.isExactlyA("java.util.Optional", call.getQualifier());
        } catch (RuntimeException error) {
            return false; // an unresolved type says nothing either way
        }
    }

    /** S4: the call resolves to a same-file declaration that returns non-null and cannot be overridden. */
    private String proveByDeclaration(ASTMethodCall call) {
        try {
            OverloadSelectionResult resolution = call.getOverloadSelectionInfo();
            if (resolution.isFailed()) {
                return null;
            }
            if (!(call.getMethodType().getSymbol() instanceof JMethodSymbol methodSymbol)) {
                return null;
            }
            ASTMethodDeclaration declaration = methodSymbol.tryGetNode();
            if (declaration == null || declaration.isAbstract() || !cannotBeOverriddenOutsideThisFile(declaration)) {
                return null;
            }
            if (isDeclaredNullable(declaration.getModifiers(), declaration.getResultTypeNode())) {
                return null;
            }
            return "method " + declaration.getName() + " is declared to return non-null in this file";
        } catch (RuntimeException | AssertionError error) {
            return null; // type resolution is a bonus, never a crash
        }
    }

    private static boolean cannotBeOverriddenOutsideThisFile(ASTMethodDeclaration declaration) {
        if (declaration.isStatic() || declaration.isFinal()
                || declaration.getVisibility() == ModifierOwner.Visibility.V_PRIVATE) {
            return true;
        }
        ASTTypeDeclaration owner = declaration.ancestors(ASTTypeDeclaration.class).first();
        return owner != null
                && (owner.isFinal() || owner.isRecord() || owner.isEnum() || owner.isAnonymous());
    }

    // endregion

    // region variables and fields

    private String proveAccess(ASTExpression expression) {
        if (expression instanceof ASTFieldAccess field) {
            JFieldSymbol symbol = field.getReferencedSym();
            if (symbol == null) {
                return null;
            }
            if (symbol.isEnumConstant()) {
                return "enum constant " + field.getName() + " is never null";
            }
            // any other qualified read goes through a receiver this file cannot speak for
            if (!(field.getQualifier() instanceof ASTThisExpression)
                    && !(field.getQualifier() instanceof ASTSuperExpression)) {
                return null;
            }
            ASTVariableId id = symbol.tryGetNode();
            return id != null && id.isField() ? fieldProof(id) : null;
        }
        ASTVariableAccess variable = (ASTVariableAccess) expression;
        JVariableSymbol symbol = variable.getReferencedSym();
        if (symbol == null) {
            return null; // an unresolvable name says nothing either way
        }
        if (symbol instanceof JFieldSymbol fieldSymbol && fieldSymbol.isEnumConstant()) {
            return "enum constant " + variable.getName() + " is never null"; // static-imported constant
        }
        ASTVariableId id = symbol.tryGetNode();
        if (id == null) {
            return null; // declared in another file
        }
        if (id.isEnumConstant()) {
            return "enum constant " + id.getName() + " is never null";
        }
        if (id.isField()) {
            return fieldProof(id);
        }
        if (id.isLocalVariable()) {
            return localProof(id, symbol);
        }
        return null; // parameters, resources, foreach and pattern variables, catch and lambda parameters
    }

    /** S3: the field is declared in this file without a {@code @Nullable}. */
    private static String fieldProof(ASTVariableId id) {
        if (!(id.getParent() instanceof ASTVariableDeclarator declarator)
                || !(declarator.getParent() instanceof ASTFieldDeclaration declaration)) {
            return null;
        }
        if (isDeclaredNullable(declaration.getModifiers(), declaration.firstChild(ASTType.class))) {
            return null;
        }
        return "field " + id.getName() + " is declared non-null in this file";
    }

    /** S2: the declaration and every assignment in the method are each provably non-null. */
    private String localProof(ASTVariableId id, JVariableSymbol symbol) {
        if (!(id.getParent() instanceof ASTVariableDeclarator declarator)
                || !(declarator.getParent() instanceof ASTLocalVariableDeclaration declaration)) {
            return null;
        }
        if (isDeclaredNullable(declaration.getModifiers(), declaration.firstChild(ASTType.class))) {
            return null;
        }
        ASTBlock body = enclosingBody(id);
        if (body == null) {
            return null;
        }
        List<ASTExpression> sources = new ArrayList<>();
        if (declarator.getInitializer() != null) {
            sources.add(declarator.getInitializer());
        }
        for (ASTAssignmentExpression assignment : body.descendants(ASTAssignmentExpression.class)) {
            if (assignsTo(assignment, symbol)) {
                if (assignment.getOperator() != AssignmentOp.ASSIGN) {
                    return null; // a compound assignment is not a plain source
                }
                sources.add((ASTExpression) assignment.getChild(1));
            }
        }
        for (ASTUnaryExpression unary : body.descendants(ASTUnaryExpression.class)) {
            if (!unary.getOperator().isPure() && assignsTo(unary.getOperand(), symbol)) {
                return null; // an increment or decrement rewrites the variable
            }
        }
        if (sources.isEmpty()) {
            return null;
        }
        for (ASTExpression source : sources) {
            if (prove(source) == null) {
                return null;
            }
        }
        return "local variable " + id.getName() + " is only assigned from non-null sources in its method";
    }

    private static ASTBlock enclosingBody(ASTVariableId id) {
        ASTExecutableDeclaration executable = id.ancestors(ASTExecutableDeclaration.class).first();
        if (executable != null) {
            return executable.getBody();
        }
        ASTInitializer initializer = id.ancestors(ASTInitializer.class).first();
        return initializer != null ? initializer.getBody() : null;
    }

    private static boolean assignsTo(ASTExpression leftHandSide, JVariableSymbol symbol) {
        return leftHandSide instanceof ASTVariableAccess access && symbol.equals(access.getReferencedSym());
    }

    private static boolean assignsTo(ASTAssignmentExpression assignment, JVariableSymbol symbol) {
        return assignsTo((ASTExpression) assignment.getChild(0), symbol);
    }

    /**
     * Whether the declaration or its outermost type carries an annotation named
     * {@code Nullable}. Name-matched rather than type-resolved so the judgement cannot silently
     * flip when a classpath is missing; the ruleset bans every non-JSpecify spelling of the name.
     * An annotation on a type <em>argument</em> ({@code List<@Nullable String>}) is deliberately
     * not the declaration's own nullness and does not count.
     */
    private static boolean isDeclaredNullable(ASTModifierList modifiers, ASTType type) {
        if (hasNullableName(modifiers)) {
            return true;
        }
        if (type == null) {
            return false;
        }
        for (ASTAnnotation annotation : type.children(ASTAnnotation.class)) {
            if ("Nullable".equals(annotation.getSimpleName())) {
                return true;
            }
        }
        // the array spelling "int @Nullable []" puts the annotation on the dimension, one
        // ArrayDimensions node below the type itself
        for (net.sourceforge.pmd.lang.java.ast.ASTArrayDimensions dimensions
                : type.children(net.sourceforge.pmd.lang.java.ast.ASTArrayDimensions.class)) {
            for (net.sourceforge.pmd.lang.java.ast.ASTArrayTypeDim dimension
                    : dimensions.children(net.sourceforge.pmd.lang.java.ast.ASTArrayTypeDim.class)) {
                for (ASTAnnotation annotation : dimension.children(ASTAnnotation.class)) {
                    if ("Nullable".equals(annotation.getSimpleName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasNullableName(ASTModifierList modifiers) {
        for (ASTAnnotation annotation : modifiers.children(ASTAnnotation.class)) {
            if ("Nullable".equals(annotation.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private NullCheckShapes.Imports imports(JavaNode node) {
        ASTCompilationUnit root = node.getRoot();
        if (analysedUnit != root) {
            analysedUnit = root;
            imports = NullCheckShapes.importsOf(root);
        }
        return imports;
    }

    // endregion
}
