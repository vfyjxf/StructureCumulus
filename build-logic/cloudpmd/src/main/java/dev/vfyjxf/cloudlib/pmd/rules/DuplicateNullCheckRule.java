package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.sourceforge.pmd.lang.java.ast.ASTAssertStatement;
import net.sourceforge.pmd.lang.java.ast.ASTAssignmentExpression;
import net.sourceforge.pmd.lang.java.ast.ASTBlock;
import net.sourceforge.pmd.lang.java.ast.ASTBreakStatement;
import net.sourceforge.pmd.lang.java.ast.ASTCatchClause;
import net.sourceforge.pmd.lang.java.ast.ASTConditionalExpression;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTContinueStatement;
import net.sourceforge.pmd.lang.java.ast.ASTDoStatement;
import net.sourceforge.pmd.lang.java.ast.ASTExplicitConstructorInvocation;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTExpressionStatement;
import net.sourceforge.pmd.lang.java.ast.ASTFieldAccess;
import net.sourceforge.pmd.lang.java.ast.ASTForeachStatement;
import net.sourceforge.pmd.lang.java.ast.ASTForStatement;
import net.sourceforge.pmd.lang.java.ast.ASTIfStatement;
import net.sourceforge.pmd.lang.java.ast.ASTInfixExpression;
import net.sourceforge.pmd.lang.java.ast.ASTInitializer;
import net.sourceforge.pmd.lang.java.ast.ASTLabeledStatement;
import net.sourceforge.pmd.lang.java.ast.ASTLambdaExpression;
import net.sourceforge.pmd.lang.java.ast.ASTLocalVariableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodReference;
import net.sourceforge.pmd.lang.java.ast.ASTResource;
import net.sourceforge.pmd.lang.java.ast.ASTResourceList;
import net.sourceforge.pmd.lang.java.ast.ASTReturnStatement;
import net.sourceforge.pmd.lang.java.ast.ASTStatement;
import net.sourceforge.pmd.lang.java.ast.ASTSuperExpression;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchArrowBranch;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchArrowRHS;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchBranch;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchExpression;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchFallthroughBranch;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchStatement;
import net.sourceforge.pmd.lang.java.ast.ASTSynchronizedStatement;
import net.sourceforge.pmd.lang.java.ast.ASTThisExpression;
import net.sourceforge.pmd.lang.java.ast.ASTThrowStatement;
import net.sourceforge.pmd.lang.java.ast.ASTTryStatement;
import net.sourceforge.pmd.lang.java.ast.ASTFinallyClause;
import net.sourceforge.pmd.lang.java.ast.ASTUnaryExpression;
import net.sourceforge.pmd.lang.java.ast.ASTVariableAccess;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclarator;
import net.sourceforge.pmd.lang.java.ast.ASTVariableId;
import net.sourceforge.pmd.lang.java.ast.ASTWhileStatement;
import net.sourceforge.pmd.lang.java.ast.ASTYieldStatement;
import net.sourceforge.pmd.lang.java.ast.AssignmentOp;
import net.sourceforge.pmd.lang.java.ast.BinaryOp;
import net.sourceforge.pmd.lang.java.ast.JavaNode;
import net.sourceforge.pmd.lang.java.ast.UnaryOp;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.java.symbols.JVariableSymbol;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * The same variable is checked for null again when its nullness is already established.
 *
 * <p>The rule walks one method, constructor or initializer at a time, carrying a set of
 * variables whose nullness the executed statements have already pinned down — as either
 * non-null or null — and reports any further null check on such a variable. Knowledge is only
 * acquired where it is unconditional, and it is dropped the moment its variable is assigned:
 * <ul>
 *   <li>an {@code Objects.requireNonNull(x)} / {@code Checks.checkNotNull(x)} call that is
 *       executed unconditionally (a statement, an initializer, the right-hand side of an
 *       assignment) pins its argument non-null;</li>
 *   <li>a guard {@code if (x == null) return/throw/break/continue;} — and the
 *       {@code if (x != null) ... else return/throw;} mirror — pins {@code x} for the
 *       statements after the {@code if}, and a plain {@code if (x != null) {...}} pins it
 *       inside the then-branch; the else-branch is pinned the opposite way;</li>
 *   <li>inside one condition, the left null check pins the variable for the right operand, so
 *       {@code x != null && x != null} and {@code x == null || x == null} are reported.</li>
 * </ul>
 *
 * <p>Conservatism, on purpose: branches merge by intersection, so a guard that holds only in
 * one branch never survives the join; loops and switches drop everything gained inside; a
 * {@code try} body teaches its catches nothing; assertions pin nothing (they may be disabled);
 * only local variables and parameters are tracked, because nothing outside the method can
 * reassign those; and a labelled {@code break}/{@code continue} that escapes a statement makes
 * that statement keep only what it knew before.
 *
 * <p>Violations carry the whole message themselves, so the {@code message} attribute of the
 * ruleset reference is informational only. Messages therefore stay free of apostrophes and braces,
 * which {@code java.text.MessageFormat} would otherwise reinterpret.
 */
