package dev.vfyjxf.cloudlib.gradle.pmd;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Fails when a directory of the main source set holds Java sources but no
 * {@code package-info.java}.
 *
 * <p>PMD's model is one file at a time, so it can never report a missing file; a file-system walk
 * is the only way to state "every package is declared". The check also carries the packages'
 * {@code @NullMarked} requirement, which the ruleset cannot enforce on files that do not exist.
 */
public abstract class VerifyPackageInfoTask extends DefaultTask {

    private static final String PACKAGE_INFO = "package-info.java";

    private static final String JAVA_SUFFIX = ".java";

    /** The main source roots to walk; directories under the build directory are already gone. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceRoots();

    @TaskAction
    public void verifyPackageInfo() {
        Set<String> packages = new TreeSet<>();
        Set<String> missing = new TreeSet<>();
        for (File root : getSourceRoots()) {
            Path rootPath = root.toPath();
            if (!Files.isDirectory(rootPath)) {
                continue;
            }
            scan(rootPath, packages, missing);
        }
        if (!missing.isEmpty()) {
            throw new GradleException("packages without a " + PACKAGE_INFO + " (" + missing.size()
                    + " of " + packages.size() + "):\n  " + String.join("\n  ", missing));
        }
        getLogger().lifecycle("{} present for all {} packages", PACKAGE_INFO, packages.size());
    }

    /**
     * Records every directory that directly holds a Java source, together with whether that
     * directory declares its package.
     */
    private static void scan(Path root, Set<String> packages, Set<String> missing) {
        Map<Path, Boolean> declared = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(JAVA_SUFFIX))
                    .forEach(path -> declared.merge(path.getParent(),
                            PACKAGE_INFO.equals(path.getFileName().toString()), Boolean::logicalOr));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to scan " + root, e);
        }
        for (Map.Entry<Path, Boolean> entry : declared.entrySet()) {
            packages.add(root.relativize(entry.getKey()).toString());
            if (!entry.getValue()) {
                missing.add(root.relativize(entry.getKey()).toString());
            }
        }
    }

}
