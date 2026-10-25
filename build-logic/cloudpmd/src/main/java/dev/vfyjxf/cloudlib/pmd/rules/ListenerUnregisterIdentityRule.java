package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.List;

import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorCall;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodReference;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A-09 of the framework catalogue: the event layer identifies listeners by reference equality
 * ({@code Events.java:203-209}), so two shapes are guaranteed bugs:
 * <ol>
 *   <li><b>Shape 1</b> — the argument of {@code unregister} / {@code isRegistered} is a fresh
 *       lambda, method reference or object creation: a new instance can never equal the
 *       registered one, so the call is a no-op that looks like cleanup;</li>
 *   <li><b>Shape 2</b> — a listener registered with {@code registerManaged(listener, n)} is
 *       wrapped ({@code Event.java:62-66} promises a new object), so unregistering the original
 *       listener afterwards silently does nothing. The pairing is same-file, by written text:
 *       a {@code registerManaged(x, 5)} and an {@code unregister(x)} in one compilation unit.</li>
 * </ol>
 *
 * <p>Both shapes are anchored on the receiver resolving to the event layer —
 * {@code EventDefinition}, {@code Event}, {@code EventChannel}, {@code EventHandler},
 * {@code SimpleEvent} — which is what keeps {@code AtomicReference.set} and any foreign
 * {@code unregister} out.
 */
public class ListenerUnregisterIdentityRule extends AbstractJavaRulechainRule {

    private static final List<String> eventLayerTypes = List.of(
            "dev.vfyjxf.cloudlib.api.event.EventDefinition",
            "dev.vfyjxf.cloudlib.api.event.Event",
            "dev.vfyjxf.cloudlib.api.event.EventChannel",
            "dev.vfyjxf.cloudlib.api.event.EventHandler",
            "dev.vfyjxf.cloudlib.api.event.SimpleEvent");

    private ASTCompilationUnit analysedUnit;
    private FrameworkAnchors.Imports imports = FrameworkAnchors.Imports.EMPTY;

    public ListenerUnregisterIdentityRule() {
        super(ASTMethodCall.class);
        setMessage("The event layer identifies listeners by reference, and this call cannot match");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        RuleContext ctx = asCtx(data);
        rememberImports(node);
        String name = node.getMethodName();
        if ("registerManaged".equals(name)) {
            return data; // its pairing is judged from the unregister side, below
        }
        if (!"unregister".equals(name) && !"isRegistered".equals(name) || !receiverIsEventLayer(node)
                || node.getArguments().getNumChildren() == 0) {
            return data;
        }
        ASTExpression listener = (ASTExpression) node.getArguments().getChild(0);
        if (listener instanceof ASTLambdaExpression || listener instanceof ASTMethodReference
                || listener instanceof ASTConstructorCall) {
            ctx.addViolationWithMessage(node,
                    name + "(" + shapeOf(listener) + ") is a guaranteed no-op — the event layer compares"
                            + " listeners by reference, and a freshly created one can never equal the"
                            + " registered instance");
        }
        if ("unregister".equals(name) && registeredManagedInThisFile(node)) {
            ctx.addViolationWithMessage(node,
                    "this unregister cannot match the registerManaged of the same listener text"
                            + " — registerManaged stores a wrapper, unregister compares against the"
                            + " original");
        }
        return data;
    }

    private static String shapeOf(ASTExpression listener) {
        if (listener instanceof ASTLambdaExpression) {
            return "a lambda";
        }
        if (listener instanceof ASTMethodReference) {
            return "a method reference";
        }
        return "a new object";
    }

    private boolean receiverIsEventLayer(ASTMethodCall node) {
        ASTExpression receiver = node.getQualifier();
        if (receiver != null) {
            return eventLayerTypes.stream()
                    .anyMatch(fqn -> FrameworkAnchors.isA(receiver, fqn, imports));
        }
        String owner = FrameworkAnchors.resolvedOwner(node);
        return owner != null && eventLayerTypes.contains(owner);
    }

    /**
     * Shape 2, same compilation unit: some {@code registerManaged(<listener>, <count>)} call in
     * this file wrote the same listener text this {@code unregister} now uses. Text comparison
     * is the catalogue's own pairing, and conservative: a hit means the same expression was
     * written twice.
     */
    private boolean registeredManagedInThisFile(ASTMethodCall unregister) {
        ASTExpression listener = (ASTExpression) unregister.getArguments().getChild(0);
        String listenerText = FrameworkAnchors.sourceTextOf(listener);
        for (ASTMethodCall call : unregister.getRoot().descendants(ASTMethodCall.class)) {
            if (!"registerManaged".equals(call.getMethodName())
                    || call.getArguments().getNumChildren() < 2
                    || call.getArguments().getChild(1) instanceof ASTLambdaExpression) {
                continue;
            }
            if (listenerText.equals(FrameworkAnchors
                    .sourceTextOf((ASTExpression) call.getArguments().getChild(0)))) {
                return true;
            }
        }
        return false;
    }

    private void rememberImports(ASTMethodCall node) {
        ASTCompilationUnit root = node.getRoot();
        if (analysedUnit != root) {
            analysedUnit = root;
            imports = FrameworkAnchors.importsOf(root);
        }
    }
}