public class DuplicateNullCheckRule extends AbstractJavaRulechainRule {

    private RuleContext ctx;
    private NullCheckShapes.Imports imports = NullCheckShapes.Imports.EMPTY;
    private boolean hasLabelledJumps;

    public DuplicateNullCheckRule() {
        super(ASTMethodDeclaration.class, ASTConstructorDeclaration.class, ASTInitializer.class);
        setMessage("Do not check a variable for null whose nullness is already established");
    }

    @Override
    public Object visit(ASTMethodDeclaration node, Object data) {
        analyse(node, node.getBody(), data);
        return data;
    }

    @Override
    public Object visit(ASTConstructorDeclaration node, Object data) {
        analyse(node, node.getBody(), data);
        return data;
    }

    @Override
    public Object visit(ASTInitializer node, Object data) {
        analyse(node, node.getBody(), data);
        return data;
    }

    private void analyse(JavaNode owner, ASTBlock body, Object data) {
        if (body == null) {
            return;
        }
        this.ctx = asCtx(data);
        this.imports = NullCheckShapes.importsOf(owner.getRoot());
        this.hasLabelledJumps = body.descendants(ASTBreakStatement.class).toStream().anyMatch(s -> s.getLabel() != null)
                || body.descendants(ASTContinueStatement.class).toStream().anyMatch(s -> s.getLabel() != null);
        execBlock(body, new Env());
    }

    // region what the environment knows

    private static final class Fact {

        private final boolean nonNull;
        private final String origin;

        Fact(boolean nonNull, String origin) {
            this.nonNull = nonNull;
            this.origin = origin;
        }
    }

    private static final class Env {

        private final Map<JVariableSymbol, Fact> facts;

        Env() {
            this(new HashMap<>());
        }

        private Env(Map<JVariableSymbol, Fact> facts) {
            this.facts = facts;
        }

        Env copy() {
            return new Env(new HashMap<>(facts));
        }

        Fact get(JVariableSymbol symbol) {
            return facts.get(symbol);
        }

        void learn(JVariableSymbol symbol, boolean nonNull, String origin) {
            facts.put(symbol, new Fact(nonNull, origin));
        }

        void forget(JVariableSymbol symbol) {
            facts.remove(symbol);
        }

        void forgetAll(Set<JVariableSymbol> symbols) {
            facts.keySet().removeAll(symbols);
        }

        void resetTo(Env other) {
            facts.clear();
            facts.putAll(other.facts);
        }

        /** The knowledge both environments agree on — the only knowledge a branch join may keep. */
        static Env merge(Env first, Env second) {
            Env joined = new Env();
            for (Map.Entry<JVariableSymbol, Fact> entry : first.facts.entrySet()) {
                Fact other = second.facts.get(entry.getKey());
                if (other != null && other.nonNull == entry.getValue().nonNull) {
                    joined.facts.put(entry.getKey(), entry.getValue());
                }
            }
            return joined;
        }
    }

    /** A local variable or parameter this method can pin down; anything else is not tracked. */
    private JVariableSymbol trackable(ASTExpression expression) {
        if (!(expression instanceof ASTVariableAccess access)) {
            return null;
        }
        JVariableSymbol symbol = access.getReferencedSym();
        if (symbol == null) {
            return null;
        }
        ASTVariableId declaration = symbol.tryGetNode();
        return declaration != null && (declaration.isLocalVariable() || declaration.isFormalParameter())
                ? symbol
                : null;
    }

