package dev.vfyjxf.cumulus.uitest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** The structurecumulusuitest mod's identity; it carries only the dev-only visual scenarios. */
@Mod(value = UiTestMod.modId, dist = Dist.CLIENT)
public final class UiTestMod {

    public static final String modId = "structurecumulusuitest";

    public UiTestMod(IEventBus modBus) {
        modBus.addListener(VisualPayloads::register);
        modBus.addListener(VisualHud::register);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> VisualClientState.tick());
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, VisualFixtureCommand::register);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, PreviewDevCommand::register);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, BuildDevCommand::register);
        VisualHighlights.register();
        VisualInspectHandler.register();
    }
}
