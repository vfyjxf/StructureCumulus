package dev.vfyjxf.cumulus.kernel.engine;

/** What an attempt may store across attempts; the attempt cache itself is the host's. */
// Nothing reads this and no attempt cache exists yet.
public enum CacheMode {
    OFF,
    PARTIALS,
    OUTCOMES
}
