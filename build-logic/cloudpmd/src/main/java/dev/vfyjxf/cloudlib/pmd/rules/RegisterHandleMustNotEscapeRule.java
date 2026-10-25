package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.Set;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTAssignmentExpression;
import net.sourceforge.pmd.lang.java.ast.ASTClassType;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTExecutableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFieldAccess;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFormalParameter;
import net.sourceforge.pmd.lang.java.ast.ASTInitializer;
import net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTType;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTVariableAccess;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclarator;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.symbols.JVariableSymbol;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A-05 of the framework catalogue: a registration-window handle — a parameter whose type is
 * named {@code *Register}, the shapes of {@code dev.vfyjxf.cloudlib.api.register} and
 * {@code dev.vfyjxf.nimbus.api.register} — is only valid while dispatch runs. {@code
 * DispatchRegister.apply()} closes the window, and every later call throws
 * {@code IllegalStateException}; the catalogue's shapes of escape are storing the handle in a
 * field (assignment to a field, a field's initializer, or a lambda captured by an initializer
 * block) and handing it to the async machinery ({@code CompletableFuture}, {@code Executor},
 * {@code ForkJoinPool}), where it is used after the window closed.
 *
 * <p>What is deliberately not an escape: calling methods on the handle, passing it to a helper
 * — including inside a callback — and any class that itself implements a {@code *Register}
 * interface, whose methods legitimately take and return handles.
 */
public class RegisterHandleMustNotEscapeRule extends AbstractJavaRulechainRule {

    private static final Set<String> asyncMachinery =
            Set.of("CompletableFuture", "Executor", "ExecutorService", "ScheduledExecutorService",
                    "ForkJoinPool", "Executors");

    public RegisterHandleMustNotEscapeRule() {
        super(ASTFormalParameter.class);
        setMessage("A registration handle must not survive the dispatch it belongs to");
    }

    @Override
    public Object visit(ASTFormalParameter node, Object data) {
        RuleContext ctx = asCtx(data);
        if (!isRegisterParameter(node) || hostImplementsRegisterInterface(node)) {
            return data;
        }
        JVariableSymbol handle = node.getVarId().getSymbol();
        ASTExecutableDeclaration enclosing = node.ancestors(ASTExecutableDeclaration.class).first();
        if (handle == null || enclosing == null || enclosing.getBody() == null) {
            return data;
        }
        for (ASTAssignmentExpression assignment
                : enclosing.getBody().descendants(ASTAssignmentExpression.class).crossFindBoundaries()) {
            if (references((ASTExpression) assignment.getChild(1), handle)
                    && isFieldAccess((ASTExpression) assignment.getChild(0))) {
                ctx.addViolationWithMessage(assignment,
                        "the registration handle " + node.getVarId().getName()
                                + " is stored into a field — it is only valid during dispatch and throws"
                                + " IllegalStateException afterwards");
            }
        }
        for (ASTVariableDeclarator declarator
                : enclosing.getBody().descendants(ASTVariableDeclarator.class).crossFindBoundaries()) {
            if (declarator.getParent() instanceof ASTFieldDeclaration
                    && declarator.getInitializer() != null
                    && references(declarator.getInitializer(), handle)) {
                ctx.addViolationWithMessage(declarator,
                        "a field is initialized from the registration handle "
                                + node.getVarId().getName()
                                + " — it is only valid during dispatch");
            }
        }
        for (ASTLambdaExpression lambda : enclosing.getBody().descendants(ASTLambdaExpression.class).crossFindBoundaries()) {
            net.sourceforge.pmd.lang.ast.Node lambdaBody = FrameworkAnchors.lambdaBodyOf(lambda);
            if (lambdaBody == null) {
                continue;
            }
            if (lambdaBody.descendants(ASTVariableAccess.class)
                    .filter(v -> references(v, handle)).nonEmpty()
                    && isCapturedBeyondTheMethod(lambda)) {
                ctx.addViolationWithMessage(lambda,
                        "the registration handle " + node.getVarId().getName()
                                + " is captured by a lambda that outlives the dispatch window");
            }
        }
        for (ASTMethodCall call : enclosing.getBody().descendants(ASTMethodCall.class).crossFindBoundaries()) {
            if (isAsyncMachineryCall(call) && call.getArguments()
                    .descendants(ASTVariableAccess.class).crossFindBoundaries()
                    .filter(v -> references(v, handle) && !isPassThroughToASameFileMethod(v))
                    .nonEmpty()) {
                ctx.addViolationWithMessage(call,
                        "the registration handle " + node.getVarId().getName()
                                + " is handed to the async machinery — by the time it runs, dispatch has"
                                + " closed the registration window");
            }
        }
        return data;
    }

