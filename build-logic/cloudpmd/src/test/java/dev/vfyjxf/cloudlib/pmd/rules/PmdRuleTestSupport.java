package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.List;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;

/**
 * Runs one rule of this package over an in-memory source, without a Gradle project around it: the
 * source text is handed to PMD as a text file and the rule is the only rule in the ruleset.
 *
 * <p>The rule instance is copied by PMD before it runs, so a test must judge by the violations only.
 */
final class PmdRuleTestSupport {

    private PmdRuleTestSupport() {}

    /** Analyses {@code source} with {@code rule} and returns the violations found. */
    static List<RuleViolation> violationsOf(Rule rule, String fileName, String source) {
        return analyze(rule, fileName, source).getViolations();
    }

    /** Analyses {@code source} with {@code rule} and returns the whole report. */
    static Report analyze(Rule rule, String fileName, String source) {
        rule.setLanguage(JavaLanguageModule.getInstance());
        if (rule.getName() == null) {
            // a rule built in code has no name yet; production supplies it from the ruleset
            rule.setName(rule.getClass().getSimpleName());
        }
        PMDConfiguration configuration = new PMDConfiguration();
        configuration.setIgnoreIncrementalAnalysis(true);
        try (PmdAnalysis analysis = PmdAnalysis.create(configuration)) {
            analysis.files().addSourceFile(FileId.fromPathLikeString(fileName), source);
            analysis.addRuleSet(RuleSet.forSingleRule(rule));
            return analysis.performAnalysisAndCollectReport();
        }
    }

    /**
     * Overrides a property by name, the way a ruleset does with a {@code <property>} element. A
     * descriptor looked up by name is only known as {@code PropertyDescriptor<?>}, so the value has
     * to be handed over unchecked.
     */
    @SuppressWarnings("unchecked")
    static void setProperty(Rule rule, String name, Object value) {
        rule.setProperty((PropertyDescriptor<Object>) rule.getPropertyDescriptor(name), value);
    }

}
