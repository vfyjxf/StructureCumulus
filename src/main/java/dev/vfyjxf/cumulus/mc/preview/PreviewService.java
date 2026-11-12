package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.StructureCumulus;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.mc.LevelAccess;
import dev.vfyjxf.cumulus.mc.build.ItemSource;
import dev.vfyjxf.cumulus.mc.pref.BuildPreset;
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
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("a preview needs a player"));
            StructureCumulus.logger.info("preview {} refused: the command source has no player", id.text());
            return 0;
        }
        Opened opened = openFor(player, id, origin);
        if (!opened.opened()) {
            source.sendFailure(Component.literal(opened.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(opened.message()), false);
        return 1;
    }

    /** The JEI "ghost to world" entry: the crosshair supplies the origin and chat carries the outcome. */
    public static void open(ServerPlayer player, NamespacedId id) {
        player.sendSystemMessage(Component.literal(openFor(player, id, null).message()));
    }

    private record Opened(boolean opened, String message) {}

    private static Opened failed(String message) {
        return new Opened(false, message);
    }

    /** The compiled blueprint of one id, or the refusal message the open path reports. */
    private record Loaded(@Nullable Blueprint blueprint, String failure) {}

    private static Loaded load(NamespacedId id) {
        StructureKernel kernel = LevelAccess.kernel();
        if (kernel == null) {
            StructureCumulus.logger.info("preview {} refused: the server state is not open", id.text());
            return new Loaded(null, "StructureCumulus is not running");
        }
        Document document = kernel.document(id);
        if (document == null) {
            StructureCumulus.logger.info("preview {} refused: no document of that id", id.text());
            return new Loaded(null, "no document " + id.text());
        }
        CompileResult compiled = kernel.compile(document);
        Blueprint blueprint = compiled.blueprint();
        if (blueprint == null) {
            StructureCumulus.logger.info(
                "preview {} refused: compile reported {} finding(s), first: {}",
                id.text(),
                compiled.findings().findings().size(),
                compiled.findings().findings().isEmpty() ? "none" : compiled.findings().findings().get(0).message()
            );
            return new Loaded(null, "compile " + id.text() + " failed");
        }
        return new Loaded(blueprint, "");
    }

    private static Opened openFor(ServerPlayer player, NamespacedId id, @Nullable LatticePoint origin) {
        Loaded loaded = load(id);
        Blueprint blueprint = loaded.blueprint();
        if (blueprint == null) {
            return failed(loaded.failure());
        }
        List<LatticePoint> origins = origins(player, origin);
        if (origins.isEmpty()) {
            StructureCumulus.logger.info("preview {} refused: no origin candidate", id.text());
            return failed("look at a block, or pass an origin");
        }
        PreviewSession previous = sessions.remove(key(player));
        if (previous != null) {
            previous.close();
        }
        PreviewSession.OpenResult opened = PreviewSession.open(player, blueprint, origins, player.getDirection());
        PreviewSession session = opened.session();
        if (session == null) {
            StructureCumulus.logger.info("preview {} refused: {}", id.text(), opened.failure());
            return failed("preview " + id.text() + ": " + opened.failure());
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
        return new Opened(
            true,
            "preview " + id.text() + " at " + origins.get(0) + ": " + candidateCount + " candidate(s)"
        );
    }

    /**
     * {@code /cumulus build preset preview <name>}: the ordinary open pinned to the preset's stored pose
     * and source, so a preview shows exactly what the matching {@code apply} would build (design 15
     * §13.3). Cycling is refused inside the session; a variant switch is still the ordinary re-open.
     */
    public static int openAt(CommandSourceStack source, BuildPreset preset, ItemSource items) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("a preview needs a player"));
            StructureCumulus.logger.info("preset preview {} refused: the command source has no player", preset.name());
            return 0;
        }
        Loaded loaded = load(preset.blueprintId());
        Blueprint blueprint = loaded.blueprint();
        if (blueprint == null) {
            source.sendFailure(Component.literal(loaded.failure()));
            return 0;
        }
        PreviewSession previous = sessions.remove(key(player));
        if (previous != null) {
            previous.close();
        }
        PreviewSession.OpenResult opened = PreviewSession.openPreset(player, blueprint, preset, items);
        PreviewSession session = opened.session();
        if (session == null) {
            StructureCumulus.logger.info("preset preview {} refused: {}", preset.name(), opened.failure());
            source.sendFailure(Component.literal("preview preset " + preset.name() + ": " + opened.failure()));
            return 0;
        }
        sessions.put(key(player), session);
        String pose = BuildPreset.pose(preset.siting());
        StructureCumulus.logger.info(
            "preset preview {} opened for {}: {} at {}",
            preset.name(),
            player.getGameProfile().getName(),
            preset.blueprintId().text(),
            preset.siting().origin()
        );
        source.sendSuccess(
            () -> Component.literal(
                "preview preset " + preset.name() + ": " + preset.blueprintId().text() + " at " + pose
                        + ", cycling is off"
            ),
            false
        );
        return 1;
    }

    /** The context a preset save copies, or null when the player has no live session. */
    public static @Nullable PreviewContext context(ServerPlayer player) {
        PreviewSession session = sessions.get(key(player));
        return session == null ? null : session.context();
    }

    /**
     * One control from the client; a session that cannot recompute is closed. A variant switch is the
     * session's ordinary open with the next member at the same origin: the new id brings its own
     * preference memory, and nothing else about the session survives (design 15 §12.2).
     */
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
            NamespacedId next = session.variantSwitch();
            LatticePoint origin = next == null ? null : session.siting().origin();
            session.close();
            if (next != null && origin != null) {
                Opened opened = openFor(player, next, origin);
                if (!opened.opened()) {
                    player.sendSystemMessage(Component.literal(opened.message()));
                }
            }
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
