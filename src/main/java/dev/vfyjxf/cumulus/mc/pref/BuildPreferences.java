package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One player's effective preferences: the siting mode, the removal policy, the ordered scoring
 * weights and the per-blueprint choice history. The value is immutable, and {@link #remember} returns
 * the bounded LRU successor.
 */
public record BuildPreferences(
    OrientationMode orientationMode,
    @Nullable Heading fixedHeading,
    RemovalPolicy removalPolicy,
    List<PlacementWeight> weights,
    Map<NamespacedId, ChoiceHistory> remembered
) {

    /** The default number of remembered blueprints; the server config can move it. */
    public static final int defaultRememberedCapacity = 64;

    public BuildPreferences {
        Objects.requireNonNull(orientationMode, "orientationMode");
        Objects.requireNonNull(removalPolicy, "removalPolicy");
        weights = List.copyOf(weights);
        remembered = Collections.unmodifiableMap(new LinkedHashMap<>(remembered));
    }

    /** The config's defaults, with an empty memory. */
    public static BuildPreferences of(
        OrientationMode mode,
        @Nullable Heading heading,
        RemovalPolicy removal,
        List<PlacementWeight> weights
    ) {
        return new BuildPreferences(mode, heading, removal, weights, Map.of());
    }

    public @Nullable ChoiceHistory history(NamespacedId blueprint) {
        return remembered.get(blueprint);
    }

    /** The remembered choice for one blueprint, or null when the blueprint has no history. */
    public @Nullable Siting preferred(NamespacedId blueprint) {
        ChoiceHistory history = remembered.get(blueprint);
        return history == null ? null : history.preferred();
    }

    /** Appends one choice and keeps the most recently used blueprints, oldest evicted first. */
    public BuildPreferences remember(NamespacedId blueprint, Siting choice, int capacity) {
        return withRemembered(remember(remembered, blueprint, choice, capacity));
    }

    /** The LRU rule as a pure function, so the store writes the memory without building a value. */
    public static Map<NamespacedId, ChoiceHistory> remember(
        Map<NamespacedId, ChoiceHistory> remembered,
        NamespacedId blueprint,
        Siting choice,
        int capacity
    ) {
        Objects.requireNonNull(remembered, "remembered");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(choice, "choice");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity >= 1");
        }
        Map<NamespacedId, ChoiceHistory> next = new LinkedHashMap<>(remembered);
        ChoiceHistory history = next.remove(blueprint);
        next.put(blueprint, (history == null ? ChoiceHistory.empty() : history).append(choice));
        while (next.size() > capacity) {
            Iterator<NamespacedId> eldest = next.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
        return Collections.unmodifiableMap(next);
    }

    /** Drops one blueprint's memory, or every blueprint's when the argument is null. */
    public BuildPreferences forget(@Nullable NamespacedId blueprint) {
        if (blueprint == null) {
            return new BuildPreferences(orientationMode, fixedHeading, removalPolicy, weights, Map.of());
        }
        if (!remembered.containsKey(blueprint)) {
            return this;
        }
        Map<NamespacedId, ChoiceHistory> next = new LinkedHashMap<>(remembered);
        next.remove(blueprint);
        return new BuildPreferences(orientationMode, fixedHeading, removalPolicy, weights, next);
    }

    public BuildPreferences withRemembered(Map<NamespacedId, ChoiceHistory> next) {
        return new BuildPreferences(orientationMode, fixedHeading, removalPolicy, weights, next);
    }

    public BuildPreferences withOrientationMode(OrientationMode mode) {
        return new BuildPreferences(mode, fixedHeading, removalPolicy, weights, remembered);
    }

    public BuildPreferences withFixedHeading(@Nullable Heading heading) {
        return new BuildPreferences(orientationMode, heading, removalPolicy, weights, remembered);
    }

    public BuildPreferences withRemovalPolicy(RemovalPolicy policy) {
        return new BuildPreferences(orientationMode, fixedHeading, policy, weights, remembered);
    }

    public BuildPreferences withWeights(List<PlacementWeight> next) {
        return new BuildPreferences(orientationMode, fixedHeading, removalPolicy, next, remembered);
    }

    /** The heading an anchorless cycle starts from: the fixed heading, or the player's facing. */
    public Heading startHeading(Heading playerFacing) {
        Objects.requireNonNull(playerFacing, "playerFacing");
        if (orientationMode == OrientationMode.fixedHeading && fixedHeading != null) {
            return fixedHeading;
        }
        return playerFacing;
    }

    /** The weight list as the config's lowercase spelling, for {@code /cumulus prefs show}. */
    public List<String> weightKeys() {
        List<String> keys = new ArrayList<>(weights.size());
        for (PlacementWeight weight : weights) {
            keys.add(weight.key());
        }
        return List.copyOf(keys);
    }
}
