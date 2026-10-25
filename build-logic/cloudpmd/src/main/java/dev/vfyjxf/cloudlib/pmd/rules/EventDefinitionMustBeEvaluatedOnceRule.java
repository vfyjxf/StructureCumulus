package dev.vfyjxf.cloudlib.pmd.rules;

import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTInitializer;
import net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A-10 of the framework catalogue: {@code Events.define(...)} and
 * {@code Events.createGeneric(...)}, and the {@code create} of {@code Event} and
 * {@code SimpleEvent}, build an identity — {@code EventDefinitionImpl} has no
 * {@code equals}, and {@code EventChannelImpl} keys a map by it — so a definition must be
 * evaluated exactly once and held for the life of the program: a field initializer (an
 * interface constant included). Called twice, the second value is a different event: the
 * register and the invoker never meet, silently.
 *
 * <p>The judgement walks up from the call: the first enclosing field declaration is the legal
 * shape; the first enclosing method, constructor or lambda is the violation — a call inside a
 * lambda re-evaluates every time the lambda runs, even when the lambda itself sits in a field
 * initializer. A {@code static} initializer block is accepted as the moral equivalent of a
 * field initializer (it runs exactly once); an instance initializer block runs per instance
 * and is a violation.
 *
 * <p>Everything is anchored: {@code define}/{@code createGeneric} must resolve to
 * {@code dev.vfyjxf.cloudlib.api.event.Events}, {@code create} to {@code Event} or
 * {@code SimpleEvent} by owner or return type — {@code ModConfigSpec.Builder#define} has the
 * same name and shape and is a dozen legal call sites in the two projects. The framework's
 * own {@code dev.vfyjxf.cloudlib.api.event} package forwards these calls internally and is
 * exempt.
 */
public class EventDefinitionMustBeEvaluatedOnceRule extends AbstractJavaRulechainRule {

    private static final String EVENTS = "dev.vfyjxf.cloudlib.api.event.Events";
    private static final String EVENT = "dev.vfyjxf.cloudlib.api.event.Event";
    private static final String SIMPLE_EVENT = "dev.vfyjxf.cloudlib.api.event.SimpleEvent";

    private ASTCompilationUnit analysedUnit;
    private FrameworkAnchors.Imports imports = FrameworkAnchors.Imports.EMPTY;

    public EventDefinitionMustBeEvaluatedOnceRule() {
        super(ASTMethodCall.class);
        setMessage("An event definition must be evaluated once, in a field initializer");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        RuleContext ctx = asCtx(data);
        rememberImports(node);
        ASTCompilationUnit root = node.getRoot();
        if (root.getPackageDeclaration() != null
                && "dev.vfyjxf.cloudlib.api.event".equals(root.getPackageDeclaration().getName())) {
            return data;
        }
        if (!isDefinitionCall(node)) {
            return data;
        }
        String verdict = placementOf(node);
        if (verdict != null) {
            ctx.addViolationWithMessage(node,
                    "Events." + node.getMethodName() + " evaluates a fresh identity here (" + verdict
                            + ") — hold the result in a field initializer, or register and invoke"
                            + " will never meet");
        }
        return data;
    }

    private boolean isDefinitionCall(ASTMethodCall node) {
        String name = node.getMethodName();
        if (("define".equals(name) || "createGeneric".equals(name))) {
            return FrameworkAnchors.resolvesTo(node, EVENTS, imports);
        }
        if ("create".equals(name)) {
            return FrameworkAnchors.resolvesTo(node, EVENT, imports)
                    || FrameworkAnchors.resolvesTo(node, SIMPLE_EVENT, imports)
                    || returns(node, EVENT) || returns(node, SIMPLE_EVENT);
        }
        return false;
    }

    private boolean returns(ASTMethodCall node, String canonicalName) {
        try {
            return net.sourceforge.pmd.lang.java.types.TypeTestUtil.isExactlyA(canonicalName, node);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Where the call sits: {@code null} for the legal shapes (a field initializer or a static
     * initializer), a short name of the illegal one otherwise.
     */
    private static String placementOf(ASTMethodCall node) {
        for (var ancestor : node.ancestors()) {
            if (ancestor instanceof ASTFieldDeclaration) {
                return null;
            }
            if (ancestor instanceof ASTInitializer initializer) {
                return initializer.isStatic() ? null : "an instance initializer block";
            }
            if (ancestor instanceof ASTLambdaExpression) {
                return "a lambda";
            }
            if (ancestor instanceof ASTMethodDeclaration declaration) {
                return "method " + declaration.getName();
            }
            if (ancestor instanceof ASTConstructorDeclaration) {
                return "a constructor";
            }
        }
        return null;
    }

    private void rememberImports(ASTMethodCall node) {
        ASTCompilationUnit root = node.getRoot();
        if (analysedUnit != root) {
            analysedUnit = root;
            imports = FrameworkAnchors.importsOf(root);
        }
    }
}
