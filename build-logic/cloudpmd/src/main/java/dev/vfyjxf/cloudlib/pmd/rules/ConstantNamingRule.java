package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.regex.Pattern;

import net.sourceforge.pmd.lang.java.ast.ASTEnumConstant;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTVariableId;
import net.sourceforge.pmd.lang.java.ast.JModifier;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * Constants are lowerCamelCase, not SHOUTY_SNAKE_CASE.
 *
 * <p>Two names are checked, and two of the rule catalogue's decisions are baked in:
 * <ul>
 *   <li>a field declared <em>explicitly</em> {@code static final} must match
 *       {@code constantPattern}. The quantifier is {@code *}, not {@code +}, so a single upper case
 *       letter such as {@code H} is reported — the old Spotless lint's {@code {1,}} missed exactly
 *       that case.</li>
 *   <li>an enum constant must match {@code enumConstantPattern}, which deliberately allows
 *       underscores so that names such as {@code top_left} stay legal.</li>
 *   <li>fields declared inside an interface (or annotation type) are skipped whole: they are
 *       implicitly {@code public static final}, so an implicit constant such as
 *       {@code Easing.QUAD_IN} is not an explicitly written one and must not be renamed. There is
 *       no platform property for this; the exemption is the rule's semantics.</li>
 * </ul>
 *
 * <p>Standard {@code ConstantName} and {@code FieldNamingConventions} are deliberately not used:
 * neither can exempt interface members, and they disagree about whether enum constants are in
 * scope. This rule covers both in one place.
 *
 * <p>Violations carry the whole message themselves, so the {@code message} attribute of the
 * ruleset reference is informational only. Messages therefore stay free of apostrophes and braces,
 * which {@code java.text.MessageFormat} would otherwise reinterpret.
 */
public class ConstantNamingRule extends AbstractJavaRulechainRule {

    /** lowerCamelCase; {@code *} so that the single letter {@code H} is caught. */
    private static final String defaultConstantPattern = "[a-z][a-zA-Z0-9]*";

    /** lowerCamelCase, underscores allowed on purpose ({@code top_left}). */
    private static final String defaultEnumConstantPattern = "[a-z][a-zA-Z0-9_]*";

    private final PropertyDescriptor<Pattern> constantPattern = PropertyFactory
            .regexProperty("constantPattern")
            .desc("Regex a field declared static final must match, for example height or minWidth")
            .defaultValue(defaultConstantPattern)
            .build();

    private final PropertyDescriptor<Pattern> enumConstantPattern = PropertyFactory
            .regexProperty("enumConstantPattern")
            .desc("Regex an enum constant must match; underscores are allowed, for example top_left")
            .defaultValue(defaultEnumConstantPattern)
            .build();

    public ConstantNamingRule() {
        super(ASTFieldDeclaration.class, ASTEnumConstant.class);
        definePropertyDescriptor(constantPattern);
        definePropertyDescriptor(enumConstantPattern);
        setMessage("Constant names must be lowerCamelCase");
    }

    @Override
    public Object visit(ASTFieldDeclaration node, Object data) {
        if (isInterfaceField(node) || !node.getModifiers().hasAllExplicitly(JModifier.STATIC, JModifier.FINAL)) {
            return data;
        }
        RuleContext ctx = asCtx(data);
        for (ASTVariableId varId : node.getVarIds()) {
            if (!getProperty(constantPattern).matcher(varId.getName()).matches()) {
                ctx.addViolationWithMessage(varId, "Static final field " + varId.getName() + " must match "
                        + getProperty(constantPattern) + "; write constant names in lowerCamelCase");
            }
        }
        return data;
    }

    @Override
    public Object visit(ASTEnumConstant node, Object data) {
        String name = node.getName();
        if (!getProperty(enumConstantPattern).matcher(name).matches()) {
            asCtx(data).addViolationWithMessage(node, "Enum constant " + name + " must match "
                    + getProperty(enumConstantPattern) + "; write enum constants in lowerCamelCase");
        }
        return data;
    }

    /**
     * True when the field is a member of an interface or annotation type, where it is implicitly
     * {@code public static final} and hence carries no naming intent of its own. Nested types
     * inside such an interface are ordinary types again, so only the nearest enclosing declaration
     * decides.
     */
    private static boolean isInterfaceField(ASTFieldDeclaration node) {
        return node.ancestors(ASTTypeDeclaration.class)
                .firstOpt()
                .map(ASTTypeDeclaration::isInterface)
                .orElse(false);
    }

}
