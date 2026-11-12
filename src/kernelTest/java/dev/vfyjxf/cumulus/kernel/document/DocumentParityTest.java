package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.context.Merges;
import dev.vfyjxf.cumulus.kernel.core.Catalog;
import dev.vfyjxf.cumulus.kernel.core.ContextKey;
import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Computations;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.DerivationSpec;
import dev.vfyjxf.cumulus.kernel.structure.DoorView;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationFactory;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.FactoryContext;
import dev.vfyjxf.cumulus.kernel.structure.Focus;
import dev.vfyjxf.cumulus.kernel.structure.FocusFactory;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.structure.Seam;
import dev.vfyjxf.cumulus.kernel.structure.SeamId;
import dev.vfyjxf.cumulus.kernel.structure.SeamKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** I-13: a document and the Java builder produce the same blueprint, and the blueprint-path codes fire. */
class DocumentParityTest {

    private static final NamespacedId bricks = NamespacedId.of("minecraft", "bricks");
    private static final NamespacedId stoneBricks = NamespacedId.of("minecraft", "stone_bricks");

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static NamespacedId kind(String path) {
        return NamespacedId.of(path);
    }

    @Test
    void theFurnaceMatchesTheBuilder() {
        CuboidSet shell = CuboidSet.of(Cuboid.of(0, 0, 0, 7, 6, 7)).subtract(CuboidSet.of(Cuboid.of(1, 1, 1, 6, 5, 6)))
                .subtract(CuboidSet.of(Cuboid.of(3, 1, 0, 4, 3, 1)));
        CuboidSet cavityCells = CuboidSet.of(Cuboid.of(1, 1, 1, 6, 5, 6));
        CuboidSet mouthCells = CuboidSet.of(Cuboid.of(3, 1, 0, 4, 3, 1));
        ContextKey<Long> volume = ContextKeys.of(id("volume"), Long.class, Retention.instance, Merges.sum());

        Bay wall = Bay.of(id("wall"), shell, Coverage.everyCell, Footprint.bound(), Sharing.exclusive);
        Bay cavity = Bay.of(id("cavity"), cavityCells, Coverage.everyCell, Footprint.box(), Sharing.exclusive);
        Bay mouth = Bay.of(id("mouth"), mouthCells, Coverage.oneSubject, Footprint.bound(), Sharing.exclusive);
        Blueprint built = Blueprint.builder(id("huge_furnace")).bounds(Cuboid.of(0, 0, 0, 7, 6, 7))
                .check(
                    Check.of(id("wall_bricks"), wall, Focuses.bay(), Expectations.eachCell(Expectations.block(bricks)))
                ).check(Check.of(id("cavity_air"), cavity, Focuses.bay(), Expectations.eachCell(Expectations.air())))
                .check(
                    Check.of(id("mouth_door"), mouth, Focuses.door(LatticePoint.of(3, 1, 0)), new TestDoorExpectation())
                ).computation(Computations.volume(volume, cavityCells))
                .derive(new DerivationSpec(id("capacity"), Value.object(), id("capacity"), 0)).build();

        Value brickArg = Value.object(Map.of("kind", Value.string("block"), "id", Value.string("minecraft:bricks")));
        Value airArg = Value.object(Map.of("kind", Value.string("air")));
        BlueprintSpec root = new BlueprintSpec(
            id("huge_furnace"),
            Cuboid.of(0, 0, 0, 7, 6, 7),
            false,
            false,
            Value.object(),
            List.of(
                new BaySpec(
                    id("wall"),
                    shell,
                    Coverage.everyCell,
                    Footprint.bound(),
                    Sharing.exclusive,
                    null,
                    null,
                    null
                ),
                new BaySpec(
                    id("cavity"),
                    cavityCells,
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.exclusive,
                    null,
                    null,
                    null
                ),
                new BaySpec(
                    id("mouth"),
                    mouthCells,
                    Coverage.oneSubject,
                    Footprint.bound(),
                    Sharing.exclusive,
                    null,
                    null,
                    null
                )
            ),
            List.of(),
            List.of(
                new CheckSpec(
                    id("wall_bricks"),
                    id("wall"),
                    kind("bay"),
                    Value.object(),
                    kind("each_cell"),
                    Value.object(Map.of("element", brickArg)),
                    null,
                    null
                ),
                new CheckSpec(
                    id("cavity_air"),
                    id("cavity"),
                    kind("bay"),
                    Value.object(),
                    kind("each_cell"),
                    Value.object(Map.of("element", airArg)),
                    null,
                    null
                ),
                new CheckSpec(
                    id("mouth_door"),
                    id("mouth"),
                    kind("door"),
                    Value.object(
                        Map.of("origin", Value.array(List.of(Value.number(3), Value.number(1), Value.number(0))))
                    ),
                    id("oak_door"),
                    Value.object(),
                    null,
                    null
                )
            ),
            List.of(
                new ComputationSpec(
                    id("volume"),
                    kind("volume"),
                    Value.object(),
                    cavityCells,
                    null,
                    id("volume"),
                    Set.of(),
                    null
                )
            ),
            List.of(
                new KeySpec(id("volume"), "long", Retention.instance, NamespacedId.of("sum"), null),
                new KeySpec(id("capacity"), "long", Retention.instance, null, null)
            ),
            List.of(new DerivationSpec(id("capacity"), Value.object(), id("capacity"), 0)),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null,
            null
        );

        CompileResult result = DocumentCompiler
                .compile(new Document(1, root, Map.of(), Map.of()), context(List.of(), List.of(doorFactory())));
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint compiled = result.blueprint();
        assertNotNull(compiled);
        assertEquals(built, compiled);
        assertEquals(built.plan(), compiled.plan());
        assertEquals(built.reads(), compiled.reads());
    }

