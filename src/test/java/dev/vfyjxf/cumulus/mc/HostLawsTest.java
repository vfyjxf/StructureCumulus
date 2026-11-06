package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimLedger;
import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.claim.ClaimVerdict;
import dev.vfyjxf.cumulus.kernel.claim.SettleRequest;
import dev.vfyjxf.cumulus.kernel.claim.Sharing;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.Flip;
import dev.vfyjxf.cumulus.kernel.space.LatticeKey;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.space.Tilt;
import dev.vfyjxf.cumulus.kernel.space.Turn;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import dev.vfyjxf.cumulus.mc.capability.BlockNestedExtentContext;
import dev.vfyjxf.cumulus.mc.capability.CapabilityNestedExtentProvider;
import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentHolder;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentHolders;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The host laws, asserted against a real server level booted by the JUnit-with-Minecraft runner
 * (D-76): the double proves scenarios, only the booted host proves laws.
 */
@NullMarked
class HostLawsTest {

    private static final int maxElements = 1_000_000;
    private static final NamespacedId chickenType = NamespacedId.of("minecraft", "chicken");

    private static HostServer host;

    @BeforeAll
    static void bootTheHost() throws Exception {
        host = HostServer.boot();
    }

    @AfterAll
    static void stopTheHost() {
        host.close();
    }

    @Test
    void aPolicyWithoutTheEntityKindLeavesEntitiesOutOfTheSnapshot() throws Exception {
        host.onServer(() -> {
            ServerLevel level = host.overworld();
            Region region = new Region(level, 0);
            try {
                region.place(0, 0, 0, Blocks.STONE);
                Chicken chicken = spawnChicken(level, region.block(0, 1, 0));
                Cuboid bounds = region.bounds(0, 0, 0, 1, 2, 1);

                try (SnapshotLease bare = captured(level, bounds, kinds(Set.of())).lease()) {
                    WorldView view = bare.view();
                    assertTrue(
                        view.occupants(bounds, OccupantFilter.ofKind(OccupantKind.entity)).isEmpty(),
                        "a policy without the entity kind captures no entity"
                    );
                    assertEquals(
                        1,
                        view.occupants(bounds, OccupantFilter.ofKind(OccupantKind.block)).size(),
                        "blocks are captured regardless of the kinds"
                    );
                }
                try (SnapshotLease withEntities = captured(level, bounds, kinds(Set.of(OccupantKind.entity))).lease()) {
                    List<OccupantSnapshot> bodies = withEntities.view()
                            .occupants(bounds, OccupantFilter.ofKind(OccupantKind.entity));
                    assertEquals(1, bodies.size(), "a policy with the entity kind captures the entity");
                    assertEquals(
                        chickenType,
                        bodies.get(0).descriptor().type(),
                        "the body carries the live entity type"
                    );
                }
                chicken.discard();
            } finally {
                region.clear();
            }
        });
    }

    @Test
    void aRefusedCellReadsUnreadableAndAnswersNullNeverAir() throws Exception {
        host.onServer(() -> {
            ServerLevel level = host.overworld();
            Region region = new Region(level, 1);
            try {
                BlockPos anchor = region.place(0, 0, 0, Blocks.STONE);
                int buildMin = level.getMinBuildHeight();
                int originY = anchor.getY() + 2;
                if (Math.floorMod(originY, 16) == 0) {
                    originY++;
                }
                Cuboid childWorld = Cuboid.of(
                    anchor.getX(),
                    buildMin - 25,
                    anchor.getZ(),
                    anchor.getX() + 1,
                    originY + 2,
                    anchor.getZ() + 1
                );
                NestedExtent extent = new NestedExtent(
                    NamespacedId.of("test", "height"),
                    childWorld,
                    LatticePoint.of(anchor.getX(), originY, anchor.getZ())
                );
                FakeNested nested = new FakeNested();
                nested.blocks.put(anchor, extent);

                CaptureResult.Captured result = captured(
                    level,
                    region.bounds(0, 0, 0, 1, 3, 1),
                    nested,
                    kinds(Set.of(OccupantKind.nested))
                );
                try (SnapshotLease lease = result.lease()) {
                    WorldView child = lease.view().nested(LatticePoint.of(anchor.getX(), anchor.getY(), anchor.getZ()));
                    assertNotNull(child, "the height-dipping extent opens its child frame");
                    int localBoundary = buildMin - originY;
                    LatticePoint outside = LatticePoint.of(0, localBoundary - 17, 0);
                    assertTrue(child.bounds().contains(outside), "the refused cell is inside the child bounds");
                    assertTrue(
                        child.read(outside) instanceof CellRead.Unreadable,
                        "a refused cell reads Unreadable, never air"
                    );
                    assertNull(child.get(outside, HostTypes.blockState), "a refused cell answers no value (D-80)");
                    LatticePoint partlyOutside = LatticePoint.of(0, localBoundary - 1, 0);
                    assertTrue(
                        child.read(partlyOutside) instanceof CellRead.Unreadable,
                        "a cell of a straddling section below the floor is refused too"
                    );
                    assertTrue(
                        child.read(LatticePoint.origin) instanceof CellRead.Found,
                        "an in-range cell of the same frame still reads"
                    );
                }
            } finally {
                region.clear();
            }
        });
    }

