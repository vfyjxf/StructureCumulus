package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.regex.Pattern;

import net.sourceforge.pmd.lang.java.ast.ASTExecutableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFieldAccess;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTVariableAccess;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A read accessor must not create state: inside a method whose name opens with get, find, read,
 * context, index, peek or lookup, no {@code computeIfAbsent} may run on a field map (A06-D03: the
 * reader resurrected the store an unload had just removed; K08-D03: the context read fell into the
 * table and created a context that was never derived).
 *
 * <p>The prefix names the contract — a method so named answers a question about existing state —
 * and the field receiver is what makes the call a mutation that survives the method. A local map
 * is built and dropped inside the method, and a creation point whose name states creation
 * ({@code openLevel}, {@code acquire}, {@code contribute}) is the legitimate shape the level
 * lifecycle owns. The receiver must resolve to a field symbol of this file; a receiver this file
 * cannot name is left alone, so the rule can only miss, never invent.
 */
public class ReadAccessorsMustNotCreateStateRule extends AbstractJavaRulechainRule {

    private static final Pattern READER_PREFIX = Pattern.compile("^(get|find|read|context|index|peek|lookup)");

    public ReadAccessorsMustNotCreateStateRule() {
        super(ASTMethodCall.class);
        setMessage("A read accessor must not create state");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        if (!"computeIfAbsent".equals(node.getMethodName())) {
            return data;
        }
        ASTExecutableDeclaration enclosing = node.ancestors(ASTExecutableDeclaration.class).first();
        if (!(enclosing instanceof ASTMethodDeclaration method)
                || !READER_PREFIX.matcher(method.getName()).find()) {
            return data;
        }
        if (!isFieldReceiver(node.getQualifier())) {
            return data;
        }
        asCtx(data).addViolationWithMessage(node,
                method.getName() + " reads but this computeIfAbsent creates state in a field map;"
                        + " move the creation to the lifecycle's creation point");
        return data;
    }

    /** A receiver this file declares as a field, read bare or qualified. */
    private static boolean isFieldReceiver(net.sourceforge.pmd.lang.java.ast.ASTExpression qualifier) {
        if (qualifier instanceof ASTVariableAccess access) {
            return access.getReferencedSym() != null && access.getReferencedSym().isField();
        }
        if (qualifier instanceof ASTFieldAccess field) {
            return field.getReferencedSym() != null && field.getReferencedSym().isField();
        }
        return false;
    }
}
