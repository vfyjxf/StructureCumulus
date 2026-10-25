package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.List;
import java.util.Map;

import net.sourceforge.pmd.lang.java.ast.ASTArgumentList;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A-11 of the framework catalogue: a channel or kind declared with
 * {@code ExposeChannel.of(...)} / {@code ExposeLayerChannel.of(...)} / {@code ExposeKind.of(...)}
 * only works if it was registered — {@code RuntimeExposeSession.channelOf} looks the codec up
 * in the registry and {@code serve()} silently returns false when it is missing. The judgement
 * is per compilation unit: an {@code of} call is fine when it is the argument of a
 * {@code register} call of its registry, or when the same file registers anything through that
 * registry; otherwise it is a declared identity nobody can resolve. A file-level scan is the
 * catalogue's own scope — the cross-file version is a Gradle task, not a PMD rule.
 *
 * <p>The two chains are told apart by symbol: {@code ExposeChannel} and
 * {@code ExposeLayerChannel} belong to {@code ExposeChannelRegistry}, {@code ExposeKind} to
 * {@code ExposeKindRegistry}. The escape hatch is the marker
 * {@code framework-id-unregistered-ok} on the {@code of} line (PMD's own {@code // NOPMD}
 * works as well).
 */
public class FrameworkIdMustBeRegisteredRule extends AbstractJavaRulechainRule {

    private static final String EXPOSE_CHANNEL = "dev.vfyjxf.cloudlib.api.data.expose.ExposeChannel";
    private static final String EXPOSE_LAYER_CHANNEL =
            "dev.vfyjxf.cloudlib.api.data.expose.ExposeLayerChannel";
    private static final String EXPOSE_KIND =
            "dev.vfyjxf.cloudlib.api.network.expose.subscription.ExposeKind";

    private static final Map<String, String> registryOf = Map.of(
            EXPOSE_CHANNEL, "ExposeChannelRegistry",
            EXPOSE_LAYER_CHANNEL, "ExposeChannelRegistry",
            EXPOSE_KIND, "ExposeKindRegistry");

    private static final List<String> factories = List.of(
            EXPOSE_CHANNEL, EXPOSE_LAYER_CHANNEL, EXPOSE_KIND);

    private static final String suppressionMarker = "framework-id-unregistered-ok";

    private ASTCompilationUnit analysedUnit;
    private FrameworkAnchors.Imports imports = FrameworkAnchors.Imports.EMPTY;

    public FrameworkIdMustBeRegisteredRule() {
        super(ASTMethodCall.class);
        setMessage("A declared expose channel or kind must be registered in the same file");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        RuleContext ctx = asCtx(data);
        rememberImports(node);
        if (!"of".equals(node.getMethodName()) || node.getQualifier() == null) {
            return data;
        }
        String declared = factories.stream()
                .filter(fqn -> FrameworkAnchors.isExactly(node.getQualifier(), fqn, imports))
                .findFirst().orElse(null);
        if (declared == null || isRegisteredHere(node, registryOf.get(declared))
                || lineCarriesTheMarker(node)) {
            return data;
        }
        ctx.addViolationWithMessage(node,
                registryOf.get(declared) + " never sees this " + FrameworkAnchors.sourceTextOf(node)
                        + " in this file — an unregistered id makes serve() silently drop the offer;"
                        + " wrap it in " + registryOf.get(declared) + ".register(...), or mark the line "
                        + suppressionMarker);
        return data;
    }

    /** The call itself is what was registered, or the file registers through the registry. */
    private boolean isRegisteredHere(ASTMethodCall ofCall, String registrySimpleName) {
        if (ofCall.getParent() instanceof ASTArgumentList arguments
                && arguments.getParent() instanceof ASTMethodCall maybeRegister) {
            return isRegistryRegister(maybeRegister, registrySimpleName);
        }
        for (ASTMethodCall call : ofCall.getRoot().descendants(ASTMethodCall.class)) {
            if (isRegistryRegister(call, registrySimpleName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRegistryRegister(ASTMethodCall call, String registrySimpleName) {
        if (!"register".equals(call.getMethodName()) || call.getQualifier() == null) {
            return false;
        }
        String owner = FrameworkAnchors.resolvedOwner(call);
        if (owner != null) {
            return owner.endsWith("." + registrySimpleName);
        }
        return registrySimpleName.equals(FrameworkAnchors.sourceTextOf(call.getQualifier()));
    }

    private static boolean lineCarriesTheMarker(ASTMethodCall node) {
        int line = node.getBeginLine();
        String text = node.getTextDocument().getText().toString();
        String[] lines = text.split("\n", -1);
        return line - 1 < lines.length && lines[line - 1].contains(suppressionMarker);
    }

    private void rememberImports(ASTMethodCall node) {
        ASTCompilationUnit root = node.getRoot();
        if (analysedUnit != root) {
            analysedUnit = root;
            imports = FrameworkAnchors.importsOf(root);
        }
    }
}
