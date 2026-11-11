package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The build entry of a confirmed preview. P5 wires the real executor; until then the placeholder
 * reports the call the executor would receive and accepts nothing.
 */
@FunctionalInterface
public interface PreviewConfirmHandler {

    /** The siting the user confirmed, plus the finding codes the overlay carried. */
    record Request(NamespacedId blueprintId, Siting siting, List<String> codes) {

        public Request {
            codes = List.copyOf(codes);
        }
    }

    /** True when the build started, which closes the session; false keeps the plan for another try. */
    boolean confirm(ServerPlayer player, Request request);
}
