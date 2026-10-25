package dev.vfyjxf.cloudlib.pmd.rules;

import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodReference;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A-07 of the framework catalogue: a {@code Handle} writes in three modes
 * ({@code Handle.java:37-44}) — {@code set} (authoritative change: store, dirty, fire),
 * {@code apply} (received value: store, fire, no dirty), {@code load} (NBT: store only). The
 * receive path — the consumer handed to {@code whenReceive} / {@code whenReceiveFromClient} —
 * may only {@code apply}: a {@code handle::set} echoes the value back to the sender, and a
 * {@code handle::load} never fires the listeners at all.
 *
 * <p>The anchor is the argument, not the receiver: the argument must be a method reference on,
 * or a lambda writing through, a receiver whose type is
 * {@code dev.vfyjxf.cloudlib.api.data.handle.Handle}. That is what keeps {@code AtomicReference::set}
 * and {@code ModConfigSpec.DoubleValue::set} legal — same method name, other receiver.
 */
public class ReceiveHookUsesApplyRule extends AbstractJavaRulechainRule {

    private static final String HANDLE = "dev.vfyjxf.cloudlib.api.data.handle.Handle";

    private ASTCompilationUnit analysedUnit;
    private FrameworkAnchors.Imports imports = FrameworkAnchors.Imports.EMPTY;

    public ReceiveHookUsesApplyRule() {
        super(ASTMethodCall.class);
        setMessage("A receive hook must apply, not set or load");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        RuleContext ctx = asCtx(data);
        rememberImports(node);
        String name = node.getMethodName();
        if (!"whenReceive".equals(name) && !"whenReceiveFromClient".equals(name)) {
            return data;
        }
        for (ASTExpression argument : node.getArguments()) {
            if (argument instanceof ASTMethodReference reference
                    && !reference.isConstructorReference()
                    && ("set".equals(reference.getMethodName()) || "load".equals(reference.getMethodName()))
                    && FrameworkAnchors.isA(reference.getQualifier(), HANDLE, imports)) {
                ctx.addViolationWithMessage(reference,
                        "whenReceive hands over a received value, but " + reference.getMethodName()
                                + " is the wrong write mode — " + receiveMessage(reference.getMethodName())
                                + "; use handle::apply");
            } else if (argument instanceof ASTLambdaExpression lambda) {
                checkLambda(lambda, ctx);
            }
        }
        return data;
    }

    private void checkLambda(ASTLambdaExpression lambda, RuleContext ctx) {
        net.sourceforge.pmd.lang.ast.Node body = FrameworkAnchors.lambdaBodyOf(lambda);
        if (body == null) {
            return;
        }
        for (ASTMethodCall call : FrameworkAnchors.callsOf(body)) {
            if (("set".equals(call.getMethodName()) || "load".equals(call.getMethodName()))
                    && call.getQualifier() != null
                    && FrameworkAnchors.isA(call.getQualifier(), HANDLE, imports)) {
                ctx.addViolationWithMessage(call,
                        call.getMethodName() + " inside a whenReceive lambda is the wrong write mode — "
                                + receiveMessage(call.getMethodName()) + "; apply the received value instead");
            }
        }
    }

    private static String receiveMessage(String mode) {
        return "set".equals(mode)
                ? "set marks the value dirty again and echoes it back to the sender"
                : "load stores silently and never fires the listeners";
    }

    private void rememberImports(ASTMethodCall node) {
        ASTCompilationUnit root = node.getRoot();
        if (analysedUnit != root) {
            analysedUnit = root;
            imports = FrameworkAnchors.importsOf(root);
        }
    }
}