    // endregion

    // region statements

    private void execBlock(ASTBlock block, Env env) {
        for (ASTStatement statement : block.children(ASTStatement.class)) {
            execStatement(statement, env);
        }
    }

    private void execStatement(ASTStatement statement, Env env) {
        if (!hasLabelledJumps) {
            dispatch(statement, env);
            return;
        }
        Env before = env.copy();
        dispatch(statement, env);
        // a labelled jump escapes to after this statement with older knowledge, and the label of
        // a labelled statement can always be broken out of, so gains made inside do not survive
        if (statement instanceof ASTLabeledStatement || containsEscapingJump(statement)) {
            env.resetTo(before);
            env.forgetAll(assignedIn(statement));
        }
    }

    private void dispatch(ASTStatement statement, Env env) {
        if (statement instanceof ASTIfStatement ifStatement) {
            execIf(ifStatement, env);
        } else if (statement instanceof ASTWhileStatement whileStatement) {
            execWhile(whileStatement, env);
        } else if (statement instanceof ASTDoStatement doStatement) {
            execDo(doStatement, env);
        } else if (statement instanceof ASTForStatement forStatement) {
            execFor(forStatement, env);
        } else if (statement instanceof ASTForeachStatement foreachStatement) {
            execForeach(foreachStatement, env);
        } else if (statement instanceof ASTSwitchStatement switchStatement) {
            execSwitch(switchStatement, env);
        } else if (statement instanceof ASTTryStatement tryStatement) {
            execTry(tryStatement, env);
        } else if (statement instanceof ASTSynchronizedStatement synchronizedStatement) {
            walkEffects(synchronizedStatement.getLockExpression(), env, false, true);
            execBlock(synchronizedStatement.getBody(), env);
        } else if (statement instanceof ASTBlock block) {
            execBlock(block, env);
        } else if (statement instanceof ASTLabeledStatement labelled) {
            dispatch(labelled.getStatement(), env);
        } else {
            execSimple(statement, env);
        }
    }

    private void execSimple(ASTStatement statement, Env env) {
        if (statement instanceof ASTExpressionStatement expressionStatement) {
            walkEffects(expressionStatement.getExpr(), env, false, true);
        } else if (statement instanceof ASTLocalVariableDeclaration declaration) {
            for (ASTVariableDeclarator declarator : declaration.children(ASTVariableDeclarator.class)) {
                ASTExpression initializer = declarator.getInitializer();
                if (initializer == null) {
                    continue;
                }
                walkEffects(initializer, env, false, true);
                if (isRequireNonNull(initializer)) {
                    JVariableSymbol declared = declarator.getVarId().getSymbol();
                    if (declared != null) {
                        env.learn(declared, true, originOf(initializer));
                    }
                }
            }
        } else if (statement instanceof ASTReturnStatement returnStatement) {
            walkEffects(returnStatement.getExpr(), env, false, true);
        } else if (statement instanceof ASTThrowStatement throwStatement) {
            walkEffects(throwStatement.getExpr(), env, false, true);
        } else if (statement instanceof ASTYieldStatement yieldStatement) {
            walkEffects(yieldStatement.getExpr(), env, false, true);
        } else if (statement instanceof ASTAssertStatement assertStatement) {
            // assertions can be disabled, so they pin nothing — but a duplicate in one is still one
            walkEffects(assertStatement.getCondition(), env, false, false);
        } else if (statement instanceof ASTExplicitConstructorInvocation invocation) {
            for (ASTExpression argument : invocation.getArguments().children(ASTExpression.class)) {
                walkEffects(argument, env, false, true);
            }
        }
        // break, continue, empty statements and local classes have no effect here
    }

