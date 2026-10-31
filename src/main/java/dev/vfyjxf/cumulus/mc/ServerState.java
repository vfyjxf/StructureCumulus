package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.claim.ClaimView;
import dev.vfyjxf.cumulus.kernel.context.StructureContext;
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
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.spi.Host;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentLookup;
import dev.vfyjxf.cumulus.mc.trait.TraitLookup;
import dev.vfyjxf.cumulus.mc.visual.VisualClaim;
import dev.vfyjxf.cumulus.mc.visual.VisualInstance;
import dev.vfyjxf.cumulus.mc.visual.VisualSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The server-side owner of the kernel and the per-level capture and persistence state. */
@NullMarked
public final class ServerState {

    private static final OwnerId serverOwner = OwnerId.of(NamespacedId.of(Constants.modId, "server"));

    private static @Nullable ServerState current;

    private final Catalogs catalogs;
    private final StructureKernel kernel;
    private final DatapackBlueprints blueprints;
    private final DocumentSource documents;
    private final TraitLookup traitLookup;
    private NestedExtentLookup nestedExtentLookup = NestedExtentLookup.capabilities();
    private final Map<ServerLevel, LevelStore> levels = new HashMap<>();
    private final Map<ServerLevel, LevelCapture> captures = new HashMap<>();

    private ServerState() {
        this.catalogs = Catalogs.builder().expectations(AdapterExpectations.catalog()).codes(AdapterCodes.catalog())
                .build();
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
        this.kernel = StructureKernel.create(catalogs, new Host(documents, null, StructureManager.create(), Map.of()));
        this.traitLookup = TraitLookup.load(StructureCumulus.logger);
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
        LevelWatch.clear();
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
            Map.of(),
            documents
        );
        return StructureSetCompiler.compile(document, context);
    }

    StructureKernel kernel(ServerLevel level) {
        return store(level).kernel();
    }

    SavedClaims claims(ServerLevel level) {
        return store(level).claims();
    }

    StructureManager structures(ServerLevel level) {
        return store(level).structures();
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
        return captures.computeIfAbsent(level, attached -> new LevelCapture(attached, nestedExtentLookup, traitLookup));
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

    private LevelStore store(ServerLevel level) {
        return levels.computeIfAbsent(level, attached -> {
            World world = World.of(worldId(attached));
            SavedClaims claims = attached.getDataStorage()
                    .computeIfAbsent(SavedClaims.factory(world), SavedClaims.dataName);
            StructureManager structures = StructureManager.create();
            Host host = new Host(documents, claims, structures, Map.of());
            return new LevelStore(claims, structures, StructureKernel.create(catalogs, host));
        });
    }

    private static NamespacedId worldId(ServerLevel level) {
        var location = level.dimension().location();
        return NamespacedId.of(location.getNamespace(), location.getPath());
    }

    private record LevelStore(SavedClaims claims, StructureManager structures, StructureKernel kernel) {}

    /**
     * Reads this level's formed instances and persisted claim rows without mutating either. The
     * snapshot is taken on the server thread; the client only ever sees a serialized copy.
     */
    public VisualSnapshot visualSnapshot(ServerLevel level) {
        LevelStore store = store(level);
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
            claims.add(new VisualClaim(row.cell(), row.sharing(), row.owner(), row.siting(), row.bay()));
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
