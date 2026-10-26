package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.context.ContextStore;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.World;
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

/** The M1 server-side owner of the kernel and the per-level capture cache. */
@NullMarked
public final class ServerState {

    private static final OwnerId serverOwner = OwnerId.of(NamespacedId.of(Constants.modId, "server"));

    private static @Nullable ServerState current;

    private final StructureKernel kernel;
    private final Map<ServerLevel, LevelCapture> captures = new HashMap<>();
    private long nextInstance = 1;

    private ServerState() {
        this.kernel = StructureKernel.create(Catalogs.builtin(), host());
    }

    public static void open(ServerAboutToStartEvent event) {
        current = new ServerState();
    }

    public static void close(ServerStoppingEvent event) {
        ServerState state = current;
        current = null;
        if (state != null) {
            state.captures.clear();
        }
    }

    public static void unload(LevelEvent.Unload event) {
        ServerState state = current;
        if (state != null && event.getLevel() instanceof ServerLevel level) {
            state.captures.remove(level);
        }
    }

    public static @Nullable ServerState current() {
        return current;
    }

    StructureKernel kernel() {
        return kernel;
    }

    LevelCapture capture(ServerLevel level) {
        return captures.computeIfAbsent(level, LevelCapture::new);
    }

    InstanceId instance(ServerLevel level) {
        return new InstanceId(World.of(world(level)), nextInstance++);
    }

    OwnerId owner() {
        return serverOwner;
    }

    private static Host host() {
        return new Host(BuiltinDocuments.source(), null, ContextStore.inMemory(), Map.of());
    }

    private static NamespacedId world(ServerLevel level) {
        var location = level.dimension().location();
        return NamespacedId.of(location.getNamespace(), location.getPath());
    }
}
