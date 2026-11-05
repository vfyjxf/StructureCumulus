package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.regex.Pattern;

import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFormalParameter;
import net.sourceforge.pmd.lang.java.ast.ASTLocalVariableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTType;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A level object is not an identity, so it must not key a collection.
 *
 * <p>Two failures follow from {@code Map<ServerLevel, …>}:
 * <ul>
 *   <li>the map holds a strong reference, so an unloaded level can never be collected;</li>
 *   <li>a dimension that unloads and loads again produces a new level object, so the same world
 *       owns two entries and the stale one is never visited again.</li>
 * </ul>
 *
 * <p>The fix is the level's resource key: {@code ServerLevel#dimension()} answers
 * {@code ResourceKey<Level>}, which names the dimension rather than one instance of it. A value that
 * must talk to the level (a capture, a watcher) may still hold it; the map entry, however, is keyed
 * by the resource key and created and removed by the level lifecycle.
 *
 * <p>The rule judges the <em>declared</em> first type argument, so it covers fields, local
 * variables, parameters and method return types in one pass and needs no type resolution: the outer
 * container and the key are both matched by simple name, package qualifiers allowed. A level in any
 * other position is legal — {@code Map<String, Level>} is a lookup whose value happens to be a
 * level, and {@code ResourceKey<Level>} is the fix, not the defect.
 *
 * <p>Every hit is a violation: there is no exemption for a short-lived local map. A local map keyed
 * by a level still reads as "this level is an identity", and the review that has to judge whether
 * this particular map outlives its level is more expensive than rewriting it.
 */
public class NoLevelAsCollectionKeyRule extends AbstractJavaRulechainRule {

    /** Hash-based containers, keyed or set: the ones that put the level in a hash slot. */
    private static final String CONTAINERS = "(?:[\\w$]+\\.)*(?:Map|SortedMap|NavigableMap|ConcurrentMap"
            + "|HashMap|LinkedHashMap|TreeMap|ConcurrentHashMap|ConcurrentSkipListMap|WeakHashMap"
            + "|IdentityHashMap|EnumMap|Set|SortedSet|NavigableSet|HashSet|LinkedHashSet|TreeSet"
            + "|ConcurrentSkipListSet)";

    /** The Minecraft level hierarchy, by simple name. */
    private static final String LEVELS = "(?:[\\w$]+\\.)*(?:Level|ServerLevel|ClientLevel|LevelAccessor"
            + "|LevelReader|LevelWriter)";

    private static final Pattern KEY = Pattern.compile("^" + CONTAINERS + "\\s*<\\s*" + LEVELS + "\\s*[,>]");

    public NoLevelAsCollectionKeyRule() {
        super(
            ASTFieldDeclaration.class,
            ASTLocalVariableDeclaration.class,
            ASTFormalParameter.class,
            ASTMethodDeclaration.class
        );
        setMessage("A level object must not key a collection");
    }

    @Override
    public Object visit(ASTFieldDeclaration node, Object data) {
        check(node.getTypeNode(), asCtx(data));
        return data;
    }

    @Override
    public Object visit(ASTLocalVariableDeclaration node, Object data) {
        check(node.getTypeNode(), asCtx(data));
        return data;
    }

    @Override
    public Object visit(ASTFormalParameter node, Object data) {
        check(node.getTypeNode(), asCtx(data));
        return data;
    }

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        check(node.getResultTypeNode(), asCtx(data));
        return data;
    }

    private void check(ASTType type, RuleContext ctx) {
        if (type == null) {
            return;
        }
        String declared = FrameworkAnchors.sourceTextOf(type).replaceAll("\\s+", " ").trim();
        if (declared.isEmpty() || !KEY.matcher(declared).find()) {
            return;
        }
        ctx.addViolationWithMessage(
            type,
            "A level object must not key a collection: " + declared
                    + ". Key by the level's resource key (ServerLevel#dimension()) instead."
        );
    }
}