    private void execIf(ASTIfStatement statement, Env env) {
        ConditionKnowledge knowledge = walkCondition(statement.getCondition(), env);
        env.forgetAll(knowledge.assigned);
        String origin = "null guard at line " + statement.getBeginLine();
        Env thenEnv = env.copy();
        knowledge.learnTo(thenEnv, true, origin);
        Env elseEnv = env.copy();
        knowledge.learnTo(elseEnv, false, origin);
        Env thenExit = branchExit(statement.getThenBranch(), thenEnv);
        // without an else branch, falling past the if means the condition was false
        Env elseExit = statement.getElseBranch() == null ? elseEnv
                : branchExit(statement.getElseBranch(), elseEnv);
        if (thenExit == null && elseExit == null) {
            env.resetTo(new Env()); // both branches leave; anything after is unreachable
        } else if (thenExit == null) {
            env.resetTo(elseExit);
        } else if (elseExit == null) {
            env.resetTo(thenExit);
        } else {
            env.resetTo(Env.merge(thenExit, elseExit));
        }
    }

    /** The environment a branch leaves behind, or null when the branch cannot complete normally. */
    private Env branchExit(ASTStatement branch, Env env) {
        execStatement(branch, env);
        return isTerminal(branch) ? null : env;
    }

    private void execWhile(ASTWhileStatement statement, Env env) {
        Set<JVariableSymbol> loopAssigned = assignedIn(statement);
        Env iterationEnv = env.copy();
        iterationEnv.forgetAll(loopAssigned);
        ConditionKnowledge knowledge = walkCondition(statement.getCondition(), iterationEnv);
        Env bodyEnv = iterationEnv.copy();
        knowledge.learnTo(bodyEnv, true, "loop guard at line " + statement.getBeginLine());
        execStatement(statement.getBody(), bodyEnv);
        env.forgetAll(loopAssigned);
    }

    private void execDo(ASTDoStatement statement, Env env) {
        Set<JVariableSymbol> loopAssigned = assignedIn(statement);
        Env iterationEnv = env.copy();
        iterationEnv.forgetAll(loopAssigned);
        execStatement(statement.getBody(), iterationEnv.copy());
        walkCondition(statement.getCondition(), iterationEnv);
        env.forgetAll(loopAssigned);
    }

    private void execFor(ASTForStatement statement, Env env) {
        if (statement.getInit() != null) {
            execForInit(statement.getInit(), env);
        }        Set<JVariableSymbol> loopAssigned = assignedIn(statement);
        Env iterationEnv = env.copy();
        iterationEnv.forgetAll(loopAssigned);
        if (statement.getCondition() != null) {
            ConditionKnowledge knowledge = walkCondition(statement.getCondition(), iterationEnv);
            Env bodyEnv = iterationEnv.copy();
            knowledge.learnTo(bodyEnv, true, "loop guard at line " + statement.getBeginLine());
            execStatement(statement.getBody(), bodyEnv);
        } else {
            execStatement(statement.getBody(), iterationEnv.copy());
        }
        env.forgetAll(loopAssigned);
    }

    private void execForInit(ASTStatement init, Env env) {
        // getInit() already unwraps the for-init wrapper: this is a local variable declaration
        // or a statement expression list
        if (init instanceof ASTLocalVariableDeclaration) {
            execStatement(init, env);
        } else {
            for (ASTExpression expression : init.children(ASTExpression.class)) {
                walkEffects(expression, env, false, true);
            }
        }
    }

    private void execForeach(ASTForeachStatement statement, Env env) {
        walkEffects(statement.getIterableExpr(), env, false, true);
        Set<JVariableSymbol> loopAssigned = assignedIn(statement);
        JVariableSymbol loopVariable = statement.getVarId().getSymbol();
        if (loopVariable != null) {
            loopAssigned.add(loopVariable); // the loop variable is reassigned each iteration
        }
        Env bodyEnv = env.copy();
        bodyEnv.forgetAll(loopAssigned);
        execStatement(statement.getBody(), bodyEnv);
        env.forgetAll(loopAssigned);
    }

