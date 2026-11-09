package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.regex.Pattern;

import net.sourceforge.pmd.lang.java.ast.ASTClassType;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTStringLiteral;
import net.sourceforge.pmd.lang.java.ast.ASTType;
import net.sourceforge.pmd.lang.java.ast.ASTTypeExpression;
import net.sourceforge.pmd.lang.java.ast.ASTVariableAccess;
import net.sourceforge.pmd.lang.java.ast.ASTVariableId;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.symbols.JMethodSymbol;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A finding message must not embed its own {@code SC-<FAMILY>-<NNNN>} code (K06-D06: the renderer
 * prefixes the code itself, so an embedded code renders as a double prefix).
 *
 * <p>Two sinks are checked, and only they:
 * <ul>
 *   <li>a {@code message(...)} call on a {@code Finding.builder(...)} chain — the chain may be
 *       split across a local declared as {@code Finding.Builder} in the same file;</li>
 *   <li>a call to a same-file helper named {@code code} whose return type is {@code Finding}
 *       (MatchEngine's {@code code(entry, phase, message, locus)} is the shape).</li>
 * </ul>
 *
 * <p>The sinks that deliberately carry a code are not Finding messages and stay legal: the
 * rejection constructors (BlueprintBuilder's {@code fail(code, message)}, PlanRejected) and the
 * IAE texts of Catalog.put and Check (D-66/D-69) state the code as the identity of the failure,
 * and the renderer never prefixes them. A note may legitimately name another code
 * (CaptureReports relays "reported as SC-OCC-0004"), which is why {@code note(...)} and label
 * texts are out of scope.
 *
 * <p>The literal is matched with {@code find()}, not {@code matches()}: the code may sit inside a
 * sentence ("... stays SC-OCC-0004."). Anchoring is by simple name with an import fallback never
 * needed — the builder chain and the same-file helper are both visible in the compilation unit.
 */
public class FindingMessageMustNotEmbedCodesRule extends AbstractJavaRulechainRule {

    private static final Pattern CODE = Pattern.compile("SC-[A-Z]{2,6}-\\d{4}");

    public FindingMessageMustNotEmbedCodesRule() {
        super(ASTMethodCall.class);
        setMessage("A Finding message must not embed its SC code");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        RuleContext ctx = asCtx(data);
        if ("message".equals(node.getMethodName()) && isFindingBuilderChain(node)) {
            reportEmbeddedCodes(node, ctx, "a Finding message");
        } else if ("code".equals(node.getMethodName()) && resolvesToAFindingCodeHelper(node)) {
            reportEmbeddedCodes(node, ctx, "the Finding-building code(...) helper");
        }
        return data;
    }

    private static void reportEmbeddedCodes(ASTMethodCall node, RuleContext ctx, String sink) {
        for (ASTStringLiteral literal : node.getArguments().descendants(ASTStringLiteral.class)) {
            if (CODE.matcher(literal.getConstValue()).find()) {
                ctx.addViolationWithMessage(literal,
                        "the string given to " + sink + " embeds " + literal.getConstValue()
                                + "; the renderer prefixes the finding code itself, so the code would"
                                + " appear twice");
            }
        }
    }

    /**
     * Whether the {@code message(...)} call hangs on a {@code Finding.builder(...)} chain, either
     * directly or through a local of this file declared as {@code Finding.Builder}.
     */
    private static boolean isFindingBuilderChain(ASTMethodCall call) {
        ASTExpression qualifier = call.getQualifier();
        while (qualifier instanceof ASTMethodCall chained) {
            if (isFindingBuilderCall(chained)) {
                return true;
            }
            qualifier = chained.getQualifier();
        }
        if (!(qualifier instanceof ASTVariableAccess variable)) {
            return false;
        }
        return variable.getReferencedSym() != null
                && variable.getReferencedSym().tryGetNode() instanceof ASTVariableId id
                && isFindingBuilderType(id.getTypeNode());
    }

    private static boolean isFindingBuilderCall(ASTMethodCall call) {
        if (!"builder".equals(call.getMethodName())
                || !(call.getQualifier() instanceof ASTTypeExpression typeExpression)) {
            return false;
        }
        ASTClassType named = typeExpression.firstChild(ASTClassType.class);
        return named != null && "Finding".equals(named.getSimpleName());
    }

    private static boolean isFindingBuilderType(ASTType type) {
        return type != null && FrameworkAnchors.sourceTextOf(type).replaceAll("\\s+", "")
                .startsWith("Finding.Builder");
    }

    /**
     * Whether the {@code code} call resolves to a method of this file whose return type is
     * {@code Finding}; the return type is what separates MatchEngine's Finding helper from
     * same-named helpers such as AdapterCodes' {@code code(int)} returning a FindingCode.
     */
    private static boolean resolvesToAFindingCodeHelper(ASTMethodCall call) {
        try {
            if (call.getOverloadSelectionInfo().isFailed()) {
                return false;
            }
            if (!(call.getOverloadSelectionInfo().getMethodType().getSymbol() instanceof JMethodSymbol method)) {
                return false;
            }
            if (method.tryGetNode() == null) {
                return false;
            }
            ASTType resultType = method.tryGetNode().getResultTypeNode();
            ASTClassType named = resultType instanceof ASTClassType self ? self
                    : resultType == null ? null : resultType.firstChild(ASTClassType.class);
            return named != null && "Finding".equals(named.getSimpleName());
        } catch (RuntimeException | AssertionError error) {
            return false; // an unresolved call says nothing either way
        }
    }
}
