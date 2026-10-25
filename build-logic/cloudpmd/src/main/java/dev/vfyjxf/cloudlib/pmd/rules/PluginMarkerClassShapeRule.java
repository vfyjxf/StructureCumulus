package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.List;

import net.sourceforge.pmd.lang.java.ast.ASTAnnotation;
import net.sourceforge.pmd.lang.java.ast.ASTClassDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTClassType;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.ModifierOwner;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.symbols.JClassSymbol;
import net.sourceforge.pmd.lang.java.types.TypeTestUtil;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A-03 of the framework catalogue: a class marked {@code @PluginMarker} must be a shape
 * {@code AnnotationPluginLookup} can actually instantiate — implement a {@code ModPlugin}
 * subinterface, have a public no-arg constructor (declaring none counts, the implicit one is
 * public), be static when nested, and not be abstract. Every violation is the same failure:
 * the lookup logs and drops the class, and the plugin silently never runs.
 *
 * <p>The second shape is the inverse, as a warning: a concrete class directly implementing a
 * known {@code ModPlugin} subinterface ({@code CloudLibPlugin}, {@code CloudLibClientPlugin},
 * {@code NimbusPlugin}, {@code ClientNimbusPlugin}) without {@code @PluginMarker} is invisible
 * to the lookup too — unless it is injected by hand at the entry point, which the
 * {@code exemptClasses} property carries (default: {@code NimbusEnginePlugin}). Interfaces and
 * abstract classes are skipped here: an interface names the contract, and an abstract base
 * shares code without claiming to be a loadable plugin.
 *
 * <p>The marker annotation is matched by simple name, the way {@code RedundantNullCheckRule}
 * matches {@code Nullable}: name matching cannot be fooled into matching a different retention
 * or a foreign same-named annotation without also importing this framework.
 */
public class PluginMarkerClassShapeRule extends AbstractJavaRulechainRule {

    private static final String MOD_PLUGIN = "dev.vfyjxf.cloudlib.api.plugin.ModPlugin";

    private static final List<String> knownPluginInterfaces = List.of(
            "ModPlugin",
            "CloudLibPlugin",
            "CloudLibClientPlugin",
            "NimbusPlugin",
            "ClientNimbusPlugin");

    private final PropertyDescriptor<List<String>> exemptClasses = PropertyFactory
            .stringListProperty("exemptClasses")
            .desc("Simple names of plugin classes injected by hand at the entry point, which need no marker")
            .defaultValues("NimbusEnginePlugin")
            .build();

    public PluginMarkerClassShapeRule() {
        super(ASTClassDeclaration.class);
        definePropertyDescriptor(exemptClasses);
        setMessage("@PluginMarker classes must be instantiable by AnnotationPluginLookup");
    }

    @Override
    public Object visit(ASTClassDeclaration node, Object data) {
        RuleContext ctx = asCtx(data);
        if (node.isInterface()) {
            return data;
        }
        if (hasPluginMarker(node)) {
            checkMarkedShape(node, ctx);
        } else {
            checkUnmarkedPlugin(node, ctx);
        }
        return data;
    }

    private void checkMarkedShape(ASTClassDeclaration node, RuleContext ctx) {
        if (node.isAbstract()) {
            ctx.addViolationWithMessage(node,
                    node.getSimpleName() + " is @PluginMarker but abstract — AnnotationPluginLookup logs"
                            + " and drops it, so the plugin silently never runs; make it concrete");
        }
        if (node.getVisibility() != ModifierOwner.Visibility.V_PUBLIC) {
            ctx.addViolationWithMessage(node,
                    node.getSimpleName() + " is @PluginMarker but not public — newInstance refuses a"
                            + " non-public class even with a public constructor, and the plugin is"
                            + " silently dropped");
        }
        if (!implementsModPlugin(node)) {
            ctx.addViolationWithMessage(node,
                    node.getSimpleName() + " is @PluginMarker but implements no ModPlugin subinterface"
                            + " — AnnotationPluginLookup logs and drops it");
        }
        if (isNestedAndNotStatic(node)) {
            ctx.addViolationWithMessage(node,
                    node.getSimpleName() + " is @PluginMarker but a non-static nested class"
                            + " — AnnotationPluginLookup cannot construct it; make it static or top-level");
        }
        if (!hasPublicNoArgConstructor(node)) {
            ctx.addViolationWithMessage(node,
                    node.getSimpleName() + " is @PluginMarker but has no public no-arg constructor"
                            + " — AnnotationPluginLookup logs and drops it");
        }
    }

    private void checkUnmarkedPlugin(ASTClassDeclaration node, RuleContext ctx) {
        if (node.isAbstract() || getProperty(exemptClasses).contains(node.getSimpleName())
                || !directlyImplementsKnownPluginInterface(node)) {
            return;
        }
        ctx.addViolationWithMessage(node,
                node.getSimpleName() + " implements a ModPlugin subinterface but carries no @PluginMarker"
                        + " — AnnotationPluginLookup cannot find it; mark it, or add it to the "
                        + "exemptClasses property when the entry point injects it by hand");
    }

    private static boolean hasPluginMarker(ASTClassDeclaration node) {
        for (ASTAnnotation annotation : node.getModifiers().children(ASTAnnotation.class)) {
            if ("PluginMarker".equals(annotation.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the class implements {@code ModPlugin}: by resolution when the classpath knows the
     * framework, otherwise by a direct {@code implements} clause naming a known subinterface —
     * every public entry of the framework's own plugin hierarchy is in that list.
     */
    private static boolean implementsModPlugin(ASTClassDeclaration node) {
        try {
            if (TypeTestUtil.isA(MOD_PLUGIN, node)) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // fall through to the written clause
        }
        return directlyImplementsKnownPluginInterface(node);
    }

    private static boolean directlyImplementsKnownPluginInterface(ASTClassDeclaration node) {
        return node.getSuperInterfaceTypeNodes()
                .filter(type -> knownPluginInterfaces.contains(type.getSimpleName()))
                .nonEmpty()
                || implementsSameFileKnownInterface(node);
    }

    /** An interface declared in this file that extends a known subinterface counts as well. */
    private static boolean implementsSameFileKnownInterface(ASTClassDeclaration node) {
        for (ASTClassType type : node.getSuperInterfaceTypeNodes()) {
            if (type.getTypeMirror().getSymbol() instanceof JClassSymbol symbol
                    && symbol.tryGetNode() instanceof ASTTypeDeclaration sameFile
                    && sameFile.isInterface()
                    && sameFile.getSuperInterfaceTypeNodes()
                            .filter(superType -> knownPluginInterfaces.contains(superType.getSimpleName()))
                            .nonEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNestedAndNotStatic(ASTClassDeclaration node) {
        return !node.isTopLevel() && !node.isStatic();
    }

    /** Declaring no constructor counts: the implicit constructor is public and no-arg. */
    private static boolean hasPublicNoArgConstructor(ASTClassDeclaration node) {
        List<ASTConstructorDeclaration> constructors =
                node.getDeclarations(ASTConstructorDeclaration.class).toList();
        for (ASTConstructorDeclaration constructor : constructors) {
            if (constructor.getFormalParameters().size() == 0
                    && constructor.getVisibility() == ModifierOwner.Visibility.V_PUBLIC) {
                return true;
            }
        }
        return constructors.isEmpty();
    }
}