    private void execSwitch(ASTSwitchStatement statement, Env env) {
        walkEffects(statement.getTestedExpression(), env, false, true);
        Set<JVariableSymbol> switchAssigned = assignedIn(statement);
        Env branchBase = env.copy();
        branchBase.forgetAll(switchAssigned); // fallthrough and arrow branches alike start flat
        for (ASTSwitchBranch branch : statement.getBranches()) {
            Env branchEnv = branchBase.copy();
            if (branch instanceof ASTSwitchArrowBranch arrowBranch) {
                execArrowBranch(arrowBranch.getRightHandSide(), branchEnv);
            } else if (branch instanceof ASTSwitchFallthroughBranch fallthroughBranch) {
                for (ASTStatement branchStatement : fallthroughBranch.getStatements()) {
                    execStatement(branchStatement, branchEnv);
                }
            }
        }
        env.forgetAll(switchAssigned);
    }

    private void execArrowBranch(ASTSwitchArrowRHS rightHandSide, Env env) {
        if (rightHandSide instanceof ASTBlock block) {
            execBlock(block, env);
        } else if (rightHandSide instanceof ASTExpression expression) {
            walkEffects(expression, env, false, true);
        } else if (rightHandSide instanceof ASTThrowStatement throwStatement) {
            walkEffects(throwStatement.getExpr(), env, false, true);
        }
    }

    private void execTry(ASTTryStatement statement, Env env) {
        ASTResourceList resources = statement.getResources();
        if (resources != null) {
            for (ASTResource resource : resources.children(ASTResource.class)) {
                walkEffects(resource.getInitializer(), env, false, true);
            }
        }
        Set<JVariableSymbol> tryAssigned = assignedIn(statement);
        Env inside = env.copy();
        inside.forgetAll(tryAssigned); // an exception can leave the try body at any point
        execBlock(statement.getBody(), inside.copy());
        for (ASTCatchClause clause : statement.getCatchClauses()) {
            execBlock(clause.getBody(), inside.copy());
        }
        if (statement.getFinallyClause() != null) {
            execBlock(statement.getFinallyClause().getBody(), inside.copy());
        }
        env.forgetAll(tryAssigned);
    }

    // endregion

    // region terminality and reassignment

    private static boolean isTerminal(ASTStatement statement) {
        if (statement instanceof ASTReturnStatement
                || statement instanceof ASTThrowStatement
                || statement instanceof ASTBreakStatement
                || statement instanceof ASTContinueStatement) {
            return true;
        }
        if (statement instanceof ASTBlock block) {
            int children = block.getNumChildren();
            return children > 0 && block.getChild(children - 1) instanceof ASTStatement last
                    && isTerminal(last);
        }
        if (statement instanceof ASTIfStatement ifStatement) {
            return ifStatement.getElseBranch() != null
                    && isTerminal(ifStatement.getThenBranch())
                    && isTerminal(ifStatement.getElseBranch());
        }
        return false;
    }

    /** Every tracked variable the node assigns, including compound assignment and increments. */
    private Set<JVariableSymbol> assignedIn(JavaNode node) {
        Set<JVariableSymbol> assigned = new HashSet<>();
        for (ASTAssignmentExpression assignment : node.descendants(ASTAssignmentExpression.class)) {
            JVariableSymbol target = leftHandSymbol(assignment);
            if (target != null) {
                assigned.add(target);
            }
        }
        for (ASTUnaryExpression unary : node.descendants(ASTUnaryExpression.class)) {
            if (!unary.getOperator().isPure()) {
                JVariableSymbol target = symbolOf(unary.getOperand());
                if (target != null) {
                    assigned.add(target);
                }
            }
        }
        if (node instanceof ASTForeachStatement foreach) {
            JVariableSymbol loopVariable = foreach.getVarId().getSymbol();
            if (loopVariable != null) {
                assigned.add(loopVariable);
            }
        }
        return assigned;
    }

    private static JVariableSymbol symbolOf(ASTExpression expression) {
        if (expression instanceof ASTVariableAccess access) {
            return access.getReferencedSym();
        }
        if (expression instanceof ASTFieldAccess field) {
            return field.getReferencedSym();
        }
        return null;
    }

    private static JVariableSymbol leftHandSymbol(ASTAssignmentExpression assignment) {
        return symbolOf((ASTExpression) assignment.getChild(0));
    }

