package dev.vfyjxf.cumulus;

import dev.vfyjxf.cumulus.mc.CumulusCommand;
import dev.vfyjxf.cumulus.mc.DatapackBlueprints;
import dev.vfyjxf.cumulus.mc.ServerState;
import dev.vfyjxf.cumulus.mc.WatchEvents;
import dev.vfyjxf.cumulus.mc.build.BuildConfig;
import dev.vfyjxf.cumulus.mc.build.BuildHistory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod entry point: FML constructs it once while loading, on both the client and the
 * dedicated server.
 */
@Mod(Constants.modId)
public final class StructureCumulus {

    /** The mod's one logger. */
    public static final Logger logger = LoggerFactory.getLogger("StructureCumulus");

    public StructureCumulus(ModContainer container, IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(DatapackBlueprints::addReloadListener);
        NeoForge.EVENT_BUS.addListener(ServerState::open);
        NeoForge.EVENT_BUS.addListener(ServerState::close);
        // ServerStoppingEvent is skipped when the server thread crashes; ServerStoppedEvent is not.
        NeoForge.EVENT_BUS.addListener(ServerState::stopped);
        NeoForge.EVENT_BUS.addListener(ServerState::unload);
        NeoForge.EVENT_BUS.addListener(CumulusCommand::register);
        // The undo history is in-memory: a level unload or a server stop ends what it can describe.
        NeoForge.EVENT_BUS.addListener(BuildHistory::levelUnloaded);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> BuildHistory.forgetAll());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> BuildHistory.forgetAll());
        container.registerConfig(ModConfig.Type.SERVER, BuildConfig.spec);
        WatchEvents.register();
        logger.info("{} loading", Constants.modId);
    }
}
