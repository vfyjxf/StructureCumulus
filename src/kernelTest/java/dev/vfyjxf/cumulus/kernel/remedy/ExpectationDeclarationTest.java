package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.core.ContextKeys;
import dev.vfyjxf.cumulus.kernel.core.Retention;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationDeclaration;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationDeclaration.Composite;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationDeclaration.CompositeKind;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationDeclaration.MarkerLeaf;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationDeclaration.Threshold;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationDeclaration.ThresholdKind;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.HostType;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.id;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.rotation;
import static dev.vfyjxf.cumulus.kernel.remedy.RemedyFixtures.stone;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** `Expectation.declaration()` over the shipped leaves and combinators (D-91, structure §4.12). */
class ExpectationDeclarationTest {

    @Test
    void aBlockLeafCarriesItsType() {
        assertEquals(new ExpectationDeclaration.BlockLeaf(stone), Expectations.block(stone).declaration());
    }

    @Test
    void aTraitLeafCarriesItsKeyAndPresence() {
        assertEquals(new ExpectationDeclaration.TraitLeaf(rotation, true), Expectations.trait(rotation).declaration());
        assertEquals(
            new ExpectationDeclaration.TraitLeaf(rotation, true),
            Expectations.trait(rotation, value -> value > 0).declaration()
        );
        assertEquals(
            new ExpectationDeclaration.TraitLeaf(rotation, false),
            Expectations.traitAbsent(rotation).declaration()
        );
    }

    @Test
    void theMarkerLeavesCarryNoPayload() {
        assertEquals(MarkerLeaf.air, Expectations.air().declaration());
        assertEquals(MarkerLeaf.envelopeAir, Expectations.envelopeAir().declaration());
        assertEquals(MarkerLeaf.anyOccupant, Expectations.anyOccupant().declaration());
        assertEquals(MarkerLeaf.empty, Expectations.empty().declaration());
        assertEquals(MarkerLeaf.nonFluid, Expectations.nonFluid().declaration());
    }

    @Test
    void aHostLeafCarriesItsTypeAndBothCodes() {
        HostType<Integer> type = HostType.of(id("handler"));

        ExpectationDeclaration declaration = Expectations.host(
            type,
            (handler, cell) -> true,
            Codes.expected.code(),
            Codes.unreadableRead.code(),
            "the handler answers"
        ).declaration();

        assertEquals(
            new ExpectationDeclaration.HostLeaf(id("handler"), Codes.expected.code(), Codes.unreadableRead.code()),
            declaration
        );
    }

    @Test
    void compositesCarryTheirKindAndChildrenInOrder() {
        assertEquals(
            new Composite(CompositeKind.allOf, List.of(new ExpectationDeclaration.BlockLeaf(stone), MarkerLeaf.air)),
            Expectations.allOf(Expectations.block(stone), Expectations.air()).declaration()
        );
        assertEquals(
            new Composite(
                CompositeKind.anyOf,
                List.of(new ExpectationDeclaration.BlockLeaf(stone), MarkerLeaf.anyOccupant)
            ),
            Expectations.anyOf(Expectations.block(stone), Expectations.anyOccupant()).declaration()
        );
        assertEquals(
            new Composite(CompositeKind.sequence, List.of(new ExpectationDeclaration.BlockLeaf(stone), MarkerLeaf.air)),
            Expectations.sequence(Expectations.block(stone), Expectations.air()).declaration()
        );
        assertEquals(
            new Composite(CompositeKind.not, List.of(new ExpectationDeclaration.BlockLeaf(stone))),
            Expectations.not(Expectations.block(stone)).declaration()
        );
    }

    @Test
    void aNestedCompositeDescribesItsSubtree() {
        ExpectationDeclaration declaration = Expectations
                .allOf(Expectations.block(stone), Expectations.anyOf(Expectations.air(), Expectations.empty()))
                .declaration();

        Composite outer = assertInstanceOf(Composite.class, declaration);
        Composite inner = assertInstanceOf(Composite.class, outer.children().get(1));
        assertEquals(CompositeKind.anyOf, inner.kind());
        assertEquals(List.of(MarkerLeaf.air, MarkerLeaf.empty), inner.children());
    }

