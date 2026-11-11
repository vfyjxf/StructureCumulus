package dev.vfyjxf.cumulus.mc.preview.net;

import dev.vfyjxf.cumulus.kernel.space.Siting;

import java.util.Objects;

/** One legal siting of a preview session; its orientation already passed the expressibility filter. */
public record SitingCandidate(Siting siting, int score) {

    public SitingCandidate {
        Objects.requireNonNull(siting, "siting");
    }
}
