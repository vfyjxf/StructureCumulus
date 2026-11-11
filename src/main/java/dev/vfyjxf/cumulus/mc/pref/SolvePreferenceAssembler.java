package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.remedy.SolvePreferences;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * The only preference value that crosses into the kernel (D-86): the pending cells of the chosen siting
 * as cell preferences and the per-part pins as a part map. A pin the siting cannot place is dropped, so
 * the assembler never invents a cell.
 */
public final class SolvePreferenceAssembler {

    /** One solve sees at most this many ordered cell preferences. */
    public static final int maxPreferredCells = 64;

    public static SolvePreferences assemble(
        @Nullable List<LatticePoint> pending,
        @Nullable Map<NamespacedId, LatticePoint> partPins,
        Siting siting
    ) {
        Objects.requireNonNull(siting, "siting");
        List<CuboidSet> preferred = new ArrayList<>();
        if (pending != null) {
            Set<LatticePoint> seen = new LinkedHashSet<>();
            for (LatticePoint cell : pending) {
                if (cell == null || !seen.add(cell)) {
                    continue;
                }
                LatticePoint world = siting.toWorld(cell);
                if (world == null) {
                    continue;
                }
                preferred.add(Cuboid.of(world).asSet());
                if (preferred.size() >= maxPreferredCells) {
                    break;
                }
            }
        }
        Map<NamespacedId, CuboidSet> pins = new TreeMap<>();
        if (partPins != null) {
            for (Map.Entry<NamespacedId, LatticePoint> pin : partPins.entrySet()) {
                if (pin.getKey() == null || pin.getValue() == null) {
                    continue;
                }
                LatticePoint world = siting.toWorld(pin.getValue());
                if (world == null) {
                    continue;
                }
                pins.put(pin.getKey(), Cuboid.of(world).asSet());
            }
        }
        return new SolvePreferences(preferred, pins);
    }

    private SolvePreferenceAssembler() {}
}
