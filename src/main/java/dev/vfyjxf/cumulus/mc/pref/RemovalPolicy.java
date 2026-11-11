package dev.vfyjxf.cumulus.mc.pref;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** What a player lets a build do when the plan has to take a block out. */
public enum RemovalPolicy {

    /** A plan that removes a block is refused. */
    forbid,
    /** The server's gate decides, and the command's {@code --allow-removal} lifts it for one run. */
    ask,
    /** Removal is allowed for this player. */
    allow;

    public static @Nullable RemovalPolicy parse(String name) {
        String compact = PlacementWeight.compact(name);
        for (RemovalPolicy policy : values()) {
            if (policy.name().toLowerCase(Locale.ROOT).equals(compact)) {
                return policy;
            }
        }
        return null;
    }

    public String key() {
        return PlacementWeight.snake(this);
    }
}