    /** Whether a labelled break or continue inside the node jumps to a label outside it. */
    private boolean containsEscapingJump(JavaNode node) {
        for (ASTBreakStatement jump : node.descendants(ASTBreakStatement.class)) {
            if (escapes(jump.getLabel(), jump, node)) {
                return true;
            }
        }
        for (ASTContinueStatement jump : node.descendants(ASTContinueStatement.class)) {
            if (escapes(jump.getLabel(), jump, node)) {
                return true;
            }
        }
        return false;
    }

    private static boolean escapes(String label, JavaNode jump, JavaNode scope) {
        if (label == null) {
            return false; // an unlabelled jump targets the nearest loop or switch, handled there
        }
        ASTLabeledStatement target = null;
        for (ASTLabeledStatement labelled : jump.ancestors(ASTLabeledStatement.class)) {
            if (label.equals(labelled.getLabel())) {
                target = labelled;
                break;
            }
        }
        return target == null || !isWithin(target, scope);
    }

    private static boolean isWithin(JavaNode node, JavaNode scope) {
        return node == scope || node.ancestors().toStream().anyMatch(scope::equals);
    }

    // endregion

    // region expressions that always run: acquire and kill knowledge

    /**
     * Walks an expression that is being evaluated now. {@code conditional} marks positions the
     * evaluator may skip (the right operand of {@code &&}/{@code ||}, ternary branches); only an
     * unconditional {@code requireNonNull}/{@code checkNotNull} pins its argument. When
     * {@code allowKnowledge} is false nothing is pinned — the assert statement case.
     */
    private void walkEffects(ASTExpression expression, Env env, boolean conditional, boolean allowKnowledge) {
        if (expression == null) {
            return;
        }
        NullCheckShapes.NullCheck check = nullCheckShape(expression);
        if (check != null) {
            JVariableSymbol symbol = trackable(check.checked());
            if (symbol != null) {
                Fact known = env.get(symbol);
                if (known != null) {
                    reportDuplicate(expression, symbol, known);
                }
            }
            if (check.kind() == NullCheckShapes.Kind.REQUIRE_NONNULL && !conditional && allowKnowledge) {
                if (symbol != null) {
                    env.learn(symbol, true, originOf(expression)); // the call asserts its argument
                }
            }
            walkEffectChildren(expression, env, conditional, allowKnowledge);
            return;
        }
        if (expression instanceof ASTMethodCall call) {
            walkEffectChildren(expression, env, conditional, allowKnowledge);
            return;
        }
        if (expression instanceof ASTInfixExpression infix) {
            BinaryOp operator = infix.getOperator();
            walkEffects(infix.getLeftOperand(), env, conditional, allowKnowledge);
            boolean shortCircuited = operator == BinaryOp.CONDITIONAL_AND
                    || operator == BinaryOp.CONDITIONAL_OR;
            walkEffects(infix.getRightOperand(), env, conditional || shortCircuited, allowKnowledge);
            return;
        }
        if (expression instanceof ASTConditionalExpression ternary) {
            walkEffects(ternary.getCondition(), env, conditional, allowKnowledge);
            walkEffects(ternary.getThenBranch(), env, true, allowKnowledge);
            walkEffects(ternary.getElseBranch(), env, true, allowKnowledge);
            return;
        }
        if (expression instanceof ASTAssignmentExpression assignment) {
            ASTExpression right = (ASTExpression) assignment.getChild(1);
            walkEffects(right, env, conditional, allowKnowledge);
            JVariableSymbol target = leftHandSymbol(assignment);
            if (target != null) {
                if (!conditional && allowKnowledge
                        && assignment.getOperator() == AssignmentOp.ASSIGN
                        && isRequireNonNull(right)) {
                    env.learn(target, true, originOf(right)); // the assignment stores a non-null value
                } else {
                    env.forget(target);
                }
            }
            return;
        }
        if (expression instanceof ASTUnaryExpression unary) {
            walkEffects(unary.getOperand(), env, conditional, allowKnowledge);
            if (!unary.getOperator().isPure()) {
                JVariableSymbol target = symbolOf(unary.getOperand());
                if (target != null) {
                    env.forget(target); // an increment or decrement rewrites the variable
                }
            }
            return;
        }
        if (expression instanceof ASTLambdaExpression || expression instanceof ASTMethodReference) {
            return; // deferred execution: nothing inside runs now
        }
        if (expression instanceof ASTSwitchExpression) {
            return; // branch evaluation is conditional and teaches nothing here
        }
        if (expression instanceof ASTThisExpression || expression instanceof ASTSuperExpression) {
            return;
        }
        walkEffectChildren(expression, env, conditional, allowKnowledge);
    }

