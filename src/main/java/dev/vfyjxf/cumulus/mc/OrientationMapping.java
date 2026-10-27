package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.block.state.properties.StructureMode;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Set;

/**
 * Maps kernel orientations to Minecraft directions, rotations and mirrors. An upright orientation uses the vanilla
 * mirror-then-rotate state transform; a non-upright orientation remaps the orientation-bearing properties and
 * reports the first property it cannot express.
 */
@NullMarked
public final class OrientationMapping {

    private static final Set<Class<?>> neutralEnumTypes = Set.<Class<?>>of(
        ComparatorMode.class,
        NoteBlockInstrument.class,
        PistonType.class,
        SculkSensorPhase.class,
        StructureMode.class,
        TrialSpawnerState.class,
        VaultState.class
    );

    private static final Set<String> directionNames = Set.of("down", "east", "north", "south", "up", "west");

    private OrientationMapping() {}

    /** Returns the Minecraft direction for a kernel heading. */
    public static Direction direction(Heading heading) {
        return switch (heading) {
            case north -> Direction.NORTH;
            case south -> Direction.SOUTH;
            case west -> Direction.WEST;
            case east -> Direction.EAST;
            case up -> Direction.UP;
            case down -> Direction.DOWN;
        };
    }

    /** Returns the kernel heading for a Minecraft direction. */
    public static Heading heading(Direction direction) {
        return switch (direction) {
            case NORTH -> Heading.north;
            case SOUTH -> Heading.south;
            case WEST -> Heading.west;
            case EAST -> Heading.east;
            case UP -> Heading.up;
            case DOWN -> Heading.down;
        };
    }

    /** Returns the Minecraft rotation for a kernel turn. */
    public static Rotation rotation(Turn turn) {
        return switch (turn) {
            case none -> Rotation.NONE;
            case clockwise -> Rotation.CLOCKWISE_90;
            case half -> Rotation.CLOCKWISE_180;
            case counterclockwise -> Rotation.COUNTERCLOCKWISE_90;
        };
    }

    /** Returns the kernel turn for a Minecraft rotation. */
    public static Turn turn(Rotation rotation) {
        return switch (rotation) {
            case NONE -> Turn.none;
            case CLOCKWISE_90 -> Turn.clockwise;
            case CLOCKWISE_180 -> Turn.half;
            case COUNTERCLOCKWISE_90 -> Turn.counterclockwise;
        };
    }

    /** Returns the Minecraft mirror for a kernel flip. */
    public static Mirror mirror(Flip flip) {
        return switch (flip) {
            case none -> Mirror.NONE;
            case leftRight -> Mirror.LEFT_RIGHT;
            case frontBack -> Mirror.FRONT_BACK;
        };
    }

    /** Returns the kernel flip for a Minecraft mirror. */
    public static Flip flip(Mirror mirror) {
        return switch (mirror) {
            case NONE -> Flip.none;
            case LEFT_RIGHT -> Flip.leftRight;
            case FRONT_BACK -> Flip.frontBack;
        };
    }

    /**
     * Transforms a state by an orientation: an upright orientation applies the vanilla mirror then rotation, and a
     * non-upright orientation remaps its six-way direction, all-three axis and front/top properties.
     */
    public static MappingResult transform(Orientation orientation, BlockState state) {
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(state, "state");
        if (orientation.tilt() == Tilt.level) {
            // A mirrored upright orientation is canonically a yaw turn composed with the front-back reflection.
            Flip flip = orientation.isMirror() ? Flip.frontBack : Flip.none;
            return new MappingResult.Mapped(transform(orientation.turn(), flip, state));
        }
        BlockState result = state;
        for (Property<?> property : state.getProperties()) {
            Step step = remapProperty(orientation, result, property);
            if (step instanceof Step.Failed failed) {
                return new MappingResult.Unrepresentable(orientation, failed.property(), failed.value());
            }
            result = ((Step.Ok) step).state();
        }
        return new MappingResult.Mapped(result);
    }

    /** Applies an upright orientation's mirror and then its rotation to a state. */
    @SuppressWarnings("deprecation")
    public static BlockState transform(Turn turn, Flip flip, BlockState state) {
        Objects.requireNonNull(turn, "turn");
        Objects.requireNonNull(flip, "flip");
        Objects.requireNonNull(state, "state");
        return state.mirror(mirror(flip)).rotate(rotation(turn));
    }

