package dev.vfyjxf.cumulus.mc.build;

/** The host's gate: who may build, how far, and whether a plan may remove blocks. */
public record BuildPolicy(int permissionLevel, long maxTouchedVolume, boolean allowRemoval) {

    public static final int defaultPermissionLevel = 2;
    public static final long defaultMaxTouchedVolume = 4096;
    public static final boolean defaultAllowRemoval = false;

    public BuildPolicy {
        if (permissionLevel < 0 || permissionLevel > 4) {
            throw new IllegalArgumentException("permissionLevel must be 0..4");
        }
        if (maxTouchedVolume < 0) {
            throw new IllegalArgumentException("maxTouchedVolume >= 0");
        }
    }

    public static BuildPolicy defaults() {
        return new BuildPolicy(defaultPermissionLevel, defaultMaxTouchedVolume, defaultAllowRemoval);
    }

    public BuildPolicy withAllowRemoval(boolean allowed) {
        return new BuildPolicy(permissionLevel, maxTouchedVolume, allowed);
    }

    public BuildPolicy withMaxTouchedVolume(long volume) {
        return new BuildPolicy(permissionLevel, volume, allowRemoval);
    }
}
