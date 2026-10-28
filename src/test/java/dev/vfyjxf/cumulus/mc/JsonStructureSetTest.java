package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SourceSpan;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.DistanceMetric;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.document.LinkMode;
import dev.vfyjxf.cumulus.kernel.document.LinkSpec;
import dev.vfyjxf.cumulus.kernel.document.PresenceSpec;
import dev.vfyjxf.cumulus.kernel.document.SatelliteSpec;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompileResult;
import dev.vfyjxf.cumulus.kernel.document.StructureSetCompiler;
import dev.vfyjxf.cumulus.kernel.document.StructureSetDocument;
import dev.vfyjxf.cumulus.kernel.document.StructureSetSpec;
import dev.vfyjxf.cumulus.kernel.finding.CodeEntry;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonStructureSetTest {

    private static final NamespacedId setSource = NamespacedId.of("setjson", "set.json");
    private static final NamespacedId rootSource = NamespacedId.of("setjson", "blast_furnace.json");
    private static final NamespacedId preheaterSource = NamespacedId.of("setjson", "preheater.json");
    private static final NamespacedId solarSource = NamespacedId.of("setjson", "solar.json");

    private static final NamespacedId rootId = NamespacedId.of("setjson", "blast_furnace");
    private static final NamespacedId preheaterId = NamespacedId.of("setjson", "preheater");
    private static final NamespacedId solarId = NamespacedId.of("setjson", "solar");
    private static final NamespacedId setId = NamespacedId.of("setjson", "blast_furnace_set");
    private static final NamespacedId leftId = NamespacedId.of("setjson", "left_preheater");
    private static final NamespacedId rightId = NamespacedId.of("setjson", "right_preheater");
    private static final NamespacedId solarSatelliteId = NamespacedId.of("setjson", "solar_array");

    private static final Orientation half = Orientation.of(Turn.half, Tilt.level, Flip.none);

    private static final String rootDocument = """
            {
              "schemaVersion": 1,
              "id": "setjson:blast_furnace",
              "bounds": [0, 0, 0, 3, 2, 3],
              "arguments": {"tier": 1, "material": "iron"},
              "bays": [
                {"id": "setjson:body", "boxes": [[0, 0, 0, 3, 2, 3]],
                 "minus": [[0, 0, 1, 1, 1, 1], [2, 0, 1, 1, 1, 1]],
                 "coverage": "every_cell", "footprint": "box", "sharing": "exclusive"},
                {"id": "setjson:left_link", "box": [0, 0, 1, 1, 1, 1],
                 "coverage": "every_cell", "footprint": "none", "sharing": "shared"},
                {"id": "setjson:right_link", "box": [2, 0, 1, 1, 1, 1],
                 "coverage": "every_cell", "footprint": "none", "sharing": "shared"}
              ],
              "checks": [
                {"id": "setjson:body_blocks", "bay": "setjson:body", "focus": {"kind": "bay"},
                 "expect": {"kind": "each_cell", "element": {"kind": "block", "id": "setjson:casing"}}},
                {"id": "setjson:left_socket", "bay": "setjson:left_link",
                 "focus": {"kind": "occupant", "cell": [0, 0, 1]},
                 "expect": {"kind": "block", "id": "setjson:socket"}},
                {"id": "setjson:right_socket", "bay": "setjson:right_link",
                 "focus": {"kind": "occupant", "cell": [2, 0, 1]},
                 "expect": {"kind": "block", "id": "setjson:socket"}}
              ]
            }
            """;

    private static final String preheaterDocument = """
            {
              "schemaVersion": 1,
              "kind": "blueprint",
              "id": "setjson:preheater",
              "bounds": [0, 0, 0, 2, 1, 2],
              "arguments": {"tier": 1, "material": "iron"},
              "bays": [
                {"id": "setjson:body", "box": [0, 0, 0, 1, 1, 1],
                 "coverage": "every_cell", "footprint": "box", "sharing": "exclusive"},
                {"id": "setjson:port", "box": [1, 0, 0, 1, 1, 1],
                 "coverage": "every_cell", "footprint": "box", "sharing": "exclusive"}
              ],
              "checks": [
                {"id": "setjson:body_check", "bay": "setjson:body",
                 "focus": {"kind": "occupant", "cell": [0, 0, 0]},
                 "expect": {"kind": "block", "id": "setjson:shell"}},
                {"id": "setjson:port_check", "bay": "setjson:port",
                 "focus": {"kind": "occupant", "cell": [1, 0, 0]},
                 "expect": {"kind": "block", "id": "setjson:plug"}}
              ]
            }
            """;

    private static final String solarDocument = """
            {
              "schemaVersion": 1,
              "id": "setjson:solar",
              "bounds": [0, 0, 0, 1, 1, 1],
              "bays": [
                {"id": "setjson:cell", "box": [0, 0, 0, 1, 1, 1],
                 "coverage": "every_cell", "footprint": "box", "sharing": "exclusive"}
              ],
              "checks": [
                {"id": "setjson:cell_check", "bay": "setjson:cell",
                 "focus": {"kind": "occupant", "cell": [0, 0, 0]},
                 "expect": {"kind": "block", "id": "setjson:shell"}}
              ]
            }
            """;

    private static final String setDocument = """
            {
              "schemaVersion": 1,
              "kind": "structure_set",
              "id": "setjson:blast_furnace_set",
              "root": "setjson:blast_furnace",
              "satellites": [
                {"id": "setjson:left_preheater", "blueprint": "setjson:preheater",
                 "at": [-2, 0, 1], "orientation": "identity", "required": true,
                 "arguments": {"tier": 2}, "presence": {"cell": [0, 0, 0]},
                 "links": [{"mode": "face", "parent_cell": [0, 0, 1], "satellite_cell": [1, 0, 0]}]},
                {"id": "setjson:right_preheater", "blueprint": "setjson:preheater",
                 "at": [4, 0, 1], "orientation": "half", "required": false,
                 "arguments": {"tier": 3},
                 "links": [{"mode": "face", "parent_cell": [2, 0, 1], "satellite_cell": [1, 0, 0]}]},
                {"id": "setjson:solar_array", "blueprint": "setjson:solar",
                 "at": [8, 0, 0], "orientation": "identity", "required": false,
                 "links": [{"mode": "range", "parent_cell": [2, 0, 1], "satellite_cell": [0, 0, 0],
                            "max_distance": 8, "metric": "chebyshev"}]}
              ],
              "extensions": {"vendor": "test"}
            }
            """;

    private record ReadResult(@Nullable StructureSetDocument document, List<Finding> errors) {}

    // --- 1. round-trip -----------------------------------------------------------------------

    @Test
    void structureSetRoundTripsThroughTheCanonicalWriter() {
        ReadResult first = readSet(setDocument);
        assertTrue(first.errors().isEmpty(), () -> first.errors().toString());
        StructureSetDocument document = Objects.requireNonNull(first.document(), "set");
        assertEquals(3, document.spec().satellites().size());
        assertNotNull(document.spec().source());
        assertNotNull(document.spec().satellites().get(0).source());
        PresenceSpec presence = Objects.requireNonNull(document.spec().satellites().get(0).presence(), "presence");
        assertNotNull(presence.source());
        assertNotNull(document.spec().satellites().get(0).links().get(0).source());

        String written = new StructureSetWriter().write(document);
        ReadResult second = readSet(written);
        assertTrue(second.errors().isEmpty(), () -> second.errors().toString());
        StructureSetDocument again = Objects.requireNonNull(second.document(), "second");
        assertEquals(document.withoutSpans(), again.withoutSpans());
        assertEquals(document.withoutSpans(), javaSet());
        assertEquals(written, new StructureSetWriter().write(again), "the canonical text is a fixed point");

        int schema = written.indexOf("\"schemaVersion\"");
        int kind = written.indexOf("\"kind\"");
        int id = written.indexOf("\"id\"");
        int root = written.indexOf("\"root\"");
        int satellites = written.indexOf("\"satellites\"");
        assertTrue(schema < kind && kind < id && id < root && root < satellites, written);
        assertTrue(written.contains("\"kind\": \"structure_set\""), written);
        assertTrue(written.contains("\"orientation\": \"half\""), written);
        assertTrue(written.contains("\"metric\": \"chebyshev\""), written);
    }

    private static StructureSetDocument javaSet() {
        return new StructureSetDocument(
            StructureSetDocument.currentVersion,
            new StructureSetSpec(
                setId,
                rootId,
                List.of(
                    new SatelliteSpec(
                        leftId,
                        preheaterId,
                        LatticePoint.of(-2, 0, 1),
                        Orientation.identity,
                        true,
                        Value.object(Map.of("tier", Value.number(2))),
                        PresenceSpec.cell(LatticePoint.origin),
                        List.of(LinkSpec.face(LatticePoint.of(0, 0, 1), LatticePoint.of(1, 0, 0))),
                        null
                    ),
                    new SatelliteSpec(
                        rightId,
                        preheaterId,
                        LatticePoint.of(4, 0, 1),
                        half,
                        false,
                        Value.object(Map.of("tier", Value.number(3))),
                        null,
                        List.of(LinkSpec.face(LatticePoint.of(2, 0, 1), LatticePoint.of(1, 0, 0))),
                        null
                    ),
                    new SatelliteSpec(
                        solarSatelliteId,
                        solarId,
                        LatticePoint.of(8, 0, 0),
                        Orientation.identity,
                        false,
                        Value.object(),
                        null,
                        List.of(
                            LinkSpec.range(
                                LatticePoint.of(2, 0, 1),
                                LatticePoint.of(0, 0, 0),
                                8,
                                DistanceMetric.chebyshev
                            )
                        ),
                        null
                    )
                ),
                null
            ),
            Map.of("vendor", Value.string("test"))
        );
    }

    // --- 2. compile the parsed set -----------------------------------------------------------

    @Test
    void theParsedSetCompilesAgainstTheParsedBlueprints() {
        StructureSetDocument document = Objects.requireNonNull(readSet(setDocument).document(), "set");
        Map<NamespacedId, Document> documents = new LinkedHashMap<>();
        for (String text : List.of(rootDocument, preheaterDocument, solarDocument)) {
            List<Finding> errors = new ArrayList<>();
            Document blueprint = new JsonReader().read(rootSource, text, errors);
            assertTrue(errors.isEmpty(), () -> errors.toString());
            assertNotNull(blueprint);
            documents.put(blueprint.root().id(), blueprint);
        }
        Catalogs catalogs = Catalogs.builtin();
        CompilerContext context = new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            DocumentSource.of(documents)
        );
        StructureSetCompileResult result = StructureSetCompiler.compile(document, context);
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        assertNotNull(result.set());
        assertEquals(3, Objects.requireNonNull(result.set()).satellites().size());
        assertEquals(
            Value.object(Map.of("tier", Value.number(2), "material", Value.string("iron"))),
            Objects.requireNonNull(result.set().satellite(leftId), "left").blueprint().arguments()
        );
    }

    // --- 3. the dispatcher -------------------------------------------------------------------

    @Test
    void theDispatcherSelectsTheReaderByKind() {
        List<Finding> blueprintErrors = new ArrayList<>();
        JsonDocument blueprint = JsonDocument.read(rootSource, rootDocument, blueprintErrors);
        assertTrue(blueprintErrors.isEmpty(), () -> blueprintErrors.toString());
        assertInstanceOf(JsonDocument.Blueprint.class, blueprint);

        List<Finding> kindErrors = new ArrayList<>();
        JsonDocument withKind = JsonDocument.read(preheaterSource, preheaterDocument, kindErrors);
        assertTrue(kindErrors.isEmpty(), () -> kindErrors.toString());
        assertInstanceOf(JsonDocument.Blueprint.class, withKind);

        List<Finding> setErrors = new ArrayList<>();
        JsonDocument set = JsonDocument.read(setSource, setDocument, setErrors);
        assertTrue(setErrors.isEmpty(), () -> setErrors.toString());
        assertInstanceOf(JsonDocument.StructureSet.class, set);

        List<Finding> unknownErrors = new ArrayList<>();
        assertNull(JsonDocument.read(setSource, setDocument.replace("\"structure_set\"", "\"other\""), unknownErrors));
        assertFalse(unknownErrors.isEmpty());
        assertEquals(Codes.documentField.code(), unknownErrors.get(0).code());
        assertTrue(unknownErrors.get(0).message().contains("unknown document kind"));

        List<Finding> nonStringErrors = new ArrayList<>();
        assertNull(JsonDocument.read(setSource, setDocument.replace("\"structure_set\"", "1"), nonStringErrors));
        assertEquals(Codes.documentField.code(), nonStringErrors.get(0).code());
    }

    @Test
    void aBlueprintKindIsOptionalAndTheCanonicalWriterOmitsIt() {
        List<Finding> errors = new ArrayList<>();
        Document withKind = new JsonReader().read(preheaterSource, preheaterDocument, errors);
        assertTrue(errors.isEmpty(), () -> errors.toString());
        assertNotNull(withKind);
        String written = new JsonWriter().write(withKind);
        assertFalse(written.contains("\"kind\": \"blueprint\""), written);

        List<Finding> setAsBlueprint = new ArrayList<>();
        assertNull(new JsonReader().read(setSource, setDocument, setAsBlueprint));
        assertEquals(Codes.documentField.code(), setAsBlueprint.get(0).code());
    }

    // --- 4. strict reader errors -------------------------------------------------------------

    @Test
    void theSetReaderRejectsUnknownMembersAndBadFields() {
        assertRejected(
            setDocument.replace("\"extensions\"", "\"extra\": true, \"extensions\""),
            Codes.documentField,
            "unknown member 'extra'"
        );
        assertRejected(
            setDocument.replace("\"required\": true,", "\"required\": true, \"extra\": 1,"),
            Codes.documentField,
            "unknown member 'extra'"
        );
        assertRejected(
            setDocument.replace("\"mode\": \"face\"", "\"mode\": \"face\", \"extra\": 1"),
            Codes.documentField,
            "unknown member 'extra'"
        );
        assertRejected(
            setDocument.replace("\"kind\": \"structure_set\"", "\"kind\": \"blueprint\""),
            Codes.documentField,
            "kind must be 'structure_set'"
        );
        assertRejected(
            setDocument.replace("\"schemaVersion\": 1,", ""),
            Codes.documentField,
            "missing member 'schemaVersion'"
        );
        assertRejected(
            "{\"schemaVersion\": 1, \"id\": \"setjson:s\", \"root\": \"setjson:r\", \"satellites\": []}",
            Codes.documentField,
            "missing member 'kind'"
        );
        assertRejected(
            "{\"schemaVersion\": 1, \"kind\": \"structure_set\", \"id\": \"setjson:s\", \"satellites\": []}",
            Codes.documentField,
            "missing member 'root'"
        );
        assertRejected(
            "{\"schemaVersion\": 1, \"kind\": \"structure_set\", \"root\": \"setjson:r\", \"satellites\": []}",
            Codes.documentField,
            "missing member 'id'"
        );
        assertRejected(
            "{\"schemaVersion\": 1, \"kind\": \"structure_set\", \"id\": \"setjson:s\", \"root\": \"setjson:r\"}",
            Codes.documentField,
            "missing member 'satellites'"
        );
        assertRejected(
            setDocument.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
            Codes.schemaVersion,
            "unsupported schemaVersion"
        );
        assertRejected(
            setDocument.replace("\"at\": [-2, 0, 1]", "\"at\": [-2, 0]"),
            Codes.documentField,
            "a point is [x, y, z]"
        );
        assertRejected(
            setDocument.replace("\"orientation\": \"identity\"", "\"orientation\": \"sideways\""),
            Codes.documentField,
            "unknown orientation 'sideways'"
        );
        assertRejected(
            setDocument.replace("\"arguments\": {\"tier\": 2}", "\"arguments\": [2]"),
            Codes.documentField,
            "expected a JSON object"
        );
        assertRejected(
            setDocument.replace("\"mode\": \"face\"", "\"mode\": \"sideways\""),
            Codes.documentField,
            "unknown link mode 'sideways'"
        );
        assertRejected(
            setDocument.replace(
                "\"mode\": \"face\", \"parent_cell\": [0, 0, 1], " + "\"satellite_cell\": [1, 0, 0]",
                "\"mode\": \"face\", \"parent_cell\": [0, 0, 1], \"satellite_cell\": [1, 0, 0], "
                        + "\"max_distance\": 2"
            ),
            Codes.documentField,
            "a face link carries no 'max_distance'"
        );
        assertRejected("""
                {"schemaVersion": 1, "kind": "structure_set", "id": "setjson:s",
                 "root": "setjson:r",
                 "satellites": [{"id": "setjson:a", "blueprint": "setjson:b", "at": [0, 0, 0],
                                 "links": [{"mode": "none", "parent_cell": [0, 0, 0]}]}]}
                """, Codes.documentField, "a none link carries no 'parent_cell'");
        assertRejected(
            setDocument.replace("\"max_distance\": 8, \"metric\": \"chebyshev\"", "\"metric\": \"chebyshev\""),
            Codes.documentField,
            "missing member 'max_distance'"
        );
        assertRejected(
            setDocument.replace("\"max_distance\": 8", "\"max_distance\": -1"),
            Codes.documentField,
            "max_distance must be non-negative"
        );
        assertRejected(
            setDocument.replace("\"metric\": \"chebyshev\"", "\"metric\": \"taxicab\""),
            Codes.documentField,
            "unknown distance metric 'taxicab'"
        );
        assertRejected(
            setDocument.replace(
                "\"presence\": {\"cell\": [0, 0, 0]}",
                "\"presence\": {\"cell\": [0, 0, 0], \"boxes\": [[0, 0, 0, 1, 1, 1]]}"
            ),
            Codes.documentField,
            "exactly one of 'cell' and 'boxes'"
        );
        assertRejected(
            setDocument.replace(
                "\"presence\": {\"cell\": [0, 0, 0]}",
                "\"presence\": {\"cell\": [33554431, 2047, 33554431]}"
            ),
            Codes.documentField,
            "invalid cell"
        );
        assertRejected(
            setDocument.replace("\"presence\": {\"cell\": [0, 0, 0]}", "\"presence\": {\"boxes\": []}"),
            Codes.documentField,
            "at least one cell"
        );
    }

    @Test
    void aDuplicateSatelliteIdIsADocumentDuplicate() {
        String text = """
                {"schemaVersion": 1, "kind": "structure_set", "id": "setjson:s",
                 "root": "setjson:r",
                 "satellites": [
                   {"id": "setjson:a", "blueprint": "setjson:b", "at": [0, 0, 0]},
                   {"id": "setjson:a", "blueprint": "setjson:b", "at": [1, 0, 0]}
                 ]}
                """;
        ReadResult result = readSet(text);
        assertNull(result.document());
        assertTrue(hasCode(result.errors(), Codes.documentDuplicate), () -> result.errors().toString());
        assertTrue(result.errors().stream().anyMatch(f -> f.message().contains("duplicate satellite id")));
    }

    @Test
    void readerErrorsCarryTheirSourceSpans() {
        String text = setDocument.replace("\"extensions\"", "\"extra\": true, \"extensions\"");
        ReadResult result = readSet(text);
        assertNull(result.document());
        Finding finding = result.errors().stream().filter(f -> f.code().equals(Codes.documentField.code())).findFirst()
                .orElseThrow();
        Label primary = Objects.requireNonNull(finding.primaryLabel(), "primary");
        SourceSpan span = Objects.requireNonNull(primary.locus().source(), "span");
        assertEquals(setSource, span.document());
        assertTrue(text.substring(span.start(), span.end()).contains("extra"), () -> "" + span);
    }

    // --- 5. canonical forms ------------------------------------------------------------------

    @Test
    void presenceCellAndBoxesBothRoundTrip() {
        StructureSetDocument cell = new StructureSetDocument(
            StructureSetDocument.currentVersion,
            new StructureSetSpec(
                setId,
                rootId,
                List.of(
                    new SatelliteSpec(
                        leftId,
                        preheaterId,
                        LatticePoint.of(-2, 0, 1),
                        Orientation.identity,
                        false,
                        Value.object(),
                        PresenceSpec.cell(LatticePoint.of(1, 0, 1)),
                        List.of(),
                        null
                    )
                ),
                null
            ),
            Map.of()
        );
        String cellText = new StructureSetWriter().write(cell);
        assertTrue(cellText.contains("\"cell\""), cellText);
        assertEquals(cell, Objects.requireNonNull(readSet(cellText).document(), "cell").withoutSpans());

        CuboidSet boxes = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 2));
        StructureSetDocument region = new StructureSetDocument(
            StructureSetDocument.currentVersion,
            new StructureSetSpec(
                setId,
                rootId,
                List.of(
                    new SatelliteSpec(
                        rightId,
                        preheaterId,
                        LatticePoint.of(4, 0, 1),
                        half,
                        false,
                        Value.object(),
                        PresenceSpec.of(boxes),
                        List.of(),
                        null
                    )
                ),
                null
            ),
            Map.of()
        );
        String regionText = new StructureSetWriter().write(region);
        assertTrue(regionText.contains("\"boxes\""), regionText);
        assertEquals(region, Objects.requireNonNull(readSet(regionText).document(), "region").withoutSpans());
    }

    @Test
    void faceRangeAndNoneLinksRoundTrip() {
        StructureSetDocument document = new StructureSetDocument(
            StructureSetDocument.currentVersion,
            new StructureSetSpec(
                setId,
                rootId,
                List.of(
                    new SatelliteSpec(
                        leftId,
                        preheaterId,
                        LatticePoint.of(-2, 0, 1),
                        Orientation.identity,
                        false,
                        Value.object(),
                        null,
                        List.of(
                            LinkSpec.face(LatticePoint.of(0, 0, 1), LatticePoint.of(1, 0, 0)),
                            LinkSpec.range(
                                LatticePoint.of(2, 0, 1),
                                LatticePoint.of(0, 0, 0),
                                8,
                                DistanceMetric.euclidean
                            ),
                            LinkSpec.none()
                        ),
                        null
                    )
                ),
                null
            ),
            Map.of()
        );
        String written = new StructureSetWriter().write(document);
        ReadResult read = readSet(written);
        assertTrue(read.errors().isEmpty(), () -> read.errors().toString());
        StructureSetDocument again = Objects.requireNonNull(read.document(), "again");
        assertEquals(document, again.withoutSpans());
        assertEquals(LinkMode.face, again.spec().satellites().get(0).links().get(0).mode());
        assertEquals(DistanceMetric.euclidean, again.spec().satellites().get(0).links().get(1).metric());
        assertEquals(8, again.spec().satellites().get(0).links().get(1).maxDistance());
        assertEquals(LinkMode.none, again.spec().satellites().get(0).links().get(2).mode());
        assertNull(again.spec().satellites().get(0).links().get(2).parentCell());
    }

    @Test
    void aSetWithoutOptionalMembersRoundTrips() {
        String text = """
                {"schemaVersion": 1, "kind": "structure_set", "id": "setjson:s",
                 "root": "setjson:r",
                 "satellites": [{"id": "setjson:a", "blueprint": "setjson:b", "at": [0, 0, 0]}]}
                """;
        ReadResult read = readSet(text);
        assertTrue(read.errors().isEmpty(), () -> read.errors().toString());
        StructureSetDocument document = Objects.requireNonNull(read.document(), "set");
        assertEquals(1, document.spec().satellites().size());
        SatelliteSpec satellite = document.spec().satellites().get(0);
        assertEquals(Orientation.identity, satellite.orientation());
        assertFalse(satellite.required());
        assertEquals(Value.object(), satellite.arguments());
        assertNull(satellite.presence());
        assertTrue(satellite.links().isEmpty());
        assertEquals(Map.of(), document.extensions());
        String written = new StructureSetWriter().write(document);
        assertEquals(
            document.withoutSpans(),
            Objects.requireNonNull(readSet(written).document(), "again").withoutSpans()
        );
    }

    // --- helpers -----------------------------------------------------------------------------

    private static ReadResult readSet(String text) {
        List<Finding> errors = new ArrayList<>();
        StructureSetDocument document = new StructureSetReader().read(setSource, text, errors);
        return new ReadResult(document, List.copyOf(errors));
    }

    private static void assertRejected(String text, CodeEntry code, @Nullable String message) {
        ReadResult result = readSet(text);
        assertNull(result.document(), () -> text + " -> " + result.errors());
        assertTrue(hasCode(result.errors(), code), () -> result.errors().toString());
        if (message != null) {
            assertTrue(
                result.errors().stream().anyMatch(f -> f.message().contains(message)),
                () -> "expected '" + message + "' in " + result.errors()
            );
        }
    }

    private static boolean hasCode(List<Finding> findings, CodeEntry code) {
        return findings.stream().anyMatch(f -> f.code().equals(code.code()));
    }
}
