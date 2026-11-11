package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.mc.preview.net.SitingCandidate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The expressible siting cycle of one session: origins × the four upright turns snapped to the
 * player's facing × both upright flips, then the tilted poses the blueprint's policy declares. A pose
 * whose declarations cannot express an orientation leaves the cycle and is reported instead (design
 * 15 §7.1, §7.3).
 */
final class PreviewCycle {

    /** One expressible pose; {@code score} counts the cells already satisfied at that siting. */
    record Slot(LatticePoint origin, Orientation orientation, Siting siting, int score) {}

    /** One pose the declarations cannot express, with the block that refuses it. */
    record Filtered(Siting siting, PreviewExpected.Filter filter) {}

    private final List<Slot> slots;
    private final List<Filtered> filtered;
    private final List<LatticePoint> origins;
    private int current;

    private PreviewCycle(List<Slot> slots, List<Filtered> filtered, List<LatticePoint> origins) {
        this.slots = List.copyOf(slots);
        this.filtered = List.copyOf(filtered);
        this.origins = List.copyOf(origins);
    }

    /** One slot per resolved anchor siting: an anchored blueprint has no free orientation to cycle. */
    static PreviewCycle ofSittings(Blueprint blueprint, List<Siting> sittings, Level level) {
        List<Slot> slots = new ArrayList<>();
        List<Filtered> filtered = new ArrayList<>();
        List<LatticePoint> origins = new ArrayList<>();
        for (Siting siting : sittings) {
            if (!origins.contains(siting.origin())) {
                origins.add(siting.origin());
            }
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
                    progress(level, siting, expected.states())
                )
            );
        }
        return new PreviewCycle(slots, filtered, origins);
    }

    static PreviewCycle build(Blueprint blueprint, List<LatticePoint> origins, Turn facing, Level level) {
        List<Orientation> orientations = new ArrayList<>();
        Turn[] order = {facing, facing.compose(Turn.clockwise), facing.compose(Turn.half),
                facing.compose(Turn.counterclockwise)};
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
                slots.add(new Slot(origin, orientation, siting, progress(level, siting, expected.states())));
            }
        }
        return new PreviewCycle(slots, filtered, origins);
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

    List<Filtered> filtered() {
        return filtered;
    }

    List<SitingCandidate> candidates() {
        List<SitingCandidate> candidates = new ArrayList<>(slots.size());
        for (Slot slot : slots) {
            candidates.add(new SitingCandidate(slot.siting(), slot.score()));
        }
        return List.copyOf(candidates);
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

    /** 3.7: back to the first candidate of the cycle. */
    void reset() {
        current = 0;
    }

    /** 3.7 long press: the identity pose when the cycle offers it, otherwise the first candidate. */
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

    private int find(LatticePoint origin, Orientation orientation) {
        for (int slot = 0; slot < slots.size(); slot++) {
            Slot candidate = slots.get(slot);
            if (candidate.origin().equals(origin) && candidate.orientation().equals(orientation)) {
                return slot;
            }
        }
        return -1;
    }

    /** The cells already holding their expected state under this siting; the preview's own score. */
    private static int progress(Level level, Siting siting, Map<LatticePoint, BlockState> states) {
        int satisfied = 0;
        for (Map.Entry<LatticePoint, BlockState> entry : states.entrySet()) {
            LatticePoint world = siting.toWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            BlockState live = level.getBlockState(new BlockPos(world.x(), world.y(), world.z()));
            if (live.equals(entry.getValue())) {
                satisfied++;
            }
        }
        return satisfied;
    }

    static @Nullable Turn turnOf(Direction facing) {
        return switch (facing) {
            case NORTH -> Turn.none;
            case EAST -> Turn.clockwise;
            case SOUTH -> Turn.half;
            case WEST -> Turn.counterclockwise;
            default -> null;
        };
    }
}
