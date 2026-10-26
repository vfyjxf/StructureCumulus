package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
    void nonUprightTiltsAreUnsupported() {
        Set<Tilt> seen = EnumSet.noneOf(Tilt.class);
        int unsupported = 0;
        for (Orientation orientation : Orientation.all()) {
            if (orientation.tilt() == Tilt.level) {
                continue;
            }
            unsupported++;
            OrientationMapping.MappingResult.UnsupportedTilt rejected = assertInstanceOf(
                OrientationMapping.MappingResult.UnsupportedTilt.class,
                OrientationMapping.transform(orientation, dispenser(Direction.NORTH))
            );
            assertEquals(orientation.tilt(), rejected.tilt());
            seen.add(orientation.tilt());
        }
        assertEquals(40, unsupported);
        assertEquals(EnumSet.of(Tilt.pitchUp, Tilt.pitchDown, Tilt.rollLeft, Tilt.rollRight, Tilt.inverted), seen);
    }

    private static BlockState dispenser(Direction facing) {
        return Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing)
                .setValue(DispenserBlock.TRIGGERED, true);
    }

    private static BlockState mapped(OrientationMapping.MappingResult result) {
        return assertInstanceOf(OrientationMapping.MappingResult.Mapped.class, result).state();
    }
}
