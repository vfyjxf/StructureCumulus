package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrientationMappingTest {

    private static final Map<Heading, Direction> headingPairs = Map.of(
        Heading.north,
        Direction.NORTH,
        Heading.south,
        Direction.SOUTH,
        Heading.west,
        Direction.WEST,
        Heading.east,
        Direction.EAST,
        Heading.up,
        Direction.UP,
        Heading.down,
        Direction.DOWN
    );

    private static final Map<Turn, Rotation> turnPairs = Map.of(
        Turn.none,
        Rotation.NONE,
        Turn.clockwise,
        Rotation.CLOCKWISE_90,
        Turn.half,
        Rotation.CLOCKWISE_180,
        Turn.counterclockwise,
        Rotation.COUNTERCLOCKWISE_90
    );

    private static final Map<Flip, Mirror> flipPairs = Map
            .of(Flip.none, Mirror.NONE, Flip.leftRight, Mirror.LEFT_RIGHT, Flip.frontBack, Mirror.FRONT_BACK);

    @Test
    void headingMappingCoversEveryPair() {
        assertEquals(Heading.values().length, headingPairs.size());
        assertEquals(Direction.values().length, headingPairs.size());
        for (Map.Entry<Heading, Direction> pair : headingPairs.entrySet()) {
            Heading heading = pair.getKey();
            Direction direction = pair.getValue();
            assertEquals(direction, OrientationMapping.direction(heading));
            assertEquals(heading, OrientationMapping.heading(direction));
            assertEquals(heading.stepX(), direction.getStepX());
            assertEquals(heading.stepY(), direction.getStepY());
            assertEquals(heading.stepZ(), direction.getStepZ());
        }
    }

    @Test
    void turnMappingCoversEveryPair() {
        assertEquals(Turn.values().length, turnPairs.size());
        assertEquals(Rotation.values().length, turnPairs.size());
        for (Map.Entry<Turn, Rotation> pair : turnPairs.entrySet()) {
            assertEquals(pair.getValue(), OrientationMapping.rotation(pair.getKey()));
            assertEquals(pair.getKey(), OrientationMapping.turn(pair.getValue()));
        }
    }

    @Test
    void flipMappingCoversEveryPair() {
        assertEquals(Flip.values().length, flipPairs.size());
        assertEquals(Mirror.values().length, flipPairs.size());
        for (Map.Entry<Flip, Mirror> pair : flipPairs.entrySet()) {
            assertEquals(pair.getValue(), OrientationMapping.mirror(pair.getKey()));
            assertEquals(pair.getKey(), OrientationMapping.flip(pair.getValue()));
            assertEquals(pair.getKey().isMirror(), pair.getValue() != Mirror.NONE);
        }
    }

    @Test
    void turnRotatesHeadingsLikeTheKernel() {
        for (Turn turn : Turn.values()) {
            Rotation rotation = OrientationMapping.rotation(turn);
            for (Heading heading : Heading.values()) {
                Heading expected = Orientation.of(turn, Tilt.level, Flip.none).apply(heading);
                Direction actual = rotation.rotate(OrientationMapping.direction(heading));
                assertEquals(OrientationMapping.direction(expected), actual, () -> turn + " on " + heading);
            }
        }
    }

    @Test
    void flipMirrorsHeadingsLikeTheKernel() {
        for (Flip flip : Flip.values()) {
            Mirror mirror = OrientationMapping.mirror(flip);
            for (Heading heading : Heading.values()) {
                Heading expected = Orientation.of(Turn.none, Tilt.level, flip).apply(heading);
                Direction actual = mirror.mirror(OrientationMapping.direction(heading));
                assertEquals(OrientationMapping.direction(expected), actual, () -> flip + " on " + heading);
            }
        }
    }

    @Test
    void identityOrientationLeavesTheStateUnchanged() {
        BlockState state = dispenser(Direction.UP);
        assertEquals(state, mapped(OrientationMapping.transform(Orientation.identity, state)));
    }

    @Test
    void uprightOrientationAppliesMirrorBeforeRotation() {
        BlockState state = dispenser(Direction.NORTH);
        Orientation orientation = Orientation.of(Turn.clockwise, Tilt.level, Flip.leftRight);
        assertEquals(Heading.west, orientation.apply(Heading.north));

        BlockState transformed = mapped(OrientationMapping.transform(orientation, state));
        assertEquals(Direction.WEST, transformed.getValue(DispenserBlock.FACING));
        assertTrue(transformed.getValue(DispenserBlock.TRIGGERED));
    }

    @Test
    void everyUprightOrientationMatchesTheKernelOnEveryHeading() {
        int upright = 0;
        for (Orientation orientation : Orientation.all()) {
            if (orientation.tilt() != Tilt.level) {
                continue;
            }
            upright++;
            for (Heading heading : Heading.values()) {
                BlockState transformed = mapped(
                    OrientationMapping.transform(orientation, dispenser(OrientationMapping.direction(heading)))
                );
                assertEquals(
                    OrientationMapping.direction(orientation.apply(heading)),
                    transformed.getValue(DispenserBlock.FACING),
                    () -> orientation + " on " + heading
                );
            }
        }
        assertEquals(8, upright);
    }

    @Test
    void directUprightTransformMatchesTheKernel() {
        for (Turn turn : Turn.values()) {
            for (Flip flip : Flip.values()) {
                Orientation orientation = Orientation.of(turn, Tilt.level, flip);
                for (Heading heading : Heading.values()) {
                    BlockState state = dispenser(OrientationMapping.direction(heading));
                    Direction expected = OrientationMapping.direction(orientation.apply(heading));
                    BlockState direct = OrientationMapping.transform(turn, flip, state);
                    BlockState viaOrientation = mapped(OrientationMapping.transform(orientation, state));
                    assertEquals(
                        expected,
                        direct.getValue(DispenserBlock.FACING),
                        () -> turn + "/" + flip + " on " + heading
                    );
                    assertEquals(
                        expected,
                        viaOrientation.getValue(DispenserBlock.FACING),
                        () -> turn + "/" + flip + " on " + heading
                    );
                }
            }
        }
    }

    @Test
    void everyNonUprightOrientationMapsSixWayFacing() {
        int nonUpright = 0;
        for (Orientation orientation : Orientation.all()) {
            if (orientation.tilt() == Tilt.level) {
                continue;
            }
            nonUpright++;
            for (Direction facing : Direction.values()) {
                BlockState transformed = mapped(OrientationMapping.transform(orientation, dispenser(facing)));
                assertEquals(
                    OrientationMapping.direction(orientation.apply(OrientationMapping.heading(facing))),
                    transformed.getValue(DispenserBlock.FACING),
                    () -> orientation + " on " + facing
                );
                assertTrue(transformed.getValue(DispenserBlock.TRIGGERED));
            }
        }
        assertEquals(40, nonUpright);
    }

    @Test
    void everyNonUprightOrientationMapsAllThreeAxis() {
        for (Orientation orientation : Orientation.all()) {
            if (orientation.tilt() == Tilt.level) {
                continue;
            }
            for (Direction.Axis axis : Direction.Axis.values()) {
                BlockState state = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
                BlockState transformed = mapped(OrientationMapping.transform(orientation, state));
                assertEquals(
                    OrientationMapping.direction(orientation.apply(positiveHeading(axis))).getAxis(),
                    transformed.getValue(RotatedPillarBlock.AXIS),
                    () -> orientation + " on axis " + axis
                );
            }
        }
    }

    @Test
    void everyNonUprightOrientationMapsPistonHeadWithNeutralType() {
        for (Orientation orientation : Orientation.all()) {
            if (orientation.tilt() == Tilt.level) {
                continue;
            }
            BlockState state = Blocks.PISTON_HEAD.defaultBlockState();
            BlockState transformed = mapped(OrientationMapping.transform(orientation, state));
            Heading facing = OrientationMapping.heading(state.getValue(PistonHeadBlock.FACING));
            assertEquals(
                OrientationMapping.direction(orientation.apply(facing)),
                transformed.getValue(PistonHeadBlock.FACING),
                () -> orientation + " on the piston head"
            );
            assertEquals(state.getValue(PistonHeadBlock.TYPE), transformed.getValue(PistonHeadBlock.TYPE));
            assertEquals(state.getValue(PistonHeadBlock.SHORT), transformed.getValue(PistonHeadBlock.SHORT));
        }
    }

    @Test
    void frontAndTopFramesMapExactlyWhenTheFrameExists() {
        int mappedCount = 0;
        int rejectedCount = 0;
        for (Orientation orientation : Orientation.all()) {
            for (FrontAndTop frame : FrontAndTop.values()) {
                BlockState state = Blocks.JIGSAW.defaultBlockState().setValue(JigsawBlock.ORIENTATION, frame);
                FrontAndTop expected = FrontAndTop.fromFrontAndTop(
                    OrientationMapping.direction(orientation.apply(OrientationMapping.heading(frame.front()))),
                    OrientationMapping.direction(orientation.apply(OrientationMapping.heading(frame.top())))
                );
                if (expected == null) {
                    assertEquals(frame.getSerializedName(), rejected(orientation, state).value());
                    rejectedCount++;
                } else {
                    BlockState transformed = mapped(OrientationMapping.transform(orientation, state));
                    assertEquals(expected, transformed.getValue(JigsawBlock.ORIENTATION));
                    mappedCount++;
                }
            }
        }
        assertEquals(288, mappedCount);
        assertEquals(288, rejectedCount);
    }

    @Test
    void crafterSharesTheSameFrameCapability() {
        Orientation pitchUp = Orientation.of(Turn.none, Tilt.pitchUp, Flip.none);
        BlockState pitchUpMapped = mapped(OrientationMapping.transform(pitchUp, Blocks.CRAFTER.defaultBlockState()));
        assertEquals(FrontAndTop.UP_SOUTH, frameValue(pitchUpMapped), "NORTH_UP pitched up is UP_SOUTH");

        Orientation inverted = Orientation.of(Turn.none, Tilt.inverted, Flip.none);
        assertEquals(
            "orientation",
            rejected(inverted, Blocks.CRAFTER.defaultBlockState()).property(),
            "an inverted north/up frame has no FrontAndTop image"
        );
    }

    @Test
    void partialDirectionStatesAreUnrepresentable() {
        List<BlockState> states = List.of(
            Blocks.FURNACE.defaultBlockState(),
            Blocks.HOPPER.defaultBlockState(),
            Blocks.POINTED_DRIPSTONE.defaultBlockState(),
            Blocks.LEVER.defaultBlockState()
        );
        for (Orientation orientation : Orientation.all()) {
            if (orientation.tilt() == Tilt.level) {
                continue;
            }
            for (BlockState state : states) {
                OrientationMapping.MappingResult.Unrepresentable rejected = rejected(orientation, state);
                assertFalse(rejected.property().isBlank(), "the failing property is named");
                assertFalse(rejected.value().isBlank(), "the failing value is named");
            }
        }
    }

    @Test
    void nonMechanicalOrientationPropertiesAreUnrepresentable() {
        List<BlockState> states = List.of(
            Blocks.OAK_STAIRS.defaultBlockState(),
            Blocks.OAK_DOOR.defaultBlockState(),
            Blocks.OAK_SIGN.defaultBlockState(),
            Blocks.OAK_FENCE.defaultBlockState(),
            Blocks.BAMBOO.defaultBlockState()
        );
        for (Orientation orientation : Orientation.all()) {
            if (orientation.tilt() == Tilt.level) {
                continue;
            }
            for (BlockState state : states) {
                OrientationMapping.MappingResult.Unrepresentable rejected = rejected(orientation, state);
                assertFalse(rejected.property().isBlank(), "the failing property is named");
            }
        }
    }

    @Test
    void neutralStatesAreInvariant() {
        List<BlockState> states = List.of(
            Blocks.STONE.defaultBlockState(),
            Blocks.GLASS.defaultBlockState(),
            Blocks.NOTE_BLOCK.defaultBlockState(),
            Blocks.STRUCTURE_BLOCK.defaultBlockState()
        );
        for (Orientation orientation : Orientation.all()) {
            for (BlockState state : states) {
                assertEquals(state, mapped(OrientationMapping.transform(orientation, state)));
            }
        }
    }

    @Test
    void mappedStatesRoundTripThroughTheInverse() {
        List<BlockState> states = List.of(
            dispenser(Direction.NORTH),
            Blocks.OAK_LOG.defaultBlockState(),
            Blocks.JIGSAW.defaultBlockState(),
            Blocks.STONE.defaultBlockState()
        );
        for (Orientation orientation : Orientation.all()) {
            for (BlockState state : states) {
                if (!(OrientationMapping
                        .transform(orientation, state) instanceof OrientationMapping.MappingResult.Mapped success)) {
                    continue;
                }
                assertEquals(
                    state,
                    mapped(OrientationMapping.transform(orientation.inverse(), success.state())),
                    () -> orientation + " on " + state
                );
            }
        }
    }

    @Test
    void unrepresentableCarriesOrientationAndProperty() {
        Orientation orientation = Orientation.of(Turn.none, Tilt.pitchUp, Flip.none);
        OrientationMapping.MappingResult.Unrepresentable rejected = rejected(
            orientation,
            Blocks.FURNACE.defaultBlockState()
        );
        assertEquals(orientation, rejected.orientation());
        assertEquals("facing", rejected.property());
        assertEquals("north", rejected.value());
        assertThrows(
            IllegalArgumentException.class,
            () -> new OrientationMapping.MappingResult.Unrepresentable(Orientation.identity, "facing", "north")
        );
    }

    private static Heading positiveHeading(Direction.Axis axis) {
        return switch (axis) {
            case X -> Heading.east;
            case Y -> Heading.up;
            case Z -> Heading.south;
        };
    }

    private static FrontAndTop frameValue(BlockState state) {
        return state.getValue(JigsawBlock.ORIENTATION);
    }

    private static BlockState dispenser(Direction facing) {
        return Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing)
                .setValue(DispenserBlock.TRIGGERED, true);
    }

    private static BlockState mapped(OrientationMapping.MappingResult result) {
        return assertInstanceOf(OrientationMapping.MappingResult.Mapped.class, result).state();
    }

    private static OrientationMapping.MappingResult.Unrepresentable rejected(
        Orientation orientation,
        BlockState state
    ) {
        return assertInstanceOf(
            OrientationMapping.MappingResult.Unrepresentable.class,
            OrientationMapping.transform(orientation, state),
            () -> orientation + " on " + state
        );
    }
}
