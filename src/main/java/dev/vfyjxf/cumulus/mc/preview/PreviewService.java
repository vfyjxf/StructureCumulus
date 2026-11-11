package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.mc.LevelAccess;
import dev.vfyjxf.cumulus.mc.preview.net.PreviewControlPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server's preview sessions: one per {@code (player, level)}, opened by the command or the
 * crosshair, invalidated by a world change inside the footprint and re-synced on every control.
 * Nothing on this path writes the world; only confirm leaves the session.
 */
public final class PreviewService {

    /** The crosshair reach of an originless open, in blocks (1.1). */
    private static final double reach = 24.0D;
    /** The heartbeat keeps the client's session alive without a close payload. */
    private static final long heartbeatTicks = 20L;

    private static final Map<Key, PreviewSession> sessions = new HashMap<>();
    private static PreviewConfirmHandler confirmHandler = new PreviewBuilds();

    private PreviewService() {}

    /** Replaces the built-in build handler; a host installs its own executor here. */
    public static void installConfirmHandler(PreviewConfirmHandler handler) {
        confirmHandler = handler;
    }

    static PreviewConfirmHandler confirmHandler() {
        return confirmHandler;
    }

    /** The session-opening entry: {@code /cumulus preview <id> [origin]} and its dev twin call this. */
    public static int open(CommandSourceStack source, NamespacedId id, @Nullable LatticePoint origin) {
        StructureKernel kernel = LevelAccess.kernel();
        if (kernel == null) {
            source.sendFailure(Component.literal("StructureCumulus is not running"));
            StructureCumulus.logger.info("preview {} refused: the server state is not open", id.text());
            return 0;
        }
        Blueprint blueprint = blueprint(source, kernel, id);
        if (blueprint == null) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("a preview needs a player"));
            StructureCumulus.logger.info("preview {} refused: the command source has no player", id.text());
            return 0;
        }
        List<LatticePoint> origins = origins(player, origin);
        if (origins.isEmpty()) {
            source.sendFailure(Component.literal("look at a block, or pass an origin"));
            StructureCumulus.logger.info("preview {} refused: no origin candidate", id.text());
            return 0;
        }
        PreviewSession previous = sessions.remove(key(player));
        if (previous != null) {
            previous.close();
        }
        PreviewSession.OpenResult opened = PreviewSession.open(player, blueprint, origins, player.getDirection());
        PreviewSession session = opened.session();
        if (session == null) {
            source.sendFailure(Component.literal("preview " + id.text() + ": " + opened.failure()));
            StructureCumulus.logger.info("preview {} refused: {}", id.text(), opened.failure());
            return 0;
        }
        sessions.put(key(player), session);
        StructureCumulus.logger.info(
            "preview {} opened for {}: {} candidate(s) at {}",
            id.text(),
            player.getGameProfile().getName(),
            session.payload().candidates().size(),
            origins.get(0)
        );
        int candidateCount = session.payload().candidates().size();
        source.sendSuccess(
            () -> Component.literal(
                "preview " + id.text() + " at " + origins.get(0) + ": " + candidateCount + " candidate(s)"
            ),
            false
        );
        return 1;
    }

    /** One control from the client; a session that cannot recompute is closed. */
    public static void control(
        ServerPlayer player,
        PreviewControlPayload.Control control,
        @Nullable LatticePoint target
    ) {
        PreviewSession session = sessions.get(key(player));
        if (session == null) {
            return;
        }
        if (!session.apply(player, control, target)) {
            sessions.remove(key(player));
            session.close();
        }
    }

    /** The server tick: invalidate on a footprint change, retract on timeout, drop dead sessions. */
    static void tick(MinecraftServer server) {
        if (sessions.isEmpty()) {
            return;
        }
        List<Key> dropped = new ArrayList<>();
        for (Map.Entry<Key, PreviewSession> entry : sessions.entrySet()) {
            PreviewSession session = entry.getValue();
            ServerPlayer player = session.level().getServer().getPlayerList().getPlayer(entry.getKey().player());
            if (player == null) {
                dropped.add(entry.getKey());
                continue;
            }
            if (!session.isValid()) {
                if (!session.refresh(true)) {
                    dropped.add(entry.getKey());
                }
                continue;
            }
            long idle = Math.max(0L, session.level().getGameTime() - session.syncedAt());
            if (!session.retracted() && idle > PreviewSession.retractAfterTicks) {
                session.retract();
            } else if (idle >= heartbeatTicks && idle % heartbeatTicks == 0L) {
                session.sync();
            }
        }
        for (Key key : dropped) {
            PreviewSession session = sessions.remove(key);
            if (session != null) {
                session.close();
            }
        }
    }

    /** A level going away closes its sessions; the client drops its ghost on the first missing sync. */
    static void levelUnloaded(ServerLevel level) {
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().level() != level) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
    }

    static void playerLoggedOut(ServerPlayer player) {
        PreviewSession session = sessions.remove(key(player));
        if (session != null) {
            session.close();
        }
    }

    static void closeAll() {
        for (PreviewSession session : sessions.values()) {
            session.close();
        }
        sessions.clear();
    }

    static int sessionCount() {
        return sessions.size();
    }

    static @Nullable PreviewSession session(ServerPlayer player) {
        return sessions.get(key(player));
    }

    private static Key key(ServerPlayer player) {
        return new Key(player.getUUID(), player.serverLevel().dimension());
    }

    private static @Nullable Blueprint blueprint(CommandSourceStack source, StructureKernel kernel, NamespacedId id) {
        Document document = kernel.document(id);
        if (document == null) {
            source.sendFailure(Component.literal("no document " + id.text()));
            StructureCumulus.logger.info("preview {} refused: no document of that id", id.text());
            return null;
        }
        CompileResult compiled = kernel.compile(document);
        Blueprint blueprint = compiled.blueprint();
        if (blueprint == null) {
            source.sendFailure(Component.literal("compile " + id.text() + " failed"));
            StructureCumulus.logger.info(
                "preview {} refused: compile reported {} finding(s), first: {}",
                id.text(),
                compiled.findings().findings().size(),
                compiled.findings().findings().isEmpty() ? "none" : compiled.findings().findings().get(0).message()
            );
        }
        return blueprint;
    }

    /**
     * 1.1: the crosshair hit is the first origin candidate and its face adds a place-against-the-face
     * candidate; an explicit origin is the only candidate.
     */
    private static List<LatticePoint> origins(ServerPlayer player, @Nullable LatticePoint explicit) {
        if (explicit != null) {
            return List.of(explicit);
        }
        if (!(player.pick(reach, 0.0F, false) instanceof BlockHitResult hit)) {
            return List.of();
        }
        HitResult.Type type = hit.getType();
        if (type != HitResult.Type.BLOCK) {
            return List.of();
        }
        BlockPos pos = hit.getBlockPos();
        List<LatticePoint> origins = new ArrayList<>(2);
        if (LatticePoint.inLattice(pos.getX(), pos.getY(), pos.getZ())) {
            origins.add(LatticePoint.of(pos.getX(), pos.getY(), pos.getZ()));
        }
        Direction face = hit.getDirection();
        BlockPos against = pos.relative(face);
        if (LatticePoint.inLattice(against.getX(), against.getY(), against.getZ())) {
            LatticePoint candidate = LatticePoint.of(against.getX(), against.getY(), against.getZ());
            if (!origins.contains(candidate)) {
                origins.add(candidate);
            }
        }
        return List.copyOf(origins);
    }

    private record Key(UUID player, ResourceKey<Level> level) {}
}
