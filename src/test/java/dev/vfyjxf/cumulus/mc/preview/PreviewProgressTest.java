package dev.vfyjxf.cumulus.mc.preview;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.BlueprintBuilder;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.mc.pref.PlacementWeight;
import dev.vfyjxf.cumulus.mc.pref.SitingCandidateScorer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The progress measurement, the 1.5 lock and the ranking over a synthetic world: the same code path
 * the session runs, with the world reduced to a block-state function so every input is explicit.
 */
class PreviewProgressTest {

    private static final LatticePoint origin = LatticePoint.of(0, 64, 0);
    private static final NamespacedId stone = NamespacedId.of("minecraft", "stone");

    @Test
    void anAsymmetricProgressLocksToOnePose() {
        PreviewCycle cycle = PreviewCycle.build(
            lBlueprint(),
            List.of(origin),
            Turn.none,
            world(Map.of(pos(0, 64, 0), stone(), pos(2, 64, 0), stone(), pos(0, 64, 1), stone()))
        );
        assertEquals(8, cycle.candidates().size());
        assertEquals(1, cycle.measured().stream().filter(candidate -> candidate.satisfied() == 3).count());
        cycle.lockProgress();
        assertTrue(cycle.locked(), "one pose explains all three built cells");
        assertEquals(Orientation.identity, cycle.slot().siting().placement().orientation());
    }

    @Test
    void aSymmetricProgressCannotLock() {
        PreviewCycle cycle = PreviewCycle.build(
            pairBlueprint(),
            List.of(origin),
            Turn.none,
            world(Map.of(pos(0, 64, 0), stone(), pos(2, 64, 2), stone()))
        );
        assertEquals(2, cycle.measured().stream().filter(candidate -> candidate.satisfied() == 2).count());
        cycle.lockProgress();
        assertFalse(cycle.locked(), "two poses explain a diagonal pair, so the session must not guess");
    }

    @Test
    void noProgressDoesNotLock() {
        PreviewCycle cycle = PreviewCycle.build(lBlueprint(), List.of(origin), Turn.none, world(Map.of()));
        cycle.lockProgress();
        assertFalse(cycle.locked(), "an empty world has nothing to lock to");
    }

    @Test
    void theSupportFaceIsTheUndersideForALevelBlueprint() {
        PreviewCycle cycle = PreviewCycle
                .build(singleCellBlueprint(), List.of(origin), Turn.none, world(Map.of(pos(0, 63, 0), stone())));
        assertEquals(100, cycle.measured().get(0).supportBelow());
        assertEquals(0, cycle.measured().get(0).supportAbove());
        PreviewCycle hanging = PreviewCycle
                .build(singleCellBlueprint(), List.of(origin), Turn.none, world(Map.of(pos(0, 65, 0), stone())));
        assertEquals(0, hanging.measured().get(0).supportBelow());
        assertEquals(100, hanging.measured().get(0).supportAbove());
    }

    @Test
    void theRankingPutsTheMostProgressFirstAndTiesKeepTheCanonicalOrder() {
        PreviewCycle cycle = PreviewCycle.build(
            lBlueprint(),
            List.of(origin),
            Turn.none,
            world(Map.of(pos(0, 64, 0), stone(), pos(2, 64, 0), stone(), pos(0, 64, 1), stone()))
        );
        SitingCandidateScorer.View view = new SitingCandidateScorer.View(
            LatticePoint.of(0, 64, 8),
            Heading.north,
            Heading.north,
            null,
            false
        );
        cycle.rank(
            new SitingCandidateScorer(),
            List.of(PlacementWeight.mostProgress, PlacementWeight.remembered),
            view
        );
        assertEquals(Orientation.identity, cycle.candidates().get(0).siting().placement().orientation());
        assertEquals(8, cycle.candidates().size());
    }

    @Test
    void theMeasurementIsDeterministic() {
        Function<BlockPos, BlockState> world = world(
            Map.of(pos(0, 64, 0), stone(), pos(2, 64, 0), stone(), pos(0, 64, 1), stone())
        );
        assertEquals(
            PreviewCycle.build(lBlueprint(), List.of(origin), Turn.none, world).measured(),
            PreviewCycle.build(lBlueprint(), List.of(origin), Turn.none, world).measured()
        );
    }

    /** The three-cell L with a trivial stabiliser: no upright pose but identity explains all of it. */
    private static Blueprint lBlueprint() {
        BlueprintBuilder builder = Blueprint.builder(NamespacedId.of("structurecumulus", "l_probe"))
                .bounds(Cuboid.of(0, 0, 0, 3, 1, 2));
        builder.check(cellCheck(builder, "a", 0, 0, 0));
        builder.check(cellCheck(builder, "b", 2, 0, 0));
        builder.check(cellCheck(builder, "c", 0, 0, 1));
        return builder.build();
    }

    /** The two-cell diagonal pair is invariant under the diagonal reflection, so it never locks. */
    private static Blueprint pairBlueprint() {
        BlueprintBuilder builder = Blueprint.builder(NamespacedId.of("structurecumulus", "pair_probe"))
                .bounds(Cuboid.of(0, 0, 0, 3, 1, 3));
        builder.check(cellCheck(builder, "a", 0, 0, 0));
        builder.check(cellCheck(builder, "b", 2, 0, 2));
        return builder.build();
    }

    private static Blueprint singleCellBlueprint() {
        BlueprintBuilder builder = Blueprint.builder(NamespacedId.of("structurecumulus", "cell_probe"))
                .bounds(Cuboid.of(0, 0, 0, 1, 1, 1));
        builder.check(cellCheck(builder, "a", 0, 0, 0));
        return builder.build();
    }

    private static Check<?> cellCheck(BlueprintBuilder builder, String id, int x, int y, int z) {
        Bay bay = Bay.of(
            NamespacedId.of("structurecumulus", "bay_" + id),
            Cuboid.ofSize(x, y, z, 1, 1, 1),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.shared
        );
        return Check.of(
            NamespacedId.of("structurecumulus", "check_" + id),
            bay,
            Focuses.bay(),
            Expectations.eachCell(Expectations.block(stone))
        );
    }

    private static Function<BlockPos, BlockState> world(Map<BlockPos, BlockState> blocks) {
        return position -> blocks.getOrDefault(position, Blocks.AIR.defaultBlockState());
    }

    private static BlockState stone() {
        return Blocks.STONE.defaultBlockState();
    }

    private static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    private static List<Siting> sittings(PreviewCycle cycle) {
        List<Siting> sittings = new ArrayList<>();
        for (var candidate : cycle.candidates()) {
            sittings.add(candidate.siting());
        }
        return List.copyOf(sittings);
    }

    private static Orientation turned(Turn turn) {
        return Orientation.of(turn, Tilt.level, Flip.none);
    }
}
