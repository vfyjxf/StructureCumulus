package dev.vfyjxf.cumulus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
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
        logger.info("{} loading", Constants.modId);
    }
}