    @Test
    void aCapturedNestedBodyHasAChildAndACutBodyCarriesAFinding() throws Exception {
        host.onServer(() -> {
            ServerLevel level = host.overworld();
            Region region = new Region(level, 2);
            try {
                BlockPos expandable = region.place(0, 0, 0, Blocks.STONE);
                BlockPos sibling = region.place(3, 0, 0, Blocks.STONE);
                LatticePoint nearOrigin = LatticePoint.of(expandable.getX(), expandable.getY(), expandable.getZ());
                LatticePoint siblingOrigin = LatticePoint.of(sibling.getX(), sibling.getY(), sibling.getZ());
                FakeNested nested = new FakeNested();
                nested.blocks.put(
                    expandable,
                    new NestedExtent(
                        NamespacedId.of("test", "expandable"),
                        Cuboid.of(
                            expandable.getX(),
                            expandable.getY(),
                            expandable.getZ(),
                            expandable.getX() + 2,
                            expandable.getY() + 2,
                            expandable.getZ() + 2
                        ),
                        nearOrigin
                    )
                );
                nested.blocks.put(
                    sibling,
                    new NestedExtent(
                        NamespacedId.of("test", "sibling"),
                        Cuboid.of(
                            sibling.getX(),
                            sibling.getY(),
                            sibling.getZ(),
                            sibling.getX() + 1,
                            sibling.getY() + 1,
                            sibling.getZ() + 1
                        ),
                        siblingOrigin
                    )
                );

                CaptureResult.Captured cut = captured(level, region.bounds(0, 0, 0, 4, 2, 1), nested, nestedPolicy(0));
                try (SnapshotLease lease = cut.lease()) {
                    WorldView view = lease.view();
                    assertEquals(
                        2,
                        view.occupants(region.bounds(0, 0, 0, 4, 2, 1), OccupantFilter.ofKind(OccupantKind.nested))
                                .size(),
                        "a cut body stays in the parent's occupants"
                    );
                    assertNull(view.nested(nearOrigin), "a depth-cut body opens no child");
                    assertNull(view.nested(siblingOrigin), "the second cut body opens no child");
                }
                assertEquals(2, cut.report().size(), "each cut carries one report entry");
                for (var entry : cut.report().entries()) {
                    assertEquals(AdapterCodes.cut, entry.code(), "the cut code");
                    assertTrue(
                        entry.notes().stream().anyMatch(note -> note.equals("reason: depth")),
                        "the entry names the depth ceiling"
                    );
                }

                CaptureResult.Captured expanded = captured(
                    level,
                    region.bounds(0, 0, 0, 4, 2, 1),
                    nested,
                    nestedPolicy(1)
                );
                try (SnapshotLease lease = expanded.lease()) {
                    WorldView view = lease.view();
                    WorldView first = view.nested(nearOrigin);
                    WorldView second = view.nested(siblingOrigin);
                    assertNotNull(first, "a captured nested body opens its child frame");
                    assertNotNull(second, "the sibling body opens its child frame");
                    assertEquals(1, first.frame(), "the first anchor gets frame 1");
                    assertEquals(2, second.frame(), "the second anchor gets frame 2");
                    assertEquals(1, first.depth(), "the child depth");
                    assertEquals(nearOrigin, first.origin(), "the child keeps the extent origin");
                    assertEquals(
                        2,
                        view.occupants(region.bounds(0, 0, 0, 4, 2, 1), OccupantFilter.ofKind(OccupantKind.nested))
                                .size(),
                        "the captured bodies also stay in the parent"
                    );
                }
                assertEquals(2, expanded.report().size(), "each child redeclares its extent at the ceiling");
                assertTrue(
                    expanded.report().entries().stream()
                            .allMatch(entry -> entry.notes().stream().anyMatch(note -> note.equals("reason: depth"))),
                    "the ceiling frame reports each rediscovered body as a depth cut"
                );
            } finally {
                region.clear();
            }
        });
    }

