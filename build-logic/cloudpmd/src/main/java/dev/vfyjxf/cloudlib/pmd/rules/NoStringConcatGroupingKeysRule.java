package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.Set;

import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTInfixExpression;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTStringLiteral;
import net.sourceforge.pmd.lang.java.ast.BinaryOp;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A map key must not be a {@code +}-concatenated string: the key argument of a Map's
 * {@code computeIfAbsent}, {@code put} or {@code get} may not be an additive expression that
 * contains a separator literal (X02-D01: {@code bay + "|" + instance} collided with ids that
 * themselves contain {@code "|"}, and no reader of the map can tell — a record key cannot
 * collide because its fields stay separate).
 *
 * <p>Only the key position is checked, so a log line, a path or a message built the same way
 * stays legal: the defect is the ambiguity of a delimiter inside a map key, not string
 * concatenation itself. The separator set is the classic grouping delimiters
 * ({@code | : . / - # ,}); an additive key without a literal separator (two variables, a
 * variable and a number) is still reported nowhere, because such a key cannot be read back
 * unambiguously either — but the rule's evidence is the delimiter, so only delimiter-bearing
 * keys are reported.
 *
 * <p>The receiver must be a {@code java.util.Map}: resolved when the analysis classpath is
 * present, else read from the receiver's written declaration, which is why the receiver shapes
 * this file cannot name are left alone — the rule can only miss, never invent.
 */
public class NoStringConcatGroupingKeysRule extends AbstractJavaRulechainRule {

    private static final Set<String> SEPARATORS = Set.of("|", ":", ".", "/", "-", "#", ",");

    public NoStringConcatGroupingKeysRule() {
        super(ASTMethodCall.class);
        setMessage("A map key must not be a concatenated string");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        String name = node.getMethodName();
        if (!"computeIfAbsent".equals(name) && !"put".equals(name) && !"get".equals(name)) {
            return data;
        }
        ASTExpression key = node.getArguments().size() > 0 ? node.getArguments().get(0) : null;
        if (!(key instanceof ASTInfixExpression additive) || additive.getOperator() != BinaryOp.ADD) {
            return data;
        }
        if (!isMapReceiver(node)) {
            return data;
        }
        String separator = separatorLiteralOf(additive);
        if (separator == null) {
            return data;
        }
        asCtx(data).addViolationWithMessage(additive,
                "this map key is a string joined on \"" + separator + "\", so a key part containing the"
                        + " separator collides; group by a record with one field per part instead");
        return data;
    }

    /** The first separator literal among the additive chain's operands, or null. */
    private static String separatorLiteralOf(ASTExpression additive) {
        if (additive instanceof ASTStringLiteral literal) {
            return SEPARATORS.contains(literal.getConstValue()) ? literal.getConstValue() : null;
        }
        if (!(additive instanceof ASTInfixExpression chain) || chain.getOperator() != BinaryOp.ADD) {
            return null; // a call or a parenthesed subexpression contributes no literal of its own
        }
        String left = separatorLiteralOf(chain.getLeftOperand());
        if (left != null) {
            return left;
        }
        return separatorLiteralOf(chain.getRightOperand());
    }

    private boolean isMapReceiver(ASTMethodCall call) {
        ASTExpression qualifier = call.getQualifier();
        if (qualifier == null) {
            return false;
        }
        return FrameworkAnchors.isA(qualifier, "java.util.Map", FrameworkAnchors.importsOf(call.getRoot()));
    }
}
