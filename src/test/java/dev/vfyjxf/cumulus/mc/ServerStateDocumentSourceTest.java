package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.Document;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerStateDocumentSourceTest {

    @Test
    void servesDatapackDocumentsAndFallsBackToBuiltins() throws IOException {
        ServerState state = open(Map.of("test:cumulus/structure/hut.json", document("test:hut", 1)));
        try {
            Document datapack = state.kernel().document(NamespacedId.of("test", "hut"));
            assertNotNull(datapack, "the datapack document is served");
            CompileResult compiled = state.kernel().compile(datapack);
            assertTrue(compiled.succeeded(), "the datapack document compiles");
            assertNotNull(
                state.kernel().document(BuiltinDocuments.airCellId()),
                "the built-in document remains the fallback"
            );
        } finally {
            ServerState.close(new ServerStoppingEvent(null));
        }
    }

    @Test
    void letsADatapackDocumentOverrideABuiltinId() throws IOException {
        ServerState state = open(
            Map.of("structurecumulus:cumulus/structure/air_cell.json", document("structurecumulus:air_cell", 2))
        );
        try {
            Document document = state.kernel().document(BuiltinDocuments.airCellId());
            assertNotNull(document);
            assertEquals(2, document.root().bounds().sizeX(), "the datapack document wins");
        } finally {
            ServerState.close(new ServerStoppingEvent(null));
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
