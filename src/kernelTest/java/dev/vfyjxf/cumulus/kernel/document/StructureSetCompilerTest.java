package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.finding.LabelRole;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureSetCompilerTest {

    private static final NamespacedId casing = id("casing");
    private static final NamespacedId socket = id("socket");
    private static final NamespacedId shell = id("shell");
    private static final NamespacedId plug = id("plug");

    private static final NamespacedId rootId = id("blast_furnace");
    private static final NamespacedId preheaterId = id("preheater");
    private static final NamespacedId solarId = id("solar");
    private static final NamespacedId setId = id("blast_furnace_set");

    private static final LatticePoint leftParent = LatticePoint.of(0, 0, 1);
    private static final LatticePoint rightParent = LatticePoint.of(2, 0, 1);
    private static final LatticePoint port = LatticePoint.of(1, 0, 0);
    private static final LatticePoint solarCell = LatticePoint.of(0, 0, 0);

    private static final Orientation half = Orientation.of(Turn.half, Tilt.level, Flip.none);
    private static final Orientation clockwise = Orientation.of(Turn.clockwise, Tilt.level, Flip.none);

    private static NamespacedId id(String path) {
        return NamespacedId.of("settest", path);
    }

    private static CompilerContext context(DocumentSource source) {
        Catalogs catalogs = Catalogs.builtin();
        return new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            source
        );
    }

    private static DocumentSource source(Document... documents) {
        Map<NamespacedId, Document> byId = new LinkedHashMap<>();
        for (Document document : documents) {
            byId.put(document.root().id(), document);
        }
        return DocumentSource.of(byId);
    }

    // --- fixture documents -------------------------------------------------------------------

    private static Document rootDocument() {
        return rootDocument(false);
    }

    private static Document rootDocument(boolean declareUnreadBay) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 3, 2, 3);
        CuboidSet body = bounds.asSet().subtract(CuboidSet.of(Cuboid.of(leftParent), Cuboid.of(rightParent)));
        List<BaySpec> bays = new ArrayList<>();
        bays.add(bay(id("body"), body, Coverage.everyCell, Footprint.box(), Sharing.exclusive));
        bays.add(
            bay(
                id("left_link"),
                CuboidSet.of(Cuboid.of(leftParent)),
                Coverage.everyCell,
                Footprint.none(),
                Sharing.shared
            )
        );
        bays.add(
            bay(
                id("right_link"),
                CuboidSet.of(Cuboid.of(rightParent)),
                Coverage.everyCell,
                Footprint.none(),
                Sharing.shared
            )
        );
        List<CheckSpec> checks = new ArrayList<>();
        checks.add(bayCheck(id("body_blocks"), id("body"), casing));
        checks.add(occupantCheck(id("left_socket"), id("left_link"), leftParent, socket));
        checks.add(occupantCheck(id("right_socket"), id("right_link"), rightParent, socket));
        return document(
            new BlueprintSpec(
                rootId,
                bounds,
                false,
                Value.object(Map.of("tier", Value.number(1), "material", Value.string("iron"))),
                bays,
                List.of(),
                checks,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                Map.of(),
                null
            )
        );
    }

    /**
     * A root where the left link cell is inside the bounds and, when asked, inside a bay, but no
     * check reads it. The socket bay's check reads the neighbouring cell only.
     */
    private static Document rootWithoutLeftLinkCheck(boolean declareBay) {
        return rootWithoutLeftLinkCheck(declareBay, null);
    }

    private static Document rootWithoutLeftLinkCheck(boolean declareBay, @Nullable SourceSpan source) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 3, 2, 3);
        LatticePoint readCell = LatticePoint.of(0, 0, 2);
        CuboidSet socketCells = CuboidSet.of(Cuboid.of(leftParent), Cuboid.of(readCell));
        CuboidSet body = bounds.asSet().subtract(socketCells);
        List<BaySpec> bays = new ArrayList<>();
        bays.add(bay(id("body"), body, Coverage.everyCell, Footprint.box(), Sharing.exclusive));
        List<CheckSpec> checks = new ArrayList<>();
        checks.add(bayCheck(id("body_blocks"), id("body"), casing));
        if (declareBay) {
            bays.add(bay(id("socket_bay"), socketCells, Coverage.oneSubject, Footprint.box(), Sharing.exclusive));
            checks.add(occupantCheck(id("socket_check"), id("socket_bay"), readCell, socket));
        }
        return document(
            new BlueprintSpec(
                rootId,
                bounds,
                false,
                Value.object(),
                bays,
                List.of(),
                checks,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                Map.of(),
                source
            )
        );
    }

    private static Document preheaterDocument() {
        return preheaterDocument(true);
    }

    private static Document preheaterDocument(boolean readPort) {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 2);
        List<CheckSpec> checks = new ArrayList<>();
        checks.add(occupantCheck(id("body_check"), id("body"), LatticePoint.origin, shell));
        if (readPort) {
            checks.add(occupantCheck(id("port_check"), id("port"), port, plug));
        }
        List<BaySpec> bays = new ArrayList<>();
        bays.add(
            bay(
                id("body"),
                CuboidSet.of(Cuboid.of(LatticePoint.origin)),
                Coverage.everyCell,
                Footprint.box(),
                Sharing.exclusive
            )
        );
        if (readPort) {
            bays.add(
                bay(id("port"), CuboidSet.of(Cuboid.of(port)), Coverage.everyCell, Footprint.box(), Sharing.exclusive)
            );
        }
        return document(
            new BlueprintSpec(
                preheaterId,
                bounds,
                false,
                Value.object(Map.of("tier", Value.number(1), "material", Value.string("iron"))),
                bays,
                List.of(),
                checks,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                Map.of(),
                null
            )
        );
    }

    private static Document solarDocument() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);
        return document(
            new BlueprintSpec(
                solarId,
                bounds,
                false,
                Value.object(),
                List.of(bay(id("cell"), bounds.asSet(), Coverage.everyCell, Footprint.box(), Sharing.exclusive)),
                List.of(),
                List.of(occupantCheck(id("cell_check"), id("cell"), solarCell, shell)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                Map.of(),
                null
            )
        );
    }

    private static Document document(BlueprintSpec root) {
        return new Document(Document.currentVersion, root, Map.of(), Map.of());
    }

    private static BaySpec bay(
        NamespacedId id,
        CuboidSet cells,
        Coverage coverage,
        Footprint footprint,
        Sharing sharing
    ) {
        return new BaySpec(id, cells, coverage, footprint, sharing, null, null);
    }

    private static CheckSpec bayCheck(NamespacedId id, NamespacedId bay, NamespacedId block) {
        return new CheckSpec(id, bay, id("bay"), Value.object(), id("each_cell"), eachCell(block), null, null);
    }

    private static CheckSpec occupantCheck(NamespacedId id, NamespacedId bay, LatticePoint cell, NamespacedId block) {
        return new CheckSpec(
            id,
            bay,
            id("occupant"),
            Value.object(
                Map.of(
                    "cell",
                    Value.array(List.of(Value.number(cell.x()), Value.number(cell.y()), Value.number(cell.z())))
                )
            ),
            id("block"),
            Value.object(Map.of("id", Value.string(block.text()))),
            null,
            null
        );
    }

    private static Value eachCell(NamespacedId block) {
        return Value.object(
            Map.of("element", Value.object(Map.of("kind", Value.string("block"), "id", Value.string(block.text()))))
        );
    }

    private static StructureSetDocument set(List<SatelliteSpec> satellites) {
        return new StructureSetDocument(
            StructureSetDocument.currentVersion,
            new StructureSetSpec(setId, rootId, satellites, null),
            Map.of()
        );
    }

    private static SatelliteSpec satellite(String path, LatticePoint at) {
        return satellite(path, at, Orientation.identity, Value.object(), List.of());
    }

    private static SatelliteSpec satellite(
        String path,
        LatticePoint at,
        Orientation orientation,
        Value arguments,
        List<LinkSpec> links
    ) {
        return new SatelliteSpec(id(path), preheaterId, at, orientation, false, arguments, null, links, null);
    }

    private static SatelliteSpec solar(String path, LatticePoint at, int maxDistance, DistanceMetric metric) {
        return new SatelliteSpec(
            id(path),
            solarId,
            at,
            Orientation.identity,
            false,
            Value.object(),
            null,
            List.of(LinkSpec.range(rightParent, solarCell, maxDistance, metric)),
            null
        );
    }

    private static StructureSetCompileResult compile(StructureSetDocument set, DocumentSource source) {
        return StructureSetCompiler.compile(set, context(source));
    }

    // --- 1. the valid IE-style set -----------------------------------------------------------

    @Test
    void validSetCompilesRootAndTwoPreheaters() {
        StructureSetDocument set = set(
            List.of(
                satellite(
                    "left_preheater",
                    LatticePoint.of(-2, 0, 1),
                    Orientation.identity,
                    Value.object(Map.of("tier", Value.number(2))),
                    List.of(LinkSpec.face(leftParent, port))
                ),
                satellite(
                    "right_preheater",
                    LatticePoint.of(4, 0, 1),
                    half,
                    Value.object(Map.of("tier", Value.number(3))),
                    List.of(LinkSpec.face(rightParent, port))
                )
            )
        );
        StructureSetCompileResult result = compile(set, source(rootDocument(), preheaterDocument()));
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        assertTrue(result.succeeded());
        CompiledStructureSet compiled = result.set();
        assertNotNull(compiled);
        assertEquals(setId, compiled.id());
        assertEquals(rootId, compiled.root().id());
        assertEquals(2, compiled.satellites().size());

        CompiledSatellite left = Objects.requireNonNull(compiled.satellite(id("left_preheater")), "left");
        CompiledSatellite right = Objects.requireNonNull(compiled.satellite(id("right_preheater")), "right");
        assertEquals(LatticePoint.of(-1, 0, 1), left.relativeSiting().toWorld(port));
        assertEquals(LatticePoint.of(3, 0, 1), right.relativeSiting().toWorld(port));
        assertEquals(preheaterId, left.blueprint().id());
        assertEquals(
            Value.object(Map.of("tier", Value.number(2), "material", Value.string("iron"))),
            left.blueprint().arguments()
        );
    }

    // --- 2. relative siting ------------------------------------------------------------------

    @Test
    void relativeSitingCoversIdentityHalfClockwiseAndNegativeOffsets() {
        StructureSetDocument set = set(
            List.of(
                satellite("identity", LatticePoint.of(-2, 0, 1)),
                satellite("half", LatticePoint.of(4, 0, 1), half, Value.object(), List.of()),
                satellite("clockwise", LatticePoint.of(0, 0, -3), clockwise, Value.object(), List.of())
            )
        );
        StructureSetCompileResult result = compile(set, source(rootDocument(), preheaterDocument()));
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        CompiledStructureSet compiled = Objects.requireNonNull(result.set(), "set");

        CompiledSatellite identity = Objects.requireNonNull(compiled.satellite(id("identity")), "identity");
        CompiledSatellite halfTurn = Objects.requireNonNull(compiled.satellite(id("half")), "half");
        CompiledSatellite clockwiseTurn = Objects.requireNonNull(compiled.satellite(id("clockwise")), "clockwise");

        assertEquals(Siting.of(LatticePoint.of(-2, 0, 1), Orientation.identity), identity.relativeSiting());
        assertEquals(LatticePoint.of(-1, 0, 1), identity.relativeSiting().toWorld(port));
        assertEquals(LatticePoint.of(3, 0, 1), halfTurn.relativeSiting().toWorld(port));
        assertEquals(LatticePoint.of(0, 0, -2), clockwiseTurn.relativeSiting().toWorld(port));
        assertEquals(LatticePoint.of(1, 0, 0), identity.relativeSiting().toWorld(LatticePoint.of(3, 0, -1)));
    }

    // --- 3. face, range and none -------------------------------------------------------------

    @Test
    void faceRangeAndNoneLinksAreAccepted() {
        StructureSetDocument set = set(
            List.of(
                satellite(
                    "left_preheater",
                    LatticePoint.of(-2, 0, 1),
                    Orientation.identity,
                    Value.object(),
                    List.of(LinkSpec.face(leftParent, port))
                ),
                solar("solar", LatticePoint.of(8, 0, 0), 8, DistanceMetric.chebyshev),
                satellite("logical", LatticePoint.of(4, 0, 1), half, Value.object(), List.of(LinkSpec.none()))
            )
        );
        StructureSetCompileResult result = compile(set, source(rootDocument(), preheaterDocument(), solarDocument()));
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        CompiledStructureSet compiled = Objects.requireNonNull(result.set(), "set");
        assertEquals(
            LinkMode.face,
            Objects.requireNonNull(compiled.satellite(id("left_preheater")), "left").spec().links().get(0).mode()
        );
        assertEquals(
            LinkMode.range,
            Objects.requireNonNull(compiled.satellite(id("solar")), "solar").spec().links().get(0).mode()
        );
        assertEquals(
            LinkMode.none,
            Objects.requireNonNull(compiled.satellite(id("logical")), "logical").spec().links().get(0).mode()
        );
    }

    @Test
    void aFaceLinkMustBeAdjacent() {
        StructureSetDocument distance = set(
            List.of(
                satellite(
                    "same_cell",
                    LatticePoint.of(-1, 0, 1),
                    Orientation.identity,
                    Value.object(),
                    List.of(LinkSpec.face(leftParent, port))
                )
            )
        );
        StructureSetCompileResult same = compile(distance, source(rootDocument(), preheaterDocument()));
        assertNull(same.set());
        assertFalse(same.findings().ofCode(Codes.linkDistanceInvalid.code()).isEmpty());

        StructureSetDocument diagonal = set(
            List.of(
                satellite(
                    "diagonal",
                    LatticePoint.of(0, 1, 2),
                    Orientation.identity,
                    Value.object(),
                    List.of(LinkSpec.face(leftParent, port))
                )
            )
        );
        StructureSetCompileResult rejected = compile(diagonal, source(rootDocument(), preheaterDocument()));
        assertNull(rejected.set());
        assertFalse(rejected.findings().ofCode(Codes.linkDistanceInvalid.code()).isEmpty());
    }

    // --- 4. range metrics --------------------------------------------------------------------

    @Test
    void rangeLinksRespectEachMetric() {
        LatticePoint at = LatticePoint.of(4, 2, 1);
        assertTrue(
            compile(
                set(List.of(solar("solar", at, 3, DistanceMetric.chebyshev))),
                source(rootDocument(), solarDocument())
            ).succeeded(),
            "chebyshev 2 <= 3"
        );
        assertTrue(
            compile(
                set(List.of(solar("solar", at, 3, DistanceMetric.euclidean))),
                source(rootDocument(), solarDocument())
            ).succeeded(),
            "euclidean sqrt(8) <= 3"
        );
        assertFalse(
            compile(
                set(List.of(solar("solar", at, 2, DistanceMetric.euclidean))),
                source(rootDocument(), solarDocument())
            ).succeeded(),
            "euclidean sqrt(8) > 2"
        );

        StructureSetCompileResult manhattan = compile(
            set(List.of(solar("solar", at, 3, DistanceMetric.manhattan))),
            source(rootDocument(), solarDocument())
        );
        assertNull(manhattan.set());
        assertFalse(manhattan.findings().ofCode(Codes.linkDistanceInvalid.code()).isEmpty());
    }

    // --- 5. coverage -------------------------------------------------------------------------

    @Test
    void linkCellsMustBeInsideBoundsAndReadByACheck() {
        for (boolean declareBay : List.of(false, true)) {
            StructureSetDocument set = set(
                List.of(
                    satellite(
                        "left_preheater",
                        LatticePoint.of(-2, 0, 1),
                        Orientation.identity,
                        Value.object(),
                        List.of(LinkSpec.face(leftParent, port))
                    )
                )
            );
            StructureSetCompileResult result = compile(
                set,
                source(rootWithoutLeftLinkCheck(declareBay), preheaterDocument())
            );
            assertNull(result.set(), "declareBay=" + declareBay);
            assertFalse(
                result.findings().ofCode(Codes.linkCoverageInvalid.code()).isEmpty(),
                () -> "declareBay=" + declareBay + " " + result.findings().findings()
            );
        }

        StructureSetDocument outsideRoot = set(
            List.of(
                satellite(
                    "left_preheater",
                    LatticePoint.of(-2, 0, 1),
                    Orientation.identity,
                    Value.object(),
                    List.of(LinkSpec.face(LatticePoint.of(9, 0, 1), port))
                )
            )
        );
        StructureSetCompileResult outsideRootResult = compile(outsideRoot, source(rootDocument(), preheaterDocument()));
        assertNull(outsideRootResult.set());
        assertFalse(outsideRootResult.findings().ofCode(Codes.linkCoverageInvalid.code()).isEmpty());

        StructureSetDocument outsideSatellite = set(
            List.of(
                satellite(
                    "left_preheater",
                    LatticePoint.of(-2, 0, 1),
                    Orientation.identity,
                    Value.object(),
                    List.of(LinkSpec.face(leftParent, LatticePoint.of(5, 0, 5)))
                )
            )
        );
        StructureSetCompileResult outsideSatelliteResult = compile(
            outsideSatellite,
            source(rootDocument(), preheaterDocument())
        );
        assertNull(outsideSatelliteResult.set());
        assertFalse(outsideSatelliteResult.findings().ofCode(Codes.linkCoverageInvalid.code()).isEmpty());

        StructureSetDocument unreadPort = set(
            List.of(
                satellite(
                    "left_preheater",
                    LatticePoint.of(-2, 0, 1),
                    Orientation.identity,
                    Value.object(),
                    List.of(LinkSpec.face(leftParent, port))
                )
            )
        );
        StructureSetCompileResult rejected = compile(unreadPort, source(rootDocument(), preheaterDocument(false)));
        assertNull(rejected.set());
        assertFalse(rejected.findings().ofCode(Codes.linkCoverageInvalid.code()).isEmpty());
    }

    // --- 6. duplicates -----------------------------------------------------------------------

    @Test
    void duplicatePlacementIsRejected() {
        StructureSetDocument set = set(
            List.of(satellite("first", LatticePoint.of(-2, 0, 1)), satellite("second", LatticePoint.of(-2, 0, 1)))
        );
        StructureSetCompileResult result = compile(set, source(rootDocument(), preheaterDocument()));
        assertNull(result.set());
        assertEquals(
            1,
            result.findings().ofCode(Codes.duplicatePlacement.code()).size(),
            () -> result.findings().findings().toString()
        );
    }

    @Test
    void duplicateParentFaceIsRejected() {
        StructureSetDocument set = set(
            List.of(
                satellite(
                    "left_preheater",
                    LatticePoint.of(-2, 0, 1),
                    Orientation.identity,
                    Value.object(),
                    List.of(LinkSpec.face(leftParent, port))
                ),
                satellite(
                    "other_preheater",
                    LatticePoint.of(-1, 0, 1),
                    Orientation.identity,
                    Value.object(),
                    List.of(LinkSpec.face(leftParent, LatticePoint.origin))
                )
            )
        );
        StructureSetCompileResult result = compile(set, source(rootDocument(), preheaterDocument()));
        assertNull(result.set());
        assertEquals(
            1,
            result.findings().ofCode(Codes.duplicateFaceLink.code()).size(),
            () -> result.findings().findings().toString()
        );
    }

    // --- 7. arguments and the compile cache --------------------------------------------------

    @Test
    void argumentsShallowMergeAndTheCompileCacheIsPerEffectiveArguments() {
        StructureSetDocument set = set(
            List.of(
                satellite("inherits", LatticePoint.of(-2, 0, 1)),
                satellite(
                    "tier_two_a",
                    LatticePoint.of(4, 0, 1),
                    half,
                    Value.object(Map.of("tier", Value.number(2))),
                    List.of()
                ),
                satellite(
                    "tier_two_b",
                    LatticePoint.of(0, 0, 4),
                    Orientation.identity,
                    Value.object(Map.of("tier", Value.number(2))),
                    List.of()
                ),
                satellite("empty_override", LatticePoint.of(0, 0, -4), Orientation.identity, Value.object(), List.of())
            )
        );
        StructureSetCompileResult result = compile(set, source(rootDocument(), preheaterDocument()));
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        CompiledStructureSet compiled = Objects.requireNonNull(result.set(), "set");

        CompiledSatellite inherits = Objects.requireNonNull(compiled.satellite(id("inherits")), "inherits");
        CompiledSatellite tierA = Objects.requireNonNull(compiled.satellite(id("tier_two_a")), "tierA");
        CompiledSatellite tierB = Objects.requireNonNull(compiled.satellite(id("tier_two_b")), "tierB");
        CompiledSatellite empty = Objects.requireNonNull(compiled.satellite(id("empty_override")), "empty");

        assertEquals(
            Value.object(Map.of("tier", Value.number(1), "material", Value.string("iron"))),
            inherits.blueprint().arguments()
        );
        assertEquals(
            Value.object(Map.of("tier", Value.number(2), "material", Value.string("iron"))),
            tierA.blueprint().arguments()
        );
        assertSame(inherits.blueprint(), empty.blueprint(), "same blueprint and same effective arguments reuse");
        assertSame(tierA.blueprint(), tierB.blueprint(), "same blueprint and same effective arguments reuse");
        assertNotSame(inherits.blueprint(), tierA.blueprint(), "different effective arguments compile separately");
        assertNotEquals(inherits.spec().id(), empty.spec().id(), "a reused blueprint keeps its own satellite id");
        assertNotEquals(inherits.relativeSiting(), empty.relativeSiting(), "each satellite keeps its own siting");
        assertEquals(
            Value.object(Map.of("tier", Value.number(1), "material", Value.string("iron"))),
            compiled.root().arguments()
        );
    }

    // --- 8. missing documents and nested sets ------------------------------------------------

    @Test
    void missingRootAndSatelliteDocumentsAreFindings() {
        StructureSetDocument set = set(List.of(satellite("left_preheater", LatticePoint.of(-2, 0, 1))));

        StructureSetCompileResult noRoot = compile(set, source(preheaterDocument()));
        assertNull(noRoot.set());
        assertEquals(1, noRoot.findings().ofCode(Codes.definitionMissing.code()).size());
        assertTrue(noRoot.findings().findings().get(0).message().contains("the root names no document"));

        StructureSetCompileResult noSatellite = compile(set, source(rootDocument()));
        assertNull(noSatellite.set());
        assertEquals(1, noSatellite.findings().ofCode(Codes.definitionMissing.code()).size());
        assertTrue(noSatellite.findings().findings().get(0).message().contains("the satellite"));
    }

    @Test
    void nestedStructureSetsAreRejected() {
        StructureSetDocument nested = new StructureSetDocument(
            StructureSetDocument.currentVersion,
            new StructureSetSpec(id("nested_set"), rootId, List.of(), null),
            Map.of()
        );
        StructureSetDocument set = set(List.of(satellite("left_preheater", LatticePoint.of(-2, 0, 1))));
        DocumentSource satelliteNamesASet = DocumentSource
                .of(Map.of(rootId, rootDocument()), Map.of(preheaterId, nested));
        StructureSetCompileResult asSatellite = StructureSetCompiler.compile(set, context(satelliteNamesASet));
        assertNull(asSatellite.set());
        assertEquals(1, asSatellite.findings().ofCode(Codes.documentField.code()).size());
        assertTrue(
            asSatellite.findings().findings().get(0).message().contains("names the structure set"),
            () -> asSatellite.findings().findings().get(0).message()
        );

        StructureSetDocument rootNamesASet = new StructureSetDocument(
            StructureSetDocument.currentVersion,
            new StructureSetSpec(setId, id("nested_set"), List.of(), null),
            Map.of()
        );
        DocumentSource rootIsASet = DocumentSource.of(Map.of(), Map.of(id("nested_set"), nested));
        StructureSetCompileResult asRoot = StructureSetCompiler.compile(rootNamesASet, context(rootIsASet));
        assertNull(asRoot.set());
        assertEquals(1, asRoot.findings().ofCode(Codes.documentField.code()).size());
    }

    // --- 9. presence -------------------------------------------------------------------------

    @Test
    void aPresenceRegionMustStayInsideTheSatelliteBounds() {
        SatelliteSpec outside = new SatelliteSpec(
            id("left_preheater"),
            preheaterId,
            LatticePoint.of(-2, 0, 1),
            Orientation.identity,
            true,
            Value.object(),
            PresenceSpec.of(CuboidSet.of(Cuboid.of(LatticePoint.of(3, 0, 3)))),
            List.of(),
            null
        );
        StructureSetCompileResult rejected = compile(
            set(List.of(outside)),
            source(rootDocument(), preheaterDocument())
        );
        assertNull(rejected.set());
        assertEquals(1, rejected.findings().ofCode(Codes.documentField.code()).size());
        assertTrue(rejected.findings().findings().get(0).message().contains("presence region"));

        SatelliteSpec inside = new SatelliteSpec(
            id("left_preheater"),
            preheaterId,
            LatticePoint.of(-2, 0, 1),
            Orientation.identity,
            true,
            Value.object(),
            PresenceSpec.cell(LatticePoint.origin),
            List.of(),
            null
        );
        StructureSetCompileResult accepted = compile(set(List.of(inside)), source(rootDocument(), preheaterDocument()));
        assertTrue(accepted.findings().isEmpty(), () -> accepted.findings().findings().toString());
    }

    // --- 10. spans ---------------------------------------------------------------------------

    @Test
    void findingsCarryTheSetAndBlueprintLoci() {
        SourceSpan rootSource = new SourceSpan(id("root.json"), 0, 40);
        SourceSpan setSource = new SourceSpan(id("set.json"), 0, 80);
        SourceSpan satelliteSource = new SourceSpan(id("set.json"), 10, 60);
        SourceSpan linkSource = new SourceSpan(id("set.json"), 30, 50);
        Document root = rootWithoutLeftLinkCheck(true, rootSource);
        SatelliteSpec satellite = new SatelliteSpec(
            id("left_preheater"),
            preheaterId,
            LatticePoint.of(-2, 0, 1),
            Orientation.identity,
            false,
            Value.object(),
            null,
            List.of(new LinkSpec(LinkMode.face, leftParent, port, 0, DistanceMetric.chebyshev, linkSource)),
            satelliteSource
        );
        StructureSetDocument set = new StructureSetDocument(
            StructureSetDocument.currentVersion,
            new StructureSetSpec(setId, rootId, List.of(satellite), setSource),
            Map.of()
        );
        StructureSetCompileResult result = compile(set, source(root, preheaterDocument()));
        assertNull(result.set());
        assertEquals(1, result.findings().ofCode(Codes.linkCoverageInvalid.code()).size());
        Finding coverage = result.findings().ofCode(Codes.linkCoverageInvalid.code()).get(0);
        Label primary = Objects.requireNonNull(coverage.primaryLabel(), "primary");
        assertEquals(linkSource, primary.locus().source());
        List<Label> secondary = coverage.labelsOf(LabelRole.secondary);
        assertEquals(1, secondary.size(), () -> coverage.toString());
        assertEquals(rootSource, secondary.get(0).locus().source());
    }
}
