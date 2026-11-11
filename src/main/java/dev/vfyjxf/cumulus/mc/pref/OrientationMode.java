package dev.vfyjxf.cumulus.mc.pref;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** How a preview picks the starting pose of an anchorless blueprint. */
public enum OrientationMode {

    /** The cycle starts at the player's facing and scoring reorders it; an anchor still decides. */
    autoAnchor,
    /** The cycle starts at the player's facing and the facing weight leads. */
    facePlayer,
    /** The cycle starts at {@link BuildPreferences#fixedHeading()}. */
    fixedHeading;

    public static @Nullable OrientationMode parse(String name) {
        String compact = PlacementWeight.compact(name);
        for (OrientationMode mode : values()) {
            if (mode.name().toLowerCase(Locale.ROOT).equals(compact)) {
                return mode;
            }
        }
        return null;
    }

    public String key() {
        return PlacementWeight.snake(this);
    }
}
