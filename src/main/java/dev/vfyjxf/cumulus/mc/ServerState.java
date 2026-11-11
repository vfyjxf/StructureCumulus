package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
import dev.vfyjxf.cumulus.kernel.context.StructureEvent;
import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompileResult;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompiler;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.spi.CatalogRegistrar;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.Host;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentLookup;
import dev.vfyjxf.cumulus.mc.plugin.AnnotationPluginLookup;
import dev.vfyjxf.cumulus.mc.plugin.ModPlugin;
import dev.vfyjxf.cumulus.mc.plugin.PluginLoader;
import dev.vfyjxf.cumulus.mc.pref.PreferencesSavedData;
import dev.vfyjxf.cumulus.mc.trait.TraitLookup;
import dev.vfyjxf.cumulus.mc.visual.VisualClaim;
import dev.vfyjxf.cumulus.mc.visual.VisualInstance;
import dev.vfyjxf.cumulus.mc.visual.VisualSnapshot;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** The server-side owner of the kernel and the per-level capture and persistence state. */
@NullMarked
public final class ServerState {

    private static final OwnerId serverOwner = OwnerId.of(NamespacedId.of(Constants.modId, "server"));

    /** The host's custom document key types; the shipped adapter registers none. */
    private static final Map<NamespacedId, Class<?>> hostTypes = Map.of();

    /** Written on the server thread, read by the datapack receive hook from the main thread. */
    private static volatile @Nullable ServerState current;

    private final Catalogs catalogs;
    private final StructureKernel kernel;
    private final DatapackBlueprints blueprints;
    private final DocumentSource documents;
    private final TraitLookup traitLookup;
    private NestedExtentLookup nestedExtentLookup = NestedExtentLookup.capabilities();
    private final Map<ResourceKey<Level>, LevelStore> levels = new HashMap<>();
    private final Map<ResourceKey<Level>, LevelCapture> captures = new HashMap<>();

    private ServerState() {
        this.catalogs = assembleCatalogs();
        this.blueprints = DatapackBlueprints.attach();
        DocumentSource builtin = BuiltinDocuments.source();
        // A datapack document wins over the built-in document on an id collision.
        this.documents = new DocumentSource() {
            @Override
            public @Nullable Document find(NamespacedId id) {
                Document document = blueprints.find(id);
                return document != null ? document : builtin.find(id);
            }

            @Override
            public @Nullable StructureSetDocument findSet(NamespacedId id) {
                StructureSetDocument document = blueprints.findSet(id);
                return document != null ? document : builtin.findSet(id);
            }
        };
        this.kernel = StructureKernel.create(catalogs, new Host(documents, null, StructureManager.create(), hostTypes));
        this.traitLookup = TraitLookup.load(StructureCumulus.logger);
    }

    /** The session's one assembly: built-ins, the adapter's host kinds, then plugin contributions. */
    private static Catalogs assembleCatalogs() {
        CatalogRegistrar registrar = CatalogRegistrar.builtin();
        AdapterCatalogs.seed(registrar);
        PluginLoader
                .register(StructureCumulus.logger, "catalog", AnnotationPluginLookup.of(ModPlugin.class), registrar);
        return registrar.catalogs();
    }

    public static void open(ServerAboutToStartEvent event) {
        ServerState previous = current;
        current = null;
        if (previous != null) {
            closeState(previous);
        }
        current = new ServerState();
    }

    public static void close(ServerStoppingEvent event) {
        closeCurrent();
    }

    /** The crash path: {@code ServerStoppingEvent} never fires, {@code ServerStoppedEvent} always does. */
    public static void stopped(ServerStoppedEvent event) {
        closeCurrent();
    }

    private static void closeCurrent() {
        ServerState state = current;
        current = null;
        if (state != null) {
            closeState(state);
        }
        DatapackBlueprints.clearPending();
    }

