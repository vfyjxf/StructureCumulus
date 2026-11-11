package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.mc.StructureSet;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** One set build: the placed set, one shared source, one gate and the dry/settle flags. */
public record SetBuildRequest(
    StructureSet set,
    ItemSource source,
    BuildPolicy policy,
    ServerPlayer player,
    boolean dry,
    boolean settle
) {

    public SetBuildRequest {
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(player, "player");
    }
}
