package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import dev.vfyjxf.cumulus.mc.plugin.LogCapture;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackBlueprintsTest {

    private static final NamespacedId hut = NamespacedId.of("test", "hut");
    private static final NamespacedId hall = NamespacedId.of("test", "rooms/hall");

    @Test
    void derivesTheSourceIdFromTheFilePath() {
        assertEquals(hut, DatapackBlueprints.sourceId(ResourceLocation.parse("test:cumulus/structure/hut.json")));
        assertEquals(
            hall,
            DatapackBlueprints.sourceId(ResourceLocation.parse("test:cumulus/structure/rooms/hall.json"))
        );
        assertNull(DatapackBlueprints.sourceId(ResourceLocation.parse("test:cumulus/structure/hut.nbt")));
        assertNull(DatapackBlueprints.sourceId(ResourceLocation.parse("test:cumulus/structure/.json")));
        assertNull(DatapackBlueprints.sourceId(ResourceLocation.parse("test:other/hut.json")));
    }

    @Test
    void storesDocumentsByTheirDeclaredRootId() throws IOException {
        try (MultiPackResourceManager resources = manager(
            Map.of(
                "test:cumulus/structure/rooms/hall.json",
                document("test:rooms/hall", 1),
                "test:cumulus/structure/hut.json",
                document("test:hut", 2),
                "test:cumulus/structure/hut.nbt",
                "not a document"
            )
        )) {
            DatapackBlueprints.Snapshot snapshot = DatapackBlueprints.scan(resources);
            assertTrue(snapshot.findings().isEmpty(), () -> "scan reported " + snapshot.findings());
            assertEquals(List.of(hut, hall), new ArrayList<>(snapshot.documents().keySet()));
            Document document = snapshot.find(hut);
            assertNotNull(document, "the declared root id is the lookup key");
            assertEquals(2, document.root().bounds().sizeX());
            assertNull(snapshot.find(NamespacedId.of("test", "hut.json")), "the file path is not the document id");
        }
    }

    @Test
    void theFirstPathWinsADuplicateId() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("test:cumulus/structure/z.json", document("test:duplicate", 3));
        files.put("test:cumulus/structure/a.json", document("test:duplicate", 2));
        try (MultiPackResourceManager resources = manager(files)) {
            DatapackBlueprints.Snapshot snapshot = DatapackBlueprints.scan(resources);
            Document document = snapshot.find(NamespacedId.of("test", "duplicate"));
            assertNotNull(document);
            assertEquals(2, document.root().bounds().sizeX(), "a.json sorts before z.json");
            assertEquals(1, snapshot.findings().size());
            Finding finding = snapshot.findings().get(0);
            assertEquals(Codes.documentDuplicate.code(), finding.code());
            assertTrue(finding.message().contains("test:cumulus/structure/a.json"), finding.message());
            assertTrue(finding.message().contains("test:cumulus/structure/z.json"), finding.message());
        }
    }

    @Test
    void equalDuplicateBytesCollapseWithoutAFinding() throws IOException {
        String text = document("test:duplicate", 1);
        try (MultiPackResourceManager resources = manager(
            Map.of("test:cumulus/structure/a.json", text, "test:cumulus/structure/b.json", text)
        )) {
            DatapackBlueprints.Snapshot snapshot = DatapackBlueprints.scan(resources);
            assertTrue(snapshot.findings().isEmpty(), () -> "scan reported " + snapshot.findings());
            assertEquals(1, snapshot.documents().size());
            assertNotNull(snapshot.find(NamespacedId.of("test", "duplicate")));
        }
    }

    @Test
    void malformedFilesAreSkippedWithoutKillingTheReload() throws IOException {
        try (MultiPackResourceManager resources = manager(
            Map.of(
                "test:cumulus/structure/broken.json",
                "{\"schemaVersion\": 1,",
                "test:cumulus/structure/hut.json",
                document("test:hut", 1)
            )
        )) {
            DatapackBlueprints.Snapshot snapshot = DatapackBlueprints.scan(resources);
            assertNotNull(snapshot.find(hut));
            assertEquals(1, snapshot.findings().size());
            assertEquals(Codes.readerFailed.code(), snapshot.findings().get(0).code());
        }
    }

    @Test
    void reloadReplacesAndRemovesDocuments() throws IOException {
        DatapackBlueprints blueprints = DatapackBlueprints.attach();
        try (MultiPackResourceManager first = manager(
            Map.of(
                "test:cumulus/structure/hut.json",
                document("test:hut", 1),
                "test:cumulus/structure/rooms/hall.json",
                document("test:rooms/hall", 1)
            )
        );
                MultiPackResourceManager second = manager(
                    Map.of(
                        "test:cumulus/structure/hut.json",
                        document("test:hut", 4),
                        "test:cumulus/structure/tower.json",
                        document("test:tower", 1)
                    )
                )) {
            blueprints.reload(DatapackBlueprints.scan(first));
            Document loaded = blueprints.find(hut);
            assertNotNull(loaded);
            assertEquals(1, loaded.root().bounds().sizeX());
            assertNotNull(blueprints.find(hall));

            blueprints.reload(DatapackBlueprints.scan(second));
            Document replaced = blueprints.find(hut);
            assertNotNull(replaced);
            assertEquals(4, replaced.root().bounds().sizeX());
            assertNull(blueprints.find(hall), "a removed file leaves the source");
            assertNotNull(blueprints.find(NamespacedId.of("test", "tower")));
        } finally {
            blueprints.close();
        }
        assertNull(blueprints.find(hut), "a closed source serves nothing");
    }

    @Test
    void theInitialReloadSeedsTheNextServerAndStopClearsIt() throws IOException {
        if (ServerState.current() != null) {
            ServerState.close(new ServerStoppingEvent(null));
        }
        DatapackBlueprints.receive(
            DatapackBlueprints.scan(manager(Map.of("test:cumulus/structure/hut.json", document("test:hut", 1))))
        );
        ServerState.open(new ServerAboutToStartEvent(null));
        ServerState state = ServerState.current();
        assertNotNull(state);
        try {
            assertNotNull(state.blueprints().find(hut), "the pre-start reload seeds the server source");

            DatapackBlueprints.receive(
                DatapackBlueprints.scan(manager(Map.of("test:cumulus/structure/tower.json", document("test:tower", 1))))
            );
            assertNull(state.blueprints().find(hut), "a reload replaces the snapshot");
            assertNotNull(state.blueprints().find(NamespacedId.of("test", "tower")));
        } finally {
            ServerState.close(new ServerStoppingEvent(null));
        }
        assertNull(ServerState.current());
        assertNull(state.blueprints().find(NamespacedId.of("test", "tower")), "a stopped server serves nothing");
    }

    @Test
    void scanLogsRenderedDiagnosticsWithFileLineAndColumn() throws IOException {
        try (MultiPackResourceManager resources = manager(
            Map.of("test:cumulus/structure/broken.json", "{\"schemaVersion\": 1,")
        )) {
            try (LogCapture capture = LogCapture.attach(dev.vfyjxf.cumulus.StructureCumulus.logger.getName())) {
                DatapackBlueprints.scan(resources);
                assertTrue(
                    capture.messages().stream().anyMatch(line -> line.contains("--> test:broken:1:")),
                    () -> "the reload log carries file, line and column: " + capture.messages()
                );
            }
        }
    }

    private static MultiPackResourceManager manager(Map<String, String> files) {
        Map<ResourceLocation, byte[]> packed = new LinkedHashMap<>();
        files.forEach((path, text) -> packed.put(ResourceLocation.parse(path), text.getBytes(StandardCharsets.UTF_8)));
        return new MultiPackResourceManager(PackType.SERVER_DATA, List.of(new FakePack(packed)));
    }

    private static String document(String id, int size) {
        return """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "bounds": [0, 0, 0, %d, 1, 1],
                  "bays": [
                    {"id": "cell", "box": [0, 0, 0, %d, 1, 1], "coverage": "every_cell",
                     "footprint": "box", "sharing": "shared"}
                  ],
                  "checks": [
                    {"id": "cell_air", "bay": "cell", "focus": {"kind": "bay"},
                     "expect": {"kind": "each_cell", "element": {"kind": "air"}}}
                  ]
                }
                """.formatted(id, size, size);
    }

    private record FakePack(Map<ResourceLocation, byte[]> files) implements PackResources {

        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            byte[] bytes = files.get(location);
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            String prefix = path + "/";
            files.forEach((location, bytes) -> {
                if (location.getNamespace().equals(namespace) && location.getPath().startsWith(prefix)) {
                    output.accept(location, () -> new ByteArrayInputStream(bytes));
                }
            });
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            Set<String> namespaces = new TreeSet<>();
            files.keySet().forEach(location -> namespaces.add(location.getNamespace()));
            return namespaces;
        }

        @Override
        public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
            return null;
        }

        @Override
        public PackLocationInfo location() {
            return new PackLocationInfo("fake", Component.literal("fake"), PackSource.DEFAULT, Optional.empty());
        }

        @Override
        public void close() {}
    }
}
