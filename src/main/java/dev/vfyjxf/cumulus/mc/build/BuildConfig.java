package dev.vfyjxf.cumulus.mc.build;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code config/structurecumulus-server.toml}: the server's build gate. */
public final class BuildConfig {

    private static final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue maxTouchedVolume = builder
            .comment("The most cells one build may touch; a larger plan is refused by the policy gate.")
            .defineInRange("maxTouchedVolume", (int) BuildPolicy.defaultMaxTouchedVolume, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue allowRemoval = builder
            .comment("Whether a build plan may remove blocks; the command's --allow-removal lifts it per run.")
            .define("allowRemoval", BuildPolicy.defaultAllowRemoval);

    public static final ModConfigSpec spec = builder.build();

    /** The server's configured gate. */
    public static BuildPolicy policy() {
        return new BuildPolicy(BuildPolicy.defaultPermissionLevel, maxTouchedVolume.getAsInt(), allowRemoval.get());
    }

    private BuildConfig() {}
}
