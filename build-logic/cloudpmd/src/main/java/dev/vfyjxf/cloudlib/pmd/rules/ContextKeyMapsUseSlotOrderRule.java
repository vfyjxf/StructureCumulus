package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.regex.Pattern;

import net.sourceforge.pmd.lang.java.ast.ASTAssignmentExpression;
import net.sourceforge.pmd.lang.java.ast.ASTClassType;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorCall;
import net.sourceforge.pmd.lang.java.ast.ASTType;
import net.sourceforge.pmd.lang.java.ast.ASTTypeArguments;
import net.sourceforge.pmd.lang.java.ast.ASTVariableAccess;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclarator;
import net.sourceforge.pmd.lang.java.ast.ASTVariableId;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A map keyed by {@code ContextKey} must be a {@code TreeMap} in {@code ContextKeys.order()}, not
 * a hash container (K08-D01: the derive path looks the whole record up, and a hash container keys
 * by the full record — id, type, retention and merge — while the slot order keys by the slot alone,
 * so a lookup by slot semantics silently misses).
 *
 * <p>{@code ContextKey} is a record whose {@code hashCode} deliberately hashes only the slot
 * {@code (id, type)} while equality stays the full record; a hash map therefore keeps keys that
 * compare unequal in the same bucket and needs exact-record equality on every lookup. The engine's
 * lookups are slot lookups, so the only legal container is {@code new TreeMap<>(ContextKeys.order())}.
 *
 * <p>The rule reports an allocation of {@code HashMap} or {@code LinkedHashMap} whose key type
 * argument is {@code ContextKey}, spelled plainly or qualified. A diamond or raw allocation takes
 * its key from the context this file declares for it — the variable's, the field's or the
 * assignment target's type. A {@code ContextKey} anywhere else (a value, a key of the inner map of
 * a value) is not this defect, and {@code TraitContextKey} is a different type whose name merely
 * ends in {@code ContextKey}, so the key argument must start with the type name.
 */
public class ContextKeyMapsUseSlotOrderRule extends AbstractJavaRulechainRule {

    private static final Pattern KEY_TYPE = Pattern.compile("(?:[\\w$]+\\.)*ContextKey(?![\\w$])");

    public ContextKeyMapsUseSlotOrderRule() {
        super(ASTConstructorCall.class);
        setMessage("A ContextKey map must be a TreeMap in ContextKeys.order()");
    }

    @Override
    public Object visit(ASTConstructorCall node, Object data) {
        ASTClassType constructed = node.getTypeNode();
        String simpleName = constructed == null ? "" : constructed.getSimpleName();
        if (!"HashMap".equals(simpleName) && !"LinkedHashMap".equals(simpleName)) {
            return data;
        }
        RuleContext ctx = asCtx(data);
        String keyArgument = writtenKeyArgument(node, constructed);
        if (keyArgument == null || !KEY_TYPE.matcher(keyArgument.trim()).lookingAt()) {
            return data;
        }
        ctx.addViolationWithMessage(node,
                "this " + simpleName + " is keyed by " + keyArgument.trim()
                        + ", whose hash identity is the full record; allocate new TreeMap<>(ContextKeys.order())"
                        + " so lookups key by the slot");
        return data;
    }

    /**
     * The source text of the allocated map's key type argument: the written first type argument,
     * or — for a diamond or raw allocation — the first type argument of the declared type this
     * file gives the result. Returns {@code null} when neither is visible here.
     */
    private static String writtenKeyArgument(ASTConstructorCall node, ASTClassType constructed) {
        ASTTypeArguments written = constructed.getTypeArguments();
        if (written != null && !written.isDiamond()) {
            return firstArgumentText(written);
        }
        if (node.getParent() instanceof ASTVariableDeclarator declarator) {
            return firstArgumentTextOf(declarator.getVarId().getTypeNode());
        }
        if (node.getParent() instanceof ASTAssignmentExpression assignment
                && assignment.getChild(0) instanceof ASTVariableAccess target
                && target.getReferencedSym() != null
                && target.getReferencedSym().tryGetNode() instanceof ASTVariableId id) {
            return firstArgumentTextOf(id.getTypeNode());
        }
        return null;
    }

    private static String firstArgumentTextOf(ASTType type) {
        if (type == null) {
            return null;
        }
        ASTTypeArguments arguments = type.firstChild(ASTTypeArguments.class);
        if (arguments == null) {
            return null;
        }
        return firstArgumentText(arguments);
    }

    private static String firstArgumentText(ASTTypeArguments arguments) {
        ASTType first = arguments.firstChild(ASTType.class);
        return first == null ? null : FrameworkAnchors.sourceTextOf(first);
    }
}
