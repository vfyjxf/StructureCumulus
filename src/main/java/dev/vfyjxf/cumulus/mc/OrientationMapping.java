package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.Heading;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/**
 * Maps kernel orientations to Minecraft directions, rotations and mirrors; state transforms are upright-only and
 * require a state that can express the orientation.
 */
@NullMarked
public final class OrientationMapping {

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
     * Transforms an upright state by applying its mirror and then its rotation; a non-level tilt returns
     * {@link MappingResult.UnsupportedTilt} for the capture layer's {@code SC-MC-0004}.
     */
    public static MappingResult transform(Orientation orientation, BlockState state) {
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(state, "state");
        Tilt tilt = orientation.tilt();
        if (tilt != Tilt.level) {
            return new MappingResult.UnsupportedTilt(tilt);
        }
        // A mirrored upright orientation is canonically a yaw turn composed with the front-back reflection.
        Flip flip = orientation.isMirror() ? Flip.frontBack : Flip.none;
        return new MappingResult.Mapped(transform(orientation.turn(), flip, state));
    }

    /** Applies an upright orientation's mirror and then its rotation to a state. */
    @SuppressWarnings("deprecation")
    public static BlockState transform(Turn turn, Flip flip, BlockState state) {
        Objects.requireNonNull(turn, "turn");
        Objects.requireNonNull(flip, "flip");
        Objects.requireNonNull(state, "state");
        return state.mirror(mirror(flip)).rotate(rotation(turn));
    }

    /** The outcome of an upright block-state transform. */
    public sealed interface MappingResult permits MappingResult.Mapped, MappingResult.UnsupportedTilt {

        /** A successfully transformed state. */
        record Mapped(BlockState state) implements MappingResult {
            public Mapped {
                Objects.requireNonNull(state, "state");
            }
        }

        /** A non-level tilt the upright-only transform rejects. */
        record UnsupportedTilt(Tilt tilt) implements MappingResult {
            public UnsupportedTilt {
                Objects.requireNonNull(tilt, "tilt");
                if (tilt == Tilt.level) {
                    throw new IllegalArgumentException("level is supported");
                }
            }
        }
    }
}
