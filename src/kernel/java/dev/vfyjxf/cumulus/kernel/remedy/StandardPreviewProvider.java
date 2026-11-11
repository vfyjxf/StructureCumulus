package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.LocusFrame;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationDeclaration;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The built-in `structurecumulus:preview` provider (D-85): the overlay derivation of remedy §5.2.
 * Base marks cover the blueprint's expected cells from declarations plus the captured snapshot
 * (present only when provably satisfied, else unknown); the finding's own marks are louder. A
 * context-only overlay is identical for every finding, so the solve pass's value dedup collapses it.
 */
public record StandardPreviewProvider() implements RemedyProvider {

    private static final FindingCode orientationUnrepresentable = FindingCode.of(FindingCode.Family.mc, 4);

    @Override
    public NamespacedId id() {
        return NamespacedId.of("structurecumulus", "preview");
    }

    @Override
    public List<Remedy> solve(Finding finding, RemedyContext context) {
        Blueprint blueprint = context.blueprint();
        Cuboid bounds = context.siting().toWorld(blueprint.bounds());
        if (bounds == null) {
            bounds = blueprint.bounds();
        }
        WorldView view = context.outcome() == null ? null : context.outcome().view();
        LeafCover cover = LeafCover.of(blueprint);
        Map<LatticeKey, CellMark> cells = new TreeMap<>();
        CuboidSet coverage = context.siting().toWorld(blueprint.bayCells());
        if (coverage != null) {
            for (LatticePoint world : FindingCells.cells(coverage)) {
                cells.put(LatticeKey.of(world), baseMark(world, context, cover, view));
            }
        }
        for (Map.Entry<LatticeKey, CellMark> mark : findingMarks(finding, context, cover, view).entrySet()) {
            // unknown outranks only present, so a weak base never hides a finding's mark
            cells.merge(mark.getKey(), mark.getValue(), CellMark::louder);
        }
        List<Marker> markers = new ArrayList<>();
        MarkerKind kind = markerKind(finding.code());
        Label primary = finding.primaryLabel();
        if (kind != null && primary != null) {
            markers.add(new Marker(worldLocus(primary.locus(), context), finding.message(), kind));
        }
        markers.addAll(pinMarkers(context));
        OverlayModel overlay = new OverlayModel(bounds, cells, markers);
        return List.of(new Remedy.Preview(overlay, "expected coverage of " + blueprint.id() + " at the solve siting"));
    }

    /** present only when every leaf at the cell provably holds in the capture; a failure or an opaque leaf is unknown. */
    private static CellMark baseMark(
        LatticePoint world,
        RemedyContext context,
        LeafCover cover,
        @Nullable WorldView view
    ) {
        if (view == null) {
            return CellMark.unknown;
        }
        if (!(view.read(world) instanceof CellRead.Found found)) {
            return CellMark.unknown;
        }
        LatticePoint local = context.siting().toLocal(world);
        if (local == null) {
            return CellMark.unknown;
        }
        List<ExpectationDeclaration> leaves = cover.leavesAt(local);
        if (leaves.isEmpty()) {
            return CellMark.unknown;
        }
        OccupantSnapshot occupant = found.occupant();
        boolean holds = true;
        for (ExpectationDeclaration leaf : leaves) {
            Boolean leafHolds = evaluate(leaf, occupant, view, world);
            if (leafHolds == null) {
                return CellMark.unknown;
            }
            holds &= leafHolds;
        }
        return holds ? CellMark.present : CellMark.unknown;
    }

    /** True/false when the leaf is decidable from the capture; null when it is opaque to the kernel. */
    private static @Nullable Boolean evaluate(
        ExpectationDeclaration leaf,
        OccupantSnapshot occupant,
        WorldView view,
        LatticePoint world
    ) {
        return switch (leaf) {
            case ExpectationDeclaration.BlockLeaf block -> occupant.descriptor().type().equals(block.type());
            case ExpectationDeclaration.TraitLeaf trait ->
                trait.present() ? occupant.has(trait.key()) : !occupant.has(trait.key());
            case ExpectationDeclaration.MarkerLeaf marker -> switch (marker) {
                case air -> occupant.isAir();
                case anyOccupant -> true;
                case empty -> occupant.isAir() || isVanillaFluid(occupant);
                case nonFluid -> !isVanillaFluid(occupant);
                case envelopeAir -> {
                    OccupantSnapshot stored = view.cellOccupant(world);
                    yield stored == null ? null : stored.isAir();
                }
            };
            case ExpectationDeclaration.HostLeaf ignored -> null;
            case ExpectationDeclaration.Undescribable ignored -> null;
            default -> null;
        };
    }

    private static boolean isVanillaFluid(OccupantSnapshot occupant) {
        NamespacedId type = occupant.descriptor().type();
        return type.equals(NamespacedId.of("minecraft", "water")) || type.equals(NamespacedId.of("minecraft", "lava"));
    }

