package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.sourceforge.pmd.lang.java.ast.ASTAnonymousClassDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTAnnotation;
import net.sourceforge.pmd.lang.java.ast.ASTClassType;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTSuperExpression;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.symbols.JClassSymbol;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A-01 of the framework catalogue: an override of a method carrying
 * {@code @org.jetbrains.annotations.MustBeInvokedByOverriders} must call {@code super} of that
 * method. {@code Widget.tick()} only fires the widget's tick listeners, so an override that
 * skips {@code super.tick()} silences them permanently.
 *
 * <p>The annotation is {@code CLASS} retention — invisible on any classpath PMD can read — so the
 * anchor is a hardcoded table of {@code Superclass.method} pairs, the six annotation sites
 * measured in the catalogue:
 * {@code Widget.tick}, {@code Widget.collectInspectionInfo}, {@code CompositeWidget.tick},
 * {@code CompositeWidget.collectInspectionInfo}, {@code BasicScreen.init},
 * {@code BasicMenuScreen.init}. The table is the rule property {@code annotatedMethods}, so a
 * new annotation site is one ruleset line instead of a code change.
 *
 * <p>A method declaration counts when it carries {@code @Override} and the {@code extends} chain
 * of its hosting type — walked through same-file declarations, then through the classpath when
 * the chain leaves this file — contains a superclass named by a pair with the method's name. The
 * declaration itself does not count (an override always overrides something in a proper
 * ancestor), which is what keeps {@code BasicScreen.init()}, overriding Minecraft's
 * {@code Screen.init()} outside the table, legal. Abstract and static declarations are skipped;
 * the first superclass hit up the chain names the pair in the message.
 */
public class MustCallSuperOnAnnotatedOverrideRule extends AbstractJavaRulechainRule {

    private static final List<String> defaultPairs = List.of(
            "Widget.tick",
            "Widget.collectInspectionInfo",
            "CompositeWidget.tick",
            "CompositeWidget.collectInspectionInfo",
            "BasicScreen.init",
            "BasicMenuScreen.init");

    private final PropertyDescriptor<List<String>> annotatedMethods = PropertyFactory
            .stringListProperty("annotatedMethods")
            .desc("Superclass.method pairs whose methods carry @MustBeInvokedByOverriders, "
                    + "as SuperclassSimpleName.methodName")
            .defaultValue(defaultPairs)
            .build();

    public MustCallSuperOnAnnotatedOverrideRule() {
        super(ASTMethodDeclaration.class);
        definePropertyDescriptor(annotatedMethods);
        setMessage("An override of a @MustBeInvokedByOverriders method must call super of it");
    }

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        RuleContext ctx = asCtx(data);
        String methodName = node.getName();
        Set<String> methodNames = methodNames();
        if (node.getBody() == null || !methodNames.contains(methodName) || !hasOverride(node)) {
            return data;
        }
        String superclass = annotatedSuperclassOf(hostOf(node), methodName);
        if (superclass == null || callsSuperOf(node, methodName)) {
            return data;
        }
        ctx.addViolationWithMessage(node,
                node.getName() + "() overrides " + superclass + "." + methodName
                        + "() which is @MustBeInvokedByOverriders — call super." + methodName + "()");
        return data;
    }

    private Set<String> methodNames() {
        return getProperty(annotatedMethods).stream()
                .map(pair -> pair.substring(pair.indexOf('.') + 1))
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean hasOverride(ASTMethodDeclaration node) {
        for (ASTAnnotation annotation : node.getModifiers().children(ASTAnnotation.class)) {
            if ("Override".equals(annotation.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private static ASTTypeDeclaration hostOf(ASTMethodDeclaration node) {
        return node.ancestors(ASTTypeDeclaration.class).first();
    }

    /**
     * The name of the nearest proper ancestor named by a table pair for {@code methodName}. The
     * chain is walked over the {@code extends} type nodes while the superclass is declared in
     * this file, then over the classpath symbol chain once it leaves.
     */
    private String annotatedSuperclassOf(ASTTypeDeclaration host, String methodName) {
        List<String> pairs = getProperty(annotatedMethods);
        ASTClassType typeNode = host == null ? null : host.getSuperClassTypeNode();
        JClassSymbol symbol = null;
        int depth = 0;
        while (depth++ < 16) {
            String name;
            if (typeNode != null) {
                name = typeNode.getSimpleName();
                symbol = typeNode.getTypeMirror().getSymbol() instanceof JClassSymbol classSymbol
                        ? classSymbol
                        : null;
                typeNode = null;
            } else if (symbol != null) {
                symbol = symbol.getSuperclass();
                if (symbol == null) {
                    return null;
                }
                name = symbol.getSimpleName();
            } else {
                return null;
            }
            if (pairs.contains(name + "." + methodName)) {
                return name;
            }
            if (symbol != null && symbol.tryGetNode() instanceof ASTTypeDeclaration sameFile) {
                typeNode = sameFile.getSuperClassTypeNode();
                symbol = null;
            } else if (symbol != null
                    && symbol.tryGetNode() instanceof ASTAnonymousClassDeclaration anonymous) {
                typeNode = anonymous.getSuperClassTypeNode();
                symbol = null;
            }
        }
        return null;
    }

    private static boolean callsSuperOf(ASTMethodDeclaration node, String methodName) {
        for (ASTMethodCall call : node.getBody().descendants(ASTMethodCall.class)) {
            if (methodName.equals(call.getMethodName())
                    && call.getQualifier() instanceof ASTSuperExpression) {
                return true;
            }
        }
        return false;
    }
}
