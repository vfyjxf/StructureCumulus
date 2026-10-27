package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Computations;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentCompilerTest {

    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone_bricks");
    private static final NamespacedId campfire = NamespacedId.of("minecraft", "campfire");

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
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

    @Test
    void campfireDocumentCompilesToTheJavaBlueprint() {
        CuboidSet shellCells = CuboidSet.of(Cuboid.of(0, 0, 0, 3, 3, 3)).subtract(Cuboid.of(1, 1, 1, 2, 2, 2))
                .subtract(Cuboid.of(1, 0, 1, 2, 1, 2));
        CuboidSet hollowCells = CuboidSet.of(Cuboid.of(1, 1, 1, 2, 2, 2));
        CuboidSet fireCells = CuboidSet.of(Cuboid.of(1, 0, 1, 2, 1, 2));
        ContextKey<Long> air = ContextKeys.of(id("air_cells"), Long.class, Retention.instance, Merges.sum(), null);

        Value block = Value.object(Map.of("kind", Value.string("block"), "id", Value.string("minecraft:stone_bricks")));
        Value airElement = Value.object(Map.of("kind", Value.string("air")));
        Value campfireBlock = Value
                .object(Map.of("kind", Value.string("block"), "id", Value.string("minecraft:campfire")));
        Value litProperty = Value.object(
            Map.of("kind", Value.string("property"), "key", Value.string("lit"), "value", Value.string("true"))
        );
        Value allOf = Value.object(Map.of("children", Value.array(List.of(campfireBlock, litProperty))));
        Value occupantCell = Value
                .object(Map.of("cell", Value.array(List.of(Value.number(1), Value.number(0), Value.number(1)))));

        BlueprintSpec root = new BlueprintSpec(
            id("campfire"),
            Cuboid.of(0, 0, 0, 3, 3, 3),
            Value.object(),
            List.of(
                new BaySpec(
                    id("shell"),
                    shellCells,
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.exclusive,
                    null,
                    null
                ),
                new BaySpec(
                    id("hollow"),
                    hollowCells,
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.exclusive,
                    null,
                    null
                ),
                new BaySpec(id("fire"), fireCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive, null, null)
            ),
            List.of(),
            List.of(
                new CheckSpec(
                    id("shell_bricks"),
                    id("shell"),
                    id("bay"),
                    Value.object(),
                    id("each_cell"),
                    Value.object(Map.of("element", block)),
                    null,
                    null
                ),
                new CheckSpec(
                    id("hollow_air"),
                    id("hollow"),
                    id("bay"),
                    Value.object(),
                    id("each_cell"),
                    Value.object(Map.of("element", airElement)),
                    null,
                    null
                ),
                new CheckSpec(id("lit"), id("fire"), id("occupant"), occupantCell, id("all_of"), allOf, null, null)
            ),
            List.of(
                new ComputationSpec(
                    id("air_cells"),
                    id("air"),
                    Value.object(),
                    hollowCells,
                    null,
                    id("air_cells"),
                    Set.of(),
                    null
                )
            ),
            List.of(new KeySpec(id("air_cells"), "long", Retention.instance, NamespacedId.of("sum"), null, null)),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null
        );
        Document document = new Document(1, root, Map.of(), Map.of());

        CompileResult result = DocumentCompiler.compile(document, context());
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint compiled = result.blueprint();
        assertNotNull(compiled);

        Bay shell = Bay.of(id("shell"), shellCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay hollow = Bay.of(id("hollow"), hollowCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay fire = Bay.of(id("fire"), fireCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Blueprint javaBlueprint = Blueprint.builder(id("campfire")).bounds(Cuboid.of(0, 0, 0, 3, 3, 3))
                .check(
                    Check.of(id("shell_bricks"), shell, Focuses.bay(), Expectations.eachCell(Expectations.block(stone)))
                ).check(Check.of(id("hollow_air"), hollow, Focuses.bay(), Expectations.eachCell(Expectations.air())))
                .check(
                    Check.of(
                        id("lit"),
                        fire,
                        Focuses.occupant(LatticePoint.of(1, 0, 1)),
                        Expectations.allOf(Expectations.block(campfire), Expectations.property("lit", "true"))
                    )
                ).computation(Computations.air(air, hollowCells)).build();
        assertEquals(javaBlueprint, compiled);
    }

    @Test
    void placementQualifiesAndTranslates() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        BlueprintSpec definition = new BlueprintSpec(
            id("unit"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            Value.object(),
            List.of(new BaySpec(id("wall"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive, null, null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null
        );
        Value block = Value.object(Map.of("kind", Value.string("block"), "id", Value.string("minecraft:stone_bricks")));
        BlueprintSpec root = new BlueprintSpec(
            id("root"),
            Cuboid.of(0, 0, 0, 2, 1, 1),
            Value.object(),
            List.of(),
            List.of(),
            List.of(
                new CheckSpec(
                    id("wall_check"),
                    id("south/wall"),
                    id("bay"),
                    Value.object(),
                    id("each_cell"),
                    Value.object(Map.of("element", block)),
                    null,
                    null
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(new PlacementSpec(id("unit"), LatticePoint.of(1, 0, 0), id("south"), null)),
            Map.of(),
            null
        );
        Document document = new Document(1, root, Map.of(id("unit"), definition), Map.of());
        CompileResult result = DocumentCompiler.compile(document, context());
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint blueprint = result.blueprint();
        assertNotNull(blueprint);
        assertNotNull(blueprint.bay(id("south/wall")));
        assertEquals(
            CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1)),
            Objects.requireNonNull(blueprint.bay(id("south/wall")), "bay").cells()
        );
    }

    @Test
    void badSchemaAndMissingBayAreFindingsNotExceptions() {
        BlueprintSpec root = new BlueprintSpec(
            id("bad"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            Value.object(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null
        );
        CompileResult version = DocumentCompiler.compile(new Document(2, root, Map.of(), Map.of()), context());
        assertNull(version.blueprint());
        assertEquals(
            FindingCode.of(FindingCode.Family.doc, 1),
            Objects.requireNonNull(version.findings().primary(), "primary").code()
        );

        CheckSpec missingBay = new CheckSpec(
            id("check"),
            id("absent"),
            id("bay"),
            Value.object(),
            id("each_cell"),
            Value.object(Map.of("element", Value.object(Map.of("kind", Value.string("air"))))),
            null,
            null
        );
        BlueprintSpec root2 = new BlueprintSpec(
            id("bad"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            Value.object(),
            List.of(),
            List.of(),
            List.of(missingBay),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null
        );
        CompileResult missing = DocumentCompiler.compile(new Document(1, root2, Map.of(), Map.of()), context());
        assertNull(missing.blueprint());
        assertFalse(missing.findings().isEmpty());
        assertEquals(
            FindingCode.of(FindingCode.Family.doc, 6),
            Objects.requireNonNull(missing.findings().primary(), "primary").code()
        );
    }

    @Test
    void documentBuildErrorsKeepTheirSpecificCode() {
        CuboidSet cell = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        Value air = Value.object(Map.of("kind", Value.string("air")));
        BlueprintSpec root = new BlueprintSpec(
            id("overlap"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            Value.object(),
            List.of(
                new BaySpec(id("a"), cell, Coverage.everyCell, Footprint.box(), Sharing.exclusive, null, null),
                new BaySpec(id("b"), cell, Coverage.everyCell, Footprint.box(), Sharing.exclusive, null, null)
            ),
            List.of(),
            List.of(
                new CheckSpec(
                    id("a_check"),
                    id("a"),
                    id("bay"),
                    Value.object(),
                    id("each_cell"),
                    Value.object(Map.of("element", air)),
                    null,
                    null
                ),
                new CheckSpec(
                    id("b_check"),
                    id("b"),
                    id("bay"),
                    Value.object(),
                    id("each_cell"),
                    Value.object(Map.of("element", air)),
                    null,
                    null
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null
        );
        CompileResult result = DocumentCompiler.compile(new Document(1, root, Map.of(), Map.of()), context());
        assertNull(result.blueprint());
        assertEquals(
            FindingCode.of(FindingCode.Family.bpt, 2),
            Objects.requireNonNull(result.findings().primary(), "primary").code()
        );
    }

    @Test
    void traitAbsentContributesItsTraitIdToReads() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        NamespacedId energy = id("energy");
        Value cell = Value
                .object(Map.of("cell", Value.array(List.of(Value.number(0), Value.number(0), Value.number(0)))));
        BlueprintSpec root = new BlueprintSpec(
            id("trait_absent"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            Value.object(),
            List.of(new BaySpec(id("bay"), cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive, null, null)),
            List.of(),
            List.of(
                new CheckSpec(
                    id("absent"),
                    id("bay"),
                    id("occupant"),
                    cell,
                    id("trait_absent"),
                    Value.object(Map.of("id", Value.string("test:energy"))),
                    null,
                    null
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null
        );
        CompileResult result = DocumentCompiler.compile(new Document(1, root, Map.of(), Map.of()), context());
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint blueprint = result.blueprint();
        assertNotNull(blueprint);
        assertEquals(Set.of(energy), blueprint.reads().traits());
    }
}
