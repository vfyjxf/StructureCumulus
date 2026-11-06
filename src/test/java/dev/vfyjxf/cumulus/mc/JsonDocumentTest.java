package dev.vfyjxf.cumulus.mc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.document.BaySpec;
import dev.vfyjxf.cumulus.kernel.document.BlueprintSpec;
import dev.vfyjxf.cumulus.kernel.document.CheckSpec;
import dev.vfyjxf.cumulus.kernel.document.CompileResult;
import dev.vfyjxf.cumulus.kernel.document.CompilerContext;
import dev.vfyjxf.cumulus.kernel.document.ComputationSpec;
import dev.vfyjxf.cumulus.kernel.document.Document;
import dev.vfyjxf.cumulus.kernel.document.DocumentCompiler;
import dev.vfyjxf.cumulus.kernel.document.DocumentSource;
import dev.vfyjxf.cumulus.kernel.document.KeySpec;
import dev.vfyjxf.cumulus.kernel.document.PlacementSpec;
import dev.vfyjxf.cumulus.kernel.document.SeamSpec;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Computations;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.kernel.structure.SeamId;
import dev.vfyjxf.cumulus.kernel.structure.SeamKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDocumentTest {

    private static final NamespacedId documentId = NamespacedId.of("test", "workshop.json");

    private static final String richDocument = """
            {
              "schemaVersion": 1,
              "id": "test:workshop",
              "bounds": [0, 0, 0, 5, 4, 5],
              "definitions": {
                "test:unit": {
                  "bounds": [0, 0, 0, 1, 1, 1],
                  "bays": [
                    {"id": "cell", "box": [0,0,0,1,1,1], "coverage": "every_cell",
                     "footprint": "box", "sharing": "shared"}
                  ],
                  "seams": [
                    {"id": "port", "box": [0,0,0,1,1,1], "kind": "item",
                     "properties": {"side": "north", "rate": "fast"}}
                  ]
                }
              },
              "placements": [
                {"definition": "test:unit", "as": "test:left", "at": [0, 0, 0]},
                {"definition": "test:unit", "as": "test:right", "at": [2, 0, 0]}
              ],
              "bays": [
                {"id": "floor", "boxes": [[0,0,0,5,1,5]], "minus": [[2,0,2,3,1,3]],
                 "coverage": "any_subject", "footprint": {"declared": [[0,0,0,1,1,1]]},
                 "sharing": "exclusive", "description": "the floor"}
              ],
              "checks": [
                {"id": "floor_air", "bay": "test:left/cell", "focus": {"kind": "bay"},
                 "expect": {"kind": "each_cell", "element": {"kind": "air"}}},
                {"id": "portal", "bay": "floor", "focus": {"kind": "occupant", "cell": [0,0,0]},
                 "expect": {"kind": "all_of", "children": [
                   {"kind": "block", "id": "minecraft:stone_bricks"},
                   {"kind": "property", "key": "lit", "value": "true"}]},
                 "description": "the portal"}
              ],
              "computations": [
                {"id": "test:count", "kind": "count", "output": "test:count",
                 "region": [[0,0,0,5,1,5]], "inputs": ["test:count"], "extra": {"nested": true}}
              ],
              "keys": [
                {"id": "test:count", "type": "long", "retention": "instance", "merge": "sum",
                 "whenAbsent": 0},
                {"id": "test:flag", "type": "boolean", "retention": "match", "whenAbsent": false}
              ],
              "derivations": [
                {"deriver": "test:counter", "output": "test:count", "order": 0,
                 "arguments": {"step": 1}}
              ],
              "anchors": [{"role": "front", "property": "facing", "value": "north"}],
              "orientation": {"primary": "front",
                "allowed": ["identity", "clockwise", "half", "counterclockwise"], "upright": true},
              "arguments": {"tier": 2, "flags": ["a", "b"], "missing": null},
              "extensions": {"test:vendor": {"z": 1, "a": 2}}
            }
            """;

    private static final String campfireDocument = """
            {
              "schemaVersion": 1,
              "id": "mymod:campfire",
              "bounds": [0, 0, 0, 3, 3, 3],
              "bays": [
                {"id": "shell", "boxes": [[0,0,0,3,3,3]], "minus": [[1,1,1,1,1,1], [1,0,1,1,1,1]],
                 "coverage": "every_cell", "footprint": "box", "sharing": "exclusive"},
                {"id": "hollow", "box": [1,1,1,1,1,1], "coverage": "every_cell",
                 "footprint": "box", "sharing": "exclusive"},
                {"id": "fire", "box": [1,0,1,1,1,1], "coverage": "every_cell",
                 "footprint": "box", "sharing": "exclusive"}
              ],
              "checks": [
                {"id": "shell_bricks", "bay": "shell", "focus": {"kind": "bay"},
                 "expect": {"kind": "each_cell", "element": {"kind": "block", "id": "minecraft:stone_bricks"}}},
                {"id": "hollow_air", "bay": "hollow", "focus": {"kind": "bay"},
                 "expect": {"kind": "each_cell", "element": {"kind": "air"}}},
                {"id": "lit", "bay": "fire", "focus": {"kind": "occupant", "cell": [1,0,1]},
                 "expect": {"kind": "all_of", "children": [
                   {"kind": "block", "id": "minecraft:campfire"},
                   {"kind": "any_occupant"}]}}
              ],
              "computations": [
                {"kind": "air", "region": [[1,1,1,1,1,1]],
                 "output": "mymod:air_cells"}
              ],
              "keys": [
                {"id": "mymod:air_cells", "type": "long", "retention": "instance", "merge": "sum"}
              ]
            }
            """;

    @Test
    void readsTheRepresentativeDocument() {
        Document document = read(richDocument);
        assertEquals(1, document.schemaVersion());
        assertEquals(NamespacedId.of("test", "workshop"), document.root().id());
        assertEquals(Cuboid.of(0, 0, 0, 5, 4, 5), document.root().bounds());
        assertEquals(1, document.definitions().size());
        assertEquals(2, document.root().placements().size());
        assertEquals(1, document.root().bays().size());
        assertEquals(2, document.root().checks().size());
        assertEquals(1, document.root().computations().size());
        assertEquals(2, document.root().keys().size());
        assertEquals(1, document.root().derivations().size());
        assertEquals(1, document.root().anchors().size());
        assertNotNull(document.root().orientation());
        assertEquals(Set.of(NamespacedId.of("test", "count")), document.root().computations().get(0).inputs());
        assertEquals(
            Value.object(Map.of("extra", Value.object(Map.of("nested", Value.bool(true))))),
            document.root().computations().get(0).args()
        );
        assertEquals(Value.number(0), document.root().keys().get(0).whenAbsent());
        assertEquals(Retention.match, document.root().keys().get(1).retention());
        assertEquals(NamespacedId.of("test", "counter"), document.root().derivations().get(0).deriver());
        assertEquals(1, document.extensions().size());
        assertEquals(
            Value.object(Map.of("z", Value.number(1), "a", Value.number(2))),
            document.extensions().get("test:vendor")
        );

        BlueprintSpec definition = document.definitions().get(NamespacedId.of("test", "unit"));
        assertNotNull(definition);
        assertEquals(1, definition.seams().size());
        SeamSpec seam = definition.seams().get(0);
        assertEquals(new SeamId(NamespacedId.of("port")), seam.id());
        assertEquals(SeamKind.item, seam.kind());
        assertEquals(Map.of("rate", "fast", "side", "north"), seam.properties());
        assertNotNull(seam.source(), "a seam carries its offset");
        assertNotNull(document.root().source(), "the root carries the document offset");
        assertNotNull(document.root().bays().get(0).source(), "a bay carries its offset");
        assertNotNull(document.root().checks().get(1).source(), "a check carries its offset");
    }

    @Test
    void widensBayBoxesAndDeclaredFootprints() {
        Document document = read(richDocument);
        BaySpec floor = document.root().bays().get(0);
        CuboidSet expected = CuboidSet.of(Cuboid.of(0, 0, 0, 5, 1, 5)).subtract(Cuboid.ofSize(2, 0, 2, 3, 1, 3));
        assertEquals(expected, floor.cells());
        assertEquals(Coverage.anySubject, floor.coverage());
        assertEquals(Footprint.declared(CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1))), floor.footprint());
        assertEquals(Sharing.exclusive, floor.sharing());
        assertEquals("the floor", floor.description());
    }

    @Test
    void writesCanonicalV1Text() {
        Document document = read(richDocument);
        String text = new JsonWriter().write(document);

        int schema = text.indexOf("\n  \"schemaVersion\"");
        int id = text.indexOf("\n  \"id\"");
        int bounds = text.indexOf("\n  \"bounds\"");
        int definitions = text.indexOf("\n  \"definitions\"");
        int placements = text.indexOf("\n  \"placements\"");
        int bays = text.indexOf("\n  \"bays\"");
        int checks = text.indexOf("\n  \"checks\"");
        int computations = text.indexOf("\n  \"computations\"");
        int keys = text.indexOf("\n  \"keys\"");
        int derivations = text.indexOf("\n  \"derivations\"");
        int anchors = text.indexOf("\n  \"anchors\"");
        int orientation = text.indexOf("\n  \"orientation\"");
        int arguments = text.indexOf("\n  \"arguments\"");
        int extensions = text.indexOf("\n  \"extensions\"");
        assertTrue(schema >= 0 && schema < id, "schemaVersion leads");
        assertTrue(id < bounds, "id before bounds");
        assertTrue(bounds < definitions, "bounds before definitions");
        assertTrue(definitions < placements, "definitions before placements");
        assertTrue(placements < bays, "placements before bays");
        assertTrue(bays < checks, "bays before checks");
        assertTrue(checks < computations, "checks before computations");
        assertTrue(computations < keys, "computations before keys");
        assertTrue(keys < derivations, "keys before derivations");
        assertTrue(derivations < anchors, "derivations before anchors");
        assertTrue(anchors < orientation, "anchors before orientation");
        assertTrue(orientation < arguments, "orientation before arguments");
        assertTrue(arguments < extensions, "arguments before extensions");
        assertTrue(text.contains("\"schemaVersion\": 1"), "the version is a number");
        assertTrue(text.contains("\"coverage\": \"every_cell\""), "coverage uses the lowercase vocabulary");
        assertTrue(text.contains("\"coverage\": \"any_subject\""), "coverage uses the lowercase vocabulary");
        assertTrue(text.contains("\"sharing\": \"shared\""), "sharing uses the lowercase vocabulary");
        assertTrue(text.contains("\"retention\": \"instance\""), "retention uses the lowercase vocabulary");
        assertTrue(text.contains("\"kind\": \"item\""), "seam kinds use the lowercase vocabulary");
        assertTrue(text.contains("\"identity\""), "orientations use the lowercase vocabulary");
        assertTrue(text.contains("\"kind\": \"each_cell\""), "expectation kinds use snake_case");
        assertTrue(text.contains("\"footprint\": \"box\""), "a simple footprint is a string");
        assertTrue(text.contains("\"declared\""), "a declared footprint keeps its cells");
        assertTrue(text.contains("\"merge\": \"sum\""), "a built-in merge drops the default namespace");
        assertTrue(text.indexOf("\"definition\": \"test:unit\"") >= 0, "definitions are written with ids");
        assertTrue(text.contains("\"id\": \"test:workshop\""), "the root keeps its id");
        assertTrue(text.indexOf("\"id\": \"cell\"") > 0, "a local id drops the default namespace");
        assertTrue(text.indexOf("\"extensions\"") > 0, "document extensions are written");
        assertTrue(text.indexOf("\"test:vendor\"") < text.indexOf("\"a\": 2"), "extension keys are ascending");
        assertTrue(text.contains("\"one_subject\"") || !text.contains("\"oneSubject\""), "no Java enum name leaks");
        assertTrue(!text.contains("SourceSpan") && !text.contains("\"source\""), "spans are not content");
    }

    @Test
    void roundTripsCanonically() {
        Document first = read(richDocument);
        JsonWriter writer = new JsonWriter();
        String canonical = writer.write(first);
        List<Finding> errors = new ArrayList<>();
        Document second = new JsonReader().read(documentId, canonical, errors);
        assertTrue(errors.isEmpty(), () -> errors.toString());
        assertNotNull(second);
        assertEquals(canonical, writer.write(second), "write → read → write is stable");
        assertEquals(withoutSpans(first), withoutSpans(second), "spans do not change content");
    }

    @Test
    void reorderedMembersProduceEqualDocuments() {
        String first = """
                {"schemaVersion":1,"id":"test:order","bounds":[0,0,0,1,1,1],
                 "bays":[{"id":"cell","box":[0,0,0,1,1,1],"coverage":"every_cell",
                          "footprint":"box","sharing":"shared"}],
                 "checks":[{"id":"c","bay":"cell","focus":{"kind":"bay"},
                            "expect":{"kind":"each_cell","element":{"kind":"air"}}}]}
                """;
        String second = """
                {"checks":[{"expect":{"element":{"kind":"air"},"kind":"each_cell"},
                            "focus":{"kind":"bay"},"bay":"cell","id":"c"}],
                 "bays":[{"sharing":"shared","footprint":"box","coverage":"every_cell",
                          "box":[0,0,0,1,1,1],"id":"cell"}],
                 "bounds":[0,0,0,1,1,1],"id":"test:order","schemaVersion":1}
                """;
        assertEquals(withoutSpans(read(first)), withoutSpans(read(second)));
        assertEquals(new JsonWriter().write(read(first)), new JsonWriter().write(read(second)));
    }

    @Test
    void malformedJsonIsAReaderFailure() {
        List<Finding> errors = new ArrayList<>();
        Document document = new JsonReader().read(documentId, "{\"schemaVersion\": 1,", errors);
        assertNull(document);
        assertEquals(1, errors.size());
        assertEquals(FindingCode.of(FindingCode.Family.doc, 13), errors.get(0).code());
        assertTrue(errors.get(0).hasPrimary(), "a parse failure is located");
    }

    @Test
    void unsupportedVersionIsReadAsAVersionError() {
        List<Finding> errors = new ArrayList<>();
        Document document = new JsonReader().read(documentId, "{\"schemaVersion\": 2}", errors);
        assertNull(document);
        assertEquals(1, errors.size());
        assertEquals(FindingCode.of(FindingCode.Family.doc, 1), errors.get(0).code());
        assertTrue(errors.get(0).hasPrimary(), "the version member is located");
    }

    @Test
    void missingFieldIsAReaderFinding() {
        String text = """
                {"schemaVersion":1,"id":"test:missing","bays":[],"checks":[]}
                """;
        List<Finding> errors = new ArrayList<>();
        Document document = new JsonReader().read(documentId, text, errors);
        assertNull(document);
        assertTrue(
            errors.stream().anyMatch(f -> f.code().equals(FindingCode.of(FindingCode.Family.doc, 2))),
            () -> errors.toString()
        );
        assertTrue(errors.stream().allMatch(Finding::hasPrimary), "every shape finding is located");
    }

    @Test
    void unknownMemberIsAReaderFindingWithASpan() {
        String text = """
                {"schemaVersion":1,"id":"test:unknown","bounds":[0,0,0,1,1,1],
                 "bays":[],"checks":[],"seams":[]}
                """;
        List<Finding> errors = new ArrayList<>();
        Document document = new JsonReader().read(documentId, text, errors);
        assertNull(document);
        Finding finding = errors.stream().filter(f -> f.code().equals(FindingCode.of(FindingCode.Family.doc, 2)))
                .findFirst().orElseThrow();
        assertTrue(finding.message().contains("seams"), () -> finding.message());
        assertNotNull(finding.primaryLabel(), "unknown members are located");
        assertNotNull(Objects.requireNonNull(finding.primaryLabel()).locus().source(), "the span points into the text");
    }

    @Test
    void badBoxIsAReaderFinding() {
        String text = """
                {"schemaVersion":1,"id":"test:box","bounds":[0,0,0,-1,1,1],"bays":[],"checks":[]}
                """;
        List<Finding> errors = new ArrayList<>();
        assertNull(new JsonReader().read(documentId, text, errors));
        assertTrue(errors.stream().anyMatch(f -> f.code().equals(FindingCode.of(FindingCode.Family.doc, 2))));
        assertTrue(errors.stream().allMatch(Finding::hasPrimary));
    }

    @Test
    void fractionalNumbersAreNotRepresentable() {
        String text = """
                {"schemaVersion":1,"id":"test:fraction","bounds":[0,0,0,1,1,1],
                 "bays":[],"checks":[],"arguments":{"ratio":1.5}}
                """;
        List<Finding> errors = new ArrayList<>();
        assertNull(new JsonReader().read(documentId, text, errors));
        assertTrue(
            errors.stream().anyMatch(f -> f.code().equals(FindingCode.of(FindingCode.Family.doc, 2))),
            () -> errors.toString()
        );
    }

    @Test
    void unknownOrientationIsAReaderFinding() {
        String text = """
                {"schemaVersion":1,"id":"test:orientation","bounds":[0,0,0,1,1,1],
                 "bays":[],"checks":[],"orientation":{"primary":"front","allowed":["sideways"]}}
                """;
        List<Finding> errors = new ArrayList<>();
        assertNull(new JsonReader().read(documentId, text, errors));
        assertTrue(errors.stream().anyMatch(f -> f.message().contains("sideways")), () -> errors.toString());
    }

    @Test
    void unknownKeyTypeIsLeftToTheCompiler() {
        String text = """
                {"schemaVersion":1,"id":"test:key","bounds":[0,0,0,1,1,1],
                 "bays":[{"id":"cell","box":[0,0,0,1,1,1],"coverage":"every_cell",
                          "footprint":"box","sharing":"exclusive"}],
                 "checks":[{"id":"c","bay":"cell","focus":{"kind":"bay"},
                            "expect":{"kind":"each_cell","element":{"kind":"air"}}}],
                 "keys":[{"id":"test:widget","type":"widget","retention":"instance"}]}
                """;
        Document document = read(text);
        assertEquals("widget", document.root().keys().get(0).type());
        CompileResult result = DocumentCompiler.compile(document, context());
        assertNull(result.blueprint());
        assertEquals(
            FindingCode.of(FindingCode.Family.doc, 8),
            Objects.requireNonNull(result.findings().primary()).code()
        );
    }

    @Test
    void definitionRootOnlyFieldsAreLeftToTheCompiler() {
        String text = """
                {"schemaVersion":1,"id":"test:definition","bounds":[0,0,0,2,1,1],
                 "definitions":{"test:unit":{"bounds":[0,0,0,1,1,1],
                   "checks":[{"id":"c","bay":"cell","focus":{"kind":"bay"},
                              "expect":{"kind":"each_cell","element":{"kind":"air"}}}]}},
                 "placements":[{"definition":"test:unit","as":"unit","at":[0,0,0]}],
                 "bays":[],"checks":[]}
                """;
        Document document = read(text);
        CompileResult result = DocumentCompiler.compile(document, context());
        assertNull(result.blueprint());
        assertEquals(
            FindingCode.of(FindingCode.Family.doc, 2),
            Objects.requireNonNull(result.findings().primary()).code()
        );
    }

    @Test
    void campfireDocumentCompiles() {
        Document document = read(campfireDocument);
        CompileResult result = DocumentCompiler.compile(document, context());
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        assertNotNull(result.blueprint(), "the campfire lowers cleanly");
        assertEquals(NamespacedId.of("mymod", "campfire"), result.blueprint().id());
        assertEquals(3, result.blueprint().bays().size());
        assertEquals(3, result.blueprint().checks().size());
    }

    @Test
    void anOmittedComputationIdDefaultsToTheOutput() {
        Document document = read(campfireDocument);
        ComputationSpec computation = document.root().computations().get(0);
        assertEquals(computation.output(), computation.id(), "an omitted id defaults to the output");
        assertNotNull(DocumentCompiler.compile(document, context()).blueprint());
    }

    @Test
    void jsonAndBuilderProduceTheSameBlueprint() {
        String apiCampfire = """
                {
                  "schemaVersion": 1,
                  "id": "mymod:campfire",
                  "bounds": [0, 0, 0, 3, 3, 3],
                  "bays": [
                    {"id": "mymod:shell", "boxes": [[0,0,0,3,3,3]], "minus": [[1,1,1,1,1,1], [1,0,1,1,1,1]],
                     "coverage": "every_cell", "footprint": "box", "sharing": "exclusive"},
                    {"id": "mymod:hollow", "box": [1,1,1,1,1,1], "coverage": "every_cell",
                     "footprint": "box", "sharing": "exclusive"},
                    {"id": "mymod:fire", "box": [1,0,1,1,1,1], "coverage": "every_cell",
                     "footprint": "box", "sharing": "exclusive"}
                  ],
                  "checks": [
                    {"id": "mymod:shell_bricks", "bay": "mymod:shell", "focus": {"kind": "bay"},
                     "expect": {"kind": "each_cell", "element": {"kind": "block", "id": "minecraft:stone_bricks"}}},
                    {"id": "mymod:hollow_air", "bay": "mymod:hollow", "focus": {"kind": "bay"},
                     "expect": {"kind": "each_cell", "element": {"kind": "air"}}},
                    {"id": "mymod:fire", "bay": "mymod:fire", "focus": {"kind": "occupant", "cell": [1,0,1]},
                     "expect": {"kind": "block", "id": "minecraft:campfire"}}
                  ],
                  "computations": [
                    {"kind": "air", "region": [[1,1,1,1,1,1]], "output": "mymod:air_cells"}
                  ],
                  "keys": [
                    {"id": "mymod:air_cells", "type": "long", "retention": "instance", "merge": "sum"}
                  ],
                  "anchors": [{"role": "front", "property": "facing", "value": "north"}],
                  "orientation": {"primary": "front",
                    "allowed": ["identity", "clockwise", "half", "counterclockwise"], "upright": true}
                }
                """;
        CompileResult campfireResult = DocumentCompiler.compile(read(apiCampfire), context());
        assertTrue(campfireResult.findings().isEmpty(), () -> campfireResult.findings().findings().toString());
        Blueprint compiledCampfire = campfireResult.blueprint();
        assertNotNull(compiledCampfire);

        CuboidSet shellCells = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 3, 3)).subtract(Cuboid.of(1, 1, 1, 2, 2, 2))
                .subtract(Cuboid.of(1, 0, 1, 2, 1, 2));
        CuboidSet hollowCells = CuboidSet.of(Cuboid.of(1, 1, 1, 2, 2, 2));
        CuboidSet fireCells = CuboidSet.of(Cuboid.of(1, 0, 1, 2, 1, 2));
        ContextKey<Long> airCells = ContextKeys
                .of(NamespacedId.of("mymod", "air_cells"), Long.class, Retention.instance, Merges.sum(), null);
        Blueprint javaCampfire = Blueprint.builder(NamespacedId.of("mymod", "campfire"))
                .bounds(Cuboid.of(0, 0, 0, 3, 3, 3))
                .check(
                    Check.of(
                        NamespacedId.of("mymod", "shell_bricks"),
                        Bay.of(
                            NamespacedId.of("mymod", "shell"),
                            shellCells,
                            Coverage.everyCell,
                            Footprint.box(),
                            Sharing.exclusive
                        ),
                        Focuses.bay(),
                        Expectations.eachCell(Expectations.block(NamespacedId.of("minecraft", "stone_bricks")))
                    )
                )
                .check(
                    Check.of(
                        NamespacedId.of("mymod", "hollow_air"),
                        Bay.of(
                            NamespacedId.of("mymod", "hollow"),
                            hollowCells,
                            Coverage.everyCell,
                            Footprint.box(),
                            Sharing.exclusive
                        ),
                        Focuses.bay(),
                        Expectations.eachCell(Expectations.air())
                    )
                )
                .check(
                    Check.of(
                        NamespacedId.of("mymod", "fire"),
                        Bay.of(
                            NamespacedId.of("mymod", "fire"),
                            fireCells,
                            Coverage.everyCell,
                            Footprint.box(),
                            Sharing.exclusive
                        ),
                        Focuses.occupant(LatticePoint.of(1, 0, 1)),
                        Expectations.block(NamespacedId.of("minecraft", "campfire"))
                    )
                ).computation(Computations.air(airCells, hollowCells))
                .anchor(new AnchorSpec(NamespacedId.of("front"), "facing", "north"))
                .orientation(
                    OrientationPolicy.builder(NamespacedId.of("front"))
                            .allow(
                                Orientation.identity,
                                Orientation.of(Turn.clockwise, Tilt.level, Flip.none),
                                Orientation.of(Turn.half, Tilt.level, Flip.none),
                                Orientation.of(Turn.counterclockwise, Tilt.level, Flip.none)
                            ).uprightOnly().build()
                ).build();
        assertEquals(javaCampfire, compiledCampfire);

        String counted = """
                {"schemaVersion":1,"id":"test:computed","bounds":[0,0,0,2,1,1],
                 "bays":[{"id":"test:cell","box":[0,0,0,2,1,1],"coverage":"every_cell",
                          "footprint":"box","sharing":"shared"}],
                 "checks":[{"id":"test:c","bay":"test:cell","focus":{"kind":"bay"},
                            "expect":{"kind":"each_cell","element":{"kind":"air"}}}],
                 "computations":[
                   {"kind":"count","region":[[0,0,0,2,1,1]],"output":"test:bodies"},
                   {"kind":"volume","region":[[0,0,0,2,1,1]],"output":"test:volume"}],
                 "keys":[
                   {"id":"test:bodies","type":"long","retention":"instance","merge":"sum"},
                   {"id":"test:volume","type":"long","retention":"instance","merge":"sum"}]}
                """;
        CompileResult countedResult = DocumentCompiler.compile(read(counted), context());
        assertTrue(countedResult.findings().isEmpty(), () -> countedResult.findings().findings().toString());
        Blueprint compiledCounted = countedResult.blueprint();
        assertNotNull(compiledCounted);

        CuboidSet region = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        ContextKey<Long> bodies = ContextKeys
                .of(NamespacedId.of("test", "bodies"), Long.class, Retention.instance, Merges.sum(), null);
        ContextKey<Long> volume = ContextKeys
                .of(NamespacedId.of("test", "volume"), Long.class, Retention.instance, Merges.sum(), null);
        Blueprint javaCounted = Blueprint.builder(NamespacedId.of("test", "computed")).bounds(
            Cuboid.of(0, 0, 0, 2, 1, 1)
        ).check(
            Check.of(
                NamespacedId.of("test", "c"),
                Bay.of(NamespacedId.of("test", "cell"), region, Coverage.everyCell, Footprint.box(), Sharing.shared),
                Focuses.bay(),
                Expectations.eachCell(Expectations.air())
            )
        ).computation(Computations.count(bodies, region)).computation(Computations.volume(volume, region)).build();
        assertEquals(javaCounted, compiledCounted);
    }

    @Test
    void theWriterOmitsTheDerivedId() {
        Document document = read(campfireDocument);
        String text = new JsonWriter().write(document);
        JsonObject written = JsonParser.parseString(text).getAsJsonObject();
        for (JsonElement element : written.getAsJsonArray("computations")) {
            assertFalse(element.getAsJsonObject().has("id"), "the derived id is not written");
        }
        Document back = read(text);
        assertEquals(withoutSpans(document), withoutSpans(back));
    }

    @Test
    void writerRefusesUnrepresentableRootMembers() {
        Document document = read(campfireDocument);
        BlueprintSpec root = document.root();
        Document withSeam = new Document(
            document.schemaVersion(),
            new BlueprintSpec(
                root.id(),
                root.bounds(),
                root.envelope(),
                root.arguments(),
                root.bays(),
                List.of(
                    new SeamSpec(
                        new SeamId(NamespacedId.of("port")),
                        Cuboid.of(0, 0, 0, 1, 1, 1),
                        SeamKind.item,
                        Map.of(),
                        null
                    )
                ),
                root.checks(),
                root.computations(),
                root.keys(),
                root.derivations(),
                root.anchors(),
                root.orientation(),
                root.placements(),
                Map.of(),
                null
            ),
            document.definitions(),
            document.extensions()
        );
        assertThrows(IllegalStateException.class, () -> new JsonWriter().write(withSeam));
    }

    @Test
    void rootExtensionsStayAtTheDocumentLevel() {
        Document document = read(richDocument);
        assertTrue(document.root().extensions().isEmpty(), "the v1 envelope extensions are document-level");
        assertEquals(Map.of("test:vendor", document.extensions().get("test:vendor")), document.extensions());
    }

    @Test
    void rootEnvelopeIsReadWrittenAndCompiled() {
        String text = """
                {"schemaVersion":1,"id":"test:envelope","bounds":[0,0,0,2,1,1],
                 "envelope":true,"bays":[],"checks":[]}
                """;
        Document document = read(text);
        assertTrue(document.root().envelope(), "the reader carries the root flag");
        String written = new JsonWriter().write(document);
        assertTrue(written.contains("\"envelope\": true"));
        int bounds = written.indexOf("\"bounds\"");
        int envelope = written.indexOf("\"envelope\"");
        int bays = written.indexOf("\"bays\"");
        assertTrue(bounds >= 0 && bounds < envelope && envelope < bays, "envelope follows bounds");
        Document back = read(written);
        assertEquals(withoutSpans(document), withoutSpans(back));
        assertEquals(written, new JsonWriter().write(back), "the written form is a fixed point");

        CompileResult result = DocumentCompiler.compile(back, context());
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint blueprint = result.blueprint();
        assertNotNull(blueprint);
        assertEquals(1, blueprint.bays().size(), "an envelope-only root gets one synthetic bay");
        assertEquals(1, blueprint.checks().size(), "an envelope-only root gets one synthetic check");
        assertEquals(NamespacedId.of("structurecumulus", "envelope"), blueprint.bays().get(0).id());
    }

    @Test
    void absentAndFalseEnvelopeStayOff() {
        String absent = """
                {"schemaVersion":1,"id":"test:envelope_off","bounds":[0,0,0,1,1,1],
                 "bays":[],"checks":[]}
                """;
        String off = """
                {"schemaVersion":1,"id":"test:envelope_off","bounds":[0,0,0,1,1,1],
                 "envelope":false,"bays":[],"checks":[]}
                """;
        Document absentDocument = read(absent);
        Document offDocument = read(off);
        assertFalse(absentDocument.root().envelope());
        assertFalse(offDocument.root().envelope());
        assertEquals(withoutSpans(absentDocument), withoutSpans(offDocument));
        assertFalse(new JsonWriter().write(offDocument).contains("\"envelope\""));
    }

    @Test
    void definitionEnvelopeAndNonBooleanAreRejected() {
        String definition = """
                {"schemaVersion":1,"id":"test:definition_envelope","bounds":[0,0,0,1,1,1],
                 "definitions":{"test:unit":{"bounds":[0,0,0,1,1,1],"envelope":true}},
                 "bays":[],"checks":[]}
                """;
        List<Finding> definitionErrors = new ArrayList<>();
        assertNull(new JsonReader().read(documentId, definition, definitionErrors));
        assertEquals(
            FindingCode.of(FindingCode.Family.doc, 2),
            Objects.requireNonNull(definitionErrors.get(0), "finding").code()
        );
        assertTrue(definitionErrors.get(0).message().contains("unknown member 'envelope'"));

        String nonBoolean = """
                {"schemaVersion":1,"id":"test:non_boolean","bounds":[0,0,0,1,1,1],
                 "envelope":1,"bays":[],"checks":[]}
                """;
        List<Finding> typeErrors = new ArrayList<>();
        assertNull(new JsonReader().read(documentId, nonBoolean, typeErrors));
        assertEquals(
            FindingCode.of(FindingCode.Family.doc, 2),
            Objects.requireNonNull(typeErrors.get(0), "finding").code()
        );
        assertTrue(typeErrors.get(0).message().contains("expected a boolean"));
    }

    @Test
    void jsonEnvelopeCompilesToTheJavaBlueprint() {
        String text = """
                {"schemaVersion":1,"id":"test:envelope_parity","bounds":[0,0,0,3,1,1],
                 "envelope":true,
                 "bays":[{"id":"test:wall","box":[0,0,0,1,1,1],"coverage":"every_cell",
                          "footprint":"box","sharing":"exclusive"}],
                 "checks":[{"id":"test:wall_air","bay":"test:wall",
                            "focus":{"kind":"occupant","cell":[0,0,0]},
                            "expect":{"kind":"air"}}]}
                """;
        CompileResult result = DocumentCompiler.compile(read(text), context());
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint compiled = result.blueprint();
        assertNotNull(compiled);
        Bay wall = Bay.of(
            NamespacedId.of("test", "wall"),
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Blueprint javaBlueprint = Blueprint.builder(NamespacedId.of("test", "envelope_parity"))
                .bounds(Cuboid.of(0, 0, 0, 3, 1, 1))
                .check(
                    Check.of(
                        NamespacedId.of("test", "wall_air"),
                        wall,
                        Focuses.occupant(LatticePoint.origin),
                        Expectations.air()
                    )
                ).envelope().build();
        assertEquals(javaBlueprint.id(), compiled.id());
        assertEquals(javaBlueprint.bounds(), compiled.bounds());
        assertEquals(javaBlueprint.bays(), compiled.bays());
        assertEquals(javaBlueprint.checks(), compiled.checks());
        assertEquals(javaBlueprint.computations(), compiled.computations());
        assertEquals(javaBlueprint.arguments(), compiled.arguments());
        assertEquals(javaBlueprint, compiled);
        assertEquals(javaBlueprint.plan(), compiled.plan());
        assertEquals(javaBlueprint.reads(), compiled.reads());
    }

    @Test
    void nullArgumentValuesSurviveTheRoundTrip() {
        Document document = read(richDocument);
        assertInstanceOf(Value.NullValue.class, document.root().arguments().get("missing"));
        String text = new JsonWriter().write(document);
        assertTrue(text.contains("\"missing\": null"), "a null value is serialized");
        Document back = read(text);
        assertInstanceOf(Value.NullValue.class, back.root().arguments().get("missing"));
    }

    @Test
    void spansPointAtTheOffendingMember() {
        String text = """
                {"schemaVersion":1,"id":"test:unknown","bounds":[0,0,0,1,1,1],
                 "bays":[],"checks":[],"seams":[]}
                """;
        List<Finding> errors = new ArrayList<>();
        assertNull(new JsonReader().read(documentId, text, errors));
        Finding finding = errors.stream().filter(f -> f.code().equals(FindingCode.of(FindingCode.Family.doc, 2)))
                .findFirst().orElseThrow();
        assertNotNull(finding.primaryLabel());
        var span = Objects.requireNonNull(finding.primaryLabel()).locus().source();
        assertNotNull(span);
        assertEquals("\"seams\"", text.substring(span.start(), span.end()));

        Document document = read(richDocument);
        var baySource = Objects.requireNonNull(document.root().bays().get(0).source());
        String bayText = richDocument.substring(baySource.start(), baySource.end());
        assertTrue(bayText.startsWith("{") && bayText.endsWith("}"), "a spec span covers its object");
        assertTrue(bayText.contains("\"id\": \"floor\""), "the bay span is the bay object");
    }

    @Test
    void lenientJsonWithoutRecoverableOffsetsReportsNoPositions() {
        String text = """
                {
                  // Gson accepts this; the offset scan does not
                  "schemaVersion": 1,
                  "id": "test:lenient",
                  "bounds": [0,0,0,1,1,1],
                  "bays": [],
                  "checks": []
                }
                """;
        List<Finding> errors = new ArrayList<>();
        Document document = new JsonReader().read(documentId, text, errors);
        assertNotNull(document, "the lenient document still reads");
        assertEquals(1, errors.size());
        assertEquals(FindingCode.of(FindingCode.Family.doc, 12), errors.get(0).code());
        assertNull(document.root().source(), "no span was recovered");
    }

    @Test
    void allFootprintsRoundTrip() {
        String text = """
                {"schemaVersion":1,"id":"test:footprints","bounds":[0,0,0,4,1,1],
                 "bays":[
                   {"id":"b","box":[0,0,0,1,1,1],"coverage":"every_cell","footprint":"box","sharing":"exclusive"},
                   {"id":"d","box":[1,0,0,2,1,1],"coverage":"one_subject","footprint":"bound","sharing":"shared"},
                   {"id":"n","box":[2,0,0,3,1,1],"coverage":"any_subject","footprint":"none","sharing":"shared"},
                   {"id":"x","box":[3,0,0,4,1,1],"coverage":"every_cell",
                    "footprint":{"declared":[[0,0,0,1,1,1]]},"sharing":"exclusive"}],
                 "checks":[]}
                """;
        Document document = read(text);
        assertInstanceOf(Footprint.Box.class, document.root().bays().get(0).footprint());
        assertInstanceOf(Footprint.Bound.class, document.root().bays().get(1).footprint());
        assertInstanceOf(Footprint.None.class, document.root().bays().get(2).footprint());
        assertInstanceOf(Footprint.Declared.class, document.root().bays().get(3).footprint());
        String written = new JsonWriter().write(document);
        Document back = read(written);
        assertEquals(withoutSpans(document), withoutSpans(back));
    }

    @Test
    void allOrientationNamesRoundTrip() {
        OrientationPolicy policy = OrientationPolicy.builder(NamespacedId.of("front"))
                .allow(Orientation.all().toArray(new Orientation[0])).build();
        BlueprintSpec root = new BlueprintSpec(
            NamespacedId.of("test", "orientations"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            false,
            Value.object(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            policy,
            List.of(),
            Map.of(),
            null
        );
        Document document = new Document(1, root, Map.of(), Map.of());
        String text = new JsonWriter().write(document);
        assertTrue(text.contains("\"identity\""), "the identity turn is written");
        assertTrue(
            text.contains("\"clockwise\"") && text.contains("\"half\"") && text.contains("\"counterclockwise\""),
            "the level turns keep their names"
        );
        assertTrue(text.contains("\"pitch_up\"") && text.contains("_mirror"), "tilt and mirror names are written");
        List<Finding> errors = new ArrayList<>();
        Document back = new JsonReader().read(documentId, text, errors);
        assertTrue(errors.isEmpty(), () -> errors.toString());
        assertNotNull(back);
        assertEquals(policy, back.root().orientation());
    }

    @Test
    void keyTypesAndWhenAbsentArePreserved() {
        String text = """
                {"schemaVersion":1,"id":"test:keys","bounds":[0,0,0,1,1,1],
                 "bays":[],"checks":[],
                 "keys":[
                   {"id":"test:l","type":"long","retention":"instance","merge":"sum","whenAbsent":1},
                   {"id":"test:i","type":"int","retention":"match","whenAbsent":2},
                   {"id":"test:b","type":"boolean","retention":"instance","whenAbsent":true},
                   {"id":"test:s","type":"string","retention":"instance","whenAbsent":"s"},
                   {"id":"test:id","type":"id","retention":"instance","whenAbsent":"minecraft:stone"},
                   {"id":"test:c","type":"cuboid","retention":"instance","whenAbsent":[0,0,0,1,1,1]},
                   {"id":"test:cs","type":"cuboid_set","retention":"instance",
                    "whenAbsent":[[0,0,0,1,1,1]]}]}
                """;
        Document document = read(text);
        List<KeySpec> keys = document.root().keys();
        assertEquals(7, keys.size());
        assertEquals(
            List.of("long", "int", "boolean", "string", "id", "cuboid", "cuboid_set"),
            keys.stream().map(KeySpec::type).toList()
        );
        assertEquals(NamespacedId.of("sum"), keys.get(0).merge());
        assertEquals(Value.number(1), keys.get(0).whenAbsent());
        assertEquals(Value.number(2), keys.get(1).whenAbsent());
        assertEquals(Value.bool(true), keys.get(2).whenAbsent());
        assertEquals(Value.string("s"), keys.get(3).whenAbsent());
        assertEquals(Value.string("minecraft:stone"), keys.get(4).whenAbsent());
        assertTrue(keys.get(5).whenAbsent() instanceof Value.ArrayValue);
        assertTrue(keys.get(6).whenAbsent() instanceof Value.ArrayValue);
        String written = new JsonWriter().write(document);
        Document back = read(written);
        assertEquals(withoutSpans(document), withoutSpans(back));
    }

    @Test
    void semanticErrorsAreLeftToTheCompiler() {
        String missingBay = """
                {"schemaVersion":1,"id":"test:semantic","bounds":[0,0,0,1,1,1],
                 "bays":[],"checks":[{"id":"c","bay":"absent","focus":{"kind":"bay"},
                   "expect":{"kind":"each_cell","element":{"kind":"air"}}}]}
                """;
        CompileResult missing = DocumentCompiler.compile(read(missingBay), context());
        assertNull(missing.blueprint());
        assertEquals(
            FindingCode.of(FindingCode.Family.doc, 6),
            Objects.requireNonNull(missing.findings().primary()).code()
        );

        String cycle = """
                {"schemaVersion":1,"id":"test:cycle","bounds":[0,0,0,1,1,1],
                 "definitions":{
                   "test:a":{"bounds":[0,0,0,1,1,1],
                     "placements":[{"definition":"test:b","as":"b","at":[0,0,0]}]},
                   "test:b":{"bounds":[0,0,0,1,1,1],
                     "placements":[{"definition":"test:a","as":"a","at":[0,0,0]}]}},
                 "placements":[{"definition":"test:a","as":"a","at":[0,0,0]}],
                 "bays":[],"checks":[]}
                """;
        CompileResult looped = DocumentCompiler.compile(read(cycle), context());
        assertNull(looped.blueprint());
        assertEquals(
            FindingCode.of(FindingCode.Family.doc, 9),
            Objects.requireNonNull(looped.findings().primary()).code()
        );
    }

    @Test
    void writerKeepsCanonicalMemberOrderInsideObjects() {
        String text = new JsonWriter().write(read(richDocument));
        assertOrdered(
            text,
            "\"id\": \"floor\"",
            "\"boxes\"",
            "\"coverage\"",
            "\"footprint\"",
            "\"sharing\"",
            "\"description\""
        );
        assertOrdered(text, "\"id\": \"portal\"", "\"bay\"", "\"focus\"", "\"expect\"", "\"description\"");
        int focus = text.indexOf("\"focus\"");
        int kind = text.indexOf("\"kind\"", focus);
        int cell = text.indexOf("\"cell\"", focus);
        assertTrue(kind >= 0 && kind < cell, "kind is the first focus member");
        assertOrdered(text, "\"test:unit\"", "\"bounds\"", "\"bays\"", "\"seams\"");
        assertOrdered(text, "\"properties\"", "\"rate\"", "\"side\"");
    }

    @Test
    void definitionsAndExtensionsAreOrdered() {
        String first = """
                {"schemaVersion":1,"id":"test:order","bounds":[0,0,0,1,1,1],
                 "definitions":{
                   "test:z":{"bounds":[0,0,0,1,1,1]},
                   "test:a":{"bounds":[0,0,0,1,1,1],"arguments":{"k":1}}},
                 "bays":[],"checks":[],
                 "extensions":{"test:z":{"v":1},"test:a":{"v":2}}}
                """;
        Document document = read(first);
        assertEquals(
            List.of(NamespacedId.of("test", "a"), NamespacedId.of("test", "z")),
            List.copyOf(document.definitions().keySet())
        );
        assertEquals(List.of("test:a", "test:z"), List.copyOf(document.extensions().keySet()));
        String text = new JsonWriter().write(document);
        assertTrue(text.indexOf("\"test:a\"") < text.indexOf("\"test:z\""), "definition keys ascend");
    }

    @Test
    void crlfDocumentsKeepTheirOffsets() {
        String text = "{\r\n  \"schemaVersion\": 1,\r\n  \"id\": \"test:crlf\",\r\n"
                + "  \"bounds\": [0,0,0,1,1,1],\r\n  \"bays\": [],\r\n  \"checks\": [],\r\n" + "  \"seams\": []\r\n}";
        List<Finding> errors = new ArrayList<>();
        assertNull(new JsonReader().read(documentId, text, errors));
        Finding finding = errors.stream().filter(f -> f.code().equals(FindingCode.of(FindingCode.Family.doc, 2)))
                .findFirst().orElseThrow();
        var span = Objects.requireNonNull(Objects.requireNonNull(finding.primaryLabel()).locus().source());
        assertEquals("\"seams\"", text.substring(span.start(), span.end()));

        String truncated = "{\r\n  \"schemaVersion\": 1,\r\n  \"id\": \"test:crlf\"\r\n";
        List<Finding> parseErrors = new ArrayList<>();
        assertNull(new JsonReader().read(documentId, truncated, parseErrors));
        Finding parse = parseErrors.get(0);
        assertEquals(FindingCode.of(FindingCode.Family.doc, 13), parse.code());
        assertTrue(parse.hasPrimary(), "the CRLF parse failure is located");
        var parseSpan = Objects.requireNonNull(Objects.requireNonNull(parse.primaryLabel()).locus().source());
        assertTrue(parseSpan.start() >= 0 && parseSpan.start() <= truncated.length());
    }

    @Test
    void everyTruncationOfTheRepresentativeDocumentIsSafe() {
        JsonReader reader = new JsonReader();
        for (int length = 0; length < richDocument.length(); length++) {
            String prefix = richDocument.substring(0, length);
            List<Finding> errors = new ArrayList<>();
            assertDoesNotThrow(() -> reader.read(documentId, prefix, errors), () -> "length " + prefix.length());
        }
    }

    @Test
    void documentMapsStayImmutableAndOrdered() {
        Document document = read(richDocument);
        assertThrows(UnsupportedOperationException.class, () -> document.definitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.extensions().clear());
        assertEquals(List.of(NamespacedId.of("test", "unit")), List.copyOf(document.definitions().keySet()));
    }

    @Test
    void aDeclaredFootprintOverAnotherBaysOwnedCellsFailsCompile() {
        String text = """
                {"schemaVersion":1,"id":"test:owned_overlap","bounds":[0,0,0,2,1,1],
                 "bays":[
                   {"id":"core","box":[0,0,0,1,1,1],"coverage":"every_cell",
                    "footprint":"box","sharing":"exclusive"},
                   {"id":"apron","box":[1,0,0,1,1,1],"coverage":"every_cell",
                    "footprint":{"declared":[[0,0,0,1,1,1]]},"sharing":"shared"}],
                 "checks":[
                   {"id":"core_check","bay":"core","focus":{"kind":"occupant","cell":[0,0,0]},
                    "expect":{"kind":"any_occupant"}},
                   {"id":"apron_check","bay":"apron","focus":{"kind":"occupant","cell":[1,0,0]},
                    "expect":{"kind":"any_occupant"}}]}
                """;
        CompileResult result = DocumentCompiler.compile(read(text), context());
        assertNull(result.blueprint());
        assertEquals(
            FindingCode.of(FindingCode.Family.bpt, 2),
            Objects.requireNonNull(result.findings().primary()).code()
        );
    }

    private static void assertOrdered(String text, String anchor, String... members) {
        int index = text.indexOf(anchor);
        assertTrue(index >= 0, () -> "missing anchor " + anchor);
        for (String member : members) {
            int next = text.indexOf(member, index + anchor.length());
            assertTrue(next > index, () -> "expected " + member + " after " + anchor);
            index = next;
        }
    }

    private static CompilerContext context() {
        Catalogs catalogs = Catalogs.builtin();
        return new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            DocumentSource.empty()
        );
    }

    private static Document read(String text) {
        List<Finding> errors = new ArrayList<>();
        Document document = new JsonReader().read(documentId, text, errors);
        assertTrue(errors.isEmpty(), () -> "unexpected findings: " + errors);
        assertNotNull(document, "the document resolves");
        return document;
    }

    private static Document withoutSpans(Document document) {
        return new Document(
            document.schemaVersion(),
            withoutSpans(document.root()),
            document.definitions().entrySet().stream().collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> withoutSpans(entry.getValue()),
                    (left, right) -> left,
                    TreeMap::new
                )
            ),
            document.extensions()
        );
    }

    private static BlueprintSpec withoutSpans(BlueprintSpec spec) {
        List<BaySpec> bays = spec.bays().stream().map(JsonDocumentTest::withoutSpans).toList();
        List<SeamSpec> seams = spec.seams().stream().map(JsonDocumentTest::withoutSpans).toList();
        List<CheckSpec> checks = spec.checks().stream().map(JsonDocumentTest::withoutSpans).toList();
        List<ComputationSpec> computations = spec.computations().stream().map(JsonDocumentTest::withoutSpans).toList();
        List<KeySpec> keys = spec.keys().stream().map(JsonDocumentTest::withoutSpans).toList();
        List<PlacementSpec> placements = spec.placements().stream().map(JsonDocumentTest::withoutSpans).toList();
        return new BlueprintSpec(
            spec.id(),
            spec.bounds(),
            spec.envelope(),
            spec.arguments(),
            bays,
            seams,
            checks,
            computations,
            keys,
            spec.derivations(),
            spec.anchors(),
            spec.orientation(),
            placements,
            spec.extensions(),
            null
        );
    }

    private static BaySpec withoutSpans(BaySpec spec) {
        return new BaySpec(
            spec.id(),
            spec.cells(),
            spec.coverage(),
            spec.footprint(),
            spec.sharing(),
            spec.description(),
            null
        );
    }

    private static SeamSpec withoutSpans(SeamSpec spec) {
        return new SeamSpec(spec.id(), spec.cells(), spec.kind(), spec.properties(), null);
    }

    private static CheckSpec withoutSpans(CheckSpec spec) {
        return new CheckSpec(
            spec.id(),
            spec.bay(),
            spec.focusKind(),
            spec.focusArgs(),
            spec.expectationKind(),
            spec.expectationArgs(),
            spec.description(),
            null
        );
    }

    private static ComputationSpec withoutSpans(ComputationSpec spec) {
        return new ComputationSpec(
            spec.id(),
            spec.kind(),
            spec.args(),
            spec.region(),
            spec.bay(),
            spec.output(),
            spec.inputs(),
            null
        );
    }

    private static KeySpec withoutSpans(KeySpec spec) {
        return new KeySpec(spec.id(), spec.type(), spec.retention(), spec.merge(), spec.whenAbsent(), null);
    }

    private static PlacementSpec withoutSpans(PlacementSpec spec) {
        return new PlacementSpec(spec.definition(), spec.offset(), spec.alias(), null);
    }
}