    @Test
    void aThresholdCarriesItsKindCountAndChildren() {
        assertEquals(
            new Threshold(
                ThresholdKind.atLeast,
                2,
                List.of(new ExpectationDeclaration.BlockLeaf(stone), MarkerLeaf.air)
            ),
            Expectations.atLeast(2, Expectations.block(stone), Expectations.air()).declaration()
        );
        assertEquals(
            new Threshold(ThresholdKind.atMost, 0, List.of(new ExpectationDeclaration.BlockLeaf(stone))),
            Expectations.atMost(0, Expectations.block(stone)).declaration()
        );
        assertEquals(
            new Threshold(ThresholdKind.exactly, 1, List.of(new ExpectationDeclaration.BlockLeaf(stone))),
            Expectations.exactly(1, Expectations.block(stone)).declaration()
        );
    }

    @Test
    void eachCellDescribesAsAPerCellComposite() {
        assertEquals(
            new Composite(CompositeKind.eachCell, List.of(new ExpectationDeclaration.BlockLeaf(stone))),
            Expectations.eachCell(Expectations.block(stone)).declaration()
        );
    }

    @Test
    void aCustomExpectationIsUndescribable() {
        Expectation<OccupantSnapshot> custom = Expectations
                .custom(OccupantSnapshot.class, ExpectationMeta.anonymous(), ctx -> Reply.match());

        assertEquals(new ExpectationDeclaration.Undescribable(), custom.declaration());
    }

    @Test
    void thePortIsTotalAndPureOverEveryBuiltinExpectation() {
        List<Expectation<?>> builtins = List.of(
            Expectations.block(stone),
            Expectations.air(),
            Expectations.envelopeAir(),
            Expectations.anyOccupant(),
            Expectations.empty(),
            Expectations.nonFluid(),
            Expectations.trait(rotation),
            Expectations.trait(rotation, value -> value > 0),
            Expectations.traitAbsent(rotation),
            Expectations.host(
                HostType.of(id("handler")),
                (handler, cell) -> true,
                Codes.expected.code(),
                Codes.unreadableRead.code(),
                "the handler answers"
            ),
            Expectations.context(ContextKeys.of(id("value"), Long.class, Retention.instance), value -> value > 0),
            Expectations.allOf(Expectations.block(stone), Expectations.air()),
            Expectations.anyOf(Expectations.block(stone), Expectations.air()),
            Expectations.sequence(Expectations.block(stone), Expectations.air()),
            Expectations.not(Expectations.block(stone)),
            Expectations.atLeast(1, Expectations.block(stone)),
            Expectations.atMost(1, Expectations.block(stone)),
            Expectations.exactly(1, Expectations.block(stone)),
            Expectations.eachCell(Expectations.block(stone)),
            Expectations.custom(OccupantSnapshot.class, ExpectationMeta.anonymous(), ctx -> Reply.match())
        );

        for (Expectation<?> expectation : builtins) {
            ExpectationDeclaration first = expectation.declaration();
            assertNotNull(first);
            assertEquals(1, arms(first), () -> expectation + " describes as several arms: " + first);
            assertEquals(first, expectation.declaration());
        }
    }

    private static int arms(ExpectationDeclaration declaration) {
        int arms = 0;
        if (declaration instanceof ExpectationDeclaration.BlockLeaf) {
            arms++;
        }
        if (declaration instanceof ExpectationDeclaration.TraitLeaf) {
            arms++;
        }
        if (declaration instanceof ExpectationDeclaration.HostLeaf) {
            arms++;
        }
        if (declaration instanceof MarkerLeaf) {
            arms++;
        }
        if (declaration instanceof Composite) {
            arms++;
        }
        if (declaration instanceof Threshold) {
            arms++;
        }
        if (declaration instanceof ExpectationDeclaration.Undescribable) {
            arms++;
        }
        return arms;
    }
}
