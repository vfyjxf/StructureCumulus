package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.space.Heading;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/** {@code config/structurecumulus-preferences.toml}: the server's preference defaults and bounds. */
public final class PrefConfig {

    /** The default scoring order; {@code align} joins it at P8. */
    public static final List<String> defaultWeightKeys = List
            .of("facing_player", "most_progress", "fewest_removals", "nearest_to_player", "remembered", "support");

    private static final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

    private static final ModConfigSpec.EnumValue<OrientationMode> orientationMode = builder
            .comment("The starting pose of an anchorless preview: autoAnchor, facePlayer or fixedHeading.")
            .defineEnum("defaults.orientationMode", OrientationMode.autoAnchor);

    private static final ModConfigSpec.EnumValue<Heading> fixedHeading = builder
            .comment("The heading fixed_heading starts from: north, south, east, west, up or down.")
            .defineEnum("defaults.fixedHeading", Heading.north);

    private static final ModConfigSpec.EnumValue<RemovalPolicy> removalPolicy = builder
            .comment("What a build may remove: forbid, ask (the server gate decides) or allow.")
            .defineEnum("defaults.removalPolicy", RemovalPolicy.forbid);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> weights = builder
            .comment("The ordered siting weights; a weight that is left out is not scored.")
            .defineList("defaults.weights", defaultWeightKeys, () -> defaultWeightKeys.get(0), PrefConfig::isWeight);

    private static final ModConfigSpec.IntValue rememberedCapacity = builder
            .comment("How many blueprints keep a choice history per player; the oldest is evicted first.")
            .defineInRange("rememberedCapacity", BuildPreferences.defaultRememberedCapacity, 1, 4096);

    public static final ModConfigSpec spec = builder.build();

    /** The configured defaults, with an empty memory. */
    public static BuildPreferences defaults() {
        try {
            List<PlacementWeight> parsed = parse(weights.get());
            return BuildPreferences.of(orientationMode.get(), fixedHeading.get(), removalPolicy.get(), parsed);
        } catch (IllegalStateException notLoaded) {
            // a config that has not loaded yet answers the shipped defaults
            return BuildPreferences
                    .of(OrientationMode.autoAnchor, Heading.north, RemovalPolicy.forbid, parse(defaultWeightKeys));
        }
    }

    public static int rememberedCapacity() {
        try {
            return rememberedCapacity.getAsInt();
        } catch (IllegalStateException notLoaded) {
            return BuildPreferences.defaultRememberedCapacity;
        }
    }

    private static List<PlacementWeight> parse(List<? extends String> keys) {
        List<PlacementWeight> parsed = new ArrayList<>();
        for (String key : keys) {
            PlacementWeight weight = PlacementWeight.parse(key);
            if (weight != null && !parsed.contains(weight)) {
                parsed.add(weight);
            }
        }
        return List.copyOf(parsed);
    }

    private static boolean isWeight(Object element) {
        return element instanceof String key && PlacementWeight.parse(key) != null;
    }

    private PrefConfig() {}
}
