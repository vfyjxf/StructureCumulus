package dev.vfyjxf.cloudlib.pmd.rules;

import net.sourceforge.pmd.lang.java.ast.ASTAssignmentExpression;
import net.sourceforge.pmd.lang.java.ast.ASTClassType;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTFieldAccess;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTRecordDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTReturnStatement;
import net.sourceforge.pmd.lang.java.ast.ASTTypeExpression;
import net.sourceforge.pmd.lang.java.ast.ASTVariableAccess;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclarator;
import net.sourceforge.pmd.lang.java.ast.ASTVariableId;
import net.sourceforge.pmd.lang.java.ast.JModifier;
import net.sourceforge.pmd.lang.java.ast.ModifierOwner.Visibility;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.symbols.JFieldSymbol;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * A collection that leaves the module must keep canonical order: a public method must not return
 * {@code Set.copyOf} or {@code Map.copyOf} directly, and a public record must not initialise a
 * component with them (X03-D06: both freeze the source collection's iteration order, and a hash
 * source's order carries the JVM's hash salt, so identity-hash order leaks across JVMs and two
 * runs of the same build disagree — D-75's answer is an ordered immutable collection).
 *
 * <p>Only the public surface is gated. A private or local copy keeps the order inside the module,
 * where the canonicalisation that follows (a sort, a TreeMap copy) is visible in the same method;
 * {@code List.copyOf} keeps encounter order and is legal everywhere. The rule reports the copyOf
 * call only where it <em>is</em> the whole returned or assigned value — a wrap such as
 * {@code sorted(Set.copyOf(xs))} leaves the order to the wrapper and stays legal.
 *
 * <p>A record component assignment is recognised by its field symbol: a record holds no instance
 * fields but its components, so a non-static field target inside the record's constructor is a
 * component; {@code Visibility} counts interface members as public, which is their real surface.
 */
public class PublicCollectionsKeepCanonicalOrderRule extends AbstractJavaRulechainRule {

    public PublicCollectionsKeepCanonicalOrderRule() {
        super(ASTMethodCall.class);
        setMessage("A public collection must keep canonical order");
    }

    @Override
    public Object visit(ASTMethodCall node, Object data) {
        if (!isSetOrMapCopyOf(node)) {
            return data;
        }
        RuleContext ctx = asCtx(data);
        if (returnedFromAPublicMethod(node)) {
            ctx.addViolationWithMessage(node,
                    "this return hands out " + copyTarget(node) + ".copyOf, whose iteration order is the"
                            + " source's — identity-hash order varies per JVM; return an ordered immutable"
                            + " collection instead");
            return data;
        }
        if (assignedToAPublicRecordComponent(node)) {
            ASTAssignmentExpression assignment = (ASTAssignmentExpression) node.getParent();
            ctx.addViolationWithMessage(node,
                    "record component " + assignment.getChild(0).getImage() + " is initialised with "
                            + copyTarget(node) + ".copyOf, freezing the source's hash order into a"
                            + " public surface; use an ordered immutable collection");
        }
        return data;
    }

    private static boolean returnedFromAPublicMethod(ASTMethodCall node) {
        if (!(node.getParent() instanceof ASTReturnStatement)) {
            return false;
        }
        ASTMethodDeclaration method = node.ancestors(ASTMethodDeclaration.class).first();
        return method != null && method.getVisibility() == Visibility.V_PUBLIC;
    }

    private static boolean assignedToAPublicRecordComponent(ASTMethodCall node) {
        if (!(node.getParent() instanceof ASTAssignmentExpression assignment)) {
            return false;
        }
        ASTRecordDeclaration record = node.ancestors(ASTRecordDeclaration.class).first();
        if (record == null || record.getVisibility() != Visibility.V_PUBLIC) {
            return false;
        }
        return assignsAComponentOf(assignment, record);
    }

    /**
     * A record component of {@code record}: a field of the record itself that is not one of its
     * static fields. A component declares no {@code FieldDeclaration} — it lives in the record
     * header — so only a written static field needs the modifiers test; components are final, so
     * the compiler already confines their assignment to a constructor.
     */
    private static boolean assignsAComponentOf(ASTAssignmentExpression assignment, ASTRecordDeclaration record) {
        ASTExpression target = (ASTExpression) assignment.getChild(0);
        if (!(symbolOf(target) instanceof JFieldSymbol field) || field.getEnclosingClass() == null) {
            return false;
        }
        if (field.getEnclosingClass().tryGetNode() != record) {
            return false;
        }
        if (field.tryGetNode() instanceof ASTVariableId id
                && id.getParent() instanceof ASTVariableDeclarator declarator
                && declarator.getParent() instanceof ASTFieldDeclaration declaration) {
            return !declaration.getModifiers().hasAllExplicitly(JModifier.STATIC);
        }
        return true;
    }

    private static JFieldSymbol symbolOf(ASTExpression target) {
        if (target instanceof ASTVariableAccess access) {
            return access.getReferencedSym() instanceof JFieldSymbol field ? field : null;
        }
        if (target instanceof ASTFieldAccess fieldAccess) {
            return fieldAccess.getReferencedSym() instanceof JFieldSymbol field ? field : null;
        }
        return null;
    }

    private static boolean isSetOrMapCopyOf(ASTMethodCall call) {
        if (!"copyOf".equals(call.getMethodName())
                || !(call.getQualifier() instanceof ASTTypeExpression typeExpression)) {
            return false;
        }
        ASTClassType named = typeExpression.firstChild(ASTClassType.class);
        return named != null && ("Set".equals(named.getSimpleName()) || "Map".equals(named.getSimpleName()));
    }

    private static String copyTarget(ASTMethodCall call) {
        ASTClassType named = ((ASTTypeExpression) call.getQualifier()).firstChild(ASTClassType.class);
        return named.getSimpleName();
    }
}
