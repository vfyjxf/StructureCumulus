package dev.vfyjxf.cumulus.kernel.structure;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvelopeTest {

    private static final NamespacedId envelope = NamespacedId.of("structurecumulus", "envelope");
    private static final FindingCode envelopeCode = FindingCode.of(FindingCode.Family.exp, 6);

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static Bay bay(String path, CuboidSet cells, Coverage coverage, Footprint footprint) {
        return Bay.of(id(path), cells, coverage, footprint, Sharing.exclusive);
    }

    private static Check<?> eachCell(String path, Bay bay) {
        return Check.of(id(path), bay, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()));
    }

    @Test
    void anEmptyEnvelopeAddsNothing() {
        CuboidSet cells = CuboidSet.of(Cuboid.of(0, 0, 0, 2, 2, 2));
        Bay bay = bay("all", cells, Coverage.everyCell, Footprint.box());
        Check<?> check = eachCell("air", bay);
        Blueprint plain = Blueprint.builder(id("envelope_none")).bounds(Cuboid.of(0, 0, 0, 2, 2, 2)).check(check)
                .build();
        Blueprint enclosed = Blueprint.builder(id("envelope_none")).bounds(Cuboid.of(0, 0, 0, 2, 2, 2)).check(check)
                .envelope().build();

        assertEquals(plain, enclosed);
        assertEquals(1, enclosed.bays().size());
        assertEquals(1, enclosed.checks().size());
        assertNull(enclosed.bay(envelope));
        assertEquals(plain.bayCells(), enclosed.bayCells());
        assertEquals(plain.reads(), enclosed.reads());
    }

    @Test
    void anEnvelopeOnlyBlueprintGetsTheLastBayAndCheck() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        Blueprint blueprint = Blueprint.builder(id("envelope_only")).bounds(bounds).envelope().build();

        assertEquals(1, blueprint.bays().size());
        assertEquals(1, blueprint.checks().size());
        Bay bay = blueprint.bays().get(0);
        assertEquals(envelope, bay.id());
        assertEquals(bounds.asSet(), bay.cells());
        assertEquals(Coverage.everyCell, bay.coverage());
        assertInstanceOf(Footprint.None.class, bay.footprint());
        assertEquals(Sharing.shared, bay.sharing());
        assertNotNull(bay.description());

        Check<?> check = blueprint.checks().get(0);
        assertEquals(envelope, check.id());
        assertEquals(bay, check.bay());
        assertEquals(BayView.class, check.focus().subject());
        assertEquals(BayView.class, check.expectation().subject());
        assertEquals(envelopeCode, check.expectation().meta().mismatchCode());
        assertEquals(bounds.asSet(), check.reads().cells());
        assertEquals(blueprint.checks(), blueprint.plan().checks());
        assertEquals(blueprint.checks().size() - 1, blueprint.plan().checks().indexOf(check));
        assertEquals(bounds.asSet(), blueprint.reads().cells());
    }

    @Test
    void declaredFootprintsAndBaysLeaveTheEnvelope() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 4, 1, 1);
        Bay wall = bay("wall", CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)), Coverage.everyCell, Footprint.box());
        Bay owned = bay(
            "owned",
            CuboidSet.of(Cuboid.of(1, 0, 0, 2, 1, 1)),
            Coverage.everyCell,
            Footprint.declared(CuboidSet.of(Cuboid.of(2, 0, 0, 3, 1, 1)))
        );
        Blueprint blueprint = Blueprint.builder(id("envelope_partial")).bounds(bounds).check(eachCell("wall", wall))
                .check(eachCell("owned", owned)).envelope().build();

        Bay derived = blueprint.bay(envelope);
        assertNotNull(derived, "the uncovered cells are the envelope");
        assertEquals(CuboidSet.of(Cuboid.of(3, 0, 0, 4, 1, 1)), derived.cells());
        assertEquals(3, blueprint.bays().size());
        assertEquals(envelope, blueprint.bays().get(2).id());
        assertEquals(envelope, blueprint.checks().get(2).id());
    }

    @Test
    void theReservedIdCollidesWithADeclaredBayOrCheck() {
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 1, 1);
        Bay reservedBay = Bay.of(
            envelope,
            CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        Check<?> bayCheck = eachCell("bay_check", reservedBay);
        IllegalArgumentException bayFailure = assertThrows(
            IllegalArgumentException.class,
            () -> Blueprint.builder(id("reserved_bay")).bounds(bounds).check(bayCheck).envelope().build()
        );
        assertTrue(Objects.requireNonNull(bayFailure.getMessage()).startsWith("SC-BPT-0004"));

        Bay normal = bay("normal", CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)), Coverage.everyCell, Footprint.box());
        Check<?> reservedCheck = Check
                .of(envelope, normal, Focuses.bay(), Expectations.eachCell(Expectations.anyOccupant()));
        IllegalArgumentException checkFailure = assertThrows(
            IllegalArgumentException.class,
            () -> Blueprint.builder(id("reserved_check")).bounds(bounds).check(reservedCheck).envelope().build()
        );
        assertTrue(Objects.requireNonNull(checkFailure.getMessage()).startsWith("SC-BPT-0004"));

        assertNotNull(Blueprint.builder(id("reserved_free")).bounds(bounds).check(bayCheck).build());
    }

    @Test
    void buildIsIdempotentAndLeavesTheBuilderUntouched() {
        BlueprintBuilder builder = Blueprint.builder(id("envelope_twice")).bounds(Cuboid.of(0, 0, 0, 2, 1, 1))
                .envelope();
        Blueprint first = builder.build();
        Blueprint second = builder.build();
        assertEquals(first, second);
        assertEquals(1, second.checks().size());
        assertEquals(1, second.bays().size());

        Bay wall = bay("wall", CuboidSet.of(Cuboid.of(0, 0, 0, 1, 1, 1)), Coverage.everyCell, Footprint.box());
        Blueprint third = builder.check(eachCell("wall", wall)).build();
        assertEquals(2, third.checks().size());
        assertEquals(id("wall"), third.checks().get(0).id());
        assertEquals(envelope, third.checks().get(1).id());
        assertEquals(2, third.bays().size());
        assertEquals(id("wall"), third.bays().get(0).id());
        assertEquals(envelope, third.bays().get(1).id());
    }

    @Test
    void envelopeAirAndEachCellCarryTheEnvelopeCode() {
        assertEquals(envelopeCode, Expectations.envelopeAir().meta().mismatchCode());
        assertEquals(FindingCode.of(FindingCode.Family.exp, 1), Expectations.air().meta().mismatchCode());
        assertEquals(envelopeCode, Expectations.eachCell(Expectations.envelopeAir()).meta().mismatchCode());
        assertEquals(
            FindingCode.of(FindingCode.Family.exp, 1),
            Expectations.eachCell(Expectations.air()).meta().mismatchCode()
        );
    }

    @Test
    void aBlueprintWithoutChecksAndWithoutEnvelopeStillFails() {
        IllegalArgumentException failure = assertThrows(
            IllegalArgumentException.class,
            () -> Blueprint.builder(id("no_checks")).bounds(Cuboid.of(0, 0, 0, 1, 1, 1)).build()
        );
        assertTrue(Objects.requireNonNull(failure.getMessage()).startsWith("SC-BPT-0001"));
    }
}
