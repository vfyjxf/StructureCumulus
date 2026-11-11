package dev.vfyjxf.cumulus.kernel.remedy;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.finding.Applicability;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationDeclaration;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.Trait;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The built-in `structurecumulus:cell_repair` provider (D-85): the finding→edit mapping of remedy
 * §5.1. Pure and re-entrant; it reads the blueprint's declarations through the D-91 port and
 * classifies cells against the captured snapshot in {@link RemedyContext#outcome()}, never a live
 * world. With no outcome, or over an undescribable declaration, the remedy is advisory.
 */
public record StandardRepairProvider() implements RemedyProvider {

    @Override
    public NamespacedId id() {
        return NamespacedId.of("structurecumulus", "cell_repair");
    }

    @Override
    public List<Remedy> solve(Finding finding, RemedyContext context) {
        FindingCode code = finding.code();
        if (code.equals(Codes.expected.code())) {
            return repairExpected(finding, context);
        }
        if (code.equals(Codes.negationMatched.code())) {
            return repairNegation(finding, context);
        }
        if (code.equals(Codes.envelopeOpen.code())) {
            return removeAt(
                FindingCells.pairs(finding, context.siting()),
                "the envelope is closed; remove the foreign occupant"
            );
        }
        if (code.equals(Codes.thresholdImpossible.code())) {
            return repairThreshold(finding, context);
        }
        if (code.equals(Codes.claimConflict.code())) {
            return List
                    .of(advisory("the cells are held by a conflicting claim; resolve the claim or move the structure"));
        }
        return List.of();
    }

    private List<Remedy> repairExpected(Finding finding, RemedyContext context) {
        List<FindingCells.CellRef> cells = FindingCells.pairs(finding, context.siting());
        if (cells.isEmpty()) {
            return List.of();
        }
        LeafCover cover = LeafCover.of(context.blueprint());
        WorldView view = context.outcome() == null ? null : context.outcome().view();
        List<Edit> chosen = new ArrayList<>();
        List<Remedy> siblings = new ArrayList<>();
        boolean advisoryOnly = false;
        for (FindingCells.CellRef cell : cells) {
            List<ExpectationDeclaration> leaves = cover.leavesAt(cell.local());
            if (leaves.isEmpty()) {
                continue;
            }
            OccupantSnapshot occupant = occupantAt(view, cell.world());
            if (occupant == null) {
                // no capture: the absent/different distinction is unknowable; unreadable: the preview owns the mark
                advisoryOnly = view == null;
                continue;
            }
            List<Edit> candidates = candidatesFor(leaves, occupant, cell.world());
            if (candidates.isEmpty()) {
                advisoryOnly = true;
                continue;
            }
            chosen.add(candidates.get(0));
            for (int i = 1; i < candidates.size() && siblings.size() + 1 < context.maxResults(); i++) {
                siblings.add(single(candidates.get(i), "an equally legal alternative"));
            }
        }
        if (chosen.isEmpty()) {
            return advisoryOnly
                    ? List.of(advisory("the declaration at the finding's cells is not machine-describable"))
                    : List.of();
        }
        List<Remedy> out = new ArrayList<>();
        out.add(plan(chosen));
        out.addAll(siblings);
        return out;
    }

    private List<Remedy> repairNegation(Finding finding, RemedyContext context) {
        List<FindingCells.CellRef> cells = FindingCells.pairs(finding, context.siting());
        if (cells.isEmpty()) {
            return List.of();
        }
        LeafCover cover = LeafCover.of(context.blueprint());
        List<Edit> chosen = new ArrayList<>();
        List<Remedy> siblings = new ArrayList<>();
        boolean resolved = false;
        boolean advisory = false;
        for (FindingCells.CellRef cell : cells) {
            List<ExpectationDeclaration> leaves = cover.negatedAt(cell.local());
            if (leaves.isEmpty()) {
                continue;
            }
            resolved = true;
            CuboidSet cellSet = CuboidSet.of(Cuboid.of(cell.world()));
            boolean opaque = false;
            List<Edit> candidates = new ArrayList<>();
            for (ExpectationDeclaration leaf : leaves) {
                if (leaf instanceof ExpectationDeclaration.HostLeaf
                        || leaf instanceof ExpectationDeclaration.Undescribable) {
                    opaque = true;
                } else if (leaf instanceof ExpectationDeclaration.TraitLeaf trait) {
                    // the negated leaf matched; only a present trait can be broken by removing it
                    if (trait.present()) {
                        candidates.add(new Edit.SetTrait(cellSet, Trait.absent(trait.key())));
                    } else {
                        opaque = true;
                    }
                } else {
                    candidates.add(new Edit.Remove(cellSet));
                }
            }
            if (opaque || candidates.isEmpty()) {
                advisory = true;
                continue;
            }
            // a negated junction holds when several children hold; breaking any one suffices
            chosen.add(candidates.get(0));
            for (int i = 1; i < candidates.size() && siblings.size() + 1 < context.maxResults(); i++) {
                siblings.add(single(candidates.get(i), "an equally legal alternative"));
            }
        }
        if (!resolved) {
            return List.of();
        }
        if (chosen.isEmpty()) {
            return advisory ? List.of(advisory("the negated declaration is not machine-describable")) : List.of();
        }
        List<Remedy> out = new ArrayList<>();
        out.add(plan(chosen));
        out.addAll(siblings);
        return out;
    }

    private List<Remedy> repairThreshold(Finding finding, RemedyContext context) {
        List<FindingCells.CellRef> cells = FindingCells.pairs(finding, context.siting());
        if (cells.isEmpty()) {
            return List.of();
        }
        LeafCover cover = LeafCover.of(context.blueprint());
        Set<Map.Entry<Check<?>, ExpectationDeclaration.Threshold>> sites = new LinkedHashSet<>();
        for (FindingCells.CellRef cell : cells) {
            sites.addAll(cover.thresholdsAt(cell.local()));
        }
        if (sites.isEmpty()) {
            return List.of(advisory("the failing threshold is not a declared kernel threshold"));
        }
        List<Remedy> out = new ArrayList<>();
        for (Map.Entry<Check<?>, ExpectationDeclaration.Threshold> site : sites) {
            if (out.size() >= context.maxResults()) {
                break;
            }
            out.addAll(repairThresholdSite(cells, site.getKey(), site.getValue(), context));
        }
        List<Remedy> unique = new ArrayList<>(new LinkedHashSet<>(out));
        return unique.size() > context.maxResults() ? new ArrayList<>(unique.subList(0, context.maxResults())) : unique;
    }

    /** One site's repair: the most preferred legal cell first, every unchosen candidate a sibling (never silent). */
    private List<Remedy> repairThresholdSite(
        List<FindingCells.CellRef> cells,
        Check<?> check,
        ExpectationDeclaration.Threshold threshold,
        RemedyContext context
    ) {
        if (threshold.kind() != ExpectationDeclaration.ThresholdKind.atLeast) {
            return List.of(
                advisory("a threshold excess admits no machine edit; remove occupants or relax the declaration")
            );
        }
        List<NamespacedId> types = new ArrayList<>();
        for (ExpectationDeclaration child : threshold.children()) {
            for (ExpectationDeclaration leaf : LeafCover.positiveLeaves(child)) {
                if (leaf instanceof ExpectationDeclaration.BlockLeaf block && !types.contains(block.type())) {
                    types.add(block.type());
                }
            }
        }
        if (types.isEmpty()) {
            return List.of(advisory("the threshold's children declare no block type to place"));
        }
        WorldView view = context.outcome() == null ? null : context.outcome().view();
        if (view == null) {
            return List.of(advisory("without a captured snapshot the deficit cells cannot be told apart"));
        }
        Map<LatticeKey, FindingCells.CellRef> candidates = new TreeMap<>();
        for (FindingCells.CellRef cell : cells) {
            if (!check.bay().cells().contains(cell.local())) {
                continue;
            }
            OccupantSnapshot occupant = occupantAt(view, cell.world());
            if (occupant == null || types.contains(occupant.descriptor().type())) {
                continue;
            }
            candidates.put(LatticeKey.of(cell.local()), cell);
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<FindingCells.CellRef> ordered = orderByPreference(new ArrayList<>(candidates.values()), check, context);
        FindingCells.CellRef best = ordered.get(0);
        List<Remedy> out = new ArrayList<>();
        out.add(single(fillEdit(best, types.get(0), view), "the most preferred legal cell for the threshold deficit"));
        for (int i = 1; i < types.size(); i++) {
            out.add(single(fillEdit(best, types.get(i), view), "an equally legal alternative type"));
        }
        for (int i = 1; i < ordered.size(); i++) {
            out.add(single(fillEdit(ordered.get(i), types.get(0), view), "an equally legal alternative cell"));
        }
        return out;
    }

    private List<Remedy> removeAt(List<FindingCells.CellRef> cells, String rationale) {
        if (cells.isEmpty()) {
            return List.of();
        }
        CuboidSet cellSet = unionOfWorld(cells);
        Edit edit = new Edit.Remove(cellSet);
        EditStep step = new EditStep(edit, Locus.world(cellSet), Applicability.machineApplicable);
        return List.of(
            new Remedy.Repair(new EditPlan(List.of(step), rationale), Applicability.machineApplicable, rationale)
        );
    }

    /** The legal edits for one cell, least destructive first so the primary pick is never a removal of choice. */
    private static List<Edit> candidatesFor(
        List<ExpectationDeclaration> leaves,
        OccupantSnapshot occupant,
        LatticePoint worldCell
    ) {
        CuboidSet cellSet = CuboidSet.of(Cuboid.of(worldCell));
        List<Edit> constructive = new ArrayList<>();
        boolean airFamily = false;
        boolean removeCandidate = false;
        List<NamespacedId> blockTypes = new ArrayList<>();
        List<ExpectationDeclaration.TraitLeaf> traits = new ArrayList<>();
        for (ExpectationDeclaration leaf : leaves) {
            switch (leaf) {
                case ExpectationDeclaration.BlockLeaf block -> {
                    if (!blockTypes.contains(block.type())) {
                        blockTypes.add(block.type());
                    }
                }
                case ExpectationDeclaration.TraitLeaf trait -> traits.add(trait);
                case ExpectationDeclaration.MarkerLeaf marker -> {
                    if (marker == ExpectationDeclaration.MarkerLeaf.air
                            || marker == ExpectationDeclaration.MarkerLeaf.empty
                            || marker == ExpectationDeclaration.MarkerLeaf.nonFluid) {
                        airFamily = true;
                    }
                }
                default -> {}
            }
        }
        if (airFamily && !occupant.isAir()) {
            removeCandidate = true;
        }
        NamespacedId occupantType = occupant.descriptor().type();
        if (!blockTypes.isEmpty()) {
            if (occupant.isAir()) {
                for (NamespacedId type : blockTypes) {
                    constructive.add(new Edit.Place(cellSet, type, Map.of()));
                }
            } else if (!blockTypes.contains(occupantType)) {
                for (NamespacedId type : blockTypes) {
                    constructive.add(new Edit.Replace(cellSet, type, Map.of()));
                }
            } else {
                addTraitRemovals(constructive, traits, cellSet);
            }
        } else if (!airFamily) {
            addTraitRemovals(constructive, traits, cellSet);
        }
        List<Edit> out = new ArrayList<>(constructive);
        if (removeCandidate) {
            out.add(new Edit.Remove(cellSet));
        }
        return out;
    }

    /** Only a failed traitAbsent is repairable: absence is the one trait state the declaration names. */
    private static void addTraitRemovals(
        List<Edit> edits,
        List<ExpectationDeclaration.TraitLeaf> traits,
        CuboidSet cellSet
    ) {
        for (ExpectationDeclaration.TraitLeaf trait : traits) {
            if (!trait.present()) {
                edits.add(new Edit.SetTrait(cellSet, Trait.absent(trait.key())));
            }
        }
    }

    private static @Nullable OccupantSnapshot occupantAt(@Nullable WorldView view, LatticePoint worldCell) {
        if (view == null) {
            return null;
        }
        return view.read(worldCell) instanceof CellRead.Found found ? found.occupant() : null;
    }

    /** Pin, then preferredCells, then canonical lattice order (D-86's conflict order); a pin never filters. */
    private static List<FindingCells.CellRef> orderByPreference(
        List<FindingCells.CellRef> candidates,
        Check<?> check,
        RemedyContext context
    ) {
        SolvePreferences preferences = context.preferences();
        List<CuboidSet> preferred = preferences == null ? List.of() : preferences.preferredCells();
        CuboidSet pin = pinOf(preferences, check, context.siting());
        candidates.sort((left, right) -> {
            int byPin = Boolean.compare(!isPinned(left, pin), !isPinned(right, pin));
            if (byPin != 0) {
                return byPin;
            }
            int byPreference = Integer
                    .compare(preferenceRank(preferred, left.world()), preferenceRank(preferred, right.world()));
            if (byPreference != 0) {
                return byPreference;
            }
            return LatticeKey.of(left.local()).compareTo(LatticeKey.of(right.local()));
        });
        return candidates;
    }

    /** The pin in the blueprint's local frame; null when absent or unmappable, which ignores it like a miss. */
    private static @Nullable CuboidSet pinOf(@Nullable SolvePreferences preferences, Check<?> check, Siting siting) {
        if (preferences == null) {
            return null;
        }
        CuboidSet pin = preferences.partPins().get(check.bay().id());
        return pin == null ? null : siting.toLocal(pin);
    }

    private static boolean isPinned(FindingCells.CellRef cell, @Nullable CuboidSet pin) {
        return pin != null && pin.contains(cell.local());
    }

    private static int preferenceRank(List<CuboidSet> preferred, LatticePoint worldCell) {
        for (int i = 0; i < preferred.size(); i++) {
            if (preferred.get(i).contains(worldCell)) {
                return i;
            }
        }
        return preferred.size();
    }

    private static Edit fillEdit(FindingCells.CellRef cell, NamespacedId type, WorldView view) {
        CuboidSet cellSet = CuboidSet.of(Cuboid.of(cell.world()));
        OccupantSnapshot occupant = occupantAt(view, cell.world());
        return occupant != null && occupant.isAir()
                ? new Edit.Place(cellSet, type, Map.of())
                : new Edit.Replace(cellSet, type, Map.of());
    }

    private static Remedy single(Edit edit, String rationale) {
        Applicability applicability = applicabilityOf(edit);
        Locus locus = Locus.world(cellsOf(edit));
        return new Remedy.Repair(
            new EditPlan(List.of(new EditStep(edit, locus, applicability)), rationale),
            applicability,
            rationale
        );
    }

    private static Remedy plan(List<Edit> chosen) {
        Map<Object, List<Edit>> groups = new LinkedHashMap<>();
        for (Edit edit : chosen) {
            groups.computeIfAbsent(payloadKey(edit), ignored -> new ArrayList<>()).add(edit);
        }
        List<EditStep> steps = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        Applicability weakest = Applicability.machineApplicable;
        for (List<Edit> group : groups.values()) {
            Edit merged = merge(group);
            Applicability applicability = applicabilityOf(merged);
            if (applicability.rank() > weakest.rank()) {
                weakest = applicability;
            }
            steps.add(new EditStep(merged, Locus.world(cellsOf(merged)), applicability));
            parts.add(describe(merged));
        }
        String rationale = String.join("; ", parts);
        return new Remedy.Repair(new EditPlan(steps, rationale), weakest, rationale);
    }

    private static Object payloadKey(Edit edit) {
        return switch (edit) {
            case Edit.Place place -> List.of("place", place.type());
            case Edit.Replace replace -> List.of("replace", replace.type());
            case Edit.Remove ignored -> "remove";
            case Edit.SetTrait setTrait -> List.of("trait", setTrait.trait().key().id());
        };
    }

    private static Edit merge(List<Edit> group) {
        CuboidSet cells = CuboidSet.empty();
        for (Edit edit : group) {
            cells = cells.union(cellsOf(edit));
        }
        Edit first = group.get(0);
        return switch (first) {
            case Edit.Place place -> new Edit.Place(cells, place.type(), place.properties());
            case Edit.Replace replace -> new Edit.Replace(cells, replace.type(), replace.properties());
            case Edit.Remove ignored -> new Edit.Remove(cells);
            case Edit.SetTrait setTrait -> new Edit.SetTrait(cells, setTrait.trait());
        };
    }

    private static String describe(Edit edit) {
        return switch (edit) {
            case Edit.Place place -> "place " + place.type() + " at " + place.cells().volume() + " cell(s)";
            case Edit.Replace replace ->
                "replace with " + replace.type() + " at " + replace.cells().volume() + " cell(s)";
            case Edit.Remove remove -> "remove the occupant at " + remove.cells().volume() + " cell(s)";
            case Edit.SetTrait setTrait ->
                "set trait " + setTrait.trait().key().id() + " at " + setTrait.cells().volume() + " cell(s)";
        };
    }

    private static CuboidSet cellsOf(Edit edit) {
        return switch (edit) {
            case Edit.Place place -> place.cells();
            case Edit.Replace replace -> replace.cells();
            case Edit.Remove remove -> remove.cells();
            case Edit.SetTrait setTrait -> setTrait.cells();
        };
    }

    private static Applicability applicabilityOf(Edit edit) {
        // the kernel cannot know whether the host can write a trait, so SetTrait is never machine-applicable
        return edit instanceof Edit.SetTrait ? Applicability.maybeIncorrect : Applicability.machineApplicable;
    }

    private static Remedy advisory(String rationale) {
        return new Remedy.Repair(new EditPlan(List.of(), rationale), Applicability.hasPlaceholders, rationale);
    }

    private static CuboidSet unionOfWorld(List<FindingCells.CellRef> cells) {
        CuboidSet out = CuboidSet.empty();
        for (FindingCells.CellRef cell : cells) {
            out = out.union(Cuboid.of(cell.world()));
        }
        return out;
    }

}
