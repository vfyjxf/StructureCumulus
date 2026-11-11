package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.SolvePreferences;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolvePreferenceAssemblerTest {

    private static final Siting siting = Siting.of(LatticePoint.of(100, 64, 100), Orientation.identity);
    private static final NamespacedId shell = NamespacedId.of("structurecumulus", "shell");

    @Test
    void noPreferencesIsTheCanonicalOrder() {
        SolvePreferences preferences = SolvePreferenceAssembler.assemble(null, null, siting);
        assertTrue(preferences.preferredCells().isEmpty());
        assertTrue(preferences.partPins().isEmpty());
    }

    @Test
    void thePendingCellsBecomeWorldFrameCellPreferencesInOrder() {
        List<LatticePoint> pending = List.of(cell(1, 0, 0), cell(0, 0, 0), cell(2, 0, 0));
        SolvePreferences preferences = SolvePreferenceAssembler.assemble(pending, null, siting);
        assertEquals(List.of(world(1, 0, 0), world(0, 0, 0), world(2, 0, 0)), preferences.preferredCells());
    }

    @Test
    void aPartPinBecomesAWorldFramePin() {
        Map<NamespacedId, LatticePoint> pins = new LinkedHashMap<>();
        pins.put(shell, cell(2, 0, 1));
        SolvePreferences preferences = SolvePreferenceAssembler.assemble(null, pins, siting);
        assertEquals(Map.of(shell, world(2, 0, 1)), preferences.partPins());
    }

    @Test
    void aPinTheSitingCannotPlaceIsDroppedRatherThanInvented() {
        Siting edge = Siting.of(LatticePoint.of(LatticePoint.maxXz, 64, 0), Orientation.identity);
        Map<NamespacedId, LatticePoint> pins = new LinkedHashMap<>();
        pins.put(shell, cell(1, 0, 0));
        SolvePreferences preferences = SolvePreferenceAssembler.assemble(List.of(cell(1, 0, 0)), pins, edge);
        assertTrue(preferences.partPins().isEmpty(), "an out-of-lattice pin is dropped");
        assertTrue(preferences.preferredCells().isEmpty(), "an out-of-lattice pending cell is dropped");
    }

    @Test
    void aDuplicateCellIsPreferredOnce() {
        List<LatticePoint> pending = List.of(cell(1, 0, 0), cell(1, 0, 0), cell(0, 0, 0));
        SolvePreferences preferences = SolvePreferenceAssembler.assemble(pending, null, siting);
        assertEquals(List.of(world(1, 0, 0), world(0, 0, 0)), preferences.preferredCells());
    }

    @Test
    void theCellPreferencesAreCapped() {
        List<LatticePoint> pending = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            pending.add(cell(index, 0, 0));
        }
        SolvePreferences preferences = SolvePreferenceAssembler.assemble(pending, null, siting);
        assertEquals(SolvePreferenceAssembler.maxPreferredCells, preferences.preferredCells().size());
    }

    @Test
    void theAssemblyIsDeterministic() {
        List<LatticePoint> pending = List.of(cell(3, 0, 0), cell(1, 0, 0));
        Map<NamespacedId, LatticePoint> pins = new LinkedHashMap<>();
        pins.put(shell, cell(2, 0, 0));
        pins.put(NamespacedId.of("structurecumulus", "door"), cell(0, 0, 0));
        assertEquals(
            SolvePreferenceAssembler.assemble(pending, pins, siting),
            SolvePreferenceAssembler.assemble(pending, pins, siting)
        );
        assertEquals(
            SolvePreferenceAssembler.assemble(pending, pins, siting).partPins().keySet().stream().toList(),
            List.of(NamespacedId.of("structurecumulus", "door"), shell)
        );
    }

    private static LatticePoint cell(int x, int y, int z) {
        return LatticePoint.of(x, y, z);
    }

    private static CuboidSet world(int x, int y, int z) {
        return Cuboid.of(Objects.requireNonNull(siting.toWorld(cell(x, y, z)))).asSet();
    }
}
