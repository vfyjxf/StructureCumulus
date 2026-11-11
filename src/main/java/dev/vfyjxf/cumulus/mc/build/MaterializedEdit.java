package dev.vfyjxf.cumulus.mc.build;

import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One edit lowered to world payloads: the cells to write, what they cost and what went wrong. */
public record MaterializedEdit(
    Map<LatticePoint, BlockPlacement> placements,
    List<ItemRequirement> items,
    List<Finding> findings
) {

    public MaterializedEdit {
        Objects.requireNonNull(placements, "placements");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(findings, "findings");
        placements = Collections.unmodifiableMap(new TreeMap<>(placements));
        items = List.copyOf(items);
        findings = List.copyOf(findings);
    }
}
