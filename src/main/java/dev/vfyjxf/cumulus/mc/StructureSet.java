package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.document.CompiledSatellite;
import dev.vfyjxf.cumulus.kernel.document.CompiledStructureSet;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A compiled structure set placed by the host: the root siting and each satellite's absolute siting.
 *
 * <p>The host places the root; a satellite's absolute siting is {@code rootSiting.compose(relativeSiting)}.
 * A null composition is a set finding, never a crash.
 */
public record StructureSet(
    CompiledStructureSet compiled,
    Siting rootSiting,
    Cuboid rootBounds,
    List<Satellite> satellites
) {

    public StructureSet {
        Objects.requireNonNull(compiled, "compiled");
        Objects.requireNonNull(rootSiting, "rootSiting");
        Objects.requireNonNull(rootBounds, "rootBounds");
        Objects.requireNonNull(satellites, "satellites");
        satellites = List.copyOf(satellites);
        if (satellites.size() != compiled.satellites().size()) {
            throw new IllegalArgumentException("a placed set carries every compiled satellite");
        }
    }

    public NamespacedId id() {
        return compiled.id();
    }

    public Blueprint root() {
        return compiled.root();
    }

    public @Nullable Satellite satellite(NamespacedId id) {
        Objects.requireNonNull(id, "id");
        for (Satellite satellite : satellites) {
            if (satellite.id().equals(id)) {
                return satellite;
            }
        }
        return null;
    }

    /**
     * Composes the host root siting with every satellite's relative siting and maps the presence region
     * into world space. A mapping failure is a finding; the returned set is null when an error exists.
     */
    public static Placement place(CompiledStructureSet compiled, Siting rootSiting) {
        Objects.requireNonNull(compiled, "compiled");
        Objects.requireNonNull(rootSiting, "rootSiting");
        List<Finding> findings = new ArrayList<>();
        Cuboid rootBounds = rootSiting.toWorld(compiled.root().bounds());
        if (rootBounds == null) {
            findings.add(
                problem(
                    Codes.locusUnmapped,
                    "the root bounds of " + compiled.id().text() + " leave the lattice at " + rootSiting.origin(),
                    Locus.local(compiled.root().bounds().asSet())
                )
            );
            return new Placement(null, findings);
        }
        Set<NamespacedId> keys = new HashSet<>();
        keys.add(compiled.root().id());
        List<Satellite> placed = new ArrayList<>(compiled.satellites().size());
        for (CompiledSatellite candidate : compiled.satellites()) {
            SatelliteSpec spec = candidate.spec();
            if (!keys.add(spec.id())) {
                findings.add(
                    problem(
                        Codes.documentDuplicate,
                        "the satellite id " + spec.id().text() + " is already a structure key in this set",
                        Locus.local(candidate.blueprint().bounds().asSet())
                    )
                );
                continue;
            }
            Siting siting = rootSiting.compose(candidate.relativeSiting());
            if (siting == null) {
                findings.add(
                    problem(
                        Codes.locusUnmapped,
                        "the relative siting of " + spec.id().text() + " leaves the lattice under "
                                + rootSiting.origin(),
                        Locus.local(candidate.blueprint().bounds().asSet())
                    )
                );
                continue;
            }
            if (siting.equals(rootSiting)) {
                findings.add(
                    problem(
                        Codes.rootPlacementCollision,
                        "the satellite " + spec.id().text() + " is placed exactly on the root",
                        Locus.local(candidate.blueprint().bounds().asSet())
                    )
                );
                continue;
            }
            Cuboid worldBounds = siting.toWorld(candidate.blueprint().bounds());
            if (worldBounds == null) {
                findings.add(
                    problem(
                        Codes.locusUnmapped,
                        "the bounds of " + spec.id().text() + " leave the lattice at " + siting.origin(),
                        Locus.local(candidate.blueprint().bounds().asSet())
                    )
                );
                continue;
            }
            CuboidSet presenceLocal = spec.presence() == null
                    ? candidate.blueprint().bounds().asSet()
                    : spec.presence().cells();
            if (!candidate.blueprint().bounds().asSet().containsAll(presenceLocal)) {
                findings.add(
                    problem(
                        Codes.documentField,
                        "the presence region of " + spec.id().text() + " leaves the satellite bounds",
                        Locus.local(presenceLocal)
                    )
                );
                continue;
            }
            CuboidSet presenceWorld = siting.toWorld(presenceLocal);
            if (presenceWorld == null) {
                findings.add(
                    problem(
                        Codes.locusUnmapped,
                        "the presence region of " + spec.id().text() + " leaves the lattice at " + siting.origin(),
                        Locus.local(presenceLocal)
                    )
                );
                continue;
            }
            placed.add(new Satellite(spec.id(), candidate, siting, worldBounds, presenceWorld));
        }
        if (hasErrors(findings)) {
            return new Placement(null, findings);
        }
        return new Placement(new StructureSet(compiled, rootSiting, rootBounds, placed), findings);
    }

    /**
     * Validates a hand-built set against the plan rules the compiler enforces, before any presence
     * probe or instance allocation. The returned findings are empty for a valid set.
     */
    public static List<Finding> validate(StructureSet set) {
        Objects.requireNonNull(set, "set");
        List<Finding> findings = new ArrayList<>();
        Map<Siting, Satellite> placements = new HashMap<>();
        for (Satellite satellite : set.satellites()) {
            if (satellite.siting().equals(set.rootSiting())) {
                findings.add(
                    problem(
                        Codes.rootPlacementCollision,
                        "the satellite " + satellite.id().text() + " is placed exactly on the root",
                        Locus.world(satellite.presenceCells())
                    )
                );
            }
            Satellite previous = placements.putIfAbsent(satellite.siting(), satellite);
            if (previous != null) {
                findings.add(
                    problem(
                        Codes.duplicatePlacement,
                        "the satellite " + satellite.id().text() + " repeats the placement of " + previous.id().text(),
                        Locus.world(satellite.presenceCells())
                    )
                );
            }
            if (!satellite.worldBounds().asSet().containsAll(satellite.presenceCells())) {
                findings.add(
                    problem(
                        Codes.documentField,
                        "the presence region of " + satellite.id().text() + " leaves the satellite bounds",
                        Locus.world(satellite.presenceCells())
                    )
                );
            }
        }
        Map<FaceWorld, Satellite> faces = new HashMap<>();
        for (Satellite satellite : set.satellites()) {
            for (LinkSpec link : satellite.links()) {
                if (link.mode() != LinkMode.face || link.parentCell() == null || link.satelliteCell() == null) {
                    continue;
                }
                LatticePoint parent = set.rootSiting().toWorld(link.parentCell());
                LatticePoint port = satellite.siting().toWorld(link.satelliteCell());
                if (parent == null || port == null) {
                    continue;
                }
                FaceWorld face = new FaceWorld(
                    parent,
                    (long) port.x() - parent.x(),
                    (long) port.y() - parent.y(),
                    (long) port.z() - parent.z()
                );
                Satellite previous = faces.putIfAbsent(face, satellite);
                if (previous != null) {
                    findings.add(
                        problem(
                            Codes.duplicateFaceLink,
                            "the parent cell " + link.parentCell() + " already carries a face link to "
                                    + satellite.id().text(),
                            Locus.world(CuboidSet.of(Cuboid.of(parent), Cuboid.of(port)))
                        )
                    );
                }
            }
        }
        return List.copyOf(findings);
    }

    private static boolean hasErrors(List<Finding> findings) {
        for (Finding finding : findings) {
            if (finding.severity() == Severity.error) {
                return true;
            }
        }
        return false;
    }

    private static Finding problem(CodeEntry entry, String message, Locus locus) {
        return Finding.builder(entry).phase(Phase.check).message(message).label(Label.primary(locus, message)).build();
    }

    private record FaceWorld(LatticePoint parent, long dx, long dy, long dz) {}

    /** The placement outcome: the placed set, or the findings that prevented it. */
    public record Placement(@Nullable StructureSet set, List<Finding> findings) {

        public Placement {
            Objects.requireNonNull(findings, "findings");
            findings = List.copyOf(findings);
        }

        public boolean succeeded() {
            return set != null;
        }
    }

    /** One placed satellite: its absolute siting, world bounds and world-space presence cells. */
    public record Satellite(
        NamespacedId id,
        CompiledSatellite compiled,
        Siting siting,
        Cuboid worldBounds,
        CuboidSet presenceCells
    ) {

        public Satellite {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(compiled, "compiled");
            Objects.requireNonNull(siting, "siting");
            Objects.requireNonNull(worldBounds, "worldBounds");
            Objects.requireNonNull(presenceCells, "presenceCells");
            if (presenceCells.isEmpty()) {
                throw new IllegalArgumentException("a satellite needs a presence region");
            }
        }

        public Blueprint blueprint() {
            return compiled.blueprint();
        }

        public SatelliteSpec spec() {
            return compiled.spec();
        }

        public boolean required() {
            return compiled.spec().required();
        }

        public List<LinkSpec> links() {
            return compiled.spec().links();
        }
    }
}
