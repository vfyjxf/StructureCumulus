package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.SolvePreferences;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.mc.build.ItemSource;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;

/**
 * The build entry of a confirmed preview. The session hands over the siting, the item source and the
 * solve preferences it assembled; the handler is the executor flow of design 14 §10.
 */
@FunctionalInterface
public interface PreviewConfirmHandler {

    /**
     * The siting the user confirmed, the finding codes the overlay carried, the session's item source
     * and the cell-level preferences assembled for this siting.
     */
    record Request(
        NamespacedId blueprintId,
        Siting siting,
        List<String> codes,
        ItemSource source,
        SolvePreferences preferences
    ) {

        public Request {
            Objects.requireNonNull(blueprintId, "blueprintId");
            Objects.requireNonNull(siting, "siting");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(preferences, "preferences");
            codes = List.copyOf(codes);
        }
    }

    /** True when the build started, which closes the session; false keeps the plan for another try. */
    boolean confirm(ServerPlayer player, Request request);
}