    /**
     * A parameter whose type is named {@code *Register}. The parameter's type node is the class
     * type itself — a parameter carries no {@code ASTType} wrapper — with the wrapper kept for
     * the other declaration shapes.
     */
    private static boolean isRegisterParameter(ASTFormalParameter node) {
        ASTType type = node.getTypeNode();
        if (type == null) {
            return false;
        }
        if (type instanceof ASTClassType named) {
            return named.getSimpleName().endsWith("Register");
        }
        ASTClassType child = type.firstChild(ASTClassType.class);
        return child != null && child.getSimpleName().endsWith("Register");
    }

    /** A class implementing a *Register interface is the machinery itself, not a consumer. */
    private static boolean hostImplementsRegisterInterface(ASTFormalParameter node) {
        ASTTypeDeclaration host = node.ancestors(ASTTypeDeclaration.class).first();
        return host != null && !host.isInterface() && host.getSuperInterfaceTypeNodes()
                .filter(type -> type.getSimpleName().endsWith("Register")).nonEmpty();
    }

    private static boolean isFieldAccess(ASTExpression expression) {
        if (expression instanceof ASTVariableAccess access) {
            return access.getReferencedSym() != null && access.getReferencedSym().isField();
        }
        return expression instanceof ASTFieldAccess field
                && field.getReferencedSym() != null && field.getReferencedSym().isField();
    }

    private static boolean references(ASTExpression expression, JVariableSymbol handle) {
        if (expression instanceof ASTVariableAccess access && references(access, handle)) {
            return true; // the expression may be the reference itself, not a container of it
        }
        return expression.descendants(ASTVariableAccess.class).crossFindBoundaries()
                .filter(access -> handle.equals(access.getReferencedSym())).nonEmpty();
    }

    private static boolean references(ASTVariableAccess access, JVariableSymbol handle) {
        return handle.equals(access.getReferencedSym());
    }

    /**
     * The handle is a plain argument of a call whose method is declared in this file — the
     * catalogue's let-out: a helper receives the handle and runs with it now, so the async
     * wrapper is not keeping the handle itself. Anything else — a receiver of a call, a
     * stored value, an argument of a foreign method — does count.
     */
    private static boolean isPassThroughToASameFileMethod(ASTVariableAccess access) {
        if (!(access.getParent() instanceof net.sourceforge.pmd.lang.java.ast.ASTArgumentList arguments)
                || !(arguments.getParent() instanceof ASTMethodCall call)) {
            return false;
        }
        try {
            var resolution = call.getOverloadSelectionInfo();
            return !resolution.isFailed()
                    && resolution.getMethodType().getSymbol() != null
                    && resolution.getMethodType().getSymbol().tryGetNode() != null;
        } catch (RuntimeException | AssertionError error) {
            return false;
        }
    }

    /**
     * A lambda under an initializer or a field declaration is stored, not run here — one that
     * merely wraps a helper call inside the method's own execution is fine.
     */
    private static boolean isCapturedBeyondTheMethod(ASTLambdaExpression lambda) {
        for (Node ancestor : lambda.ancestors()) {
            if (ancestor instanceof ASTInitializer || ancestor instanceof ASTFieldDeclaration) {
                return true;
            }
            if (ancestor instanceof ASTExecutableDeclaration) {
                return false;
            }
        }
        return false;
    }

    private static boolean isAsyncMachineryCall(ASTMethodCall call) {
        ASTExpression receiver = call.getQualifier();
        if (receiver instanceof ASTVariableAccess access) {
            return asyncMachinery.contains(access.getName());
        }
        if (receiver instanceof ASTFieldAccess field) {
            return asyncMachinery.contains(field.getName());
        }
        if (receiver instanceof net.sourceforge.pmd.lang.java.ast.ASTTypeExpression type) {
            // the static shape: CompletableFuture.runAsync(...)
            var named = type.firstChild(net.sourceforge.pmd.lang.java.ast.ASTClassType.class);
            return named != null && asyncMachinery.contains(named.getSimpleName());
        }
        String owner = FrameworkAnchors.resolvedOwner(call);
        return owner != null && owner.startsWith("java.util.concurrent.");
    }
}
