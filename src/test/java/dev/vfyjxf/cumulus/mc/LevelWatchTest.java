package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelWatchTest {

    private static final NamespacedId type = NamespacedId.of("test", "unit");

    @Test
    void frameAddsEveryCoveredChunkAndItsWorldBounds() {
        LevelWatch.WatchRegion region = new LevelWatch.WatchRegion();
        Cuboid bounds = Cuboid.of(-1, 0, -1, 16, 16, 16);

        region.frame(bounds, LatticePoint.origin);

        assertEquals(
            Set.of(ChunkPos.asLong(-1, -1), ChunkPos.asLong(-1, 0), ChunkPos.asLong(0, -1), ChunkPos.asLong(0, 0)),
            region.chunks()
        );
        assertEquals(List.of(bounds), region.bounds());
    }

    @Test
    void childFrameChunksAreTranslatedByTheChildOrigin() {
        LevelWatch.WatchRegion region = new LevelWatch.WatchRegion();
        LatticePoint origin = LatticePoint.of(32, 64, 32);

        region.frame(Cuboid.of(0, 0, 0, 2, 2, 2), origin);

        assertEquals(Set.of(ChunkPos.asLong(2, 2)), region.chunks());
        assertEquals(List.of(Cuboid.of(32, 64, 32, 34, 66, 34)), region.bounds());
    }

    @Test
    void mergedChildRegionAddsChunksBoundsAndEntities() {
        UUID entity = UUID.randomUUID();
        LevelWatch.WatchRegion root = new LevelWatch.WatchRegion();
        root.frame(Cuboid.of(0, 0, 0, 1, 1, 1), LatticePoint.origin);
        LevelWatch.WatchRegion child = new LevelWatch.WatchRegion();
        child.frame(Cuboid.of(0, 0, 0, 1, 1, 1), LatticePoint.of(32, 64, 32));
        child.entity(entity, type, LatticePoint.of(32, 64, 32), Cuboid.of(32, 64, 32, 33, 65, 33));

        root.merge(child);

        assertEquals(2, root.chunks().size());
        assertEquals(2, root.bounds().size());
        assertEquals(1, root.entities().size());
        assertTrue(root.entities().containsKey(entity));
    }

    @Test
    void entityFingerprintsDeduplicateByUuid() {
        UUID entity = UUID.randomUUID();
        LevelWatch.WatchRegion region = new LevelWatch.WatchRegion();

        region.entity(entity, type, LatticePoint.origin, Cuboid.of(0, 0, 0, 1, 1, 1));
        region.entity(entity, type, LatticePoint.origin, Cuboid.of(0, 0, 0, 2, 2, 2));

        assertEquals(1, region.entities().size());
        assertEquals(
            Cuboid.of(0, 0, 0, 1, 1, 1),
            Objects.requireNonNull(region.entities().get(entity), "fingerprint").footprint()
        );
    }

    @Test
    void entityFingerprintIsAValue() {
        UUID entity = UUID.randomUUID();
        LatticePoint anchor = LatticePoint.of(1, 2, 3);
        Cuboid footprint = Cuboid.of(1, 2, 3, 3, 5, 4);
        LevelWatch.EntityFingerprint first = new LevelWatch.EntityFingerprint(entity, type, anchor, footprint);

        assertEquals(first, new LevelWatch.EntityFingerprint(entity, type, anchor, footprint));
        assertNotEquals(first, new LevelWatch.EntityFingerprint(entity, type, LatticePoint.origin, footprint));
    }

    @Test
    void emptyRegionHasNoChunksBoundsOrFingerprints() {
        LevelWatch.WatchRegion region = new LevelWatch.WatchRegion();

        assertTrue(region.chunks().isEmpty());
        assertTrue(region.bounds().isEmpty());
        assertTrue(region.entities().isEmpty());
    }
}