    /** The sub-expressions that are evaluated whenever {@code expression} itself is. */
    private void walkEffectChildren(ASTExpression expression, Env env, boolean conditional, boolean allowKnowledge) {
        if (expression instanceof ASTMethodCall call) {
            if (call.getQualifier() != null) {
                walkEffects(call.getQualifier(), env, conditional, allowKnowledge);
            }
            for (ASTExpression argument : call.getArguments().children(ASTExpression.class)) {
                walkEffects(argument, env, conditional, allowKnowledge);
            }
            return;
        }
        for (ASTExpression child : expression.children(ASTExpression.class)) {
            walkEffects(child, env, conditional, allowKnowledge);
        }
    }

    // endregion

    // region conditions: knowledge with polarity, in evaluation order

    private static final class ConditionKnowledge {

        private final Map<JVariableSymbol, Boolean> whenTrue = new HashMap<>();
        private final Map<JVariableSymbol, Boolean> whenFalse = new HashMap<>();
        private final Set<JVariableSymbol> assigned = new HashSet<>();

        void learnTo(Env env, boolean conditionValue, String origin) {
            for (Map.Entry<JVariableSymbol, Boolean> entry
                    : (conditionValue ? whenTrue : whenFalse).entrySet()) {
                env.learn(entry.getKey(), entry.getValue(), origin);
            }
        }
    }

    private ConditionKnowledge walkCondition(ASTExpression condition, Env env) {
        ConditionKnowledge knowledge = new ConditionKnowledge();
        walkCondition(condition, env, knowledge, true);
        return knowledge;
    }

    /**
     * Walks a boolean expression in evaluation order. Null checks already covered by the
     * environment are reported; the polarity of a new check is recorded — but only at a boolean
     * position ({@code recordKnowledge}), because a null check buried in a comparison or a call
     * argument constrains nothing outside itself.
     */
    private void walkCondition(ASTExpression expression, Env env, ConditionKnowledge knowledge,
            boolean recordKnowledge) {
        NullCheckShapes.NullCheck check = nullCheckShape(expression);
        if (check != null) {
            JVariableSymbol symbol = trackable(check.checked());
            if (symbol != null) {
                Fact known = env.get(symbol);
                if (known != null) {
                    reportDuplicate(expression, symbol, known);
                }
                boolean pinsNullness = check.kind() != NullCheckShapes.Kind.REQUIRE_NONNULL
                        && check.kind() != NullCheckShapes.Kind.CASE_NULL;
                if (recordKnowledge && pinsNullness) {
                    boolean trueMeansNull = check.kind().trueMeansNull();
                    knowledge.whenTrue.put(symbol, !trueMeansNull);
                    knowledge.whenFalse.put(symbol, trueMeansNull);
                }
            }
            for (ASTExpression child : expression instanceof ASTMethodCall call
                    ? call.getArguments().children(ASTExpression.class).toList()
                    : expression.children(ASTExpression.class).toList()) {
                walkCondition(child, env, knowledge, false);
            }
            return;
        }
        if (expression instanceof ASTUnaryExpression unary) {
            if (unary.getOperator() == UnaryOp.NEGATION) {
                ConditionKnowledge operand = walkCondition(unary.getOperand(), env);
                knowledge.assigned.addAll(operand.assigned);
                if (recordKnowledge) {
                    knowledge.whenTrue.putAll(operand.whenFalse); // De Morgan swaps the polarity
                    knowledge.whenFalse.putAll(operand.whenTrue);
                }
                return;
            }
            walkCondition(unary.getOperand(), env, knowledge, false);
            return;
        }
        if (expression instanceof ASTInfixExpression infix) {
            BinaryOp operator = infix.getOperator();
            if (operator == BinaryOp.CONDITIONAL_AND || operator == BinaryOp.CONDITIONAL_OR) {
                boolean and = operator == BinaryOp.CONDITIONAL_AND;
                ConditionKnowledge left = walkCondition(infix.getLeftOperand(), env);
                Env rightEnv = env.copy();
                learnPolarity(rightEnv, and ? left.whenTrue : left.whenFalse, "guard");
                ConditionKnowledge right = walkCondition(infix.getRightOperand(), rightEnv);
                knowledge.assigned.addAll(left.assigned);
                knowledge.assigned.addAll(right.assigned);
                if (recordKnowledge) {
                    if (and) {
                        knowledge.whenTrue.putAll(left.whenTrue);
                        knowledge.whenTrue.putAll(right.whenTrue);
                        intersectInto(knowledge.whenFalse, left.whenFalse, right.whenFalse);
                    } else {
                        knowledge.whenFalse.putAll(left.whenFalse);
                        knowledge.whenFalse.putAll(right.whenFalse);
                        intersectInto(knowledge.whenTrue, left.whenTrue, right.whenTrue);
                    }
                }
                return;
            }
            walkCondition(infix.getLeftOperand(), env, knowledge, false);
            walkCondition(infix.getRightOperand(), env, knowledge, false);
            return;
        }
        if (expression instanceof ASTConditionalExpression ternary) {
            walkCondition(ternary.getCondition(), env, new ConditionKnowledge(), true); // local to the ternary
            walkCondition(ternary.getThenBranch(), env, knowledge, false);
            walkCondition(ternary.getElseBranch(), env, knowledge, false);
            return;
        }
        if (expression instanceof ASTAssignmentExpression assignment) {
            JVariableSymbol target = leftHandSymbol(assignment);
            if (target != null) {
                knowledge.assigned.add(target);
            }
            walkCondition((ASTExpression) assignment.getChild(1), env, knowledge, false);
            return;
        }
        if (expression instanceof ASTLambdaExpression || expression instanceof ASTMethodReference) {
            return;
        }
        for (ASTExpression child : expression.children(ASTExpression.class)) {
            walkCondition(child, env, knowledge, false);
        }
    }

