package dev.vfyjxf.cloudlib.pmd.rules;

import net.sourceforge.pmd.lang.java.ast.ASTArgumentList;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.InvocationNode;
import net.sourceforge.pmd.lang.java.ast.JavaNode;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.symbols.JExecutableSymbol;
import net.sourceforge.pmd.lang.java.types.OverloadSelectionResult;
import net.sourceforge.pmd.lang.java.types.TypeTestUtil;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A-02 of the framework catalogue, with the fw-05 {@code string()} variant folded in: the two
 * raw exits of {@code dev.vfyjxf.cloudlib.data.lang.LangEntry} may only flow into the slot that
 * wants what they yield. {@code key()} yields the bare translation key, which only a key slot
 * may take — a text slot paints {@code nimbus.content.items} on screen; {@code string()} yields
 * translated text, which a key slot would look up and never find.
 *
 * <p>A slot is judged by the resolved overload: the outer invocation is one of the whitelisted
 * key sinks — {@code KeyMapping} construction, any method of {@code OptionSpec},
 * {@code Component.translatable}, {@code RichText.Builder.translatable}, {@code LangFile.add},
 * {@code Map.get} — for a key slot; otherwise a resolved formal parameter type of
 * {@code java.lang.String} is a text slot. The receiver of the {@code .key()} / {@code .string()}
 * call must be exactly {@code LangEntry}: the two projects contain over a hundred unrelated
 * {@code key()} methods ({@code profile.key()}, {@code StyleValue.key()}), so an unanchored name
 * match is worthless. Anything unresolved is passed over in silence.
 *
 * <p>{@code LangEntry.get()}, {@code component()} and {@code RichText.of(LangEntry)} never
 * appear here: they are not raw exits.
 */
public class LangKeyAsLiteralTextRule extends AbstractJavaRulechainRule {

    private static final String LANG_ENTRY = "dev.vfyjxf.cloudlib.data.lang.LangEntry";

    /** Key sinks: owner canonical name (or a nested type of it, for OptionSpec) plus method. */
    private static final String[][] KEY_SINKS = {
            {"net.minecraft.client.KeyMapping", "<init>"},
            {"dev.vfyjxf.nimbus.api.content.OptionSpec", "*"},
            {"net.minecraft.network.chat.Component", "translatable"},
            {"dev.vfyjxf.cloudlib.api.text.RichText.Builder", "translatable"},
            {"dev.vfyjxf.cloudlib.api.text.RichText$Builder", "translatable"},
            {"dev.vfyjxf.cloudlib.api.text.TranslatableNode", "<init>"},
            {"dev.vfyjxf.cloudlib.data.lang.LangFile", "add"},
            {"java.util.Map", "get"},
    };

    private ASTCompilationUnit analysedUnit;
    private FrameworkAnchors.Imports imports = FrameworkAnchors.Imports.EMPTY;

    public LangKeyAsLiteralTextRule() {
        super(ASTMethodCall.class, ASTConstructorCall.class);
        setMessage("A LangEntry raw exit went into a slot that wants the other kind of string");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        rememberImports(node);
        checkInvocation(node, node.getMethodName(), node.getOverloadSelectionInfo(), asCtx(data));
        return data;
    }

    @Override
    public Object visit(ASTConstructorCall node, Object data) {
        rememberImports(node);
        checkInvocation(node, "<init>", node.getOverloadSelectionInfo(), asCtx(data));
        return data;
    }

    private void checkInvocation(InvocationNode invocation, String invokedName,
            OverloadSelectionResult resolution, RuleContext ctx) {
        ASTArgumentList arguments = invocation.getArguments();
        String owner = ownerOf(resolution);
        for (int i = 0; i < arguments.getNumChildren(); i++) {
            if (!(arguments.getChild(i) instanceof ASTMethodCall raw)
                    || raw.getQualifier() == null
                    || !("key".equals(raw.getMethodName()) || "string".equals(raw.getMethodName()))
                    || !FrameworkAnchors.isExactly(raw.getQualifier(), LANG_ENTRY, imports)) {
                continue;
            }
            boolean keyExit = "key".equals(raw.getMethodName());
            String slot = slotOf(invokedName, owner, resolution, i);
            if ("text".equals(slot) && keyExit) {
                ctx.addViolationWithMessage(raw,
                        "LangEntry." + raw.getMethodName() + "() yields the bare translation key, but it is "
                                + "passed as literal text to " + describe(owner, invokedName)
                                + " — the screen would show the key itself; use entry.get() or a Component");
            } else if ("key".equals(slot) && !keyExit) {
                ctx.addViolationWithMessage(raw,
                        "LangEntry." + raw.getMethodName() + "() yields already-translated text, but "
                                + describe(owner, invokedName)
                                + " wants a raw translation key — pass entry.key()");
            }
        }
    }

    /**
     * The slot the {@code i}-th argument sits in, or {@code null} when the overload did not
     * resolve or the slot takes neither a key nor text.
     */
    private static String slotOf(String invokedName, String owner, OverloadSelectionResult resolution,
            int i) {
        if (owner != null && isKeySink(owner, invokedName)) {
            return "key";
        }
        try {
            if (resolution != null && !resolution.isFailed()) {
                return TypeTestUtil.isA("java.lang.String", resolution.ithFormalParam(i))
                        ? "text"
                        : null;
            }
        } catch (RuntimeException | AssertionError ignored) {
            // an unresolved overload says nothing either way
        }
        return null;
    }

    private static String ownerOf(OverloadSelectionResult resolution) {
        try {
            if (resolution == null || resolution.isFailed()) {
                return null;
            }
            JExecutableSymbol symbol = resolution.getMethodType().getSymbol();
            return symbol == null ? null : symbol.getEnclosingClass().getCanonicalName();
        } catch (RuntimeException | AssertionError error) {
            return null; // type resolution is a bonus, never a crash
        }
    }

    private static boolean isKeySink(String owner, String invokedName) {
        for (String[] sink : KEY_SINKS) {
            boolean ownerMatches = "*".equals(sink[1])
                    ? owner.equals(sink[0]) || owner.startsWith(sink[0] + "$")
                            || owner.startsWith(sink[0] + ".")
                    : owner.equals(sink[0]);
            if (ownerMatches && ("*".equals(sink[1]) || sink[1].equals(invokedName))) {
                return true;
            }
        }
        return false;
    }

    private static String describe(String owner, String invokedName) {
        String name = "<init>".equals(invokedName) ? "constructor" : invokedName + "()";
        return owner == null ? name : owner + "." + name;
    }

    private void rememberImports(JavaNode node) {
        ASTCompilationUnit root = node.getRoot();
        if (analysedUnit != root) {
            analysedUnit = root;
            imports = FrameworkAnchors.importsOf(root);
        }
    }
}
