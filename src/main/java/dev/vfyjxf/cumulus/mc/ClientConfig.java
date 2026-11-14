package dev.vfyjxf.cumulus.mc;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code config/structurecumulus-client.toml}: the client's narration switches (design 16 §13). */
public final class ClientConfig {

    /** The one speed key's own range: the key's declared bounds and the slider's clamp (design 17 §7.5). */
    private static final double minSpeed = 0.25D;
    private static final double maxSpeed = 4.0D;

    private static final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue animationEnabled = builder
            .comment("Master switch for the reveal fade, the pop-in, the fade-out and the celebration.")
            .define("animation.enabled", true);

    private static final ModConfigSpec.DoubleValue animationSpeed = builder
            .comment("Divides every animation cadence; it changes no cap, no count and no order.")
            .defineInRange("animation.speed", 1.0D, minSpeed, maxSpeed);

    private static final ModConfigSpec.BooleanValue animationSound = builder
            .comment("The rate-limited place and break cues of a placement animation.").define("animation.sound", true);

    private static final ModConfigSpec.BooleanValue animationCelebrate = builder
            .comment("The formed celebration: one particle ring and one level-up cue per run.")
            .define("animation.celebrate", true);

    private static final ModConfigSpec.BooleanValue hudProgress = builder
            .comment("The total bar, the segmented bar and the materials bar; the stage line stays.")
            .define("hud.progress", true);

    private static final ModConfigSpec.BooleanValue hudStageBanner = builder
            .comment("The stage banner; the HUD's stage line stays when it is off.").define("hud.stageBanner", true);

    private static final ModConfigSpec.BooleanValue cinematicsEnabled = builder
            .comment("The cinema mode's C key; false leaves the key inert and the mode unreachable.")
            .define("cinematics.enabled", true);

    private static final ModConfigSpec.DoubleValue cinematicsSpeed = builder
            .comment("Scales the camera program's playback; it never changes a shot or an order.")
            .defineInRange("cinematics.speed", 1.0D, 0.25D, 4.0D);

    private static final ModConfigSpec.BooleanValue cinematicsLetterbox = builder
            .comment("The black bars of the cinema mode's frame.").define("cinematics.letterbox", true);

    public static final ModConfigSpec spec = builder.build();

    public static boolean animationEnabled() {
        try {
            return animationEnabled.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static double animationSpeed() {
        try {
            return animationSpeed.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0D;
        }
    }

    /** Writes the one speed key in memory; the screen's next frame reads it (design 17 §7.5, D-114). */
    public static void setAnimationSpeed(double speed) {
        if (!spec.isLoaded()) {
            return;
        }
        animationSpeed.set(Math.max(minSpeed, Math.min(maxSpeed, speed)));
    }

    /** Writes the one speed key to disk and fires the reload it owns (design 17 §7.5). */
    public static void saveAnimationSpeed() {
        if (spec.isLoaded()) {
            spec.save();
        }
    }

    public static boolean animationSound() {
        try {
            return animationSound.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static boolean animationCelebrate() {
        try {
            return animationCelebrate.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static boolean hudProgress() {
        try {
            return hudProgress.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static boolean hudStageBanner() {
        try {
            return hudStageBanner.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static boolean cinematicsEnabled() {
        try {
            return cinematicsEnabled.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static double cinematicsSpeed() {
        try {
            return cinematicsSpeed.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0D;
        }
    }

    public static boolean cinematicsLetterbox() {
        try {
            return cinematicsLetterbox.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    private ClientConfig() {}
}
