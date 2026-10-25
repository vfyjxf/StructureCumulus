package dev.vfyjxf.cumulus.kernel.engine;

/** The determinism tuple's knobs; a host may narrow, never widen, {@link #defaults()}. */
// No field of this record is read yet.
public record MatchPolicy(int maxSectionsPerUnit, CacheMode cache, boolean parallel) {

    public static final int DEFAULT_MAX_SECTIONS_PER_UNIT = 8;

    public MatchPolicy {
        if (maxSectionsPerUnit < 1) {
            throw new IllegalArgumentException("maxSectionsPerUnit must be at least 1: " + maxSectionsPerUnit);
        }
        if (cache == null) {
            throw new IllegalArgumentException("cache");
        }
    }

    public static MatchPolicy defaults() {
        return new MatchPolicy(DEFAULT_MAX_SECTIONS_PER_UNIT, CacheMode.OUTCOMES, false);
    }

    public static MatchPolicy of(int maxSectionsPerUnit, CacheMode cache, boolean parallel) {
        return new MatchPolicy(maxSectionsPerUnit, cache, parallel);
    }
}
