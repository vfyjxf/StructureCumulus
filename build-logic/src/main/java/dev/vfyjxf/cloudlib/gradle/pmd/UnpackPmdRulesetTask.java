package dev.vfyjxf.cloudlib.gradle.pmd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Unpacks the two rulesets out of the {@code cloudpmd} artifact, which is the only copy of them
 * there is. The PMD tasks read the unpacked files, so an edit to a ruleset reaches them through
 * the artifact rather than through a second copy maintained here.
 *
 * <p>There are two: {@code pmd/cloudlib-ruleset.xml}, the error rules the gate task fails on, and
 * {@code pmd/cloudlib-warnings.xml}, the warnings the advisory task only reports. They are split
 * because Gradle's {@code Pmd} task can fail on violations or ignore all of them, with nothing in
 * between — the severity layering lives in the ruleset files, one task each.
 *
 * <p>This runs as a task instead of during plugin application because the unpacked files live
 * under the build directory, next to everything a {@code clean} wipes: only a task makes sure they
 * are recreated before the PMD tasks read them.
 */
public abstract class UnpackPmdRulesetTask extends DefaultTask {

    /** The resource, inside the artifact, that holds the error ruleset. */
    public static final String RULESET_RESOURCE = "pmd/cloudlib-ruleset.xml";

    /** The resource, inside the artifact, that holds the warnings ruleset. */
    public static final String WARNINGS_RESOURCE = "pmd/cloudlib-warnings.xml";

    /** The artifacts that may carry the rulesets. */
    @Classpath
    public abstract ConfigurableFileCollection getRulesetArtifacts();

    /** Where the unpacked error ruleset is handed to the gate PMD task. */
    @OutputFile
    public abstract RegularFileProperty getRulesetFile();

    /** Where the unpacked warnings ruleset is handed to the advisory PMD task. */
    @OutputFile
    public abstract RegularFileProperty getWarningsFile();

    @TaskAction
    public void unpackRulesets() {
        writeResource(RULESET_RESOURCE, getRulesetFile());
        writeResource(WARNINGS_RESOURCE, getWarningsFile());
    }

    /** Writes {@code resource} to {@code target}, leaving the file alone when it is already current. */
    private void writeResource(String resource, RegularFileProperty target) {
        byte[] bytes = readResource(resource);
        Path path = target.get().getAsFile().toPath();
        try {
            if (!Files.isRegularFile(path) || !Arrays.equals(Files.readAllBytes(path), bytes)) {
                Files.createDirectories(path.getParent());
                Files.write(path, bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to unpack " + resource + " to " + path, e);
        }
    }

    private byte[] readResource(String resource) {
        for (File artifact : getRulesetArtifacts()) {
            if (!artifact.isFile()) {
                continue;
            }
            try (ZipFile archive = new ZipFile(artifact)) {
                ZipEntry entry = archive.getEntry(resource);
                if (entry == null) {
                    continue;
                }
                try (InputStream in = archive.getInputStream(entry)) {
                    return in.readAllBytes();
                }
            } catch (IOException e) {
                throw new UncheckedIOException("failed to read " + resource + " from " + artifact, e);
            }
        }
        throw new IllegalStateException(
                "none of " + getRulesetArtifacts().getFiles() + " provides " + resource);
    }

}
