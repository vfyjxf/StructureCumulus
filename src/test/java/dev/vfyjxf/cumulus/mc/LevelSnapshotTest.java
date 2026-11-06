package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.FindingCode;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.SectionKey;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.HostType;
import dev.vfyjxf.cumulus.kernel.world.OccupantDescriptor;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantRef;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadRefusal;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SectionSlot;
import dev.vfyjxf.cumulus.kernel.world.SectionSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelSnapshotTest {

    @Test
    void airIsSynthesizedOnlyInsideTheCapturedBounds() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 1);
        SectionKey key = SectionKey.of(snapshot.world(), 0, LatticePoint.origin, 16);
        OccupantSnapshot stone = occupant(snapshot, LatticePoint.origin, "stone");
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(LatticePoint.origin, stone), 42L);
        LevelSnapshot view = new LevelSnapshot(
            snapshot,
            Cuboid.of(0, 0, 0, 2, 2, 2),
            Map.of(key, new SectionSlot.Present(section))
        );

        assertSame(stone, view.occupant(LatticePoint.origin));
        assertEquals(
            OccupantSnapshot.air,
            Objects.requireNonNull(view.occupant(LatticePoint.of(1, 0, 0)), "air").descriptor().type()
        );
        assertNull(view.occupant(LatticePoint.of(2, 0, 0)));
        assertEquals(ReadStatus.outOfBounds, view.slot(view.sectionOf(LatticePoint.of(16, 0, 0))).status());
        assertTrue(view.slot(key).readable());
        assertEquals(42L, view.contentDigest(key));
        assertFalse(view.occupants(Cuboid.of(0, 0, 0, 2, 2, 2), OccupantFilter.any()).isEmpty());
    }

    @Test
    void blockBeatsLargerEntityWhileTheEntityAnswersOverAir() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 1);
        LatticePoint foot = LatticePoint.origin;
        LatticePoint head = LatticePoint.of(0, 1, 0);
        SectionKey key = SectionKey.of(snapshot.world(), 0, foot, 16);
        OccupantSnapshot stone = occupant(snapshot, foot, "stone");
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(foot, stone), 42L);
        OccupantSnapshot zombie = entity(snapshot, foot, "zombie", Cuboid.of(0, 0, 0, 1, 2, 1));
        LevelSnapshot view = new LevelSnapshot(
            snapshot,
            Cuboid.of(0, 0, 0, 2, 3, 2),
            Map.of(key, new SectionSlot.Present(section)),
            List.of(zombie)
        );

        assertSame(stone, view.occupant(foot));
        assertSame(zombie, view.occupant(head));
        assertTrue(Objects.requireNonNull(view.occupant(LatticePoint.of(0, 2, 0)), "air").isAir());

        SectionSlot slot = view.slot(key);
        assertTrue(slot instanceof SectionSlot.Present);
        SectionSnapshot captured = ((SectionSlot.Present) slot).section();
        assertSame(stone, captured.occupantAt(foot));
        assertEquals(42L, view.contentDigest(key));

        assertSame(stone, view.occupant(stone.ref()));
        assertSame(zombie, view.occupant(zombie.ref()));
        assertNull(view.occupant(new OccupantRef(snapshot, 1, zombie.descriptor().type(), OccupantKind.entity, foot)));

        assertEquals(
            List.of(zombie),
            view.occupants(Cuboid.of(0, 0, 0, 2, 3, 2), OccupantFilter.ofKind(OccupantKind.entity))
        );
        assertEquals(List.of(stone, zombie), view.occupants(Cuboid.of(0, 0, 0, 2, 3, 2), OccupantFilter.any()));
    }

    @Test
    void cellOccupantAnswersTheStoredCellUnderABody() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 17);
        LatticePoint foot = LatticePoint.origin;
        LatticePoint head = LatticePoint.of(0, 1, 0);
        SectionKey key = SectionKey.of(snapshot.world(), 0, foot, 16);
        OccupantSnapshot stone = occupant(snapshot, foot, "stone");
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(foot, stone), 42L);
        OccupantSnapshot zombie = entity(snapshot, foot, "zombie", Cuboid.of(0, 0, 0, 1, 2, 1));
        LevelSnapshot view = new LevelSnapshot(
            snapshot,
            Cuboid.of(0, 0, 0, 2, 3, 2),
            Map.of(key, new SectionSlot.Present(section)),
            List.of(zombie)
        );

        assertSame(zombie, assertInstanceOf(CellRead.Found.class, view.read(head)).occupant());
        assertSame(stone, view.cellOccupant(foot), "the stored cell answers the stone");
        assertTrue(Objects.requireNonNull(view.cellOccupant(head), "air").isAir(), "the cell under the body is air");
        assertNull(view.cellOccupant(LatticePoint.of(9, 9, 9)), "outside the bounds is not a cell answer");
    }

    @Test
    void equalFootprintsTieBreakByRef() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 2);
        LatticePoint foot = LatticePoint.origin;
        SectionKey key = SectionKey.of(snapshot.world(), 0, foot, 16);
        OccupantSnapshot stone = occupant(snapshot, foot, "stone");
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(foot, stone), 1L);
        OccupantSnapshot chicken = entity(snapshot, foot, "chicken", Cuboid.of(0, 0, 0, 1, 1, 1));
        LevelSnapshot view = new LevelSnapshot(
            snapshot,
            Cuboid.of(0, 0, 0, 2, 2, 2),
            Map.of(key, new SectionSlot.Present(section)),
            List.of(chicken)
        );

        assertSame(stone, view.occupant(foot));
        assertSame(chicken, view.occupant(chicken.ref()));
        assertSame(stone, view.occupant(stone.ref()));
    }

    @Test
    void equalEntityFootprintsTieBreakByRef() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 3);
        LatticePoint foot = LatticePoint.origin;
        OccupantSnapshot alpha = entity(snapshot, foot, "alpha", Cuboid.of(0, 0, 0, 1, 1, 1));
        OccupantSnapshot beta = entity(snapshot, foot, "beta", Cuboid.of(0, 0, 0, 1, 1, 1));
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 2, 2, 2), Map.of(), List.of(beta, alpha));

        assertSame(alpha, view.occupant(foot));
    }

    @Test
    void entityWinsOverAir() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 4);
        OccupantSnapshot zombie = entity(snapshot, LatticePoint.origin, "zombie", Cuboid.of(0, 0, 0, 1, 1, 1));
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 2, 2, 2), Map.of(), List.of(zombie));

        assertSame(zombie, view.occupant(LatticePoint.origin));
        assertTrue(Objects.requireNonNull(view.occupant(LatticePoint.of(1, 0, 0)), "air").isAir());
    }

    @Test
    void entityFootprintLeavesTheCaptureWholeAndResolvesByRef() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 5);
        LatticePoint anchor = LatticePoint.of(-1, 0, 0);
        OccupantSnapshot zombie = entity(snapshot, anchor, "zombie", Cuboid.of(-1, 0, 0, 1, 1, 1));
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 2, 2, 2), Map.of(), List.of(zombie));

        assertSame(zombie, view.occupant(LatticePoint.origin));
        assertEquals(Cuboid.of(-1, 0, 0, 1, 1, 1), zombie.footprint());
        assertSame(zombie, view.occupant(zombie.ref()));
        assertNull(view.occupant(anchor));
        assertEquals(
            List.of(zombie),
            view.occupants(Cuboid.of(0, 0, 0, 2, 2, 2), OccupantFilter.ofKind(OccupantKind.entity))
        );
    }

    @Test
    void overlappingEntitiesPreferTheSmallerFootprint() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 6);
        LatticePoint foot = LatticePoint.origin;
        OccupantSnapshot big = entity(snapshot, foot, "big", Cuboid.of(0, 0, 0, 2, 2, 1));
        OccupantSnapshot small = entity(snapshot, foot, "small", Cuboid.of(0, 0, 0, 1, 1, 1));
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 3, 3, 2), Map.of(), List.of(big, small));

        assertSame(small, view.occupant(foot));
        assertSame(big, view.occupant(LatticePoint.of(1, 1, 0)));
        assertEquals(2, view.occupants(Cuboid.of(0, 0, 0, 3, 3, 2), OccupantFilter.any()).size());
    }

    @Test
    void readOutsideBoundsAndForeignFramesUseTheKernelOutOfBoundsRefusal() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 7);
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 2, 2, 2), Map.of());
        LatticePoint outside = LatticePoint.of(2, 0, 0);

        assertEquals(Cuboid.of(0, 0, 0, 2, 2, 2), view.bounds());
        assertEquals(LatticePoint.origin, view.origin());
        assertEquals(SnapshotPolicy.minimal(), view.policy());
        ReadRefusal refusal = assertInstanceOf(CellRead.Unreadable.class, view.read(outside)).refusal();
        assertEquals(ReadStatus.outOfBounds, refusal.status());
        assertEquals(Codes.unreadableRead.code(), refusal.code());
        assertNull(view.occupant(outside));
        assertEquals(refusal, view.refusalAt(outside));
        assertEquals(ReadStatus.outOfBounds, view.statusOf(outside));

        SectionSlot.Refused otherFrame = assertInstanceOf(
            SectionSlot.Refused.class,
            view.slot(new SectionKey(snapshot.world(), 1, 0, 0, 0))
        );
        assertEquals(ReadStatus.outOfBounds, otherFrame.status());
        assertEquals(Codes.unreadableRead.code(), otherFrame.code());
        SectionKey foreignWorld = new SectionKey(World.of(NamespacedId.of("test", "other")), 0, 0, 0, 0);
        assertEquals(
            Codes.unreadableRead.code(),
            assertInstanceOf(SectionSlot.Refused.class, view.slot(foreignWorld)).code()
        );
    }

    @Test
    void partialSectionsRejectOnlyTheirRefusedCells() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 8);
        LatticePoint readable = LatticePoint.of(1, 0, 0);
        LatticePoint refusedCell = LatticePoint.of(2, 0, 0);
        SectionKey key = SectionKey.of(snapshot.world(), 0, LatticePoint.origin, 16);
        OccupantSnapshot stone = occupant(snapshot, LatticePoint.origin, "stone");
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(LatticePoint.origin, stone), 42L);
        ReadRefusal refusal = new ReadRefusal(ReadStatus.denied, FindingCode.of(FindingCode.Family.mc, 2));
        SectionSlot.Partial partial = new SectionSlot.Partial(section, Map.of(refusedCell, refusal));
        SnapshotPolicy policy = new SnapshotPolicy(
            64,
            1,
            Set.of(OccupantKind.entity),
            Set.of(NamespacedId.of("test", "energy")),
            List.of()
        );
        LevelSnapshot view = new LevelSnapshot(
            snapshot,
            Cuboid.of(0, 0, 0, 3, 1, 1),
            Map.of(key, partial),
            List.of(),
            policy
        );

        assertEquals(policy, view.policy());
        assertSame(partial, view.slot(key));
        assertSame(stone, view.occupant(LatticePoint.origin));
        assertTrue(Objects.requireNonNull(view.occupant(readable), "air").isAir());
        assertEquals(ReadStatus.readable, view.statusOf(readable));

        ReadRefusal actual = assertInstanceOf(CellRead.Unreadable.class, view.read(refusedCell)).refusal();
        assertEquals(refusal, actual);
        assertNull(view.occupant(refusedCell));
        assertEquals(refusal, view.refusalAt(refusedCell));
        assertEquals(ReadStatus.denied, view.statusOf(refusedCell));
        assertNull(view.occupant(OccupantSnapshot.air(snapshot, 0, refusedCell).ref()));
        assertEquals(List.of(stone), view.occupants(Cuboid.of(0, 0, 0, 3, 1, 1), OccupantFilter.any()));

        long digest = view.contentDigest(key);
        assertNotEquals(0L, digest);
        assertNotEquals(42L, digest);
        SectionSlot.Partial relabelled = new SectionSlot.Partial(
            section,
            Map.of(refusedCell, new ReadRefusal(ReadStatus.denied, FindingCode.of(FindingCode.Family.mc, 3)))
        );
        LevelSnapshot sameDigest = new LevelSnapshot(
            snapshot,
            Cuboid.of(0, 0, 0, 3, 1, 1),
            Map.of(key, relabelled),
            List.of(),
            policy
        );
        assertEquals(digest, sameDigest.contentDigest(key));

        SectionSlot.Partial otherStatus = new SectionSlot.Partial(
            section,
            Map.of(refusedCell, new ReadRefusal(ReadStatus.notCaptured, FindingCode.of(FindingCode.Family.mc, 3)))
        );
        LevelSnapshot differentDigest = new LevelSnapshot(
            snapshot,
            Cuboid.of(0, 0, 0, 3, 1, 1),
            Map.of(key, otherStatus),
            List.of(),
            policy
        );
        assertNotEquals(digest, differentDigest.contentDigest(key));
    }

    @Test
    void refusedSectionsAnswerEveryCellWithTheirCodeAndZeroDigest() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 9);
        SectionKey key = SectionKey.of(snapshot.world(), 0, LatticePoint.origin, 16);
        SectionSlot.Refused refused = new SectionSlot.Refused(
            ReadStatus.notCaptured,
            FindingCode.of(FindingCode.Family.mc, 1)
        );
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 2, 1, 1), Map.of(key, refused));

        ReadRefusal actual = assertInstanceOf(CellRead.Unreadable.class, view.read(LatticePoint.origin)).refusal();
        assertEquals(ReadStatus.notCaptured, actual.status());
        assertEquals(refused.code(), actual.code());
        assertNull(view.occupant(LatticePoint.origin));
        assertFalse(view.slot(key).readable());
        assertEquals(ReadStatus.notCaptured, view.slot(key).status());
        assertEquals(0L, view.contentDigest(key));
    }

    @Test
    void entityBodiesKeepSameTypeSameAnchorEntitiesDistinct() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 10);
        NamespacedId type = NamespacedId.of("minecraft", "zombie");
        UUID firstId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID secondId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        LatticePoint anchor = LatticePoint.origin;
        Cuboid footprint = Cuboid.of(0, 0, 0, 1, 2, 1);
        OccupantSnapshot first = OccupantDescriptors.entity(snapshot, 0, anchor, type, firstId, footprint);
        OccupantSnapshot second = OccupantDescriptors.entity(snapshot, 0, anchor, type, secondId, footprint);

        assertEquals(NamespacedId.of("minecraft", firstId.toString()), first.ref().body());
        assertEquals(NamespacedId.of("minecraft", secondId.toString()), second.ref().body());
        assertNull(occupant(snapshot, anchor, "stone").ref().body());
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 1, 2, 1), Map.of(), List.of(second, first));

        assertEquals(2, view.occupants(Cuboid.of(0, 0, 0, 1, 2, 1), OccupantFilter.ofKind(OccupantKind.entity)).size());
        assertSame(first, view.occupant(first.ref()));
        assertSame(second, view.occupant(second.ref()));
        assertEquals(
            OccupantKind.entity,
            Objects.requireNonNull(view.occupant(anchor), "narrowed").descriptor().kind()
        );
    }

    @Test
    void nestedBodyLivesInTheParentAndTheChildKeepsItsOwnFrame() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 11);
        LatticePoint anchor = LatticePoint.origin;
        NamespacedId nestedType = NamespacedId.of("test", "nested");
        OccupantSnapshot stone = occupant(snapshot, anchor, "stone");
        SectionKey rootKey = SectionKey.of(snapshot.world(), 0, anchor, 16);
        SectionSnapshot rootSection = SectionSnapshot.of(rootKey, Map.of(anchor, stone), 7L);
        Cuboid footprint = Cuboid.of(0, 0, 0, 2, 2, 2);
        OccupantSnapshot nested = OccupantDescriptors.nested(snapshot, 0, anchor, nestedType, footprint);

        LatticePoint childCell = LatticePoint.of(1, 0, 0);
        SectionKey childKey = SectionKey.of(snapshot.world(), 1, childCell, 16);
        OccupantSnapshot dirt = occupant(snapshot, 1, childCell, "dirt");
        SectionSnapshot childSection = SectionSnapshot.of(childKey, Map.of(childCell, dirt), 9L);
        FrameData childData = new FrameData(
            snapshot,
            1,
            1,
            LatticePoint.of(100, 200, 300),
            Cuboid.of(0, 0, 0, 2, 2, 2),
            Map.of(childKey, new SectionSlot.Present(childSection)),
            List.of(),
            List.of(),
            Map.of()
        );
        FrameData rootData = new FrameData(
            snapshot,
            0,
            0,
            LatticePoint.origin,
            Cuboid.of(0, 0, 0, 2, 2, 2),
            Map.of(rootKey, new SectionSlot.Present(rootSection)),
            List.of(),
            List.of(nested),
            Map.of(anchor, childData)
        );
        SnapshotPolicy policy = new SnapshotPolicy(64, 1, Set.of(OccupantKind.nested), Set.of(), List.of());
        LevelSnapshot view = new LevelSnapshot(snapshot, policy, rootData);
        WorldView child = Objects.requireNonNull(view.nested(anchor), "child view");

        assertEquals(0, view.frame());
        assertEquals(0, view.depth());
        assertNull(view.parent());
        assertEquals(LatticePoint.origin, view.origin());
        assertEquals(1, child.frame());
        assertEquals(1, child.depth());
        assertSame(view, child.parent());
        assertEquals(LatticePoint.of(100, 200, 300), child.origin());
        assertEquals(Cuboid.of(0, 0, 0, 2, 2, 2), child.bounds());
        assertEquals(Cuboid.of(100, 200, 300, 102, 202, 302), child.bounds().translate(100, 200, 300));
        assertEquals(snapshot, child.snapshotId());
        assertEquals(policy, child.policy());
        assertNull(view.nested(childCell));
        assertNull(child.nested(anchor));

        assertSame(stone, view.occupant(anchor));
        assertSame(nested, view.occupant(nested.ref()));
        assertSame(dirt, child.occupant(childCell));
        assertNull(child.occupant(nested.ref()));
        assertNull(view.occupant(dirt.ref()));
        assertNull(view.occupant(new OccupantRef(snapshot, 1, nestedType, OccupantKind.nested, anchor)));
        assertEquals(ReadStatus.readable, child.statusOf(childCell));
        assertEquals(ReadStatus.outOfBounds, child.statusOf(LatticePoint.of(2, 0, 0)));

        assertEquals(
            List.of(nested),
            view.occupants(Cuboid.of(0, 0, 0, 2, 2, 2), OccupantFilter.ofKind(OccupantKind.nested))
        );
        assertEquals(List.of(nested), view.occupants(Cuboid.of(0, 0, 0, 2, 2, 2), OccupantFilter.ofType(nestedType)));
        assertEquals(2, view.occupants(Cuboid.of(0, 0, 0, 2, 2, 2), OccupantFilter.any()).size());
        assertTrue(view.occupants(Cuboid.of(10, 0, 0, 12, 2, 2), OccupantFilter.any()).isEmpty());
    }

    @Test
    void aNestedBodyIsAnOccupantOverAir() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 12);
        NamespacedId nestedType = NamespacedId.of("test", "nested");
        LatticePoint anchor = LatticePoint.origin;
        OccupantSnapshot nested = OccupantDescriptors
                .nested(snapshot, 0, anchor, nestedType, Cuboid.of(0, 0, 0, 2, 2, 2));
        FrameData rootData = new FrameData(
            snapshot,
            0,
            0,
            LatticePoint.origin,
            Cuboid.of(0, 0, 0, 2, 2, 2),
            Map.of(),
            List.of(),
            List.of(nested),
            Map.of()
        );
        LevelSnapshot view = new LevelSnapshot(snapshot, SnapshotPolicy.minimal(), rootData);

        assertSame(nested, view.occupant(anchor));
        assertEquals(OccupantKind.nested, Objects.requireNonNull(view.occupant(anchor), "nested").descriptor().kind());
        assertEquals(nested.ref(), assertInstanceOf(CellRead.Found.class, view.read(anchor)).occupant().ref());
        assertEquals(List.of(nested), view.occupants(Cuboid.of(0, 0, 0, 2, 2, 2), OccupantFilter.any()));
    }

    @Test
    void framesAreUniqueAcrossTheWholeCapture() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 13);
        FrameData first = new FrameData(
            snapshot,
            1,
            1,
            LatticePoint.origin,
            Cuboid.of(0, 0, 0, 1, 1, 1),
            Map.of(),
            List.of(),
            List.of(),
            Map.of()
        );
        FrameData second = new FrameData(
            snapshot,
            1,
            1,
            LatticePoint.origin,
            Cuboid.of(0, 0, 0, 1, 1, 1),
            Map.of(),
            List.of(),
            List.of(),
            Map.of()
        );
        FrameData root = new FrameData(
            snapshot,
            0,
            0,
            LatticePoint.origin,
            Cuboid.of(0, 0, 0, 2, 1, 1),
            Map.of(),
            List.of(),
            List.of(),
            Map.of(LatticePoint.origin, first, LatticePoint.of(1, 0, 0), second)
        );

        assertThrows(IllegalArgumentException.class, () -> new LevelSnapshot(snapshot, SnapshotPolicy.minimal(), root));
    }

    @Test
    void typedReadsDispatchToTheFrameSnapshot() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 14);
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 2, 2);
        LatticePoint anchor = LatticePoint.origin;
        SectionKey key = SectionKey.of(snapshot.world(), 0, anchor, 16);
        BlockState water = Blocks.WATER.defaultBlockState();
        OccupantSnapshot block = occupant(snapshot, anchor, "water");
        SectionSnapshot section = SectionSnapshot.of(key, Map.of(anchor, block), 7L);
        SnapshotLevel host = hostSnapshot(bounds, water, EntityType.CHICKEN);
        FrameData data = new FrameData(
            snapshot,
            0,
            0,
            LatticePoint.origin,
            bounds,
            Map.of(key, new SectionSlot.Present(section)),
            List.of(),
            List.of(),
            Map.of(),
            host
        );
        LevelSnapshot view = new LevelSnapshot(snapshot, SnapshotPolicy.minimal(), data);

        assertSame(water, view.get(anchor, HostTypes.blockState));
        assertSame(water, view.get(block.ref(), HostTypes.blockState));
        assertEquals(water.getFluidState(), view.get(anchor, HostTypes.fluidState));
        assertSame(EntityType.CHICKEN, view.get(anchor, HostTypes.entityType));
        assertNull(view.get(anchor, HostType.of(NamespacedId.of("test", "unknown"))));
        assertNull(view.get(LatticePoint.of(2, 0, 0), HostTypes.blockState), "outside the snapshot is null");
        assertNull(view.get(LatticePoint.of(2, 0, 0), HostTypes.fluidState), "outside the snapshot is null");
        assertNull(view.get(LatticePoint.of(2, 0, 0), HostTypes.entityType));
    }

    @Test
    void aViewWithoutAHostSnapshotAnswersNull() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 15);
        LevelSnapshot view = new LevelSnapshot(snapshot, Cuboid.of(0, 0, 0, 2, 2, 2), Map.of());

        assertNull(view.get(LatticePoint.origin, HostTypes.blockState));
        assertNull(view.get(LatticePoint.origin, HostTypes.fluidState));
        assertNull(view.get(LatticePoint.origin, HostTypes.entityType));
    }

    @Test
    void nestedFramesReadTheirOwnSnapshot() {
        SnapshotId snapshot = new SnapshotId(World.of(NamespacedId.of("test", "world")), 16);
        LatticePoint anchor = LatticePoint.origin;
        LatticePoint childCell = LatticePoint.of(1, 0, 0);
        Cuboid bounds = Cuboid.of(0, 0, 0, 2, 2, 2);
        NamespacedId nestedType = NamespacedId.of("test", "nested");
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        SectionKey rootKey = SectionKey.of(snapshot.world(), 0, anchor, 16);
        OccupantSnapshot block = occupant(snapshot, anchor, "stone");
        SectionSnapshot rootSection = SectionSnapshot.of(rootKey, Map.of(anchor, block), 7L);
        OccupantSnapshot nested = OccupantDescriptors.nested(snapshot, 0, anchor, nestedType, bounds);

        LatticePoint childOrigin = LatticePoint.of(10, 20, 30);
        SectionKey childKey = SectionKey.of(snapshot.world(), 1, childCell, 16);
        OccupantSnapshot dirt = occupant(snapshot, 1, childCell, "dirt");
        SectionSnapshot childSection = SectionSnapshot.of(childKey, Map.of(childCell, dirt), 9L);
        FrameData childData = new FrameData(
            snapshot,
            1,
            1,
            childOrigin,
            bounds,
            Map.of(childKey, new SectionSlot.Present(childSection)),
            List.of(),
            List.of(),
            Map.of(),
            hostSnapshot(childOrigin, bounds, log, null, childCell)
        );
        FrameData rootData = new FrameData(
            snapshot,
            0,
            0,
            LatticePoint.origin,
            bounds,
            Map.of(rootKey, new SectionSlot.Present(rootSection)),
            List.of(),
            List.of(nested),
            Map.of(anchor, childData),
            hostSnapshot(LatticePoint.origin, bounds, stone, null, anchor)
        );
        SnapshotPolicy policy = new SnapshotPolicy(64, 1, Set.of(OccupantKind.nested), Set.of(), List.of());
        LevelSnapshot view = new LevelSnapshot(snapshot, policy, rootData);
        WorldView child = Objects.requireNonNull(view.nested(anchor), "child view");

        assertSame(stone, view.get(anchor, HostTypes.blockState));
        assertTrue(Objects.requireNonNull(child.get(LatticePoint.origin, HostTypes.blockState), "child air").isAir());
        assertSame(log, child.get(childCell, HostTypes.blockState));
        assertTrue(Objects.requireNonNull(view.get(childCell, HostTypes.blockState), "root air").isAir());
    }

    private static SnapshotLevel hostSnapshot(Cuboid bounds, BlockState state, @Nullable EntityType<?> entityType) {
        return hostSnapshot(LatticePoint.origin, bounds, state, entityType, LatticePoint.origin);
    }

    private static SnapshotLevel hostSnapshot(
        LatticePoint origin,
        Cuboid bounds,
        BlockState state,
        @Nullable EntityType<?> entityType,
        LatticePoint cell
    ) {
        int worldX = origin.x() + cell.x();
        int worldY = origin.y() + cell.y();
        int worldZ = origin.z() + cell.z();
        PalettedContainer<BlockState> states = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        states.set(worldX & 15, worldY & 15, worldZ & 15, state);
        FluidState[] fluids = new FluidState[SnapshotLevel.sectionSize
                * SnapshotLevel.sectionSize
                * SnapshotLevel.sectionSize];
        Arrays.fill(fluids, Fluids.EMPTY.defaultFluidState());
        fluids[SnapshotLevel.fluidIndex(worldX, worldY, worldZ)] = state.getFluidState();
        long key = SnapshotLevel.worldSectionKey(worldX, worldY, worldZ);
        List<SnapshotLevel.CapturedEntity> entities = entityType == null
                ? List.of()
                : List.of(
                    new SnapshotLevel.CapturedEntity(
                        UUID.nameUUIDFromBytes(new byte[]{1}),
                        NamespacedId.of("minecraft", "chicken"),
                        Cuboid.of(cell),
                        entityType
                    )
                );
        return new SnapshotLevel(origin, bounds, -64, 384, Map.of(key, states), Map.of(key, fluids), entities);
    }

    private static OccupantSnapshot entity(SnapshotId snapshot, LatticePoint anchor, String type, Cuboid footprint) {
        NamespacedId id = NamespacedId.of("test", type);
        OccupantRef ref = new OccupantRef(snapshot, 0, id, OccupantKind.entity, anchor);
        return new OccupantSnapshot(ref, new OccupantDescriptor(id, OccupantKind.entity), footprint, List.of());
    }

    private static OccupantSnapshot occupant(SnapshotId snapshot, LatticePoint cell, String type) {
        return occupant(snapshot, 0, cell, type);
    }

    private static OccupantSnapshot occupant(SnapshotId snapshot, int frame, LatticePoint cell, String type) {
        NamespacedId id = NamespacedId.of("test", type);
        OccupantRef ref = new OccupantRef(snapshot, frame, id, OccupantKind.block, cell);
        return new OccupantSnapshot(ref, new OccupantDescriptor(id, OccupantKind.block), Cuboid.of(cell), List.of());
    }
}
