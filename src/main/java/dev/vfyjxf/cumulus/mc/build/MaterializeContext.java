package dev.vfyjxf.cumulus.mc.build;

import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/** The host facts a materializer may read. */
public record MaterializeContext(ServerLevel level) {

    public MaterializeContext {
        Objects.requireNonNull(level, "level");
    }
}
