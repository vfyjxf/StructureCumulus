package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.core.Values;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.FindingBundle;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.Severity;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Placement;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** The only structure-set lowering function: documents in, compiled set or findings out. */
public final class StructureSetCompiler {

    public static StructureSetCompileResult compile(StructureSetDocument document, CompilerContext context) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(context, "context");
        try {
            return compileChecked(document, context);
        } catch (IllegalArgumentException rejected) {
            List<Finding> findings = new ArrayList<>();
            error(
                findings,
                Codes.documentField,
                "the structure set cannot be lowered: " + rejected.getMessage(),
                document.spec().source(),
                null,
                null
            );
            return new StructureSetCompileResult(FindingBundle.of(findings), null);
        }
    }

    private static StructureSetCompileResult compileChecked(StructureSetDocument document, CompilerContext context) {
        List<Finding> findings = new ArrayList<>();
        if (document.schemaVersion() != StructureSetDocument.currentVersion) {
            error(
                findings,
                Codes.schemaVersion,
                "unsupported schema version " + document.schemaVersion(),
                document.spec().source(),
                null,
                null
            );
            return new StructureSetCompileResult(FindingBundle.of(findings), null);
        }
        StructureSetSpec spec = document.spec();
        Map<CacheKey, CompileResult> cache = new LinkedHashMap<>();
        Blueprint root = null;
        Document rootDocument = resolve(context.documents(), spec.root(), "the root", spec.source(), findings);
        if (rootDocument != null) {
            root = compileOnce(rootDocument, cache, context, findings).blueprint();
        }
        List<CompiledSatellite> satellites = new ArrayList<>();
        for (SatelliteSpec satellite : spec.satellites()) {
            Document documentOfSatellite = resolve(
                context.documents(),
                satellite.blueprint(),
                "the satellite " + satellite.id().text(),
                satellite.source(),
                findings
            );
            if (documentOfSatellite == null) {
                continue;
            }
            Value effective = merge(documentOfSatellite.root().arguments(), satellite, findings);
            if (effective == null) {
                continue;
            }
            CompileResult compiled = compileOnce(
                withArguments(documentOfSatellite, effective),
                cache,
                context,
                findings
            );
            if (compiled.blueprint() != null) {
                satellites.add(new CompiledSatellite(satellite, compiled.blueprint()));
            }
        }
        if (hasErrors(findings) || root == null || satellites.size() != spec.satellites().size()) {
            return new StructureSetCompileResult(FindingBundle.of(findings), null);
        }
        CompiledStructureSet set = new CompiledStructureSet(spec.id(), root, satellites);
        validate(set, findings);
        if (hasErrors(findings)) {
            return new StructureSetCompileResult(FindingBundle.of(findings), null);
        }
        return new StructureSetCompileResult(FindingBundle.of(findings), set);
    }

    private static @Nullable Document resolve(
        DocumentSource documents,
        NamespacedId id,
        String role,
        @Nullable SourceSpan span,
        List<Finding> findings
    ) {
        Document document = documents.find(id);
        if (document != null) {
            return document;
        }
        if (documents.findSet(id) != null) {
            error(
                findings,
                Codes.documentField,
                role + " names the structure set " + id.text() + ", not a blueprint",
                span,
                null,
                null
            );
            return null;
        }
        error(findings, Codes.definitionMissing, role + " names no document " + id.text(), span, null, null);
        return null;
    }

    private static CompileResult compileOnce(
        Document document,
        Map<CacheKey, CompileResult> cache,
        CompilerContext context,
        List<Finding> findings
    ) {
        CacheKey key = new CacheKey(document.root().id(), document.root().arguments());
        CompileResult cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        CompileResult compiled = DocumentCompiler.compile(document, context);
        cache.put(key, compiled);
        findings.addAll(compiled.findings().findings());
        return compiled;
    }

    private static @Nullable Value merge(Value base, SatelliteSpec satellite, List<Finding> findings) {
        if (!(base instanceof Value.ObjectValue baseObject)) {
            error(
                findings,
                Codes.documentField,
                "the blueprint " + satellite.blueprint().text() + " has non-object root arguments",
                satellite.source(),
                null,
                null
            );
            return null;
        }
        Value arguments = satellite.arguments();
        if (!(arguments instanceof Value.ObjectValue override)) {
            error(
                findings,
                Codes.documentField,
                "the satellite " + satellite.id().text() + " has non-object arguments",
                satellite.source(),
                null,
                null
            );
            return null;
        }
        Map<String, Value> members = new TreeMap<>(Values.codePointOrder());
        members.putAll(baseObject.members());
        members.putAll(override.members());
        return new Value.ObjectValue(members, baseObject.span());
    }

    private static Document withArguments(Document document, Value arguments) {
        BlueprintSpec root = document.root();
        if (root.arguments().equals(arguments)) {
            return document;
        }
        BlueprintSpec replaced = new BlueprintSpec(
            root.id(),
            root.bounds(),
            root.envelope(),
            root.envelopeEntities(),
            arguments,
            root.bays(),
            root.seams(),
            root.checks(),
            root.computations(),
            root.keys(),
            root.derivations(),
            root.anchors(),
            root.orientation(),
            root.placements(),
            root.extensions(),
            root.source()
        );
        return new Document(document.schemaVersion(), replaced, document.definitions(), document.extensions());
    }

    private static void validate(CompiledStructureSet set, List<Finding> findings) {
        Map<Placement, SatelliteSpec> placements = new LinkedHashMap<>();
        Map<FaceKey, LinkSpec> faces = new LinkedHashMap<>();
        for (CompiledSatellite compiled : set.satellites()) {
            SatelliteSpec satellite = compiled.spec();
            Placement placement = new Placement(satellite.orientation(), satellite.at());
            if (placement.equals(Placement.identity)) {
                error(
                    findings,
                    Codes.rootPlacementCollision,
                    "the satellite " + satellite.id().text() + " is placed exactly on the root",
                    satellite.source(),
                    cellLocus(satellite.at()),
                    null
                );
            }
            SatelliteSpec previousPlacement = placements.putIfAbsent(placement, satellite);
            if (previousPlacement != null) {
                error(
                    findings,
                    Codes.duplicatePlacement,
                    "the satellite " + satellite.id().text() + " repeats the placement of "
                            + previousPlacement.id().text(),
                    satellite.source(),
                    cellLocus(satellite.at()),
                    previousPlacement.source() == null
                            ? null
                            : Label.secondary(
                                Locus.definition(previousPlacement.source()),
                                "the first satellite with this placement"
                            )
                );
            }
            validatePresence(compiled, findings);
            for (LinkSpec link : satellite.links()) {
                validateLink(set.root(), compiled, link, faces, findings);
            }
        }
    }

    private static void validatePresence(CompiledSatellite compiled, List<Finding> findings) {
        PresenceSpec presence = compiled.spec().presence();
        if (presence == null) {
            return;
        }
        Blueprint blueprint = compiled.blueprint();
        if (blueprint.bounds().asSet().containsAll(presence.cells())) {
            return;
        }
        SourceSpan span = presence.source() == null ? compiled.spec().source() : presence.source();
        error(
            findings,
            Codes.documentField,
            "the presence region of " + compiled.spec().id().text() + " leaves the satellite bounds",
            span,
            Locus.local(presence.cells()),
            blueprintCellLabel(
                blueprint,
                presence.cells().decomposition().get(0).min(),
                "the satellite blueprint " + compiled.spec().blueprint().text()
            )
        );
    }

    private static void validateLink(
        Blueprint root,
        CompiledSatellite compiled,
        LinkSpec link,
        Map<FaceKey, LinkSpec> faces,
        List<Finding> findings
    ) {
        SatelliteSpec satellite = compiled.spec();
        if (link.mode() == LinkMode.none) {
            return;
        }
        LatticePoint parent = link.parentCell();
        LatticePoint port = link.satelliteCell();
        if (parent == null || port == null) {
            linkError(
                findings,
                Codes.documentField,
                "the link of " + satellite.id().text() + " needs both cells",
                link,
                satellite,
                null
            );
            return;
        }
        if (!root.bounds().contains(parent)) {
            linkError(
                findings,
                Codes.linkCoverageInvalid,
                "the parent cell " + parent + " of " + satellite.id().text() + " is outside the root blueprint",
                link,
                satellite,
                blueprintCellLabel(root, parent, "the root blueprint " + root.id().text())
            );
            return;
        }
        if (!covers(root, parent)) {
            linkError(
                findings,
                Codes.linkCoverageInvalid,
                "the parent cell " + parent + " of " + satellite.id().text() + " is not read by any root check",
                link,
                satellite,
                blueprintCellLabel(root, parent, "the root blueprint " + root.id().text())
            );
            return;
        }
        Blueprint satelliteBlueprint = compiled.blueprint();
        if (!satelliteBlueprint.bounds().contains(port)) {
            linkError(
                findings,
                Codes.linkCoverageInvalid,
                "the satellite cell " + port + " of " + satellite.id().text() + " is outside the satellite blueprint",
                link,
                satellite,
                blueprintCellLabel(satelliteBlueprint, port, "the satellite blueprint " + satellite.blueprint().text())
            );
            return;
        }
        if (!covers(satelliteBlueprint, port)) {
            linkError(
                findings,
                Codes.linkCoverageInvalid,
                "the satellite cell " + port + " of " + satellite.id().text() + " is not read by any satellite check",
                link,
                satellite,
                blueprintCellLabel(satelliteBlueprint, port, "the satellite blueprint " + satellite.blueprint().text())
            );
            return;
        }
        LatticePoint worldPort = compiled.relativeSiting().toWorld(port);
        if (worldPort == null) {
            linkError(
                findings,
                Codes.locusUnmapped,
                "the link of " + satellite.id().text() + " cannot be mapped into the root frame",
                link,
                satellite,
                null
            );
            return;
        }
        long dx = (long) worldPort.x() - parent.x();
        long dy = (long) worldPort.y() - parent.y();
        long dz = (long) worldPort.z() - parent.z();
        if (link.mode() == LinkMode.face) {
            validateFace(link, satellite, parent, dx, dy, dz, faces, findings);
        } else {
            validateRange(link, satellite, dx, dy, dz, findings);
        }
    }

    private static void validateFace(
        LinkSpec link,
        SatelliteSpec satellite,
        LatticePoint parent,
        long dx,
        long dy,
        long dz,
        Map<FaceKey, LinkSpec> faces,
        List<Finding> findings
    ) {
        long manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        if (manhattan != 1) {
            linkError(
                findings,
                Codes.linkDistanceInvalid,
                "the face link of " + satellite.id().text() + " is not adjacent (Manhattan distance " + manhattan + ")",
                link,
                satellite,
                null
            );
            return;
        }
        FaceKey key = new FaceKey(parent, LatticePoint.of((int) dx, (int) dy, (int) dz));
        LinkSpec previous = faces.putIfAbsent(key, link);
        if (previous != null) {
            linkError(
                findings,
                Codes.duplicateFaceLink,
                "the parent cell " + parent + " already carries a face link in direction " + key.direction(),
                link,
                satellite,
                previous.source() == null
                        ? null
                        : Label.secondary(Locus.definition(previous.source()), "the earlier link on this parent face")
            );
        }
    }

    private static void validateRange(
        LinkSpec link,
        SatelliteSpec satellite,
        long dx,
        long dy,
        long dz,
        List<Finding> findings
    ) {
        if (within(link.metric(), dx, dy, dz, link.maxDistance())) {
            return;
        }
        linkError(
            findings,
            Codes.linkDistanceInvalid,
            "the range link of " + satellite.id().text() + " exceeds " + link.maxDistance() + " under " + link.metric(),
            link,
            satellite,
            null
        );
    }

    private static boolean within(DistanceMetric metric, long dx, long dy, long dz, int maxDistance) {
        long x = Math.abs(dx);
        long y = Math.abs(dy);
        long z = Math.abs(dz);
        return switch (metric) {
            case chebyshev -> Math.max(x, Math.max(y, z)) <= maxDistance;
            case manhattan -> x + y + z <= maxDistance;
            case euclidean -> {
                if (x > maxDistance || y > maxDistance || z > maxDistance) {
                    yield false;
                }
                long limit = (long) maxDistance * maxDistance;
                long sum = x * x;
                long squareY = y * y;
                if (squareY > limit - sum) {
                    yield false;
                }
                sum += squareY;
                yield z * z <= limit - sum;
            }
        };
    }

    private static boolean covers(Blueprint blueprint, LatticePoint cell) {
        for (Check<?> check : blueprint.checks()) {
            if (check.reads().cells().contains(cell)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Label blueprintCellLabel(Blueprint blueprint, LatticePoint cell, String message) {
        SourceSpan source = blueprint.source();
        return source == null
                ? null
                : Label.secondary(Locus.definition(CuboidSet.of(Cuboid.of(cell)), source), message);
    }

    private static @Nullable Locus cellLocus(LatticePoint cell) {
        if (cell.x() >= LatticePoint.maxXz || cell.y() >= LatticePoint.maxY || cell.z() >= LatticePoint.maxXz) {
            return null;
        }
        return Locus.local(CuboidSet.of(Cuboid.of(cell)));
    }

    private static void linkError(
        List<Finding> findings,
        CodeEntry entry,
        String message,
        LinkSpec link,
        SatelliteSpec satellite,
        @Nullable Label secondary
    ) {
        SourceSpan span = link.source() == null ? satellite.source() : link.source();
        Locus fallback = null;
        if (span == null) {
            LatticePoint cell = link.parentCell() == null ? link.satelliteCell() : link.parentCell();
            if (cell != null) {
                fallback = cellLocus(cell);
            }
        }
        error(findings, entry, message, span, fallback, secondary);
    }

    private static void error(
        List<Finding> findings,
        CodeEntry entry,
        String message,
        @Nullable SourceSpan span,
        @Nullable Locus fallback,
        @Nullable Label secondary
    ) {
        Finding.Builder builder = Finding.builder(entry).phase(Phase.compile).message(message);
        if (span != null) {
            builder.label(Label.primary(Locus.definition(span), message));
        } else if (fallback != null) {
            builder.label(Label.primary(fallback, message));
        }
        if (secondary != null) {
            builder.label(secondary);
        }
        findings.add(builder.build());
    }

    private static boolean hasErrors(List<Finding> findings) {
        for (Finding finding : findings) {
            if (finding.severity() == Severity.error) {
                return true;
            }
        }
        return false;
    }

    private record CacheKey(NamespacedId blueprint, Value arguments) {}

    private record FaceKey(LatticePoint parent, LatticePoint direction) {}

    private StructureSetCompiler() {}
}
