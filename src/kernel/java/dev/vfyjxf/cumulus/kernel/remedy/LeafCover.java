package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationDeclaration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The blueprint's per-cell declarations through the D-91 port: which leaves judge which local cell.
 * A leaf under an {@code eachCell} arm judges every bay cell; any other leaf judges its focus's
 * static cells. Pure declaration data; a world is never touched.
 */
final class LeafCover {

    private record Leaf(ExpectationDeclaration declaration, boolean negated, boolean perCell) {}

    private record Entry(
        Check<?> check,
        CuboidSet focusCells,
        List<Leaf> leaves,
        List<ExpectationDeclaration.Threshold> thresholds
    ) {}

    private final List<Entry> entries;

    private LeafCover(List<Entry> entries) {
        this.entries = entries;
    }

    static LeafCover of(Blueprint blueprint) {
        List<Entry> entries = new ArrayList<>();
        for (Check<?> check : blueprint.checks()) {
            ExpectationDeclaration declaration = check.expectation().declaration();
            List<Leaf> leaves = new ArrayList<>();
            walk(declaration, false, false, leaves);
            List<ExpectationDeclaration.Threshold> thresholds = new ArrayList<>();
            collectThresholds(declaration, false, thresholds);
            entries.add(
                new Entry(
                    check,
                    check.focus().staticCells(check.bay().cells()),
                    List.copyOf(leaves),
                    List.copyOf(thresholds)
                )
            );
        }
        return new LeafCover(List.copyOf(entries));
    }

    /** The positive leaves judging the local cell, in declaration walk order. */
    List<ExpectationDeclaration> leavesAt(LatticePoint localCell) {
        return leavesAt(localCell, false);
    }

    /** The leaves under an odd number of negations judging the local cell. */
    List<ExpectationDeclaration> negatedAt(LatticePoint localCell) {
        return leavesAt(localCell, true);
    }

    private List<ExpectationDeclaration> leavesAt(LatticePoint localCell, boolean negated) {
        List<ExpectationDeclaration> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.check().bay().cells().contains(localCell)) {
                continue;
            }
            for (Leaf leaf : entry.leaves()) {
                if (leaf.negated() != negated) {
                    continue;
                }
                if (leaf.perCell() || entry.focusCells().contains(localCell)) {
                    out.add(leaf.declaration());
                }
            }
        }
        return out;
    }

    /** The positive thresholds of the checks covering the local cell, in check declaration order. */
    List<Map.Entry<Check<?>, ExpectationDeclaration.Threshold>> thresholdsAt(LatticePoint localCell) {
        List<Map.Entry<Check<?>, ExpectationDeclaration.Threshold>> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.check().bay().cells().contains(localCell)) {
                continue;
            }
            for (ExpectationDeclaration.Threshold threshold : entry.thresholds()) {
                out.add(Map.entry(entry.check(), threshold));
            }
        }
        return out;
    }

    /** The positive leaves of one declaration subtree, in walk order. */
    static List<ExpectationDeclaration> positiveLeaves(ExpectationDeclaration declaration) {
        List<Leaf> leaves = new ArrayList<>();
        walk(declaration, false, false, leaves);
        List<ExpectationDeclaration> out = new ArrayList<>();
        for (Leaf leaf : leaves) {
            if (!leaf.negated()) {
                out.add(leaf.declaration());
            }
        }
        return out;
    }

    private static void walk(ExpectationDeclaration declaration, boolean negated, boolean perCell, List<Leaf> out) {
        switch (declaration) {
            case ExpectationDeclaration.Composite composite -> {
                boolean childNegated = (composite.kind() == ExpectationDeclaration.CompositeKind.not) != negated;
                boolean childPerCell = perCell || composite.kind() == ExpectationDeclaration.CompositeKind.eachCell;
                for (ExpectationDeclaration child : composite.children()) {
                    walk(child, childNegated, childPerCell, out);
                }
            }
            case ExpectationDeclaration.Threshold threshold -> {
                for (ExpectationDeclaration child : threshold.children()) {
                    walk(child, negated, perCell, out);
                }
            }
            default -> out.add(new Leaf(declaration, negated, perCell));
        }
    }

    private static void collectThresholds(
        ExpectationDeclaration declaration,
        boolean negated,
        List<ExpectationDeclaration.Threshold> out
    ) {
        switch (declaration) {
            case ExpectationDeclaration.Composite composite -> {
                boolean childNegated = (composite.kind() == ExpectationDeclaration.CompositeKind.not) != negated;
                for (ExpectationDeclaration child : composite.children()) {
                    collectThresholds(child, childNegated, out);
                }
            }
            case ExpectationDeclaration.Threshold threshold -> {
                if (!negated) {
                    out.add(threshold);
                }
                for (ExpectationDeclaration child : threshold.children()) {
                    collectThresholds(child, negated, out);
                }
            }
            default -> {}
        }
    }
}
