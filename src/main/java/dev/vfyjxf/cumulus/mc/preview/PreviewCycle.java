package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.mc.pref.PlacementWeight;
import dev.vfyjxf.cumulus.mc.pref.SitingCandidateScorer;
import dev.vfyjxf.cumulus.mc.preview.net.SitingCandidate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The expressible siting cycle of one session: origins × the four upright turns snapped to the
 * starting heading × both upright flips, then the tilted poses the blueprint's policy declares. A pose
 * whose declarations cannot express an orientation leaves the cycle and is reported instead (design
 * 15 §7.1, §7.3). Ranking leads with the highest score; a cycle whose built cells match one pose only
 * locks to it (1.5).
 */
final class PreviewCycle {

    /** The satisfied cells and the cells a build would have to clear first, plus both support rates. */
    record Measure(int satisfied, int removals, int supportBelow, int supportAbove) {}

    /** One expressible pose; {@code measure} is what the live world says about it. */
    record Slot(LatticePoint origin, Orientation orientation, Siting siting, Measure measure) {}

    /** One pose the declarations cannot express, with the block that refuses it. */
    record Filtered(Siting siting, PreviewExpected.Filter filter) {}

    private final List<Filtered> filtered;
    private List<Slot> slots;
    private int[] scores;
    private int current;
    private boolean locked;

    private PreviewCycle(List<Slot> slots, List<Filtered> filtered) {
        this.slots = List.copyOf(slots);
        this.filtered = List.copyOf(filtered);
        this.scores = new int[this.slots.size()];
        for (int index = 0; index < this.slots.size(); index++) {
            this.scores[index] = this.slots.get(index).measure().satisfied();
        }
    }

    /** One slot per resolved anchor siting: an anchored blueprint has no free orientation to cycle. */
    static PreviewCycle ofSittings(Blueprint blueprint, List<Siting> sittings, Level level) {
        return ofSittings(blueprint, sittings, level::getBlockState);
    }

    static PreviewCycle ofSittings(Blueprint blueprint, List<Siting> sittings, Function<BlockPos, BlockState> blocks) {
        List<Slot> slots = new ArrayList<>();
        List<Filtered> filtered = new ArrayList<>();
        for (Siting siting : sittings) {
            PreviewExpected.Outcome expected = PreviewExpected.of(blueprint, siting.placement().orientation());
            PreviewExpected.Filter refused = expected.filter();
            if (refused != null) {
                filtered.add(new Filtered(siting, refused));
                continue;
            }
            slots.add(
                new Slot(
                    siting.origin(),
                    siting.placement().orientation(),
                    siting,
                    measure(blocks, blueprint, siting, expected.states())
                )
            );
        }
        return new PreviewCycle(slots, filtered);
    }

    static PreviewCycle build(Blueprint blueprint, List<LatticePoint> origins, Turn start, Level level) {
        return build(blueprint, origins, start, level::getBlockState);
    }

    static PreviewCycle build(
        Blueprint blueprint,
        List<LatticePoint> origins,
        Turn start,
        Function<BlockPos, BlockState> blocks
    ) {
        List<Orientation> orientations = new ArrayList<>();
        Turn[] order = {start, start.compose(Turn.clockwise), start.compose(Turn.half),
                start.compose(Turn.counterclockwise)};
        for (Turn turn : order) {
            for (Flip flip : new Flip[]{Flip.none, Flip.frontBack}) {
                orientations.add(Orientation.of(turn, Tilt.level, flip));
            }
        }
        OrientationPolicy policy = blueprint.orientation();
        if (policy != null) {
            for (Orientation allowed : policy.allowed()) {
                if (allowed.tilt() != Tilt.level && !orientations.contains(allowed)) {
                    orientations.add(allowed);
                }
            }
        }
        List<Slot> slots = new ArrayList<>();
        List<Filtered> filtered = new ArrayList<>();
        for (LatticePoint origin : origins) {
            for (Orientation orientation : orientations) {
                Siting siting = Siting.of(origin, orientation);
                PreviewExpected.Outcome expected = PreviewExpected.of(blueprint, orientation);
                PreviewExpected.Filter refused = expected.filter();
                if (refused != null) {
                    filtered.add(new Filtered(siting, refused));
                    continue;
                }
                slots.add(new Slot(origin, orientation, siting, measure(blocks, blueprint, siting, expected.states())));
            }
        }
        return new PreviewCycle(slots, filtered);
    }