    private static void learnPolarity(Env env, Map<JVariableSymbol, Boolean> polarity, String origin) {
        for (Map.Entry<JVariableSymbol, Boolean> entry : polarity.entrySet()) {
            env.learn(entry.getKey(), entry.getValue(), origin);
        }
    }

    private static void intersectInto(Map<JVariableSymbol, Boolean> into, Map<JVariableSymbol, Boolean> first,
            Map<JVariableSymbol, Boolean> second) {
        for (Map.Entry<JVariableSymbol, Boolean> entry : first.entrySet()) {
            Boolean other = second.get(entry.getKey());
            if (other != null && other.equals(entry.getValue())) {
                into.put(entry.getKey(), entry.getValue());
            }
        }
    }

    // endregion

    // region shared helpers

    private NullCheckShapes.NullCheck nullCheckShape(ASTExpression expression) {
        if (expression instanceof ASTInfixExpression infix) {
            return NullCheckShapes.of(infix);
        }
        if (expression instanceof ASTMethodCall call) {
            return NullCheckShapes.of(call, imports);
        }
        return null;
    }

    private boolean isRequireNonNull(ASTExpression expression) {
        if (!(expression instanceof ASTMethodCall call)) {
            return false;
        }
        NullCheckShapes.NullCheck check = NullCheckShapes.of(call, imports);
        return check != null && check.kind() == NullCheckShapes.Kind.REQUIRE_NONNULL;
    }

    private static String originOf(ASTExpression requireNonNullCall) {
        String call = requireNonNullCall instanceof ASTMethodCall methodCall
                ? methodCall.getMethodName()
                : "requireNonNull";
        return "Objects." + call + " at line " + requireNonNullCall.getBeginLine();
    }

    private void reportDuplicate(JavaNode check, JVariableSymbol symbol, Fact fact) {
        String state = fact.nonNull ? "non-null" : "null";
        ctx.addViolationWithMessage(check, "duplicate null check on " + symbol.getSimpleName()
                + ": already known " + state + " from " + fact.origin);
    }

    // endregion
}