    @Test
    void settleAnswersWithThisKeysRows() throws Exception {
        host.onServer(() -> {
            ServerLevel level = host.overworld();
            ServerState state = requireState();
            ClaimLedger ledger = state.claims(level);
            Region region = new Region(level, 3);
            LatticePoint firstCell = region.point(0, 0, 0);
            LatticePoint secondCell = region.point(1, 0, 0);
            InstanceId instance = new InstanceId(state.world(level), 1);
            ClaimRow first = row(firstCell, instance, id("first"));
            ClaimRow second = row(secondCell, instance, id("second"));
            try {
                ClaimVerdict.Settled settled = assertInstanceOf(
                    ClaimVerdict.Settled.class,
                    ledger.settle(new SettleRequest(instance, List.of(first, second), ledger.revision()))
                );
                assertEquals(List.of(first, second), settled.rows(), "the settle answers with the requested rows");
                assertEquals(ledger.revision(), settled.revision(), "the answer carries the new revision");

                InstanceId other = new InstanceId(state.world(level), 2);
                ClaimRow foreign = row(region.point(64, 0, 0), other, id("foreign"));
                ClaimVerdict.Settled secondSettle = assertInstanceOf(
                    ClaimVerdict.Settled.class,
                    ledger.settle(new SettleRequest(other, List.of(foreign), ledger.revision()))
                );
                assertEquals(List.of(foreign), secondSettle.rows(), "another key's rows never leak into the answer");
                ledger.release(other);
            } finally {
                ledger.release(instance);
            }
        });
    }

    @Test
    void aConflictCarriesItsIncumbentsAndWritesNothing() throws Exception {
        host.onServer(() -> {
            ServerLevel level = host.overworld();
            ServerState state = requireState();
            ClaimLedger ledger = state.claims(level);
            Region region = new Region(level, 4);
            LatticePoint cell = region.point(0, 0, 0);
            InstanceId holder = new InstanceId(state.world(level), 3);
            InstanceId rival = new InstanceId(state.world(level), 4);
            ClaimRow held = row(cell, holder, id("held"));
            try {
                assertInstanceOf(
                    ClaimVerdict.Settled.class,
                    ledger.settle(new SettleRequest(holder, List.of(held), ledger.revision()))
                );
                long revision = ledger.revision();

                Siting rivalSiting = Siting.of(cell, Orientation.of(Turn.clockwise, Tilt.level, Flip.none));
                ClaimRow contested = new ClaimRow(
                    LatticeKey.of(cell),
                    Sharing.exclusive,
                    rivalSiting,
                    id("rival"),
                    rival
                );
                ClaimVerdict.Conflict conflict = assertInstanceOf(
                    ClaimVerdict.Conflict.class,
                    ledger.settle(new SettleRequest(rival, List.of(contested), revision))
                );
                assertEquals(List.of(held), conflict.incumbents(), "the conflict carries the incumbent rows");
                assertEquals(revision, ledger.revision(), "a conflict writes nothing");
                assertEquals(List.of(held), ledger.read(Cuboid.of(cell)).rows(), "the incumbent rows are untouched");
            } finally {
                ledger.release(holder);
            }
        });
    }

    @Test
    void aWriteInsideTheCaptureRetiresTheLease() throws Exception {
        host.onServer(() -> {
            ServerLevel level = host.overworld();
            Region region = new Region(level, 5);
            try {
                BlockPos anchor = region.place(0, 0, 0, Blocks.STONE);
                CaptureResult.Captured result = captured(level, region.bounds(0, 0, 0, 1, 1, 1), kinds(Set.of()));
                try (SnapshotLease lease = result.lease()) {
                    assertTrue(lease.isCurrent(), "a fresh lease over a quiet region is current");
                    level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
                    assertTrue(!lease.isCurrent(), "one real write inside the capture retires the lease");
                }
            } finally {
                region.clear();
            }
        });
    }