    private static Map<LatticeKey, CellMark> findingMarks(
        Finding finding,
        RemedyContext context,
        LeafCover cover,
        @Nullable WorldView view
    ) {
        FindingCode code = finding.code();
        Map<LatticeKey, CellMark> marks = new TreeMap<>();
        if (code.equals(Codes.claimConflict.code())) {
            for (LatticePoint cell : FindingCells.world(finding, context.siting())) {
                marks.put(LatticeKey.of(cell), CellMark.blocked);
            }
            return marks;
        }
        if (code.equals(Codes.negationMatched.code()) || code.equals(Codes.envelopeOpen.code())) {
            for (LatticePoint cell : FindingCells.world(finding, context.siting())) {
                marks.put(LatticeKey.of(cell), CellMark.extra);
            }
            return marks;
        }
        if (code.equals(Codes.unreadableCell.code())
                || code.equals(Codes.footprintClipped.code())
                || code.equals(Codes.nestedCut.code())
                || code.equals(Codes.unreadableRead.code())
                || code.equals(Codes.snapshotStale.code())) {
            for (LatticePoint cell : FindingCells.world(finding, context.siting())) {
                marks.put(LatticeKey.of(cell), CellMark.unknown);
            }
            return marks;
        }
        if (code.equals(Codes.expected.code())) {
            for (FindingCells.CellRef cell : FindingCells.pairs(finding, context.siting())) {
                marks.put(LatticeKey.of(cell.world()), expectedMark(cell, cover, view));
            }
        }
        return marks;
    }

    /** An unreadable or uncaptured cell is unknown, never missing; everything else follows the declarations. */
    private static CellMark expectedMark(FindingCells.CellRef cell, LeafCover cover, @Nullable WorldView view) {
        if (view == null || !(view.read(cell.world()) instanceof CellRead.Found found)) {
            return CellMark.unknown;
        }
        OccupantSnapshot occupant = found.occupant();
        List<ExpectationDeclaration> leaves = cover.leavesAt(cell.local());
        boolean airFamily = false;
        boolean other = false;
        for (ExpectationDeclaration leaf : leaves) {
            if (leaf instanceof ExpectationDeclaration.MarkerLeaf marker
                    && (marker == ExpectationDeclaration.MarkerLeaf.air
                            || marker == ExpectationDeclaration.MarkerLeaf.empty
                            || marker == ExpectationDeclaration.MarkerLeaf.nonFluid)) {
                airFamily = true;
            } else {
                other = true;
            }
        }
        if (occupant.isAir()) {
            return other ? CellMark.missing : CellMark.wrong;
        }
        return airFamily ? CellMark.extra : CellMark.wrong;
    }

    private static @Nullable MarkerKind markerKind(FindingCode code) {
        if (code.equals(orientationUnrepresentable)) {
            return MarkerKind.warning;
        }
        if (code.equals(Codes.anchorInvalid.code()) || code.equals(Codes.budgetExhausted.code())) {
            return MarkerKind.error;
        }
        if (code.equals(Codes.cancelled.code())) {
            return MarkerKind.info;
        }
        return null;
    }

    private static Locus worldLocus(Locus locus, RemedyContext context) {
        if (locus.frame() != LocusFrame.local) {
            return locus;
        }
        CuboidSet world = context.siting().toWorld(locus.cells());
        return world == null ? locus : Locus.world(world);
    }

    /** One info marker per pin that names no part of the blueprint or cells outside its legal set. */
    private static List<Marker> pinMarkers(RemedyContext context) {
        SolvePreferences preferences = context.preferences();
        if (preferences == null || preferences.partPins().isEmpty()) {
            return List.of();
        }
        List<Marker> markers = new ArrayList<>();
        for (Map.Entry<NamespacedId, CuboidSet> pin : preferences.partPins().entrySet()) {
            if (pin.getValue().isEmpty()) {
                continue;
            }
            Bay bay = context.blueprint().bay(pin.getKey());
            if (bay == null) {
                Check<?> check = context.blueprint().check(pin.getKey());
                bay = check == null ? null : check.bay();
            }
            Locus locus = Locus.world(pin.getValue());
            if (bay == null) {
                markers.add(
                    new Marker(
                        locus,
                        "pin for " + pin.getKey() + " names no bay or check of this blueprint",
                        MarkerKind.info
                    )
                );
                continue;
            }
            CuboidSet legal = context.siting().toWorld(bay.cells());
            if (legal == null || !legal.containsAll(pin.getValue())) {
                markers.add(
                    new Marker(locus, "pin for " + pin.getKey() + " names cells outside its legal set", MarkerKind.info)
                );
            }
        }
        return markers;
    }
}
