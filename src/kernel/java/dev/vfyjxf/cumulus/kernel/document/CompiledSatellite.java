package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;

import java.util.Objects;

/** One satellite after compilation: its document spec and its effective blueprint. */
public record CompiledSatellite(SatelliteSpec spec, Blueprint blueprint) {

    public CompiledSatellite {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(blueprint, "blueprint");
    }

    public Siting relativeSiting() {
        return Siting.of(spec.at(), spec.orientation());
    }
}