    private static Step remapProperty(Orientation orientation, BlockState state, Property<?> property) {
        Class<?> valueClass = property.getValueClass();
        if (valueClass == Direction.class) {
            return remapDirection(orientation, state, property);
        }
        if (valueClass == Direction.Axis.class) {
            return remapAxis(orientation, state, property);
        }
        if (valueClass == FrontAndTop.class) {
            return remapFrame(orientation, state, property);
        }
        if (neutral(property)) {
            return new Step.Ok(state);
        }
        return new Step.Failed(property.getName(), valueName(state, property));
    }

    @SuppressWarnings("unchecked")
    private static Step remapDirection(Orientation orientation, BlockState state, Property<?> property) {
        Property<Direction> directionProperty = (Property<Direction>) property;
        if (directionProperty.getPossibleValues().size() != 6) {
            return new Step.Failed(property.getName(), valueName(state, property));
        }
        Direction mapped = direction(orientation.apply(heading(state.getValue(directionProperty))));
        return new Step.Ok(state.setValue(directionProperty, mapped));
    }

    @SuppressWarnings("unchecked")
    private static Step remapAxis(Orientation orientation, BlockState state, Property<?> property) {
        Property<Direction.Axis> axisProperty = (Property<Direction.Axis>) property;
        if (axisProperty.getPossibleValues().size() != 3) {
            return new Step.Failed(property.getName(), valueName(state, property));
        }
        Direction.Axis mapped = axis(orientation, state.getValue(axisProperty));
        return new Step.Ok(state.setValue(axisProperty, mapped));
    }

    @SuppressWarnings("unchecked")
    private static Step remapFrame(Orientation orientation, BlockState state, Property<?> property) {
        Property<FrontAndTop> frameProperty = (Property<FrontAndTop>) property;
        FrontAndTop frame = state.getValue(frameProperty);
        FrontAndTop mapped = FrontAndTop.fromFrontAndTop(
            direction(orientation.apply(heading(frame.front()))),
            direction(orientation.apply(heading(frame.top())))
        );
        if (mapped == null || !frameProperty.getPossibleValues().contains(mapped)) {
            return new Step.Failed(property.getName(), valueName(state, property));
        }
        return new Step.Ok(state.setValue(frameProperty, mapped));
    }

    private static Direction.Axis axis(Orientation orientation, Direction.Axis axis) {
        Heading positive = switch (axis) {
            case X -> Heading.east;
            case Y -> Heading.up;
            case Z -> Heading.south;
        };
        return direction(orientation.apply(positive)).getAxis();
    }

    private static boolean neutral(Property<?> property) {
        if (property instanceof BooleanProperty) {
            return !directionNames.contains(property.getName());
        }
        if (property instanceof IntegerProperty) {
            return !"rotation".equals(property.getName());
        }
        return neutralEnumTypes.contains(property.getValueClass());
    }

    private static String valueName(BlockState state, Property<?> property) {
        return valueNameTyped(state, property);
    }

    private static <T extends Comparable<T>> String valueNameTyped(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private sealed interface Step permits Step.Ok, Step.Failed {

        record Ok(BlockState state) implements Step {
            public Ok {
                Objects.requireNonNull(state, "state");
            }
        }

        record Failed(String property, String value) implements Step {
            public Failed {
                Objects.requireNonNull(property, "property");
                Objects.requireNonNull(value, "value");
            }
        }
    }

    /** The outcome of a block-state transform. */
    public sealed interface MappingResult permits MappingResult.Mapped, MappingResult.Unrepresentable {

        /** A successfully transformed state. */
        record Mapped(BlockState state) implements MappingResult {
            public Mapped {
                Objects.requireNonNull(state, "state");
            }
        }

        /**
         * A state whose orientation-bearing property cannot express the orientation. This is the payload of
         * {@code SC-MC-0004}.
         */
        record Unrepresentable(Orientation orientation, String property, String value) implements MappingResult {
            public Unrepresentable {
                Objects.requireNonNull(orientation, "orientation");
                Objects.requireNonNull(property, "property");
                Objects.requireNonNull(value, "value");
                if (orientation.tilt() == Tilt.level) {
                    throw new IllegalArgumentException("upright is supported");
                }
            }
        }
    }
}
