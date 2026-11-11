package dev.vfyjxf.cumulus.mc.preview.jei;

import dev.vfyjxf.cumulus.Constants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The client setup gate of the JEI integration. The subscriber loads on the physical client only, and
 * the loader pass behind it returns before resolving a JEI type when JEI is absent (D-90).
 */
@EventBusSubscriber(modid = Constants.modId, value = Dist.CLIENT)
public final class JeiPreviewClientEvents {

    private JeiPreviewClientEvents() {}

    @SubscribeEvent
    private static void onClientSetup(FMLClientSetupEvent event) {
        JeiPreviewPlugins.load();
    }
}
