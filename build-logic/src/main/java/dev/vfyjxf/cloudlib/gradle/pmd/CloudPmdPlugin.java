package dev.vfyjxf.cloudlib.gradle.pmd;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.quality.Pmd;
import org.gradle.api.plugins.quality.PmdExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * The CloudLib PMD 7 gate:
 * <ul>
 *   <li>applies {@code pmd} pinned to PMD {@value #PMD_VERSION} and drops PMD's own default rule
 *       sets — the rules are the ones the {@code cloudpmd} artifact selects</li>
 *   <li>puts {@code cloudpmd} on the PMD tasks' classpath, so the custom rule classes and the
 *       rulesets they are referenced from come from that one artifact</li>
 *   <li>{@code unpackPmdRuleset} copies {@code pmd/cloudlib-ruleset.xml} and
 *       {@code pmd/cloudlib-warnings.xml} out of that artifact into {@code build/cloudpmd/} and
 *       hands them to the PMD tasks</li>
 *   <li>gates {@code main} only: every other {@code Pmd} task is disabled, {@code pmdMain} keeps
 *       both reports and analyses neither the generated sources the CloudLang plugin hangs off
 *       the main source set nor the demo screens listed in {@link #SOURCE_EXCLUDES}</li>
 *   <li>{@code pmdAdvisory} analyses the very same sources with the warnings ruleset,
 *       {@code ignoreFailures} on, and writes {@code build/reports/pmd/advisory.*}: the error and
 *       warning layers of the ruleset, as two tasks, because a {@code Pmd} task either fails on
 *       violations or ignores them all</li>
 *   <li>{@code verifyPackageInfo} walks the main sources and fails when a directory holds Java
 *       sources without a {@code package-info.java}, because PMD cannot report a missing file</li>
 * </ul>
 *
 * <p>{@value #CLOUPMD_COORDINATES} is never published: the consuming projects include
 * {@code cloudlang-plugin}, and composite substitution resolves the module to its
 * {@code cloudpmd} subproject. CloudLib includes the build a second time at the top level of its
 * settings file, because the {@code includeBuild} inside {@code pluginManagement} — the one that
 * resolves this plugin — does not substitute dependencies.
 */
public class CloudPmdPlugin implements Plugin<Project> {

    /** The PMD release the gate is pinned to. */
    public static final String PMD_VERSION = "7.28.0";

    /** The rules and their selection, as one artifact. */
    public static final String CLOUPMD_COORDINATES = "dev.vfyjxf:cloudpmd:0.1.0";

    private static final String PMD_PLUGIN_ID = "pmd";

    private static final String PMD_CONFIGURATION = "pmd";

    private static final String PMD_MAIN_TASK = "pmdMain";

    private static final String PMD_ADVISORY_TASK = "pmdAdvisory";

    private static final String UNPACK_RULESET_TASK = "unpackPmdRuleset";

    private static final String VERIFY_PACKAGE_INFO_TASK = "verifyPackageInfo";

    /** The unpacked error ruleset, relative to the build directory. */
    private static final String RULESET_FILE = "cloudpmd/cloudlib-ruleset.xml";

    /** The unpacked warnings ruleset, relative to the build directory. */
    private static final String WARNINGS_FILE = "cloudpmd/cloudlib-warnings.xml";

    /** The advisory reports, relative to the build directory. */
    private static final String ADVISORY_HTML_REPORT = "reports/pmd/advisory.html";

    private static final String ADVISORY_XML_REPORT = "reports/pmd/advisory.xml";

    /** The demo screens CloudLib keeps in its main source set, excluded as files. */
    private static final List<String> SOURCE_EXCLUDES = List.of("**/dev/vfyjxf/cloudlib/test/**");

    private static final String JAVA_SOURCES = "**/*.java";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(PMD_PLUGIN_ID);

        PmdExtension pmd = project.getExtensions().getByType(PmdExtension.class);
        pmd.setToolVersion(PMD_VERSION);
        pmd.getIncrementalAnalysis().set(true);
        pmd.setRuleSets(List.of());

        Configuration cloudpmd = project.getConfigurations()
                .detachedConfiguration(project.getDependencies().create(CLOUPMD_COORDINATES));
        // The custom rule classes have to sit on PMD's classpath, next to the ruleset that names
        // them — but not in the 'pmd' configuration itself: declaring a dependency there suppresses
        // the PMD tool dependency the plugin installs by default.
        FileCollection pmdClasspath = project.getConfigurations().getByName(PMD_CONFIGURATION).plus(cloudpmd);
        TaskProvider<UnpackPmdRulesetTask> unpackRuleset = project.getTasks()
                .register(UNPACK_RULESET_TASK, UnpackPmdRulesetTask.class, task -> {
                    task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    task.setDescription("Unpacks " + UnpackPmdRulesetTask.RULESET_RESOURCE + " and "
                            + UnpackPmdRulesetTask.WARNINGS_RESOURCE
                            + " out of the cloudpmd artifact for the PMD tasks.");
                    task.getRulesetArtifacts().from(cloudpmd);
                    task.getRulesetFile().set(project.getLayout().getBuildDirectory().file(RULESET_FILE));
                    task.getWarningsFile().set(project.getLayout().getBuildDirectory().file(WARNINGS_FILE));
                });
        FileCollection ruleset = project.files(unpackRuleset.flatMap(UnpackPmdRulesetTask::getRulesetFile));
        FileCollection warnings = project.files(unpackRuleset.flatMap(UnpackPmdRulesetTask::getWarningsFile));
        pmd.setRuleSetFiles(ruleset);

        registerAdvisoryTask(project);
        configurePmdTasks(project, pmdClasspath, ruleset, warnings);
        registerPackageInfoCheck(project);
    }

    /**
     * Only the main source set is in scope — test sources are deliberately out of the gate — so
     * every other {@code Pmd} task is switched off rather than left to fail the build for rules
     * the two projects never agreed on. The two tasks that do run, the gate and the advisory one,
     * analyse exactly the same files and differ only in ruleset and failure mode.
     */
    private static void configurePmdTasks(Project project, FileCollection pmdClasspath, FileCollection ruleset,
            FileCollection warnings) {
        File buildDirectory = project.getLayout().getBuildDirectory().get().getAsFile();
        project.getTasks().withType(Pmd.class).configureEach(task -> {
            task.setPmdClasspath(pmdClasspath);
            switch (task.getName()) {
                case PMD_MAIN_TASK -> configureGate(task, project, buildDirectory, ruleset);
                case PMD_ADVISORY_TASK -> configureAdvisory(task, project, buildDirectory, warnings);
                default -> task.setEnabled(false);
            }
        });
    }

    /** Registers {@code pmdAdvisory} and hangs it off {@code check}. */
    private static void registerAdvisoryTask(Project project) {
        TaskProvider<Pmd> advisory = project.getTasks().register(PMD_ADVISORY_TASK, Pmd.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Runs the CloudLib PMD 7 warnings ruleset over the main sources and reports the "
                    + "violations without failing the build.");
        });
        project.getPlugins().withType(LifecycleBasePlugin.class, lifecycle -> project.getTasks()
                .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                .configure(check -> check.dependsOn(advisory)));
    }

    /** The gate task: the error ruleset over the main sources, failing the build on any violation. */
    private static void configureGate(Pmd task, Project project, File buildDirectory, FileCollection ruleset) {
        // the rules come from the unpacked ruleset alone — PmdPlugin configured this task before
        // the plugin got here, so both are set on the task, not only on the extension
        task.setRuleSets(List.of());
        task.setRuleSetFiles(ruleset);
        configureSources(task, project, buildDirectory);
        task.getReports().getHtml().getRequired().set(true);
        task.getReports().getXml().getRequired().set(true);
    }

    /**
     * The advisory task: the warnings ruleset over the same sources, recording its violations in
     * its own reports while letting the build pass. Gradle's {@code Pmd} task knows only "fail on
     * everything" and "ignore everything", so the warning half of the ruleset needs a second task
     * rather than a severity setting.
     */
    private static void configureAdvisory(Pmd task, Project project, File buildDirectory, FileCollection warnings) {
        task.setRuleSets(List.of());
        task.setRuleSetFiles(warnings);
        task.setIgnoreFailures(true);
        configureSources(task, project, buildDirectory);
        task.getReports().getHtml().getRequired().set(true);
        task.getReports().getHtml().getOutputLocation()
                .set(project.getLayout().getBuildDirectory().file(ADVISORY_HTML_REPORT));
        task.getReports().getXml().getRequired().set(true);
        task.getReports().getXml().getOutputLocation()
                .set(project.getLayout().getBuildDirectory().file(ADVISORY_XML_REPORT));
    }

    /**
     * Both PMD tasks cover the same files: the main sources without the generated ones and
     * without the demo screens listed in {@link #SOURCE_EXCLUDES}. They also share the aux
     * classpath — the main source set's own output plus its compile classpath, which is what
     * Gradle's PMD plugin hands the gate task. Without it the type-resolution-dependent rules
     * report differently, and the advisory report would disagree with the gate report.
     */
    private static void configureSources(Pmd task, Project project, File buildDirectory) {
        SourceSet main = mainSourceSet(project);
        // the CloudLang plugin hangs build/generated/sources/langKeys off the main source set;
        // generated code is not gated
        task.setSource(mainJavaSourceDirectories(project, buildDirectory));
        task.setClasspath(main.getOutput().plus(main.getCompileClasspath()));
        task.include(JAVA_SOURCES);
        SOURCE_EXCLUDES.forEach(task::exclude);
    }

    private static void registerPackageInfoCheck(Project project) {
        File buildDirectory = project.getLayout().getBuildDirectory().get().getAsFile();
        TaskProvider<VerifyPackageInfoTask> verifyPackageInfo = project.getTasks()
                .register(VERIFY_PACKAGE_INFO_TASK, VerifyPackageInfoTask.class, task -> {
                    task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    task.setDescription("Fails when a package of the main source set has Java sources but no "
                            + "package-info.java.");
                    task.getSourceRoots().from(mainJavaSourceDirectories(project, buildDirectory));
                });
        project.getPlugins().withType(LifecycleBasePlugin.class, lifecycle -> project.getTasks()
                .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                .configure(check -> check.dependsOn(verifyPackageInfo)));
    }

    /**
     * The main source set's Java source directories, minus the ones the build itself generates.
     */
    private static List<File> mainJavaSourceDirectories(Project project, File buildDirectory) {
        SourceSet main = mainSourceSet(project);
        return main.getJava()
                .getSrcDirs()
                .stream()
                .filter(directory -> !isUnder(directory, buildDirectory))
                .collect(Collectors.toList());
    }

    private static SourceSet mainSourceSet(Project project) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        return sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    }

    private static boolean isUnder(File directory, File buildDirectory) {
        Path path = directory.toPath().toAbsolutePath().normalize();
        Path buildPath = buildDirectory.toPath().toAbsolutePath().normalize();
        return path.startsWith(buildPath);
    }

}
