package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentReader;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
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
    private static final DocumentReader reader = new JsonReader();

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
        for (Finding finding : prepared.findings()) {
            StructureCumulus.logger.warn("{}: {}", finding.code().text(), finding.message());
        }
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
        List<Finding> findings = new ArrayList<>();
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
            Document document = reader.read(source, new String(bytes, StandardCharsets.UTF_8), errors);
            findings.addAll(errors);
            if (document == null) {
                continue;
            }
            NamespacedId id = document.root().id();
            Loaded previous = loaded.putIfAbsent(id, new Loaded(bytes, document, path));
            if (previous != null && !Arrays.equals(previous.bytes(), bytes)) {
                findings.add(duplicate(id, previous.path(), path));
            }
        }
        Map<NamespacedId, Document> documents = new TreeMap<>();
        loaded.forEach((id, value) -> documents.put(id, value.document()));
        return new Snapshot(documents, findings);
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

    public Map<NamespacedId, Document> documents() {
        return snapshot.documents();
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

    static final class Snapshot {

        private final Map<NamespacedId, Document> documents;
        private final List<Finding> findings;

        private Snapshot(Map<NamespacedId, Document> documents, List<Finding> findings) {
            this.documents = Collections.unmodifiableMap(new TreeMap<>(documents));
            this.findings = List.copyOf(findings);
        }

        static Snapshot empty() {
            return new Snapshot(Map.of(), List.of());
        }

        @Nullable
        Document find(NamespacedId id) {
            return documents.get(id);
        }

        Map<NamespacedId, Document> documents() {
            return documents;
        }

        List<Finding> findings() {
            return findings;
        }
    }

    private record Loaded(byte[] bytes, Document document, ResourceLocation path) {}

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
