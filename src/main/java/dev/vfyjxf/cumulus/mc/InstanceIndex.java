package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.context.StructureInstance;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The per-level lookup behind the funnel's fast path: which formed instances have a cell in a
 * chunk. Kept in step with the manager by {@link IndexedStructureManager}; server thread only.
 */
@NullMarked
final class InstanceIndex {

    private final Map<Long, Set<InstanceId>> byChunk = new HashMap<>();
    private final Map<InstanceId, Set<Long>> chunksOfInstance = new HashMap<>();

    void add(StructureInstance instance) {
        InstanceId id = instance.id();
        if (chunksOfInstance.containsKey(id)) {
            return;
        }
        Set<Long> keys = chunkKeys(instance.bounds());
        chunksOfInstance.put(id, keys);
        for (long key : keys) {
            byChunk.computeIfAbsent(key, ignored -> new HashSet<>()).add(id);
        }
    }

    void drop(InstanceId id) {
        Set<Long> keys = chunksOfInstance.remove(id);
        if (keys == null) {
            return;
        }
        for (long key : keys) {
            Set<InstanceId> set = byChunk.get(key);
            if (set != null) {
                set.remove(id);
                if (set.isEmpty()) {
                    byChunk.remove(key);
                }
            }
        }
    }

    void dropWorld(World world) {
        for (InstanceId id : List.copyOf(chunksOfInstance.keySet())) {
            if (id.world().equals(world)) {
                drop(id);
            }
        }
    }

    /** The instances with a cell in this position's chunk, or null when none; a live view. */
    @Nullable
    Set<InstanceId> at(BlockPos pos) {
        return byChunk.get(ChunkPos.asLong(pos));
    }

    private static Set<Long> chunkKeys(CuboidSet bounds) {
        Set<Long> keys = new HashSet<>();
        for (Cuboid box : bounds.decomposition()) {
            int minX = Math.floorDiv(box.min().x(), 16);
            int maxX = Math.floorDiv(box.max().x() - 1, 16);
            int minZ = Math.floorDiv(box.min().z(), 16);
            int maxZ = Math.floorDiv(box.max().z() - 1, 16);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    keys.add(ChunkPos.asLong(x, z));
                }
            }
        }
        return keys;
    }
}
