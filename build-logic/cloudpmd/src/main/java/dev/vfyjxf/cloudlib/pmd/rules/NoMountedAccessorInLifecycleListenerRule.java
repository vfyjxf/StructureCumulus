package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.List;
import java.util.Set;

import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A-04 of the framework catalogue: inside an {@code onInit} / {@code onMount} / {@code onUnmount}
 * / {@code onDestroy} listener, the mounted-gate accessors of {@code Widget} — {@code scene()},
 * {@code context()}, {@code path()}, {@code layout()}, {@code applyLayout()}, {@code needsLayout()},
 * called without a qualifier — throw {@code IllegalArgumentException("Widget is not mounted!")}:
 * {@code mount()} sets the mounted state only after the {@code onMount} listeners ran, and
 * {@code unmount()} clears scene and context before {@code onUnmount} runs. Every hit is a
 * guaranteed runtime throw.
 *
 * <p>The exemptions are structural, not a list: {@code nodeId()} is not a gate (it is assigned
 * before {@code onMount} dispatch and javadoc-permitted there); the listener's own parameters
 * ({@code scene}, {@code context}, {@code handle}) are used through a qualifier, and a qualified
 * call ({@code handle.scene()}, {@code w.path()}) is never this widget's accessor; and the
 * same-name methods of other types ({@code BasicScreen.scene()}, {@code SceneHandle.scene()})
 * are excluded by resolving the call's owner — only a call that resolves to the accessors
 * declared by {@code dev.vfyjxf.cloudlib.api.ui.base.Widget} counts, so an owner this file
 * cannot attribute is passed over in silence.
 */
public class NoMountedAccessorInLifecycleListenerRule extends AbstractJavaRulechainRule {

    private static final String WIDGET = "dev.vfyjxf.cloudlib.api.ui.base.Widget";

    private static final List<String> listenerRegistrations = List.of(
            "onInit", "onMount", "onUnmount", "onDestroy");

    private static final Set<String> gatedAccessors =
            Set.of("scene", "context", "path", "layout", "applyLayout", "needsLayout");

    private ASTCompilationUnit analysedUnit;
    private FrameworkAnchors.Imports imports = FrameworkAnchors.Imports.EMPTY;

    public NoMountedAccessorInLifecycleListenerRule() {
        super(ASTMethodCall.class);
        setMessage("A lifecycle listener reads a mounted-gate accessor of its own widget");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        RuleContext ctx = asCtx(data);
        rememberImports(node);
        if (!listenerRegistrations.contains(node.getMethodName()) || !receiverIsWidget(node)) {
            return data;
        }
        for (ASTExpression argument : node.getArguments()) {
            if (argument instanceof ASTLambdaExpression lambda) {
                scan(lambda, node.getMethodName(), ctx);
            }
        }
        return data;
    }

    private void scan(ASTLambdaExpression lambda, String listenerName, RuleContext ctx) {
        net.sourceforge.pmd.lang.ast.Node body = FrameworkAnchors.lambdaBodyOf(lambda);
        if (body == null) {
            return;
        }
        for (ASTMethodCall call : FrameworkAnchors.callsOf(body)) {
            if (call.getQualifier() != null
                    || call.getArguments().getNumChildren() != 0
                    || !gatedAccessors.contains(call.getMethodName())
                    || !resolvesToWidgetAccessor(call)) {
                continue;
            }
            ctx.addViolationWithMessage(call,
                    call.getMethodName() + "() inside an " + listenerName
                            + " listener reads a mounted-gate accessor of the widget itself — it throws"
                            + " IllegalArgumentException(Widget is not mounted!) there; use the listener"
                            + " parameters (scene, context, handle) instead");
        }
    }

    /** The registration is on a widget: its receiver resolves to Widget, or is one written here. */
    private boolean receiverIsWidget(ASTMethodCall node) {
        ASTExpression receiver = node.getQualifier();
        if (receiver != null) {
            return FrameworkAnchors.isA(receiver, WIDGET, imports);
        }
        String owner = FrameworkAnchors.resolvedOwner(node);
        return WIDGET.equals(owner);
    }

    /**
     * The unqualified call is one of Widget's own accessors. A subclass that redeclares an
     * accessor has its own contract and does not count; the six gates all live in
     * {@code Widget.java} itself.
     */
    private boolean resolvesToWidgetAccessor(ASTMethodCall call) {
        String owner = FrameworkAnchors.resolvedOwner(call);
        if (owner != null) {
            return WIDGET.equals(owner);
        }
        // unresolved: the declaration may still be written in this file — its owner is then
        // whatever class in this compilation unit declares it, matched by written import
        return sameFileOwnerIsWidget(call);
    }

    private boolean sameFileOwnerIsWidget(ASTMethodCall call) {
        try {
            var resolution = call.getOverloadSelectionInfo();
            if (resolution.isFailed()) {
                return false;
            }
            var symbol = resolution.getMethodType().getSymbol();
            if (symbol == null) {
                return false;
            }
            var declaringClass = symbol.getEnclosingClass();
            if (declaringClass.tryGetNode() == null) {
                return false; // declared in another file, unresolvable — say nothing
            }
            return "Widget".equals(declaringClass.getSimpleName())
                    && declaringClass.getPackageName().equals(
                            WIDGET.substring(0, WIDGET.lastIndexOf('.')));
        } catch (RuntimeException | AssertionError error) {
            return false;
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
