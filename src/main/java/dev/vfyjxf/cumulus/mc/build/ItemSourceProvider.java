package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** One item-source kind: the source for the player and level that asked for it. */
public interface ItemSourceProvider {

    NamespacedId id();

    /** Answers the source for this request, or null when the backend cannot serve it right now. */
    @Nullable
    ItemSource create(ServerPlayer player, ServerLevel level);
}
