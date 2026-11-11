package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.context.StructureManager;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.OwnerId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.spi.StructureKernel;
import dev.vfyjxf.cumulus.mc.build.Materializers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The level-scoped state another package may read. {@code ServerState} stays the owner; this facade
 * exists so a sibling package never needs package-private reach nor reflection into it.
 */
@NullMarked
public final class LevelAccess {

    private LevelAccess() {}

    /** The session's kernel with the merged document source, or null before the server opened. */
    public static @Nullable StructureKernel kernel() {
        ServerState state = ServerState.current();
        return state == null ? null : state.kernel();
    }

    /** Every blueprint id of the merged document source, sorted; empty before the server opened. */
    public static List<NamespacedId> documentIds() {
        ServerState state = ServerState.current();
        return state == null ? List.of() : state.documentIds();
    }

    public static @Nullable LevelCapture capture(ServerLevel level) {
        ServerState state = ServerState.current();
        return state == null ? null : state.capture(level);
    }

    /** The session's materializer chain; the built-in chain before the server opened. */
    public static Materializers materializers() {
        ServerState state = ServerState.current();
        return state == null ? Materializers.builtin() : state.materializers();
    }

    public static @Nullable ClaimLedger claims(ServerLevel level) {
        ServerState state = ServerState.current();
        return state == null ? null : state.claims(level);
    }

    public static @Nullable StructureManager structures(ServerLevel level) {
        ServerState state = ServerState.current();
        return state == null ? null : state.structures(level);
    }

    public static @Nullable World world(ServerLevel level) {
        ServerState state = ServerState.current();
        return state == null ? null : state.world(level);
    }

    public static @Nullable OwnerId owner() {
        ServerState state = ServerState.current();
        return state == null ? null : state.owner();
    }

    /** The same monotone block-change count a capture's watch records, region-scoped by chunks. */
    public static long blockRevision(ServerLevel level, Cuboid region) {
        return LevelWatch.revision(level, chunksOf(region));
    }

    private static Set<Long> chunksOf(Cuboid region) {
        Set<Long> chunks = new HashSet<>();
        int minX = Math.floorDiv(region.min().x(), LevelCapture.cellSize);
        int maxX = Math.floorDiv(region.max().x() - 1, LevelCapture.cellSize);
        int minZ = Math.floorDiv(region.min().z(), LevelCapture.cellSize);
        int maxZ = Math.floorDiv(region.max().z() - 1, LevelCapture.cellSize);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }
        return chunks;
    }
}
