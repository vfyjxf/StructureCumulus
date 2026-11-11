package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.SolvePreferences;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** One build run: what to build, where, from what, under which gate. */
public record BuildRequest(
    NamespacedId blueprint,
    Siting siting,
    ItemSource source,
    BuildPolicy policy,
    boolean dry,
    @Nullable SolvePreferences preferences,
    ServerPlayer player
) {

    public BuildRequest {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(siting, "siting");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(player, "player");
    }
}
