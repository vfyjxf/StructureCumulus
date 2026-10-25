package dev.vfyjxf.cloudlib.pmd.rules;

import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodReference;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A-08 of the framework catalogue: {@code Serialize.create(name, getter, setter, codec)} runs
 * its setter while <em>loading</em> — writing the handle with {@code set} or {@code apply}
 * there turns every disk read into a local change (load-time dirty, or load-time listener
 * fire). A Handle field must go through the {@code Serialize.create(name, Handle, codec)}
 * overload, which writes with {@code handle::load}, silently.
 *
 * <p>Both anchors are required: the call must resolve to
 * {@code dev.vfyjxf.cloudlib.api.data.serialize.Serialize.create} with four arguments (the
 * three-argument Handle overload is the correct one, and the same-named
 * {@code TypeDefinition.create} is something else entirely), and the third argument must write
 * through a receiver whose type is {@code dev.vfyjxf.cloudlib.api.data.handle.Handle} — a
 * method reference, or a lambda. A plain field's {@code field::set} in the four-argument
 * overload is legal and never reported.
 */
public class SerializeLoadsSilentlyRule extends AbstractJavaRulechainRule {

    private static final String SERIALIZE = "dev.vfyjxf.cloudlib.api.data.serialize.Serialize";
    private static final String HANDLE = "dev.vfyjxf.cloudlib.api.data.handle.Handle";

    private ASTCompilationUnit analysedUnit;
    private FrameworkAnchors.Imports imports = FrameworkAnchors.Imports.EMPTY;

    public SerializeLoadsSilentlyRule() {
        super(ASTMethodCall.class);
        setMessage("Serialize.create with a Handle must use the Handle overload, not a setter");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        RuleContext ctx = asCtx(data);
        rememberImports(node);
        if (!"create".equals(node.getMethodName())
                || node.getArguments().getNumChildren() != 4
                || !FrameworkAnchors.resolvesTo(node, SERIALIZE, imports)) {
            return data;
        }
        ASTExpression setter = (ASTExpression) node.getArguments().getChild(2);
        if (setter instanceof ASTMethodReference reference
                && !reference.isConstructorReference()
                && ("set".equals(reference.getMethodName()) || "apply".equals(reference.getMethodName()))
                && FrameworkAnchors.isA(reference.getQualifier(), HANDLE, imports)) {
            ctx.addViolationWithMessage(reference,
                    "Serialize.create loads from disk through this " + reference.getMethodName()
                            + " — a Handle written with " + reference.getMethodName()
                            + " on load turns the read into a local change; use the"
                            + " Serialize.create(name, handle, codec) overload instead");
        } else if (setter instanceof ASTLambdaExpression lambda) {
            checkLambda(lambda, ctx);
        }
        return data;
    }

    private void checkLambda(ASTLambdaExpression lambda, RuleContext ctx) {
        net.sourceforge.pmd.lang.ast.Node body = FrameworkAnchors.lambdaBodyOf(lambda);
        if (body == null) {
            return;
        }
        for (ASTMethodCall call : FrameworkAnchors.callsOf(body)) {
            if (("set".equals(call.getMethodName()) || "apply".equals(call.getMethodName()))
                    && call.getQualifier() != null
                    && FrameworkAnchors.isA(call.getQualifier(), HANDLE, imports)) {
                ctx.addViolationWithMessage(call,
                        call.getMethodName() + " runs while Serialize.create loads from disk — it turns"
                                + " the read into a local change; use the Serialize.create(name, handle,"
                                + " codec) overload instead");
            }
        }
    }

    private void rememberImports(ASTMethodCall node) {
        ASTCompilationUnit root = node.getRoot();
        if (analysedUnit != root) {
            analysedUnit = root;
            imports = FrameworkAnchors.importsOf(root);
        }
    }
}