    @Test
    void everyBuilderCapabilityHasADocumentForm() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 4, 1, 1);
        CuboidSet wallCells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 1, 1));
        CuboidSet unitCells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        CuboidSet eastCells = CuboidSet.of(Cuboid.of(2, 0, 0, 3, 1, 1));
        CuboidSet westCells = CuboidSet.of(Cuboid.of(3, 0, 0, 4, 1, 1));
        OrientationPolicy policy = OrientationPolicy.builder(id("front")).allow(Orientation.identity).build();
        Value arguments = Value.object(Map.of("mode", Value.string("fast")));
        DerivationSpec derivation = new DerivationSpec(id("capacity"), Value.object(), id("capacity"), 0);

        Bay wall = Bay.of(
            id("wall"),
            wallCells,
            Coverage.everyCell,
            Footprint.declared(wallCells),
            Sharing.shared,
            "the root wall"
        );
        Bay eastCell = Bay.of(id("east/cell"), eastCells, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Bay westCell = Bay.of(id("west/cell"), westCells, Coverage.everyCell, Footprint.box(), Sharing.shared);
        Blueprint built = Blueprint.builder(id("capabilities")).bounds(bounds)
                .check(
                    Check.of(
                        id("wall_check"),
                        wall,
                        Focuses.bay(),
                        Expectations.eachCell(Expectations.block(stoneBricks)),
                        "the wall bricks"
                    )
                )
                .check(
                    Check.of(
                        id("east_cells"),
                        eastCell,
                        Focuses.occupant(LatticePoint.of(2, 0, 0)),
                        Expectations.block(stoneBricks)
                    )
                )
                .check(
                    Check.of(
                        id("west_cells"),
                        westCell,
                        Focuses.occupant(LatticePoint.of(3, 0, 0)),
                        Expectations.block(stoneBricks)
                    )
                )
                .seam(
                    new Seam(
                        new SeamId(id("root_port")),
                        Cuboid.of(0, 0, 0, 1, 1, 1),
                        SeamKind.item,
                        Map.of("kind", "input")
                    )
                ).seam(new Seam(new SeamId(id("east/port")), Cuboid.of(2, 0, 0, 3, 1, 1), SeamKind.fluid, Map.of()))
                .seam(new Seam(new SeamId(id("west/port")), Cuboid.of(3, 0, 0, 4, 1, 1), SeamKind.fluid, Map.of()))
                .anchor(new AnchorSpec(id("front"), "facing", "north")).orientation(policy).derive(derivation)
                .arguments(arguments).build();

        Value blockArg = Value
                .object(Map.of("kind", Value.string("block"), "id", Value.string("minecraft:stone_bricks")));
        BlueprintSpec unit = new BlueprintSpec(
            id("unit"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            false,
            false,
            Value.object(),
            List.of(
                new BaySpec(
                    id("cell"),
                    unitCells,
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.shared,
                    null,
                    null,
                    null
                )
            ),
            List.of(new SeamSpec(new SeamId(id("port")), Cuboid.of(0, 0, 0, 1, 1, 1), SeamKind.fluid, Map.of(), null)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null,
            null
        );
        BlueprintSpec root = new BlueprintSpec(
            id("capabilities"),
            bounds,
            false,
            false,
            arguments,
            List.of(
                new BaySpec(
                    id("wall"),
                    wallCells,
                    Coverage.everyCell,
                    Footprint.declared(wallCells),
                    Sharing.shared,
                    "the root wall",
                    null,
                    null
                )
            ),
            List.of(
                new SeamSpec(
                    new SeamId(id("root_port")),
                    Cuboid.of(0, 0, 0, 1, 1, 1),
                    SeamKind.item,
                    Map.of("kind", "input"),
                    null
                )
            ),
            List.of(
                new CheckSpec(
                    id("wall_check"),
                    id("wall"),
                    kind("bay"),
                    Value.object(),
                    kind("each_cell"),
                    Value.object(Map.of("element", blockArg)),
                    "the wall bricks",
                    null
                ),
                new CheckSpec(
                    id("east_cells"),
                    id("east/cell"),
                    kind("occupant"),
                    Value.object(
                        Map.of("cell", Value.array(List.of(Value.number(2), Value.number(0), Value.number(0))))
                    ),
                    kind("block"),
                    Value.object(Map.of("id", Value.string("minecraft:stone_bricks"))),
                    null,
                    null
                ),
                new CheckSpec(
                    id("west_cells"),
                    id("west/cell"),
                    kind("occupant"),
                    Value.object(
                        Map.of("cell", Value.array(List.of(Value.number(3), Value.number(0), Value.number(0))))
                    ),
                    kind("block"),
                    Value.object(Map.of("id", Value.string("minecraft:stone_bricks"))),
                    null,
                    null
                )
            ),
            List.of(),
            List.of(new KeySpec(id("capacity"), "long", Retention.instance, null, null)),
            List.of(derivation),
            List.of(new AnchorSpec(id("front"), "facing", "north")),
            policy,
            List.of(
                new PlacementSpec(id("unit"), LatticePoint.of(2, 0, 0), id("east"), null),
                new PlacementSpec(id("unit"), LatticePoint.of(3, 0, 0), id("west"), null)
            ),
            Map.of(),
            null,
            null
        );

        CompileResult result = DocumentCompiler
                .compile(new Document(1, root, Map.of(id("unit"), unit), Map.of()), context(List.of(), List.of()));
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint compiled = result.blueprint();
        assertNotNull(compiled);
        assertEquals(built, compiled);
        assertEquals(built.plan(), compiled.plan());
        assertEquals(built.reads(), compiled.reads());
    }

    @Test
    void nestedAndTraitFocusesContributeEqualReads() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        CuboidSet nestedCells = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        CuboidSet traitCells = CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1));
        Bay nestedBay = Bay.of(id("nested_bay"), nestedCells, Coverage.everyCell, Footprint.none(), Sharing.shared);
        Bay traitBay = Bay.of(id("trait_bay"), traitCells, Coverage.everyCell, Footprint.none(), Sharing.shared);
        Blueprint built = Blueprint.builder(id("reads_parity")).bounds(bounds)
                .check(Check.of(id("nested"), nestedBay, Focuses.nested(LatticePoint.origin), new NestedProbe()))
                .check(
                    Check.of(
                        id("trait"),
                        traitBay,
                        Focuses.occupant(LatticePoint.of(1, 0, 0)),
                        Expectations.trait(new TraitKey<>(id("energy"), Object.class))
                    )
                ).build();

        BlueprintSpec root = new BlueprintSpec(
            id("reads_parity"),
            bounds,
            false,
            false,
            Value.object(),
            List.of(
                new BaySpec(
                    id("nested_bay"),
                    nestedCells,
                    Coverage.everyCell,
                    Footprint.none(),
                    Sharing.shared,
                    null,
                    null,
                    null
                ),
                new BaySpec(
                    id("trait_bay"),
                    traitCells,
                    Coverage.everyCell,
                    Footprint.none(),
                    Sharing.shared,
                    null,
                    null,
                    null
                )
            ),
            List.of(),
            List.of(
                new CheckSpec(
                    id("nested"),
                    id("nested_bay"),
                    kind("nested"),
                    Value.object(
                        Map.of("cell", Value.array(List.of(Value.number(0), Value.number(0), Value.number(0))))
                    ),
                    id("nested_probe"),
                    Value.object(),
                    null,
                    null
                ),
                new CheckSpec(
                    id("trait"),
                    id("trait_bay"),
                    kind("occupant"),
                    Value.object(
                        Map.of("cell", Value.array(List.of(Value.number(1), Value.number(0), Value.number(0))))
                    ),
                    kind("trait"),
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
            null,
            null
        );

        CompileResult result = DocumentCompiler
                .compile(new Document(1, root, Map.of(), Map.of()), context(List.of(), List.of(nestedProbeFactory())));
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint compiled = result.blueprint();
        assertNotNull(compiled);
        assertEquals(built, compiled);
        assertEquals(built.reads(), compiled.reads());
        assertTrue(compiled.reads().kinds().contains(OccupantKind.nested));
        assertEquals(Set.of(id("energy")), compiled.reads().traits());
    }

    @Test
    void definitionCycleIsLocated() {
        BlueprintSpec a = definitionOnly(
            id("def_a"),
            List.of(new PlacementSpec(id("def_b"), LatticePoint.origin, id("b"), null))
        );
        BlueprintSpec b = definitionOnly(
            id("def_b"),
            List.of(new PlacementSpec(id("def_a"), LatticePoint.origin, id("a"), null))
        );
        BlueprintSpec root = rootWithPlacements(
            id("cycled"),
            List.of(),
            List.of(),
            List.of(new PlacementSpec(id("def_a"), LatticePoint.origin, id("root_a"), null))
        );
        CompileResult result = DocumentCompiler.compile(
            new Document(1, root, Map.of(id("def_a"), a, id("def_b"), b), Map.of()),
            context(List.of(), List.of())
        );
        assertNull(result.blueprint());
        List<Finding> found = result.findings().ofCode(Codes.definitionCycle.code());
        assertEquals(1, found.size(), () -> result.findings().findings().toString());
        assertTrue(found.get(0).hasPrimary(), () -> found.get(0).toString());
    }

    @Test
    void aMissingDefinitionIsDoc0011() {
        BlueprintSpec root = rootWithPlacements(
            id("ghosted"),
            List.of(),
            List.of(),
            List.of(new PlacementSpec(id("ghost"), LatticePoint.origin, id("g"), null))
        );
        CompileResult result = DocumentCompiler
                .compile(new Document(1, root, Map.of(), Map.of()), context(List.of(), List.of()));
        assertNull(result.blueprint());
        List<Finding> found = result.findings().ofCode(Codes.definitionMissing.code());
        assertEquals(1, found.size(), () -> result.findings().findings().toString());
        assertTrue(found.get(0).hasPrimary(), () -> found.get(0).toString());
    }

    @Test
    void aPlacedBayOutsideTheRootIsDoc0005() {
        BlueprintSpec unit = new BlueprintSpec(
            id("unit"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            false,
            false,
            Value.object(),
            List.of(cellBay(id("cell"), CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)))),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null,
            null
        );
        BlueprintSpec root = rootWithPlacements(
            id("overflow"),
            List.of(),
            List.of(airCheck(id("east_check"), id("east/cell"))),
            List.of(new PlacementSpec(id("unit"), LatticePoint.of(2, 0, 0), id("east"), null))
        );
        CompileResult result = DocumentCompiler
                .compile(new Document(1, root, Map.of(id("unit"), unit), Map.of()), context(List.of(), List.of()));
        assertNull(result.blueprint());
        List<Finding> found = result.findings().ofCode(Codes.placementOutside.code());
        assertEquals(1, found.size(), () -> result.findings().findings().toString());
        assertTrue(found.get(0).message().contains("leaves the blueprint bounds"), () -> found.get(0).message());
    }

    @Test
    void duplicateIdsPerKindAreDoc0004() {
        CuboidSet cellsA = CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1));
        CuboidSet cellsB = CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1));

        BlueprintSpec twinBays = rootWithPlacements(
            id("twin_bays"),
            List.of(cellBay(id("dup"), cellsA), cellBay(id("dup"), cellsB)),
            List.of(airCheck(id("first"), id("dup")), airCheck(id("second"), id("dup"))),
            List.of()
        );
        CompileResult bayResult = compile(twinBays);
        assertNull(bayResult.blueprint());
        assertEquals(
            1,
            bayResult.findings().ofCode(Codes.documentDuplicate.code()).size(),
            () -> bayResult.findings().findings().toString()
        );

        BlueprintSpec twinSeams = new BlueprintSpec(
            id("twin_seams"),
            Cuboid.of(0, 0, 0, 2, 1, 1),
            false,
            false,
            Value.object(),
            List.of(cellBay(id("wall"), cellsA)),
            List.of(
                new SeamSpec(new SeamId(id("port")), Cuboid.of(0, 0, 0, 1, 1, 1), SeamKind.item, Map.of(), null),
                new SeamSpec(new SeamId(id("port")), Cuboid.of(0, 0, 0, 1, 1, 1), SeamKind.fluid, Map.of(), null)
            ),
            List.of(airCheck(id("cells"), id("wall"))),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null,
            null
        );
        CompileResult seamResult = compile(twinSeams);
        assertNull(seamResult.blueprint());
        assertEquals(
            1,
            seamResult.findings().ofCode(Codes.documentDuplicate.code()).size(),
            () -> seamResult.findings().findings().toString()
        );

        BlueprintSpec twinChecks = rootWithPlacements(
            id("twin_checks"),
            List.of(cellBay(id("wall"), cellsA)),
            List.of(airCheck(id("dup"), id("wall")), airCheck(id("dup"), id("wall"))),
            List.of()
        );
        CompileResult checkResult = compile(twinChecks);
        assertNull(checkResult.blueprint());
        assertEquals(
            1,
            checkResult.findings().ofCode(Codes.documentDuplicate.code()).size(),
            () -> checkResult.findings().findings().toString()
        );

        BlueprintSpec twinKeys = rootWithKeys(
            List.of(
                new KeySpec(id("k"), "long", Retention.instance, null, null),
                new KeySpec(id("k"), "int", Retention.instance, null, null)
            )
        );
        CompileResult keyResult = compile(twinKeys);
        assertNull(keyResult.blueprint());
        assertEquals(
            1,
            keyResult.findings().ofCode(Codes.documentDuplicate.code()).size(),
            () -> keyResult.findings().findings().toString()
        );

        BlueprintSpec equalKeys = rootWithKeys(
            List.of(
                new KeySpec(id("k"), "long", Retention.instance, null, null),
                new KeySpec(id("k"), "long", Retention.instance, null, null)
            )
        );
        CompileResult equalResult = compile(equalKeys);
        assertTrue(equalResult.findings().isEmpty(), () -> equalResult.findings().findings().toString());
        assertNotNull(equalResult.blueprint(), "an identical key declared twice is a no-op");
    }

    @Test
    void derivationOrderAndOutputDuplicatesAreDoc0010() {
        BlueprintSpec twinOrders = rootWithKeysAndDerivations(
            List.of(
                new KeySpec(id("out1"), "long", Retention.instance, null, null),
                new KeySpec(id("out2"), "long", Retention.instance, null, null)
            ),
            List.of(
                new DerivationSpec(id("deriver_a"), Value.object(), id("out1"), 0),
                new DerivationSpec(id("deriver_b"), Value.object(), id("out2"), 0)
            )
        );
        CompileResult orderResult = compile(twinOrders);
        assertNull(orderResult.blueprint());
        List<Finding> orderFindings = orderResult.findings().ofCode(Codes.deriveDuplicate.code());
        assertEquals(1, orderFindings.size(), () -> orderResult.findings().findings().toString());
        assertTrue(
            orderFindings.get(0).message().contains("duplicate derivation order"),
            () -> orderFindings.get(0).message()
        );

        BlueprintSpec twinOutputs = rootWithKeysAndDerivations(
            List.of(new KeySpec(id("out1"), "long", Retention.instance, null, null)),
            List.of(
                new DerivationSpec(id("deriver_a"), Value.object(), id("out1"), 0),
                new DerivationSpec(id("deriver_b"), Value.object(), id("out1"), 1)
            )
        );
        CompileResult outputResult = compile(twinOutputs);
        assertNull(outputResult.blueprint());
        List<Finding> outputFindings = outputResult.findings().ofCode(Codes.deriveDuplicate.code());
        assertEquals(1, outputFindings.size(), () -> outputResult.findings().findings().toString());
        assertTrue(
            outputFindings.get(0).message().contains("two derivations produce"),
            () -> outputFindings.get(0).message()
        );
    }

    @Test
    void anUndeclaredKeyIsDoc0007() {
        BlueprintSpec computation = new BlueprintSpec(
            id("undeclared_output"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            false,
            false,
            Value.object(),
            List.of(cellBay(id("wall"), CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)))),
            List.of(),
            List.of(airCheck(id("cells"), id("wall"))),
            List.of(
                new ComputationSpec(
                    id("ghost"),
                    kind("volume"),
                    Value.object(),
                    CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
                    null,
                    id("ghost"),
                    Set.of(),
                    null
                )
            ),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            Map.of(),
            null,
            null
        );
        CompileResult computationResult = compile(computation);
        assertNull(computationResult.blueprint());
        assertEquals(
            1,
            computationResult.findings().ofCode(Codes.keyUndeclared.code()).size(),
            () -> computationResult.findings().findings().toString()
        );

        BlueprintSpec derivation = rootWithKeysAndDerivations(
            List.of(),
            List.of(new DerivationSpec(id("deriver"), Value.object(), id("ghost"), 0))
        );
        CompileResult derivationResult = compile(derivation);
        assertNull(derivationResult.blueprint());
        assertEquals(
            1,
            derivationResult.findings().ofCode(Codes.keyUndeclared.code()).size(),
            () -> derivationResult.findings().findings().toString()
        );
    }

    @Test
    void aFocusOrExpectationWithoutAKindIsExp0004() {
        CheckSpec kindlessFocus = new CheckSpec(
            id("c"),
            id("wall"),
            NamespacedId.of("missing_kind"),
            Value.object(),
            kind("any_occupant"),
            Value.object(),
            null,
            null
        );
        CompileResult focusResult = compile(
            rootWithPlacements(
                id("kindless_focus"),
                List.of(cellBay(id("wall"), CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)))),
                List.of(kindlessFocus),
                List.of()
            )
        );
        assertNull(focusResult.blueprint());
        assertEquals(
            1,
            focusResult.findings().ofCode(Codes.factoryUnknown.code()).size(),
            () -> focusResult.findings().findings().toString()
        );

        CheckSpec kindlessExpectation = new CheckSpec(
            id("c"),
            id("wall"),
            kind("bay"),
            Value.object(),
            NamespacedId.of("missing_kind"),
            Value.object(),
            null,
            null
        );
        CompileResult expectationResult = compile(
            rootWithPlacements(
                id("kindless_expectation"),
                List.of(cellBay(id("wall"), CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)))),
                List.of(kindlessExpectation),
                List.of()
            )
        );
        assertNull(expectationResult.blueprint());
        assertEquals(
            1,
            expectationResult.findings().ofCode(Codes.factoryUnknown.code()).size(),
            () -> expectationResult.findings().findings().toString()
        );
    }

    @Test
    void aFailingFactoryIsExp0005() {
        CheckSpec boomFocus = new CheckSpec(
            id("c"),
            id("wall"),
            id("boom"),
            Value.object(),
            kind("any_occupant"),
            Value.object(),
            null,
            null
        );
        CompileResult focusResult = DocumentCompiler.compile(
            new Document(
                1,
                rootWithPlacements(
                    id("boom_focus"),
                    List.of(cellBay(id("wall"), CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)))),
                    List.of(boomFocus),
                    List.of()
                ),
                Map.of(),
                Map.of()
            ),
            context(List.of(throwingFocusFactory()), List.of())
        );
        assertNull(focusResult.blueprint());
        List<Finding> focusFindings = focusResult.findings().ofCode(Codes.factoryBad.code());
        assertEquals(1, focusFindings.size(), () -> focusResult.findings().findings().toString());
        assertTrue(focusFindings.get(0).message().contains("failed"), () -> focusFindings.get(0).message());

        CheckSpec nullExpectation = new CheckSpec(
            id("c"),
            id("wall"),
            kind("bay"),
            Value.object(),
            id("null_leaf"),
            Value.object(),
            null,
            null
        );
        CompileResult expectationResult = DocumentCompiler.compile(
            new Document(
                1,
                rootWithPlacements(
                    id("null_expectation"),
                    List.of(cellBay(id("wall"), CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)))),
                    List.of(nullExpectation),
                    List.of()
                ),
                Map.of(),
                Map.of()
            ),
            context(List.of(), List.of(nullExpectationFactory()))
        );
        assertNull(expectationResult.blueprint());
        List<Finding> expectationFindings = expectationResult.findings().ofCode(Codes.factoryBad.code());
        assertEquals(1, expectationFindings.size(), () -> expectationResult.findings().findings().toString());
        assertTrue(
            expectationFindings.get(0).message().contains("returned null"),
            () -> expectationFindings.get(0).message()
        );
    }

    private static CompileResult compile(BlueprintSpec root) {
        return DocumentCompiler.compile(new Document(1, root, Map.of(), Map.of()), context(List.of(), List.of()));
    }

    private static CompilerContext context(List<FocusFactory> focusExtras, List<ExpectationFactory> expectationExtras) {
        Catalogs catalogs = Catalogs.builtin();
        Catalog.Builder<FocusFactory> focuses = Catalog.builder(catalogs.focuses().family());
        catalogs.focuses().asMap().forEach(focuses::put);
        for (FocusFactory extra : focusExtras) {
            focuses.put(extra.id(), extra);
        }
        Catalog.Builder<ExpectationFactory> expectations = Catalog.builder(catalogs.expectations().family());
        catalogs.expectations().asMap().forEach(expectations::put);
        for (ExpectationFactory extra : expectationExtras) {
            expectations.put(extra.id(), extra);
        }
        return new CompilerContext(
            focuses.build(),
            expectations.build(),
            catalogs.computations(),
            Map.of(),
            DocumentSource.empty()
        );
    }

    private static BaySpec cellBay(NamespacedId bayId, CuboidSet cells) {
        return new BaySpec(bayId, cells, Coverage.everyCell, Footprint.box(), Sharing.exclusive, null, null, null);
    }

    private static CheckSpec airCheck(NamespacedId checkId, NamespacedId bayId) {
        return new CheckSpec(
            checkId,
            bayId,
            kind("bay"),
            Value.object(),
            kind("each_cell"),
            Value.object(Map.of("element", Value.object(Map.of("kind", Value.string("air"))))),
            null,
            null
        );
    }

    private static BlueprintSpec definitionOnly(NamespacedId id, List<PlacementSpec> placements) {
        return new BlueprintSpec(
            id,
            Cuboid.of(0, 0, 0, 1, 1, 1),
            false,
            false,
            Value.object(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            placements,
            Map.of(),
            null,
            null
        );
    }

    private static BlueprintSpec rootWithPlacements(
        NamespacedId id,
        List<BaySpec> bays,
        List<CheckSpec> checks,
        List<PlacementSpec> placements
    ) {
        return new BlueprintSpec(
            id,
            Cuboid.of(0, 0, 0, 2, 1, 1),
            false,
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
            placements,
            Map.of(),
            null,
            null
        );
    }

    private static BlueprintSpec rootWithKeys(List<KeySpec> keys) {
        return rootWithKeysAndDerivations(keys, List.of());
    }

    private static BlueprintSpec rootWithKeysAndDerivations(List<KeySpec> keys, List<DerivationSpec> derivations) {
        return new BlueprintSpec(
            id("keyed"),
            Cuboid.of(0, 0, 0, 1, 1, 1),
            false,
            false,
            Value.object(),
            List.of(cellBay(id("wall"), CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)))),
            List.of(),
            List.of(airCheck(id("cells"), id("wall"))),
            List.of(),
            keys,
            derivations,
            List.of(),
            null,
            List.of(),
            Map.of(),
            null,
            null
        );
    }

    private static ExpectationFactory doorFactory() {
        return new ExpectationFactory() {
            @Override
            public NamespacedId id() {
                return DocumentParityTest.id("oak_door");
            }

            @Override
            public Expectation<?> create(Value args, FactoryContext context) {
                return new TestDoorExpectation();
            }
        };
    }

    private static ExpectationFactory nestedProbeFactory() {
        return new ExpectationFactory() {
            @Override
            public NamespacedId id() {
                return DocumentParityTest.id("nested_probe");
            }

            @Override
            public Expectation<?> create(Value args, FactoryContext context) {
                return new NestedProbe();
            }
        };
    }

    private static ExpectationFactory nullExpectationFactory() {
        return new ExpectationFactory() {
            @Override
            public NamespacedId id() {
                return DocumentParityTest.id("null_leaf");
            }

            @Override
            public @Nullable Expectation<?> create(Value args, FactoryContext context) {
                return null;
            }
        };
    }

    private static FocusFactory throwingFocusFactory() {
        return new FocusFactory() {
            @Override
            public NamespacedId id() {
                return DocumentParityTest.id("boom");
            }

            @Override
            public Focus<?> create(Value args, FactoryContext context) {
                throw new IllegalStateException("boom");
            }
        };
    }

    private record TestDoorExpectation() implements Expectation<DoorView> {

        @Override
        public Reply test(ExpectationContext<DoorView> ctx) {
            return Reply.match();
        }

        @Override
        public Class<DoorView> subject() {
            return DoorView.class;
        }
    }

    private record NestedProbe() implements Expectation<WorldView> {

        @Override
        public Reply test(ExpectationContext<WorldView> ctx) {
            return Reply.match();
        }

        @Override
        public Class<WorldView> subject() {
            return WorldView.class;
        }
    }
}