    private static ServerState requireState() {
        ServerState state = ServerState.current();
        assertNotNull(state, "the booted server opened its state");
        return state;
    }

    private static ClaimRow row(LatticePoint cell, InstanceId instance, NamespacedId bay) {
        return new ClaimRow(
            LatticeKey.of(cell),
            Sharing.exclusive,
            Siting.of(cell, Orientation.identity),
            bay,
            instance
        );
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of("test", path);
    }

    private static SnapshotPolicy kinds(Set<OccupantKind> kinds) {
        return new SnapshotPolicy(maxElements, 1, kinds, Set.of(), List.of());
    }

    private static SnapshotPolicy nestedPolicy(int maxDepth) {
        return new SnapshotPolicy(maxElements, maxDepth, Set.of(OccupantKind.nested), Set.of(), List.of());
    }

    private static CaptureResult.Captured captured(ServerLevel level, Cuboid bounds, SnapshotPolicy policy) {
        CaptureResult result = new LevelCapture(level).capture(new CaptureRequest(bounds, policy));
        return assertInstanceOf(CaptureResult.Captured.class, result);
    }

    private static CaptureResult.Captured captured(
        ServerLevel level,
        Cuboid bounds,
        FakeNested nested,
        SnapshotPolicy policy
    ) {
        CaptureResult result = new LevelCapture(level, nested.lookup()).capture(new CaptureRequest(bounds, policy));
        return assertInstanceOf(CaptureResult.Captured.class, result);
    }

    private static Chicken spawnChicken(ServerLevel level, BlockPos at) {
        Chicken chicken = EntityType.CHICKEN.create(level);
        assertNotNull(chicken, "the chicken type creates its entity");
        chicken.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        chicken.setNoAi(true);
        assertTrue(level.addFreshEntity(chicken), "the chicken joined the level");
        return chicken;
    }

    /** One test's own area: a loaded chunk region that puts back every block it placed. */
    private static final class Region {

        private final ServerLevel level;
        private final BlockPos base;
        private final List<BlockPos> placed = new ArrayList<>();

        Region(ServerLevel level, int index) {
            this.level = level;
            this.base = new BlockPos(4096 + index * 512, -60, 4096);
            level.getChunk(base.getX() >> 4, base.getZ() >> 4);
        }

        BlockPos block(int dx, int dy, int dz) {
            return base.offset(dx, dy, dz);
        }

        BlockPos place(int dx, int dy, int dz, Block block) {
            BlockPos pos = block(dx, dy, dz);
            level.setBlock(pos, block.defaultBlockState(), 3);
            placed.add(pos);
            return pos;
        }

        LatticePoint point(int dx, int dy, int dz) {
            BlockPos pos = block(dx, dy, dz);
            return LatticePoint.of(pos.getX(), pos.getY(), pos.getZ());
        }

        Cuboid bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return Cuboid.of(
                base.getX() + minX,
                base.getY() + minY,
                base.getZ() + minZ,
                base.getX() + maxX,
                base.getY() + maxY,
                base.getZ() + maxZ
            );
        }

        void clear() {
            for (BlockPos pos : placed) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            placed.clear();
        }
    }

    /** The same fake seam the nested GameTests use: a block-holder provider answering a map. */
    private static final class FakeNested {

        private final Map<BlockPos, NestedExtent> blocks = new HashMap<>();
        private final NestedExtentLookup lookup = NestedExtentLookup.builder()
                .add(new CapabilityNestedExtentProvider<BlockNestedExtentContext, @Nullable Void>() {
                    @Override
                    public NestedExtentHolder<BlockNestedExtentContext> holder() {
                        return NestedExtentHolders.block;
                    }

                    @Override
                    public @Nullable NestedExtent get(BlockNestedExtentContext actual, @Nullable Void context) {
                        return blocks.get(actual.pos());
                    }
                }).build();

        NestedExtentLookup lookup() {
            return lookup;
        }
    }
}
