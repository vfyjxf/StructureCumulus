package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompileResult;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackStructureSetTest {

    private static final NamespacedId rootId = NamespacedId.of("test", "hut");
    private static final NamespacedId satelliteId = NamespacedId.of("test", "tower");
    private static final NamespacedId setId = NamespacedId.of("test", "hut_set");

    @Test
    void scanLoadsBlueprintAndStructureSetDocumentsSideBySide() throws IOException {
        try (MultiPackResourceManager resources = manager(
            Map.of(
                "test:cumulus/structure/hut.json",
                blueprint("test:hut", 1),
                "test:cumulus/structure/tower.json",
                blueprint("test:tower", 1),
                "test:cumulus/structure/hut_set.json",
                set("test:hut_set", "test:hut", "test:tower")
            )
        )) {
            DatapackBlueprints.Snapshot snapshot = DatapackBlueprints.scan(resources);
            assertTrue(snapshot.findings().isEmpty(), () -> "scan reported " + snapshot.findings());
            assertNotNull(snapshot.find(rootId), "the blueprint document still loads");
            assertNotNull(snapshot.find(satelliteId), "the satellite blueprint document still loads");
            StructureSetDocument document = snapshot.findSet(setId);
            assertNotNull(document, "the set document loads under its declared id");
            assertEquals(rootId, document.spec().root());
            assertEquals(1, document.spec().satellites().size());
            assertEquals(List.of(setId), snapshot.sets().keySet().stream().toList());
        }
    }

    @Test
    void duplicateStructureSetIdsKeepTheFirstPathAndReportScDoc0004() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("test:cumulus/structure/z_set.json", set("test:duplicate", "test:tower", "test:hut"));
        files.put("test:cumulus/structure/a_set.json", set("test:duplicate", "test:hut", "test:tower"));
        try (MultiPackResourceManager resources = manager(files)) {
            DatapackBlueprints.Snapshot snapshot = DatapackBlueprints.scan(resources);
            StructureSetDocument document = snapshot.findSet(NamespacedId.of("test", "duplicate"));
            assertNotNull(document);
            assertEquals(rootId, document.spec().root(), "a_set.json sorts before z_set.json");
            assertEquals(1, snapshot.findings().size());
            assertEquals(Codes.documentDuplicate.code(), snapshot.findings().get(0).code());
            assertTrue(snapshot.findings().get(0).message().contains("structure-set"));
        }
    }

    @Test
    void aMalformedSetDocumentIsSkippedWithoutKillingTheReload() throws IOException {
        String malformed = set("test:broken_set", "test:hut", "test:tower")
                .replace("\"satellites\":", "\"satellite\":");
        try (MultiPackResourceManager resources = manager(
            Map.of(
                "test:cumulus/structure/broken_set.json",
                malformed,
                "test:cumulus/structure/hut.json",
                blueprint("test:hut", 1)
            )
        )) {
            DatapackBlueprints.Snapshot snapshot = DatapackBlueprints.scan(resources);
            assertNotNull(snapshot.find(rootId));
            assertNull(snapshot.findSet(NamespacedId.of("test", "broken_set")));
            assertFalse(snapshot.findings().isEmpty());
            assertTrue(
                snapshot.findings().stream().allMatch(finding -> finding.code().equals(Codes.documentField.code())),
                () -> snapshot.findings().toString()
            );
        }
    }

    @Test
    void serverStateServesAndCompilesAStructureSet() throws IOException {
        ServerState state = open(
            Map.of(
                "test:cumulus/structure/hut.json",
                blueprint("test:hut", 1),
                "test:cumulus/structure/tower.json",
                blueprint("test:tower", 1),
                "test:cumulus/structure/hut_set.json",
                set("test:hut_set", "test:hut", "test:tower")
            )
        );
        try {
            StructureSetDocument document = state.structureSet(setId);
            assertNotNull(document, "the merged document source serves the set");
            StructureSetCompileResult result = state.compileSet(document);
            assertTrue(result.succeeded(), () -> result.findings().findings().toString());
            CompiledStructureSet compiled = result.set();
            assertNotNull(compiled);
            assertEquals(setId, compiled.id());
            assertEquals(rootId, compiled.root().id());
            assertEquals(1, compiled.satellites().size());
            assertEquals(satelliteId, compiled.satellites().get(0).blueprint().id());
            assertNotNull(state.kernel().document(rootId), "set compilation resolves the root through the same source");
        } finally {
            ServerState.close(new ServerStoppingEvent(null));
        }
    }

    @Test
    void aBlueprintAndASetMayShareOneId() throws IOException {
        try (MultiPackResourceManager resources = manager(
            Map.of(
                "test:cumulus/structure/hut.json",
                blueprint("test:hut", 1),
                "test:cumulus/structure/tower.json",
                blueprint("test:tower", 1),
                "test:cumulus/structure/same.json",
                set("test:hut", "test:hut", "test:tower")
            )
        )) {
            DatapackBlueprints.Snapshot snapshot = DatapackBlueprints.scan(resources);
            assertTrue(snapshot.findings().isEmpty(), () -> "scan reported " + snapshot.findings());
            assertNotNull(snapshot.find(NamespacedId.of("test", "hut")), "the blueprint keeps its namespace");
            assertNotNull(snapshot.findSet(NamespacedId.of("test", "hut")), "the set has its own lookup");
        }
    }

    private static ServerState open(Map<String, String> files) throws IOException {
        if (ServerState.current() != null) {
            ServerState.close(new ServerStoppingEvent(null));
        }
        try (MultiPackResourceManager resources = manager(files)) {
            DatapackBlueprints.receive(DatapackBlueprints.scan(resources));
        }
        ServerState.open(new ServerAboutToStartEvent(null));
        ServerState state = ServerState.current();
        assertNotNull(state, "the server state opens");
        return state;
    }

    private static MultiPackResourceManager manager(Map<String, String> files) {
        Map<ResourceLocation, byte[]> packed = new LinkedHashMap<>();
        files.forEach((path, text) -> packed.put(ResourceLocation.parse(path), text.getBytes(StandardCharsets.UTF_8)));
        return new MultiPackResourceManager(PackType.SERVER_DATA, List.of(new FakePack(packed)));
    }

    private static String blueprint(String id, int size) {
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

    private static String set(String id, String root, String satellite) {
        return """
                {
                  "schemaVersion": 1,
                  "kind": "structure_set",
                  "id": "%s",
                  "root": "%s",
                  "satellites": [
                    {"id": "test:embedded", "blueprint": "%s", "at": [2, 0, 0], "required": false}
                  ]
                }
                """.formatted(id, root, satellite);
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
