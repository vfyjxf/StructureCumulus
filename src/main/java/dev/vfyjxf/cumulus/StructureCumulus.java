package dev.vfyjxf.cumulus;

import dev.vfyjxf.cumulus.mc.CumulusCommand;
import dev.vfyjxf.cumulus.mc.DatapackBlueprints;
import dev.vfyjxf.cumulus.mc.ServerState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
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
        NeoForge.EVENT_BUS.addListener(ServerState::unload);
        NeoForge.EVENT_BUS.addListener(CumulusCommand::register);
        logger.info("{} loading", Constants.modId);
    }
}
