package dev.vfyjxf.cumulus.mc;

import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Lifecycle;
import dev.vfyjxf.cumulus.StructureCumulus;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.net.Proxy;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * One real dedicated server booted by a JUnit test: a flat offline overworld in a throwaway
 * directory, driven through the same chain as {@code net.minecraft.server.Main}.
 */
@NullMarked
final class HostServer implements AutoCloseable {

    private static final long readyTimeoutNanos = TimeUnit.MINUTES.toNanos(3);

    private final DedicatedServer server;
    private final Path directory;

    private HostServer(DedicatedServer server, Path directory) {
        this.server = server;
        this.directory = directory;
    }

    static HostServer boot() throws Exception {
        Path directory = Files.createTempDirectory("cumulus-host-laws");
        DedicatedServerSettings settings = new DedicatedServerSettings(writeProperties(directory));
        settings.forceSave();
        DedicatedServerProperties properties = settings.getProperties();
        RegionFileVersion.configure(properties.regionFileComression);
        Services services = Services.create(new YggdrasilAuthenticationService(Proxy.NO_PROXY), directory.toFile());
        LevelStorageSource storage = LevelStorageSource.createDefault(directory);
        LevelStorageSource.LevelStorageAccess access = storage.validateAndCreateAccess("host_laws");
        PackRepository packs = ServerPacksSource.createPackRepository(access);
        WorldLoader.InitConfig init = new WorldLoader.InitConfig(
            new WorldLoader.PackConfig(
                packs,
                new WorldDataConfiguration(properties.initialDataPackConfiguration, FeatureFlags.DEFAULT_FLAGS),
                false,
                true
            ),
            Commands.CommandSelection.DEDICATED,
            properties.functionPermissionLevel
        );
        DataFixer fixer = DataFixers.getDataFixer();
        WorldStem stem = Util.<WorldStem>blockUntilDone(executor -> WorldLoader.load(init, context -> {
            Registry<LevelStem> stems = context.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM);
            LevelSettings levelSettings = new LevelSettings(
                properties.levelName,
                properties.gamemode,
                properties.hardcore,
                properties.difficulty,
                false,
                new GameRules(),
                context.dataConfiguration()
            );
            WorldDimensions dimensions = roundTrip(
                properties.createDimensions(context.datapackWorldgen()),
                context.datapackWorldgen()
            );
            WorldDimensions.Complete complete = dimensions.bake(stems);
            Lifecycle lifecycle = complete.lifecycle().add(context.datapackWorldgen().allRegistriesLifecycle());
            return new WorldLoader.DataLoadOutput<>(
                new PrimaryLevelData(
                    levelSettings,
                    properties.worldOptions,
                    complete.specialWorldProperty(),
                    lifecycle
                ),
                complete.dimensionsRegistryAccess()
            );
        }, WorldStem::new, Util.backgroundExecutor(), executor)).get();
        access.saveDataTag(stem.registries().compositeAccess(), stem.worldData());
        DedicatedServer booted = MinecraftServer.spin(
            thread -> new DedicatedServer(
                thread,
                access,
                packs,
                stem,
                settings,
                fixer,
                services,
                LoggerChunkProgressListener::createFromGameruleRadius
            )
        );
        waitForReady(booted);
        return new HostServer(booted, directory);
    }

    ServerLevel overworld() {
        return Objects.requireNonNull(live().overworld(), "the overworld never loaded");
    }

    /** The host contract is server-thread only; every law runs inside this submit. */
    void onServer(Task action) throws Exception {
        live().submit(() -> {
            try {
                action.run();
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("the server-thread action failed", failure);
            }
        }).get(1, TimeUnit.MINUTES);
    }

    interface Task {
        void run() throws Exception;
    }

    @Override
    public void close() {
        server.halt(true);
        delete(directory);
    }

    private DedicatedServer live() {
        MinecraftServer.throwIfFatalException();
        return server;
    }

    private static void waitForReady(DedicatedServer server) throws InterruptedException {
        long deadline = System.nanoTime() + readyTimeoutNanos;
        while (!server.isReady()) {
            MinecraftServer.throwIfFatalException();
            if (!server.isRunning()) {
                throw new IllegalStateException("the server thread died before becoming ready");
            }
            if (System.nanoTime() - deadline > 0) {
                throw new IllegalStateException("the server was not ready within three minutes");
            }
            Thread.sleep(100);
        }
    }

    private static Path writeProperties(Path directory) throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path file = directory.resolve("server.properties");
        String properties = """
                online-mode=false
                server-port=%d
                level-name=host_laws
                level-type=minecraft\\:flat
                generate-structures=false
                allow-nether=false
                difficulty=peaceful
                spawn-animals=false
                spawn-npcs=false
                spawn-protection=0
                enable-status=false
                enable-query=false
                enable-rcon=false
                sync-chunk-writes=false
                max-tick-time=-1
                view-distance=4
                simulation-distance=5
                """.formatted(port);
        Files.writeString(file, properties);
        return file;
    }

    /** NeoForge's modded-dimension injection step from Main, kept so the boot matches production. */
    private static WorldDimensions roundTrip(WorldDimensions dimensions, RegistryAccess.Frozen worldgen) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, worldgen);
        return WorldDimensions.CODEC.encoder().encodeStart(ops, dimensions)
                .flatMap(written -> WorldDimensions.CODEC.decoder().parse(ops, written))
                .resultOrPartial(error -> StructureCumulus.logger.warn("dimension round trip failed: {}", error))
                .orElse(dimensions);
    }

    private static void delete(Path directory) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(HostServer::deleteOne);
                return;
            } catch (IOException failure) {
                sleepQuietly(200);
            }
        }
    }

    private static void deleteOne(Path path) {
        try {
            Files.delete(path);
        } catch (IOException failure) {
            // a held region file leaves with the temp directory when the OS reclaims it
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        }
    }
}