    boolean isEmpty() {
        return slots.isEmpty();
    }

    int current() {
        return current;
    }

    Slot slot() {
        return slots.get(current);
    }

    boolean locked() {
        return locked;
    }

    List<Filtered> filtered() {
        return filtered;
    }

    /** The ranked candidates in cycle order; {@code score} is the ranked score. */
    List<SitingCandidate> candidates() {
        List<SitingCandidate> candidates = new ArrayList<>(slots.size());
        for (int index = 0; index < slots.size(); index++) {
            candidates.add(new SitingCandidate(slots.get(index).siting(), scores[index]));
        }
        return List.copyOf(candidates);
    }

    /** The measured facts the scorer ranks, with the pre-ranking index as the canonical order. */
    List<SitingCandidateScorer.Candidate> measured() {
        List<SitingCandidateScorer.Candidate> candidates = new ArrayList<>(slots.size());
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            Measure measure = slot.measure();
            candidates.add(
                new SitingCandidateScorer.Candidate(
                    slot.siting(),
                    index,
                    measure.satisfied(),
                    measure.removals(),
                    measure.supportBelow(),
                    measure.supportAbove()
                )
            );
        }
        return List.copyOf(candidates);
    }

    /** Reorders the cycle by descending score and opens on the leading candidate. */
    void rank(SitingCandidateScorer scorer, List<PlacementWeight> weights, SitingCandidateScorer.View view) {
        List<SitingCandidateScorer.Scored> ranked = scorer.rank(measured(), view, weights);
        List<Slot> reordered = new ArrayList<>(ranked.size());
        int[] rankedScores = new int[ranked.size()];
        for (int index = 0; index < ranked.size(); index++) {
            SitingCandidateScorer.Scored scored = ranked.get(index);
            reordered.add(slots.get(indexOf(scored.siting())));
            rankedScores[index] = scored.score();
        }
        slots = List.copyOf(reordered);
        scores = rankedScores;
        current = 0;
    }

    /**
     * 1.5: locks when one pose explains strictly more built cells than every other, so no control can
     * cycle away from the only reading of the progress.
     */
    void lockProgress() {
        int best = 0;
        int bestIndex = -1;
        boolean tied = false;
        for (int index = 0; index < slots.size(); index++) {
            int satisfied = slots.get(index).measure().satisfied();
            if (satisfied > best) {
                best = satisfied;
                bestIndex = index;
                tied = false;
            } else if (satisfied == best && satisfied > 0) {
                tied = true;
            }
        }
        if (best > 0 && !tied && bestIndex >= 0) {
            locked = true;
            current = bestIndex;
        }
    }

    /** True when the cycle is a single anchored pose, which the controls cannot cycle. */
    boolean anchored() {
        return slots.size() <= 1 && filtered.isEmpty();
    }

    /** Advances the turn inside the current origin and tilt; false when no other turn is expressible. */
    boolean turn(boolean clockwise) {
        Slot from = slot();
        for (int offset = 1; offset < 4; offset++) {
            Turn step = clockwise ? Turn.values()[offset] : Turn.values()[4 - offset];
            Orientation target = Orientation.of(
                from.orientation().turn().compose(step),
                from.orientation().tilt(),
                from.orientation().isMirror() ? Flip.frontBack : Flip.none
            );
            int slot = find(from.origin(), target);
            if (slot >= 0) {
                current = slot;
                return true;
            }
        }
        return false;
    }

    /** Toggles the flip at the current origin and turn; false when the mirror is filtered out. */
    boolean flip() {
        Slot from = slot();
        Orientation target = Orientation.of(
            from.orientation().turn(),
            from.orientation().tilt(),
            from.orientation().isMirror() ? Flip.none : Flip.frontBack
        );
        int slot = find(from.origin(), target);
        if (slot < 0) {
            return false;
        }
        current = slot;
        return true;
    }

    /** Moves to the first expressible pose of the next or previous origin candidate. */
    boolean origin(int direction) {
        List<LatticePoint> origins = origins();
        int index = origins.indexOf(slot().origin());
        for (int step = 1; step <= origins.size(); step++) {
            LatticePoint target = origins.get(Math.floorMod(index + direction * step, origins.size()));
            if (target.equals(slot().origin())) {
                return false;
            }
            for (int slot = 0; slot < slots.size(); slot++) {
                if (slots.get(slot).origin().equals(target)) {
                    current = slot;
                    return true;
                }
            }
        }
        return false;
    }

    /** 3.7: back to the leading candidate. */
    void reset() {
        current = 0;
    }

    /** 3.7 long press: the identity pose when the cycle offers it, otherwise the leading candidate. */
    boolean resetIdentity() {
        for (int slot = 0; slot < slots.size(); slot++) {
            if (slots.get(slot).orientation().equals(Orientation.identity)) {
                current = slot;
                return true;
            }
        }
        current = 0;
        return false;
    }

    private List<LatticePoint> origins() {
        List<LatticePoint> origins = new ArrayList<>();
        for (Slot slot : slots) {
            if (!origins.contains(slot.origin())) {
                origins.add(slot.origin());
            }
        }
        return origins;
    }

    private int indexOf(Siting siting) {
        for (int index = 0; index < slots.size(); index++) {
            if (slots.get(index).siting().equals(siting)) {
                return index;
            }
        }
        throw new IllegalStateException("the ranked siting left the cycle: " + siting);
    }

    private int find(LatticePoint origin, Orientation orientation) {
        for (int slot = 0; slot < slots.size(); slot++) {
            Slot candidate = slots.get(slot);
            if (candidate.origin().equals(origin) && candidate.orientation().equals(orientation)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * What the live world says about one pose: satisfied cells, cells a build must clear first, and the
     * share of the world underside and top side that has a neighbour (3.2).
     */
    private static Measure measure(
        Function<BlockPos, BlockState> blocks,
        Blueprint blueprint,
        Siting siting,
        Map<LatticePoint, BlockState> states
    ) {
        int satisfied = 0;
        int removals = 0;
        for (Map.Entry<LatticePoint, BlockState> entry : states.entrySet()) {
            LatticePoint world = siting.toWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            BlockState live = blocks.apply(new BlockPos(world.x(), world.y(), world.z()));
            if (live.equals(entry.getValue())) {
                satisfied++;
            } else if (!live.isAir()) {
                removals++;
            }
        }
        Cuboid bounds = siting.toWorld(blueprint.bounds());
        if (bounds == null) {
            return new Measure(satisfied, removals, 0, 0);
        }
        int columns = 0;
        int below = 0;
        int above = 0;
        for (int x = bounds.min().x(); x < bounds.max().x(); x++) {
            for (int z = bounds.min().z(); z < bounds.max().z(); z++) {
                columns++;
                if (!blocks.apply(new BlockPos(x, bounds.min().y() - 1, z)).isAir()) {
                    below++;
                }
                if (!blocks.apply(new BlockPos(x, bounds.max().y(), z)).isAir()) {
                    above++;
                }
            }
        }
        if (columns == 0) {
            return new Measure(satisfied, removals, 0, 0);
        }
        return new Measure(satisfied, removals, (100 * below) / columns, (100 * above) / columns);
    }

    static @Nullable Turn turnOf(Direction facing) {
        return turnOf(headingOf(facing));
    }

    static @Nullable Heading headingOf(Direction facing) {
        return switch (facing) {
            case NORTH -> Heading.north;
            case SOUTH -> Heading.south;
            case EAST -> Heading.east;
            case WEST -> Heading.west;
            default -> null;
        };
    }

    static @Nullable Turn turnOf(@Nullable Heading heading) {
        if (heading == null) {
            return null;
        }
        return switch (heading) {
            case north -> Turn.none;
            case east -> Turn.clockwise;
            case south -> Turn.half;
            case west -> Turn.counterclockwise;
            default -> null;
        };
    }
}
