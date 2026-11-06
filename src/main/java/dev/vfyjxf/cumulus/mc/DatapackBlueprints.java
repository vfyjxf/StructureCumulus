package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.FindingRenderer;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.LabelRole;
import dev.vfyjxf.cumulus.kernel.finding.RenderContext;
import dev.vfyjxf.cumulus.kernel.finding.SourceText;
import dev.vfyjxf.cumulus.kernel.space.LocusFrame;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The datapack document source for {@code data/<ns>/cumulus/structure/<path>.json} files. */
@NullMarked
public final class DatapackBlueprints implements DocumentSource {

    private static final String directory = "cumulus/structure";

    private static final String extension = ".json";

    private static volatile Snapshot pending = Snapshot.empty();

    private volatile Snapshot snapshot;
    private volatile boolean closed;

    private DatapackBlueprints(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ReloadListener());
    }

    static DatapackBlueprints attach() {
        Snapshot seed = pending;
        pending = Snapshot.empty();
        return new DatapackBlueprints(seed);
    }

    static void clearPending() {
        pending = Snapshot.empty();
    }

    static void receive(Snapshot prepared) {
        ServerState state = ServerState.current();
        if (state == null) {
            pending = prepared;
        } else {
            state.blueprints().reload(prepared);
        }
    }

    static Snapshot scan(ResourceManager manager) {
        Map<ResourceLocation, Resource> resources = manager
                .listResources(directory, location -> location.getPath().endsWith(extension));
        List<ResourceLocation> ordered = new ArrayList<>(resources.keySet());
        ordered.sort(Comparator.comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath));
        Map<NamespacedId, Loaded> loaded = new TreeMap<>();
        Map<NamespacedId, LoadedSet> loadedSets = new TreeMap<>();
        List<Finding> findings = new ArrayList<>();
        Map<NamespacedId, String> texts = new TreeMap<>();
        for (ResourceLocation path : ordered) {
            NamespacedId source = sourceId(path);
            Resource resource = resources.get(path);
            if (source == null || resource == null) {
                continue;
            }
            byte[] bytes;
            try {
                bytes = read(resource);
            } catch (IOException failure) {
                findings.add(readFailure(path, failure));
                continue;
            }
            List<Finding> errors = new ArrayList<>();
            String text = new String(bytes, StandardCharsets.UTF_8);
            JsonDocument parsed = JsonDocument.read(source, text, errors);
            findings.addAll(errors);
            if (!errors.isEmpty()) {
                texts.put(source, text);
            }
            if (parsed instanceof JsonDocument.Blueprint blueprint) {
                Document document = blueprint.document();
                NamespacedId id = document.root().id();
                Loaded previous = loaded.putIfAbsent(id, new Loaded(bytes, document, path));
                if (previous != null && !Arrays.equals(previous.bytes(), bytes)) {
                    findings.add(duplicate(id, previous.path(), path));
                }
            } else if (parsed instanceof JsonDocument.StructureSet set) {
                StructureSetDocument document = set.document();
                NamespacedId id = document.spec().id();
                LoadedSet previous = loadedSets.putIfAbsent(id, new LoadedSet(bytes, document, path));
                if (previous != null && !Arrays.equals(previous.bytes(), bytes)) {
                    findings.add(duplicateSet(id, previous.path(), path));
                }
            }
        }
        Map<NamespacedId, Document> documents = new TreeMap<>();
        loaded.forEach((id, value) -> documents.put(id, value.document()));
        Map<NamespacedId, StructureSetDocument> sets = new TreeMap<>();
        loadedSets.forEach((id, value) -> sets.put(id, value.document()));
        report(findings, texts);
        return new Snapshot(documents, sets, findings);
    }

    private static void report(List<Finding> findings, Map<NamespacedId, String> texts) {
        for (Finding finding : findings) {
            RenderContext context = RenderContext.plain();
            SourceSpan span = primarySpan(finding);
            if (span != null) {
                String text = texts.get(span.document());
                if (text != null) {
                    context = RenderContext.of(SourceText.of(span.document(), text));
                }
            }
            String rendered = FindingRenderer.human(FindingBundle.of(List.of(finding)), context);
            for (String line : rendered.split("\n", -1)) {
                if (!line.isEmpty()) {
                    StructureCumulus.logger.warn(line);
                }
            }
        }
    }

    private static @Nullable SourceSpan primarySpan(Finding finding) {
        for (Label label : finding.labels()) {
            if (label.role() == LabelRole.primary && label.locus().frame() == LocusFrame.definition) {
                return label.locus().source();
            }
        }
        return null;
    }

    static @Nullable NamespacedId sourceId(ResourceLocation location) {
        String path = location.getPath();
        String prefix = directory + "/";
        if (!path.startsWith(prefix) || !path.endsWith(extension)) {
            return null;
        }
        String relative = path.substring(prefix.length(), path.length() - extension.length());
        if (relative.isEmpty()) {
            return null;
        }
        try {
            return NamespacedId.of(location.getNamespace(), relative);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    @Override
    public @Nullable Document find(NamespacedId id) {
        return snapshot.find(id);
    }

    @Override
    public @Nullable StructureSetDocument findSet(NamespacedId id) {
        return snapshot.findSet(id);
    }

    public Map<NamespacedId, Document> documents() {
        return snapshot.documents();
    }

    public Map<NamespacedId, StructureSetDocument> structureSets() {
        return snapshot.sets();
    }

    public List<Finding> findings() {
        return snapshot.findings();
    }

    void reload(Snapshot next) {
        if (!closed) {
            snapshot = next;
        }
    }

    void close() {
        closed = true;
        snapshot = Snapshot.empty();
    }

    private static byte[] read(Resource resource) throws IOException {
        try (InputStream stream = resource.open()) {
            return stream.readAllBytes();
        }
    }

    private static Finding readFailure(ResourceLocation path, IOException failure) {
        String detail = failure.getMessage();
        if (detail == null) {
            detail = failure.getClass().getSimpleName();
        }
        return Finding.builder(Codes.readerFailed).phase(Phase.compile)
                .message("could not read " + path + ": " + detail).build();
    }

    private static Finding duplicate(NamespacedId id, ResourceLocation first, ResourceLocation duplicate) {
        return Finding.builder(Codes.documentDuplicate).phase(Phase.compile)
                .message("duplicate document id " + id.text() + ": " + first + " wins over " + duplicate).build();
    }

    private static Finding duplicateSet(NamespacedId id, ResourceLocation first, ResourceLocation duplicate) {
        return Finding.builder(Codes.documentDuplicate).phase(Phase.compile)
                .message("duplicate structure-set id " + id.text() + ": " + first + " wins over " + duplicate).build();
    }

    static final class Snapshot {

        private final Map<NamespacedId, Document> documents;
        private final Map<NamespacedId, StructureSetDocument> sets;
        private final List<Finding> findings;

        private Snapshot(
            Map<NamespacedId, Document> documents,
            Map<NamespacedId, StructureSetDocument> sets,
            List<Finding> findings
        ) {
            this.documents = Collections.unmodifiableMap(new TreeMap<>(documents));
            this.sets = Collections.unmodifiableMap(new TreeMap<>(sets));
            this.findings = List.copyOf(findings);
        }

        static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), List.of());
        }

        @Nullable
        Document find(NamespacedId id) {
            return documents.get(id);
        }

        @Nullable
        StructureSetDocument findSet(NamespacedId id) {
            return sets.get(id);
        }

        Map<NamespacedId, Document> documents() {
            return documents;
        }

        Map<NamespacedId, StructureSetDocument> sets() {
            return sets;
        }

        List<Finding> findings() {
            return findings;
        }
    }

    private record Loaded(byte[] bytes, Document document, ResourceLocation path) {}

    private record LoadedSet(byte[] bytes, StructureSetDocument document, ResourceLocation path) {}

    private static final class ReloadListener extends SimplePreparableReloadListener<Snapshot> {

        @Override
        protected Snapshot prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            return scan(resourceManager);
        }

        @Override
        protected void apply(Snapshot prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
            receive(prepared);
        }
    }
}
