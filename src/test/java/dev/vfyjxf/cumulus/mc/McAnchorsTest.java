package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.Footprint;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.structure.AnchorSpec;
import dev.vfyjxf.cumulus.kernel.structure.Bay;
import dev.vfyjxf.cumulus.kernel.structure.Blueprint;
import dev.vfyjxf.cumulus.kernel.structure.Check;
import dev.vfyjxf.cumulus.kernel.structure.Coverage;
import dev.vfyjxf.cumulus.kernel.structure.Expectations;
import dev.vfyjxf.cumulus.kernel.structure.Focuses;
import dev.vfyjxf.cumulus.kernel.structure.OrientationPolicy;
import dev.vfyjxf.cumulus.kernel.structure.SitingResult;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McAnchorsTest {

    private static final NamespacedId role = NamespacedId.of("test", "main");
    private static final Cuboid bounds = Cuboid.of(0, 0, 0, 1, 1, 1);

    @Test
    void resolvesTheSitingFromTheCapturedAnchorCell() {
        BlockState furnace = Blocks.FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        SitingResult result = McAnchors.resolve(blueprint("north"), LatticePoint.origin, view(furnace));

        assertTrue(result.succeeded());
        Siting siting = result.outcome().fold(value -> value, () -> {
            throw new AssertionError("the siting is missing");
        });
        assertEquals(Orientation.identity, siting.placement().orientation());
    }

    @Test
    void theDeclaredValueSelectsTheCapturedHeading() {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
        SitingResult result = McAnchors.resolve(blueprint("east"), LatticePoint.origin, view(stairs));

        assertTrue(result.succeeded());
        Siting siting = result.outcome().fold(value -> value, () -> {
            throw new AssertionError("the siting is missing");
        });
        assertEquals(Orientation.of(Turn.clockwise, Tilt.level, Flip.none), siting.placement().orientation());
    }

    @Test
    void theDeclaredValueComparesIgnoringCase() {
        BlockState furnace = Blocks.FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);

        SitingResult result = McAnchors.resolve(blueprint("NORTH"), LatticePoint.origin, view(furnace));

        assertTrue(result.succeeded());
    }

    @Test
    void aDeclaredValueThatDiffersFromTheCapturedPropertyIsAnchorInvalid() {
        BlockState furnace = Blocks.FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);

        SitingResult result = McAnchors.resolve(blueprint("south"), LatticePoint.origin, view(furnace));

        assertFalse(result.succeeded());
        assertEquals(Codes.anchorInvalid.code(), Objects.requireNonNull(result.findings().primary(), "finding").code());
    }

    @Test
    void anUncapturedAnchorStateIsTheValueNotCapturedCode() {
        LevelSnapshot uncaptured = new LevelSnapshot(snapshot(), bounds, Map.of());

        SitingResult result = McAnchors.resolve(blueprint("north"), LatticePoint.origin, uncaptured);

        assertFalse(result.succeeded());
        assertEquals(
            AdapterCodes.valueNotCaptured,
            Objects.requireNonNull(result.findings().primary(), "finding").code()
        );
    }

    @Test
    void anAirAnchorCellIsInvalid() {
        SitingResult result = McAnchors
                .resolve(blueprint("north"), LatticePoint.origin, view(Blocks.AIR.defaultBlockState()));

        assertFalse(result.succeeded());
        assertEquals(Codes.anchorInvalid.code(), Objects.requireNonNull(result.findings().primary(), "finding").code());
    }

    @Test
    void anAnchorWithoutTheDeclaredPropertyIsInvalid() {
        SitingResult result = McAnchors
                .resolve(blueprint("facing"), LatticePoint.origin, view(Blocks.STONE.defaultBlockState()));

        assertFalse(result.succeeded());
        assertEquals(Codes.anchorInvalid.code(), Objects.requireNonNull(result.findings().primary(), "finding").code());
    }

    private static LevelSnapshot view(BlockState state) {
        SnapshotId snapshot = snapshot();
        SnapshotLevel host = hostSnapshot(state);
        FrameData data = new FrameData(
            snapshot,
            0,
            0,
            LatticePoint.origin,
            bounds,
            Map.of(),
            List.of(),
            List.of(),
            Map.of(),
            host
        );
        return new LevelSnapshot(snapshot, SnapshotPolicy.minimal(), data);
    }

    private static SnapshotLevel hostSnapshot(BlockState state) {
        PalettedContainer<BlockState> states = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        states.set(0, 0, 0, state);
        FluidState[] fluids = new FluidState[SnapshotLevel.sectionSize
                * SnapshotLevel.sectionSize
                * SnapshotLevel.sectionSize];
        Arrays.fill(fluids, Fluids.EMPTY.defaultFluidState());
        fluids[SnapshotLevel.fluidIndex(0, 0, 0)] = state.getFluidState();
        return new SnapshotLevel(
            LatticePoint.origin,
            bounds,
            -64,
            384,
            Map.of(SnapshotLevel.worldSectionKey(0, 0, 0), states),
            Map.of(SnapshotLevel.worldSectionKey(0, 0, 0), fluids),
            Map.of(),
            List.of()
        );
    }

    private static SnapshotId snapshot() {
        return new SnapshotId(World.of(NamespacedId.of("test", "world")), 1);
    }

    private static Blueprint blueprint(String value) {
        Bay bay = Bay.of(
            NamespacedId.of("test", "anchor_bay"),
            CuboidSet.of(bounds),
            Coverage.everyCell,
            Footprint.box(),
            Sharing.exclusive
        );
        return Blueprint.builder(NamespacedId.of("test", "anchor")).bounds(bounds)
                .anchor(new AnchorSpec(role, "facing", value))
                .orientation(
                    OrientationPolicy.builder(role)
                            .allowTurns(Turn.none, Turn.clockwise, Turn.half, Turn.counterclockwise).build()
                )
                .check(
                    Check.of(
                        NamespacedId.of("test", "anchor_cell"),
                        bay,
                        Focuses.occupant(LatticePoint.origin),
                        Expectations.anyOccupant()
                    )
                ).build();
    }
}