    public static void unload(LevelEvent.Unload event) {
        ServerState state = current;
        if (state == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ResourceKey<Level> key = level.dimension();
        LevelStore store = state.levels.get(key);
        if (store != null) {
            store.open = false;
            WatchEvents.dispatch(store.structures(), new StructureEvent.Unloaded(state.world(level)));
            store.claims().retire();
        }
        LevelCapture capture = state.captures.remove(key);
        if (capture != null) {
            capture.close();
        }
        LevelWatch.levelUnloaded(level);
    }

    private static void closeState(ServerState state) {
        state.blueprints.close();
        state.closeCaptures();
        LevelWatch.clear();
        for (LevelStore store : state.levels.values()) {
            store.claims().retire();
        }
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

    /** Every blueprint id of the merged source, sorted; a datapack document overrides the built-in one. */
    List<NamespacedId> documentIds() {
        TreeSet<NamespacedId> ids = new TreeSet<>(BuiltinDocuments.blueprintIds());
        ids.addAll(blueprints.documents().keySet());
        return List.copyOf(ids);
    }

    @Nullable
    StructureSetDocument structureSet(NamespacedId id) {
        return documents.findSet(id);
    }

    StructureSetCompileResult compileSet(StructureSetDocument document) {
        Objects.requireNonNull(document, "document");
        CompilerContext context = new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            hostTypes,
            documents
        );
        return StructureSetCompiler.compile(document, context);
    }

    StructureKernel kernel(ServerLevel level) {
        return require(level).kernel();
    }

    ClaimLedger claims(ServerLevel level) {
        return require(level).claims();
    }

    StructureManager structures(ServerLevel level) {
        return require(level).structures();
    }

    /**
     * Installs the host's nested-extent lookup; the default is {@link NestedExtentLookup#capabilities()}.
     * Open captures are closed because a capture keeps the lookup it was built with.
     */
    public void installNestedExtentLookup(NestedExtentLookup lookup) {
        this.nestedExtentLookup = Objects.requireNonNull(lookup, "lookup");
        closeCaptures();
    }

    LevelCapture capture(ServerLevel level) {
        ResourceKey<Level> key = level.dimension();
        LevelCapture capture = captures.get(key);
        if (capture != null && capture.level() == level) {
            return capture;
        }
        if (capture != null) {
            capture.close();
        }
        LevelCapture created = new LevelCapture(level, nestedExtentLookup, traitLookup);
        captures.put(key, created);
        return created;
    }

    void closeCaptures() {
        captures.values().forEach(LevelCapture::close);
        captures.clear();
    }

    World world(ServerLevel level) {
        return World.of(worldId(level));
    }

    OwnerId owner() {
        return serverOwner;
    }

    /** Creates this level's state; the only creation point, driven by {@code LevelEvent.Load}. */
    void openLevel(ServerLevel level) {
        // the preference store lives in the level's data storage: warm it inside this lifecycle
        PreferencesSavedData.get(level);
        LevelStore store = levels.computeIfAbsent(level.dimension(), ignored -> {
            World world = World.of(worldId(level));
            ClaimLedger claims = ClaimLedger.inMemory(world);
            InstanceIndex index = new InstanceIndex();
            StructureManager structures = new IndexedStructureManager(StructureManager.create(), index);
            Host host = new Host(documents, claims, structures, hostTypes);
            return new LevelStore(claims, structures, StructureKernel.create(catalogs, host), index);
        });
        store.open = true;
    }

    /** The funnel's fast-path lookup; null when the level has no open store. */
    @Nullable
    InstanceIndex index(ServerLevel level) {
        LevelStore store = existing(level);
        return store == null || !store.open ? null : store.index();
    }

    private @Nullable LevelStore existing(ServerLevel level) {
        return levels.get(level.dimension());
    }

    private LevelStore require(ServerLevel level) {
        LevelStore store = existing(level);
        if (store == null || !store.open) {
            throw new IllegalStateException("no structure state for " + worldId(level));
        }
        return store;
    }

    private static NamespacedId worldId(ServerLevel level) {
        var location = level.dimension().location();
        return NamespacedId.of(location.getNamespace(), location.getPath());
    }

    private static final class LevelStore {

        private final ClaimLedger claims;
        private final StructureManager structures;
        private final StructureKernel kernel;
        private final InstanceIndex index;
        private boolean open;

        private LevelStore(
            ClaimLedger claims,
            StructureManager structures,
            StructureKernel kernel,
            InstanceIndex index
        ) {
            this.claims = claims;
            this.structures = structures;
            this.kernel = kernel;
            this.index = index;
            this.open = true;
        }

        private ClaimLedger claims() {
            return claims;
        }

        private StructureManager structures() {
            return structures;
        }

        private StructureKernel kernel() {
            return kernel;
        }

        private InstanceIndex index() {
            return index;
        }
    }

    /**
     * Reads this level's formed instances and persisted claim rows without mutating either. The
     * snapshot is taken on the server thread; the client only ever sees a serialized copy.
     */
    public VisualSnapshot visualSnapshot(ServerLevel level) {
        LevelStore store = require(level);
        StructureManager structures = store.structures();
        List<VisualInstance> instances = new ArrayList<>();
        for (StructureInstance instance : structures.instances()) {
            StructureContext context = structures.context(instance.id());
            instances.add(
                new VisualInstance(
                    instance.id(),
                    instance.owner(),
                    instance.siting(),
                    instance.bounds(),
                    context.revision(),
                    context.spend(),
                    context.findings().findings().stream().map(finding -> finding.code().text()).toList(),
                    context.values().keySet().stream().map(key -> key.id().text()).toList(),
                    context.values().size()
                )
            );
        }
        ClaimView view = store.claims().read(fullLattice());
        List<VisualClaim> claims = new ArrayList<>(view.rows().size());
        for (var row : view.rows()) {
            StructureInstance owner = structures.find(row.instance());
            if (owner == null) {
                // A row whose instance is gone has no owner to show.
                continue;
            }
            claims.add(new VisualClaim(row.cell(), row.sharing(), owner.owner(), row.siting(), row.bay()));
        }
        return new VisualSnapshot(world(level).id(), level.getGameTime(), view.revision(), instances, claims);
    }

    private static Cuboid fullLattice() {
        return Cuboid.of(
            LatticePoint.minXz,
            LatticePoint.minY,
            LatticePoint.minXz,
            LatticePoint.maxXz,
            LatticePoint.maxY,
            LatticePoint.maxXz
        );
    }
}
