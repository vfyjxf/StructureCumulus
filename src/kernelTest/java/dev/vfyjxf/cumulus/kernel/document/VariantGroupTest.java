package dev.vfyjxf.cumulus.kernel.document;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Value;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.spi.Catalogs;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.VariantGroup;
import dev.vfyjxf.cumulus.kernel.structure.VariantMember;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code variants} declaration of design 15 §12: a static combination of blueprint documents,
 * carried opaquely into the compiled value, validated on every member and never silently repaired.
 */
class VariantGroupTest {

    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static NamespacedId kind(String path) {
        return NamespacedId.of(path);
    }

    private static NamespacedId coilGroup() {
        return NamespacedId.of("coil");
    }

    private static CompilerContext context(Map<NamespacedId, Document> documents) {
        Catalogs catalogs = Catalogs.builtin();
        return new CompilerContext(
            catalogs.focuses(),
            catalogs.expectations(),
            catalogs.computations(),
            Map.of(),
            DocumentSource.of(documents)
        );
    }

    private static VariantMemberSpec member(NamespacedId id, @Nullable String label) {
        return new VariantMemberSpec(id, label, null);
    }

    private static VariantGroupSpec group(NamespacedId group, List<VariantMemberSpec> members) {
        return new VariantGroupSpec(group, members, null);
    }

