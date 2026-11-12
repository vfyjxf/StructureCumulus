package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The presentation text of design 16 §3: carried, type-only, part of the value, never interpreted. */
class PresentationTextTest {

    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static NamespacedId kind(String path) {
        return NamespacedId.of(path);
    }

    private static Value blockOf(String id) {
        return Value.object(Map.of("kind", Value.string("block"), "id", Value.string(id)));
    }

    private static Value air() {
        return Value.object(Map.of("kind", Value.string("air")));
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
    void absentTextIsAbsence() {
        Blueprint blueprint = compile("part", null, null, null, null);
        assertNull(blueprint.description());
        Bay bay = blueprint.bay(id("shell"));
        assertNotNull(bay);
        assertNull(bay.description());
        assertNull(bay.hint());
        Check<?> check = blueprint.check(id("shell_check"));
        assertNotNull(check);
        assertNull(check.description());
    }

    @Test
    void allFourMembersReachTheCompiledValueAndNoFindingReadsThem() {
        CompileResult result = compileResult("part", "the part", "the firebox", "lay the ring", "why the check");
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint blueprint = result.blueprint();
        assertNotNull(blueprint);
        assertEquals("the part", blueprint.description());
        Bay bay = blueprint.bay(id("shell"));
        assertNotNull(bay);
        assertEquals("the firebox", bay.description());
        assertEquals("lay the ring", bay.hint());
        Check<?> check = blueprint.check(id("shell_check"));
        assertNotNull(check);
        assertEquals("why the check", check.description());
        assertEquals(blueprint, compile("part", "the part", "the firebox", "lay the ring", "why the check"));
    }

    @Test
    void textParticipatesInBlueprintEquality() {
        Blueprint left = compile("part", "a part", "the firebox", "lay the ring", null);
        Blueprint right = compile("part", "another part", "the firebox", "lay the ring", null);
        assertNotEquals(left, right);
        assertEquals(left, compile("part", "a part", "the firebox", "lay the ring", null));
        assertNotEquals(
            compile("part", "a part", "the firebox", "lay the ring", null),
            compile("part", "a part", "the firebox", "place it later", null)
        );
        assertNotEquals(
            compile("part", "a part", "the firebox", "lay the ring", null),
            compile("part", "a part", "the coal box", "lay the ring", null)
        );
        assertNotEquals(
            compile("part", null, "the firebox", "lay the ring", null),
            compile("part", "a part", "the firebox", "lay the ring", null)
        );
    }

    @Test
    void aDefinitionRootDescriptionIsADocumentFieldError() {
        BlueprintSpec definition = definitionSpec(id("unit"), "the unit", null, null);
        BlueprintSpec root = root(
            id("root"),
            List.of(),
            List.of(),
            List.of(new PlacementSpec(id("unit"), LatticePoint.origin, id("south"), null)),
            null
        );
        CompileResult result = DocumentCompiler
                .compile(new Document(1, root, Map.of(id("unit"), definition), Map.of()), context());
        assertNull(result.blueprint());
        assertEquals(1, result.findings().ofCode(Codes.documentField.code()).size());
        assertTrue(
            result.findings().ofCode(Codes.documentField.code()).get(0).message()
                    .contains("only bays, seams and placements")
        );
    }

    @Test
    void aPlacedBayKeepsItsOwnTextUnderTheQualifiedId() {
        BlueprintSpec definition = definitionSpec(id("unit"), null, "the placed wall", "stack it from the corner");
        NamespacedId checkId = id("wall_check");
        BlueprintSpec root = root(
            id("root"),
            List.of(),
            List.of(check(checkId, id("south/unit"), null)),
            List.of(new PlacementSpec(id("unit"), LatticePoint.origin, id("south"), null)),
            null
        );
        CompileResult result = DocumentCompiler
                .compile(new Document(1, root, Map.of(id("unit"), definition), Map.of()), context());
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint blueprint = result.blueprint();
        assertNotNull(blueprint);
        Bay bay = blueprint.bay(id("south/unit"));
        assertNotNull(bay);
        assertEquals("the placed wall", bay.description());
        assertEquals("stack it from the corner", bay.hint());
        assertNull(blueprint.description());
    }

    private static Blueprint compile(
        String path,
        @Nullable String description,
        @Nullable String bayDescription,
        @Nullable String bayHint,
        @Nullable String checkDescription
    ) {
        CompileResult result = compileResult(path, description, bayDescription, bayHint, checkDescription);
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint blueprint = result.blueprint();
        assertNotNull(blueprint);
        return blueprint;
    }

    private static CompileResult compileResult(
        String path,
        @Nullable String description,
        @Nullable String bayDescription,
        @Nullable String bayHint,
        @Nullable String checkDescription
    ) {
        BlueprintSpec root = root(
            id(path),
            List.of(
                new BaySpec(
                    id("shell"),
                    cells(),
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.exclusive,
                    bayDescription,
                    bayHint,
                    null
                )
            ),
            List.of(check(id("shell_check"), id("shell"), checkDescription)),
            List.of(),
            description
        );
        return DocumentCompiler.compile(new Document(1, root, Map.of(), Map.of()), context());
    }

    private static BlueprintSpec root(
        NamespacedId specId,
        List<BaySpec> bays,
        List<CheckSpec> checks,
        List<PlacementSpec> placements,
        @Nullable String description
    ) {
        return new BlueprintSpec(
            specId,
            bounds,
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
            description,
            null
        );
    }

    private static BlueprintSpec definitionSpec(
        NamespacedId specId,
        @Nullable String description,
        @Nullable String bayDescription,
        @Nullable String hint
    ) {
        return root(
            specId,
            List.of(
                new BaySpec(
                    specId,
                    cells(),
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.exclusive,
                    bayDescription,
                    hint,
                    null
                )
            ),
            List.of(),
            List.of(),
            description
        );
    }

    private static CheckSpec check(NamespacedId checkId, NamespacedId bay, @Nullable String description) {
        return new CheckSpec(
            checkId,
            bay,
            kind("bay"),
            Value.object(),
            kind("each_cell"),
            Value.object(Map.of("element", blockOf("minecraft:stone_bricks"))),
            description,
            null
        );
    }

    private static CuboidSet cells() {
        return CuboidSet.of(bounds);
    }
}
