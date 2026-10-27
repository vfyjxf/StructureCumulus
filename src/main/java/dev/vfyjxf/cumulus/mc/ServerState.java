package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.Host;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** The server-side owner of the kernel and the per-level capture and persistence state. */
@NullMarked
public final class ServerState {

    private static final OwnerId serverOwner = OwnerId.of(NamespacedId.of(Constants.modId, "server"));

    private static @Nullable ServerState current;

    private final Catalogs catalogs;
    private final StructureKernel kernel;
    private final DatapackBlueprints blueprints;
    private final DocumentSource documents;
    private final Map<ServerLevel, LevelStore> levels = new HashMap<>();
    private final Map<ServerLevel, LevelCapture> captures = new HashMap<>();

    private ServerState() {
        this.catalogs = Catalogs.builtin();
        this.blueprints = DatapackBlueprints.attach();
        DocumentSource builtin = BuiltinDocuments.source();
        // A datapack document wins over the built-in document on an id collision.
        this.documents = id -> {
            Document document = blueprints.find(id);
            return document != null ? document : builtin.find(id);
        };
        this.kernel = StructureKernel.create(catalogs, new Host(documents, null, ContextStore.inMemory(), Map.of()));
    }

    public static void open(ServerAboutToStartEvent event) {
        ServerState previous = current;
        current = new ServerState();
        if (previous != null) {
            closeState(previous);
        }
    }

    public static void close(ServerStoppingEvent event) {
        ServerState state = current;
        current = null;
        if (state != null) {
            closeState(state);
        }
        DatapackBlueprints.clearPending();
    }

    public static void unload(LevelEvent.Unload event) {
        ServerState state = current;
        if (state != null && event.getLevel() instanceof ServerLevel level) {
            LevelCapture capture = state.captures.remove(level);
            if (capture != null) {
                capture.close();
            }
            state.levels.remove(level);
        }
    }

    private static void closeState(ServerState state) {
        state.blueprints.close();
        state.closeCaptures();
        state.levels.clear();
    }

    public static @Nullable ServerState current() {
        return current;
    }

    StructureKernel kernel() {
        return kernel;
    }

    DatapackBlueprints blueprints() {
        return blueprints;
    }

    StructureKernel kernel(ServerLevel level) {
        return store(level).kernel();
    }

    SavedClaims claims(ServerLevel level) {
        return store(level).claims();
    }

    SavedContexts contexts(ServerLevel level) {
        return store(level).contexts();
    }

    LevelCapture capture(ServerLevel level) {
        return captures.computeIfAbsent(level, LevelCapture::new);
    }

    void closeCaptures() {
        captures.values().forEach(LevelCapture::close);
        captures.clear();
    }

    InstanceId instance(ServerLevel level) {
        return new InstanceId(World.of(world(level)), contexts(level).allocateSerial());
    }

    OwnerId owner() {
        return serverOwner;
    }

    private LevelStore store(ServerLevel level) {
        return levels.computeIfAbsent(level, attached -> {
            World world = World.of(world(attached));
            SavedClaims claims = attached.getDataStorage()
                    .computeIfAbsent(SavedClaims.factory(world), SavedClaims.dataName);
            SavedContexts contexts = attached.getDataStorage()
                    .computeIfAbsent(SavedContexts.factory(world), SavedContexts.dataName);
            Host host = new Host(documents, claims, contexts, Map.of());
            return new LevelStore(claims, contexts, StructureKernel.create(catalogs, host));
        });
    }

    private static NamespacedId world(ServerLevel level) {
        var location = level.dimension().location();
        return NamespacedId.of(location.getNamespace(), location.getPath());
    }

    private record LevelStore(SavedClaims claims, SavedContexts contexts, StructureKernel kernel) {}
}
