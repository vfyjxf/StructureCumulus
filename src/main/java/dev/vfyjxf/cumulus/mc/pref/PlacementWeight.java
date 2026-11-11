package dev.vfyjxf.cumulus.mc.pref;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** One additive term of the siting score; {@link #magnitude()} is its per-unit weight. */
public enum PlacementWeight {

    /** The pose's declared front heading matches the player's horizontal facing. */
    facingPlayer(SitingCandidateScorer.facingBonus),
    /** The pose must clear fewer occupied cells before it can be built. */
    fewestRemovals(-SitingCandidateScorer.removalPenalty),
    /** The pose already satisfies more of its expected cells. */
    mostProgress(SitingCandidateScorer.progressBonus),
    /** The pose stands nearer to the player. */
    nearestToPlayer(-SitingCandidateScorer.distanceStep),
    /** The pose is the remembered choice of its blueprint. */
    remembered(SitingCandidateScorer.rememberedBonus),
    /** The pose's support face has a neighbour. */
    support(1),
    /** Declared for the P8 neighbour alignment; it contributes nothing yet. */
    align(0);

    private final int magnitude;

    PlacementWeight(int magnitude) {
        this.magnitude = magnitude;
    }

    /** The per-unit magnitude; the scorer multiplies it by the measured quantity. */
    public int magnitude() {
        return magnitude;
    }

    /** Parses a config or command spelling such as {@code facing_player} or {@code facingPlayer}. */
    public static @Nullable PlacementWeight parse(String name) {
        String compact = compact(name);
        for (PlacementWeight weight : values()) {
            if (weight.name().toLowerCase(Locale.ROOT).equals(compact)) {
                return weight;
            }
        }
        return null;
    }

    /** The snake_case config spelling of this weight. */
    public String key() {
        return snake(this);
    }

    static String compact(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    static String snake(Enum<?> constant) {
        StringBuilder text = new StringBuilder();
        for (char character : constant.name().toCharArray()) {
            if (Character.isUpperCase(character)) {
                text.append('_').append(Character.toLowerCase(character));
            } else {
                text.append(character);
            }
        }
        return text.toString();
    }
}
