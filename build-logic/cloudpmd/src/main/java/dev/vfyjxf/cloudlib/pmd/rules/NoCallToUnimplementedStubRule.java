package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.List;

import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A-06 of the framework catalogue: two public API members are stubs that throw
 * {@code UnsupportedOperationException("Not Implemented")} unconditionally, so calling them is
 * always a crash or a no-op:
 * <ul>
 *   <li>{@code Widget.setDraggable(boolean)} — {@code api/ui/base/Widget.java:1730}, the real
 *       body is commented out;</li>
 *   <li>{@code Widgets.grid(...)} — {@code api/ui/Widgets.java:36}, builds a group, then throws.</li>
 * </ul>
 * The stub list is the rule property {@code stubs} ({@code owner#method}, owner canonical),
 * anchored on the resolved receiver type, so {@code setDragging} and any foreign
 * {@code grid} stay legal. Scanning for the thrown literal instead would flag the legitimate
 * guard throws of {@code ItemHandlers} / {@code FluidHandlers}. When a stub is implemented,
 * drop it from the property — the source lines above are the origin to check against.
 */
public class NoCallToUnimplementedStubRule extends AbstractJavaRulechainRule {

    private static final List<String> defaultStubs = List.of(
            "dev.vfyjxf.cloudlib.api.ui.base.Widget#setDraggable",
            "dev.vfyjxf.cloudlib.api.ui.Widgets#grid");

    private final PropertyDescriptor<List<String>> stubs = PropertyFactory
            .stringListProperty("stubs")
            .desc("Unimplemented public API stubs, as OwnerCanonicalName#methodName")
            .defaultValue(defaultStubs)
            .build();

    private ASTCompilationUnit analysedUnit;
    private FrameworkAnchors.Imports imports = FrameworkAnchors.Imports.EMPTY;

    public NoCallToUnimplementedStubRule() {
        super(ASTMethodCall.class);
        definePropertyDescriptor(stubs);
        setMessage("This call targets an unimplemented framework stub");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        RuleContext ctx = asCtx(data);
        rememberImports(node);
        ASTExpression receiver = node.getQualifier();
        if (receiver == null) {
            return data;
        }
        for (String stub : getProperty(stubs)) {
            int hash = stub.indexOf('#');
            if (hash < 0 || !stub.substring(hash + 1).equals(node.getMethodName())) {
                continue;
            }
            if (FrameworkAnchors.isExactly(receiver, stub.substring(0, hash), imports)) {
                ctx.addViolationWithMessage(node,
                        node.getMethodName() + " on " + stub.substring(0, hash)
                                + " is an unimplemented stub — it throws UnsupportedOperationException"
                                + " (Not Implemented); see the stubs property for the source lines");
            }
        }
        return data;
    }

    private void rememberImports(ASTMethodCall node) {
        ASTCompilationUnit root = node.getRoot();
        if (analysedUnit != root) {
            analysedUnit = root;
            imports = FrameworkAnchors.importsOf(root);
        }
    }
}