    private static BlueprintSpec root(NamespacedId id) {
        return new BlueprintSpec(
            id,
            bounds,
            false,
            false,
            Value.object(),
            List.of(
                new BaySpec(
                    id(id.path() + "_shell"),
                    CuboidSet.of(bounds),
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.exclusive,
                    null,
                    null,
                    null
                )
            ),
            List.of(),
            List.of(
                new CheckSpec(
                    id(id.path() + "_check"),
                    id(id.path() + "_shell"),
                    kind("bay"),
                    Value.object(),
                    kind("each_cell"),
                    Value.object(Map.of("element", blockOf("minecraft:stone_bricks"))),
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
    }

    private static Document document(NamespacedId id, @Nullable VariantGroupSpec variants) {
        return new Document(1, root(id), Map.of(), Map.of(), variants);
    }

    /** Two concrete coils; their declarations are the same block in declaration order. */
    private static VariantGroupSpec coilGroupSpec() {
        return group(
            coilGroup(),
            List.of(member(id("coil_basic"), "basic coil"), member(id("coil_reinforced"), "reinforced coil"))
        );
    }

    private static Map<NamespacedId, Document> coilDocuments() {
        VariantGroupSpec variants = coilGroupSpec();
        return Map.of(
            id("coil_basic"),
            document(id("coil_basic"), variants),
            id("coil_reinforced"),
            document(id("coil_reinforced"), variants)
        );
    }

    @Test
    void absenceIsNoGroup() {
        CompileResult result = DocumentCompiler.compile(document(id("plain"), null), context(Map.of()));
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint blueprint = result.blueprint();
        assertNotNull(blueprint);
        assertNull(blueprint.variants());
        Blueprint again = DocumentCompiler.compile(document(id("plain"), null), context(Map.of())).blueprint();
        assertNotNull(again);
        assertEquals(blueprint, again);
    }

    @Test
    void theGroupIsCarriedInDeclarationOrderAndParticipatesInEquality() {
        CompileResult result = DocumentCompiler
                .compile(document(id("coil_basic"), coilGroupSpec()), context(coilDocuments()));
        assertTrue(result.findings().isEmpty(), () -> result.findings().findings().toString());
        Blueprint blueprint = result.blueprint();
        assertNotNull(blueprint);
        VariantGroup variants = blueprint.variants();
        assertNotNull(variants);
        assertEquals(coilGroup(), variants.group());
        // switching order is the declaration order, not the id order (coil_basic < coil_reinforced)
        assertEquals(
            List.of(id("coil_basic"), id("coil_reinforced")),
            variants.members().stream().map(VariantMember::id).toList()
        );
        assertEquals("basic coil", variants.members().get(0).label());
        assertEquals(1, variants.indexOf(id("coil_reinforced")));
        assertEquals(-1, variants.indexOf(id("elsewhere")));
        assertEquals(
            blueprint,
            DocumentCompiler.compile(document(id("coil_basic"), coilGroupSpec()), context(coilDocuments())).blueprint()
        );
        VariantGroupSpec relabelled = group(
            coilGroup(),
            List.of(member(id("coil_basic"), "basic coil"), member(id("coil_reinforced"), "another label"))
        );
        Map<NamespacedId, Document> documents = Map.of(
            id("coil_basic"),
            document(id("coil_basic"), relabelled),
            id("coil_reinforced"),
            document(id("coil_reinforced"), relabelled)
        );
        Blueprint other = DocumentCompiler.compile(document(id("coil_basic"), relabelled), context(documents))
                .blueprint();
        assertNotNull(other);
        assertNotEquals(blueprint, other);
    }

    @Test
    void aMemberThatDoesNotCompileIsRefused() {
        VariantGroupSpec variants = coilGroupSpec();
        BlueprintSpec broken = new BlueprintSpec(
            id("coil_reinforced"),
            bounds,
            false,
            false,
            Value.object(),
            List.of(
                new BaySpec(
                    id("unused"),
                    CuboidSet.of(bounds),
                    Coverage.everyCell,
                    Footprint.box(),
                    Sharing.exclusive,
                    null,
                    null,
                    null
                )
            ),
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
        Map<NamespacedId, Document> documents = Map.of(
            id("coil_basic"),
            document(id("coil_basic"), variants),
            id("coil_reinforced"),
            new Document(1, broken, Map.of(), Map.of(), variants)
        );
        CompileResult result = DocumentCompiler.compile(document(id("coil_basic"), variants), context(documents));
        assertNull(result.blueprint());
        assertEquals(1, result.findings().ofCode(Codes.variantMember.code()).size());
        assertTrue(result.findings().ofCode(Codes.variantMember.code()).get(0).message().contains("does not compile"));
    }

    @Test
    void aMemberIdThatNamesNoDocumentIsRefused() {
        VariantGroupSpec variants = coilGroupSpec();
        Map<NamespacedId, Document> documents = Map.of(id("coil_basic"), document(id("coil_basic"), variants));
        CompileResult result = DocumentCompiler.compile(document(id("coil_basic"), variants), context(documents));
        assertNull(result.blueprint());
        assertEquals(1, result.findings().ofCode(Codes.definitionMissing.code()).size());
        assertTrue(
            result.findings().ofCode(Codes.definitionMissing.code()).get(0).message()
                    .contains("names no blueprint document")
        );
    }

    @Test
    void everyMemberMustDeclareTheSameGroup() {
        VariantGroupSpec variants = coilGroupSpec();
        Map<NamespacedId, Document> missing = Map.of(
            id("coil_basic"),
            document(id("coil_basic"), variants),
            id("coil_reinforced"),
            document(id("coil_reinforced"), null)
        );
        CompileResult absent = DocumentCompiler.compile(document(id("coil_basic"), variants), context(missing));
        assertNull(absent.blueprint());
        assertEquals(1, absent.findings().ofCode(Codes.variantGroupMismatch.code()).size());
        assertTrue(
            absent.findings().ofCode(Codes.variantGroupMismatch.code()).get(0).message().contains("no variant group")
        );

        // a different member order is a different declaration: switching order must be unambiguous
        VariantGroupSpec reordered = group(
            coilGroup(),
            List.of(member(id("coil_reinforced"), "reinforced coil"), member(id("coil_basic"), "basic coil"))
        );
        Map<NamespacedId, Document> disagreeing = Map.of(
            id("coil_basic"),
            document(id("coil_basic"), variants),
            id("coil_reinforced"),
            document(id("coil_reinforced"), reordered)
        );
        CompileResult different = DocumentCompiler.compile(document(id("coil_basic"), variants), context(disagreeing));
        assertNull(different.blueprint());
        assertEquals(1, different.findings().ofCode(Codes.variantGroupMismatch.code()).size());
        assertTrue(
            different.findings().ofCode(Codes.variantGroupMismatch.code()).get(0).message()
                    .contains("different variant group")
        );
    }

    @Test
    void aGroupOfOneIsSelfReferenceAndIsRefused() {
        VariantGroupSpec selfOnly = group(coilGroup(), List.of(member(id("coil_basic"), "basic coil")));
        Map<NamespacedId, Document> documents = Map.of(id("coil_basic"), document(id("coil_basic"), selfOnly));
        CompileResult result = DocumentCompiler.compile(document(id("coil_basic"), selfOnly), context(documents));
        assertNull(result.blueprint());
        assertEquals(1, result.findings().ofCode(Codes.documentField.code()).size());
        assertTrue(
            result.findings().ofCode(Codes.documentField.code()).get(0).message().contains("at least two members")
        );
    }

    @Test
    void twoGroupsThatNameEachOtherDoNotLoop() {
        // A claims group G over [A, B]; B claims group H over [B, A]: the member declares another group,
        // and the compiler reports it instead of following the ring
        VariantGroupSpec first = group(
            coilGroup(),
            List.of(member(id("coil_basic"), "basic coil"), member(id("coil_reinforced"), "reinforced coil"))
        );
        VariantGroupSpec second = group(
            NamespacedId.of("coil_alt"),
            List.of(member(id("coil_reinforced"), "reinforced coil"), member(id("coil_basic"), "basic coil"))
        );
        Map<NamespacedId, Document> documents = Map.of(
            id("coil_basic"),
            document(id("coil_basic"), first),
            id("coil_reinforced"),
            document(id("coil_reinforced"), second)
        );
        CompileResult basic = DocumentCompiler.compile(document(id("coil_basic"), first), context(documents));
        assertNull(basic.blueprint());
        assertEquals(1, basic.findings().ofCode(Codes.variantGroupMismatch.code()).size());
        CompileResult reinforced = DocumentCompiler
                .compile(document(id("coil_reinforced"), second), context(documents));
        assertNull(reinforced.blueprint());
        assertEquals(1, reinforced.findings().ofCode(Codes.variantGroupMismatch.code()).size());
    }

    @Test
    void aDuplicateMemberAndADeclarationOutsideItsGroupAreRefused() {
        VariantGroupSpec duplicated = group(
            coilGroup(),
            List.of(member(id("coil_basic"), "basic coil"), member(id("coil_basic"), "basic coil"))
        );
        CompileResult duplicate = DocumentCompiler.compile(document(id("coil_basic"), duplicated), context(Map.of()));
        assertNull(duplicate.blueprint());
        assertEquals(1, duplicate.findings().ofCode(Codes.documentDuplicate.code()).size());

        VariantGroupSpec outside = group(
            coilGroup(),
            List.of(member(id("coil_other"), "other coil"), member(id("coil_reinforced"), "reinforced coil"))
        );
        CompileResult notAMember = DocumentCompiler.compile(document(id("coil_basic"), outside), context(Map.of()));
        assertNull(notAMember.blueprint());
        assertEquals(1, notAMember.findings().ofCode(Codes.documentField.code()).size());
        assertTrue(
            notAMember.findings().ofCode(Codes.documentField.code()).get(0).message()
                    .contains("not a member of its own group")
        );
    }

    @Test
    void theDeclaredCapsAreDocumentErrors() {
        List<VariantMemberSpec> tooMany = new ArrayList<>();
        for (int index = 0; index <= 16; index++) {
            tooMany.add(member(id("coil_" + index), "coil " + index));
        }
        CompileResult capped = DocumentCompiler
                .compile(document(id("coil_0"), group(coilGroup(), tooMany)), context(Map.of()));
        assertNull(capped.blueprint());
        assertTrue(
            capped.findings().ofCode(Codes.documentField.code()).stream()
                    .anyMatch(finding -> finding.message().contains("over the 16 cap"))
        );

        VariantGroupSpec longLabel = group(
            coilGroup(),
            List.of(member(id("coil_basic"), "x".repeat(129)), member(id("coil_reinforced"), "reinforced"))
        );
        CompileResult label = DocumentCompiler.compile(document(id("coil_basic"), longLabel), context(Map.of()));
        assertNull(label.blueprint());
        assertTrue(
            label.findings().ofCode(Codes.documentField.code()).stream()
                    .anyMatch(finding -> finding.message().contains("over the 128 cap"))
        );

        VariantGroupSpec emptyLabel = group(
            coilGroup(),
            List.of(member(id("coil_basic"), ""), member(id("coil_reinforced"), "reinforced"))
        );
        CompileResult empty = DocumentCompiler.compile(document(id("coil_basic"), emptyLabel), context(Map.of()));
        assertNull(empty.blueprint());
        assertTrue(
            empty.findings().ofCode(Codes.documentField.code()).stream()
                    .anyMatch(finding -> finding.message().contains("empty label"))
        );
    }

    private static Value blockOf(String block) {
        return Value.object(Map.of("kind", Value.string("block"), "id", Value.string(block)));
    }
}
