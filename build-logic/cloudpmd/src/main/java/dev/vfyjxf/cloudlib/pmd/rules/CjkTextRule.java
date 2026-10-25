package dev.vfyjxf.cloudlib.pmd.rules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.reporting.RuleContext;

/**
 * Source text is English: no CJK character, in a comment or anywhere else.
 *
 * <p>This is catalogue rule C-26. The catalogue names the standard rule {@code RegexpSinglelineJava}
 * for it, but PMD 7 removed that rule — it is in no category of pmd-java 7.28.0 and no class of the
 * distribution — so the catalogue's own fallback applies: a custom rule that reads the source text
 * and matches line by line. Only the rule's name differs from the catalogue, because a class rule
 * carrying a removed standard rule's name would be misleading; the covered range and the property
 * name are the catalogue's.
 *
 * <p>The default {@code format} is the catalogue's {@code [\u4e00-\u9fff]}, the CJK Unified
 * Ideographs block. The catalogue offers a relaxed {@code [^\x00-\x7F]} variant, and it is not
 * usable on these projects: both use em dashes (U+2014) and arrows (U+2192) in javadoc throughout
 * (~300 files in CloudLib, ~240 in Nimbus). The property is there for a project that wants a wider
 * net.
 *
 * <p>Violations carry the whole message themselves, so the {@code message} attribute of the
 * ruleset reference is informational only. Messages therefore stay free of apostrophes and braces,
 * which {@code java.text.MessageFormat} would otherwise reinterpret.
 */
public class CjkTextRule extends AbstractJavaRulechainRule {

    /** CJK Unified Ideographs — the catalogue's default format. */
    private static final String defaultFormat = "[\\u4e00-\\u9fff]";

    private final PropertyDescriptor<Pattern> format = PropertyFactory
            .regexProperty("format")
            .desc("Regex matching a run of forbidden characters, applied line by line to the source text")
            .defaultValue(defaultFormat)
            .build();

    public CjkTextRule() {
        super(ASTCompilationUnit.class);
        definePropertyDescriptor(format);
        setMessage("Source files must not contain CJK text");
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
        RuleContext ctx = asCtx(data);
        String source = node.getTextDocument().getText().toString();
        Pattern pattern = getProperty(format);
        int lineNumber = 0;
        for (String line : source.split("\n", -1)) {
            lineNumber++;
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                ctx.addViolationWithPosition(node, lineNumber, lineNumber,
                        "CJK text in source: translate the comment or string to English");
            }
        }
        return data;
    }

}
