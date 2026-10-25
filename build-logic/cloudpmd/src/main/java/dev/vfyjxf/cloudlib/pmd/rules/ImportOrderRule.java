package dev.vfyjxf.cloudlib.pmd.rules;

import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTImportDeclaration;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * Import groups are ordered, and each group is sorted by full name.
 *
 * <p>Three groups, in this order (the order Spotless enforces with
 * {@code importOrder("", "java", "javax", "#")}):
 * <ol>
 *   <li>project and third-party imports — anything that is neither {@code java.}/{@code javax.}
 *       nor static;</li>
 *   <li>{@code java.} and {@code javax.};</li>
 *   <li>{@code import static …}, including on-demand static imports such as
 *       {@code import static com.x.Y.*}.</li>
 * </ol>
 *
 * <p>The group numbers must never decrease, and inside one group the full names must strictly
 * increase under {@link String#compareTo}. A violation is reported on the import that breaks the
 * order, and says where it belongs instead. Blank lines and indentation between imports are not
 * this rule's business — formatting stays with Spotless.
 *
 * <p>Violations carry the whole message themselves, so the {@code message} attribute of the
 * ruleset reference is informational only. Messages therefore stay free of apostrophes and braces,
 * which {@code java.text.MessageFormat} would otherwise reinterpret.
 */
public class ImportOrderRule extends AbstractJavaRulechainRule {

    private static final int projectRank = 1;

    private static final int jdkRank = 2;

    private static final int staticRank = 3;

    private static final String projectGroup = "project and third-party";

    private static final String jdkGroup = "java and javax";

    private static final String staticGroup = "static";

    public ImportOrderRule() {
        super(ASTCompilationUnit.class);
        setMessage("Import groups must be ordered project and third-party, then java and javax, then static, "
                + "each sorted by full name");
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        RuleContext ctx = asCtx(data);
        ASTImportDeclaration previous = null;
        int previousRank = 0;
        for (ASTImportDeclaration current : node.children(ASTImportDeclaration.class)) {
            int rank = rankOf(current);
            if (previous != null) {
                reportOutOfOrder(ctx, previous, previousRank, current, rank);
            }
            previous = current;
            previousRank = rank;
        }
        return data;
    }

    private static void reportOutOfOrder(RuleContext ctx, ASTImportDeclaration previous, int previousRank,
            ASTImportDeclaration current, int rank) {
        String name = current.getImportedName();
        if (rank < previousRank) {
            ctx.addViolationWithMessage(current, "Import " + name + " is in the " + groupOf(rank)
                    + " group, but the import before it, " + previous.getImportedName() + " at line "
                    + previous.getBeginLine() + ", is already in the " + groupOf(previousRank)
                    + " group; expected position: before " + previous.getImportedName()
                    + " (the order is project and third-party, then java and javax, then static)");
        } else if (rank == previousRank && name.compareTo(previous.getImportedName()) <= 0) {
            ctx.addViolationWithMessage(current, "Import " + name + " is out of order: inside the "
                    + groupOf(rank) + " group the full names increase strictly, so it must come before "
                    + previous.getImportedName() + " at line " + previous.getBeginLine());
        }
    }

    private static int rankOf(ASTImportDeclaration node) {
        if (node.isStatic()) {
            return staticRank;
        }
        String name = node.getImportedName();
        return name.startsWith("java.") || name.startsWith("javax.") ? jdkRank : projectRank;
    }

    private static String groupOf(int rank) {
        switch (rank) {
            case jdkRank:
                return jdkGroup;
            case staticRank:
                return staticGroup;
            default:
                return projectGroup;
        }
    }

}
