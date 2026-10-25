package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.ArrayList;
import java.util.List;

import net.sourceforge.pmd.lang.java.ast.ASTAnnotation;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTImportDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTModifierList;
import net.sourceforge.pmd.lang.java.ast.ASTPackageDeclaration;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * Every package is null-marked.
 *
 * <p>PMD sees one file at a time, so this rule is the half of "each package carries
 * {@code @NullMarked}" that can be checked per file: a compilation unit that declares no type —
 * that is, a {@code package-info.java} — must carry the annotation. Whether the annotation is
 * present at all elsewhere is checked by the plugin's {@code verifyPackageInfo} task, which can
 * walk the file system.
 *
 * <p>Both spellings in use are accepted: the fully qualified
 * {@code @org.jspecify.annotations.NullMarked} (CloudLib writes 86 that way) and a simple
 * {@code @NullMarked} backed by the matching import. A class- or member-level {@code @NullMarked}
 * is neither required nor reported.
 *
 * <p>Violations carry the whole message themselves, so the {@code message} attribute of the
 * ruleset reference is informational only. Messages therefore stay free of apostrophes and braces,
 * which {@code java.text.MessageFormat} would otherwise reinterpret.
 */
public class PackageNullMarkedRule extends AbstractJavaRulechainRule {

    private static final String nullMarkedSimpleName = "NullMarked";

    private static final String nullMarkedFullyQualifiedName = "org.jspecify.annotations.NullMarked";

    private final PropertyDescriptor<Boolean> acceptFullyQualified = PropertyFactory
            .booleanProperty("acceptFullyQualified")
            .desc("Accept the fully qualified spelling @org.jspecify.annotations.NullMarked")
            .defaultValue(true)
            .build();

    private final PropertyDescriptor<Boolean> acceptImportedSimpleName = PropertyFactory
            .booleanProperty("acceptImportedSimpleName")
            .desc("Accept @NullMarked when org.jspecify.annotations.NullMarked is imported")
            .defaultValue(true)
            .build();

    public PackageNullMarkedRule() {
        super(ASTCompilationUnit.class);
        definePropertyDescriptor(acceptFullyQualified);
        definePropertyDescriptor(acceptImportedSimpleName);
        setMessage("A package-info.java must be annotated with @NullMarked");
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        if (node.getTypeDeclarations().nonEmpty()) {
            return data; // a type was declared, so this is not a package-info.java
        }
        if (node.getModuleDeclaration() != null) {
            return data; // a modular compilation unit, not a package-info.java
        }
        if (isNullMarked(node)) {
            return data;
        }
        ASTPackageDeclaration packageDeclaration = node.getPackageDeclaration();
        asCtx(data).addViolationWithMessage(packageDeclaration != null ? packageDeclaration : node,
                "package-info.java must declare " + nullMarkedFullyQualifiedName
                        + ", or import " + nullMarkedFullyQualifiedName + " and use @" + nullMarkedSimpleName);
        return data;
    }

    private boolean isNullMarked(ASTCompilationUnit node) {
        boolean fullyQualifiedAccepted = getProperty(acceptFullyQualified);
        boolean simpleNameAccepted = getProperty(acceptImportedSimpleName) && importsNullMarked(node);
        for (ASTAnnotation annotation : declaredAnnotations(node)) {
            String written = writtenName(annotation);
            if (fullyQualifiedAccepted && nullMarkedFullyQualifiedName.equals(written)) {
                return true;
            }
            if (simpleNameAccepted && nullMarkedSimpleName.equals(written)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The annotations written before the package declaration. In PMD 7 they hang off the
     * {@link ASTPackageDeclaration}; the compilation unit's own modifier list is read too, so the
     * rule does not silently stop working if a future parser moves them one level up.
     */
    private static List<ASTAnnotation> declaredAnnotations(ASTCompilationUnit node) {
        List<ASTAnnotation> annotations = new ArrayList<>();
        ASTPackageDeclaration packageDeclaration = node.getPackageDeclaration();
        if (packageDeclaration != null) {
            annotations.addAll(packageDeclaration.getDeclaredAnnotations().toList());
        }
        for (ASTModifierList modifiers : node.children(ASTModifierList.class)) {
            annotations.addAll(modifiers.children(ASTAnnotation.class).toList());
        }
        return annotations;
    }

    /** The annotation name exactly as written in the source, without the leading {@code @}. */
    private static String writtenName(ASTAnnotation annotation) {
        String text = annotation.getTextDocument()
                .sliceOriginalText(annotation.getTextRegion())
                .toString()
                .strip();
        int start = text.startsWith("@") ? 1 : 0;
        int end = start;
        while (end < text.length() && isNameCharacter(text.charAt(end))) {
            end++;
        }
        return text.substring(start, end);
    }

    private static boolean isNameCharacter(char character) {
        return character == '.' || character == '$' || Character.isJavaIdentifierPart(character);
    }

    private static boolean importsNullMarked(ASTCompilationUnit node) {
        for (ASTImportDeclaration declaration : node.children(ASTImportDeclaration.class)) {
            if (!declaration.isStatic() && nullMarkedFullyQualifiedName.equals(declaration.getImportedName())) {
                return true;
            }
        }
        return false;
    }

}
