package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.Constants;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.world.CaptureRequest;
import dev.vfyjxf.cumulus.kernel.world.CaptureResult;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantFilter;
import dev.vfyjxf.cumulus.kernel.world.OccupantKind;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SnapshotLease;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import dev.vfyjxf.cumulus.mc.trait.BlockTraitContext;
import dev.vfyjxf.cumulus.mc.trait.TraitLookup;
import dev.vfyjxf.cumulus.mc.trait.TraitProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** GameTests for the five stable trait keys and the four-state trait slots. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class TraitCaptureGameTest {

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void chestInventoryIsPresentAndCopied(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        ItemStack live = chest.getItem(0);
        BlockPos world = helper.absolutePos(chestPos);

        CaptureResult.Captured captured = capture(
            new LevelCapture(helper.getLevel()),
            box(world),
            policy(Set.of(OccupantTraits.inventory.id()))
        );
        try (SnapshotLease lease = captured.lease()) {
            OccupantSnapshot occupant = occupant(lease.view(), point(world));
            helper.assertValueEqual(OccupantKind.blockEntity, occupant.descriptor().kind(), "the chest kind");
            helper.assertValueEqual(NamespacedId.of("minecraft", "chest"), occupant.descriptor().type(), "chest type");
            helper.assertValueEqual(1, occupant.traits().size(), "one requested trait");
            List<ItemStack> items = inventory(occupant);
            helper.assertValueEqual(27, items.size(), "the chest slot count");
            ItemStack capturedStack = items.get(0);
            helper.assertTrue(capturedStack.is(Items.DIAMOND), "the diamond is captured");
            helper.assertValueEqual(3, capturedStack.getCount(), "the stack size is captured");
            helper.assertTrue(capturedStack != live, "the captured stack is a copy");
            live.setCount(64);
            helper.assertValueEqual(3, capturedStack.getCount(), "the chest mutation does not reach the capture");
            helper.assertTrue(occupant.has(OccupantTraits.inventory), "has sees the present trait");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void plainStoneCellHasAbsentTraits(GameTestHelper helper) {
        BlockPos stonePos = new BlockPos(0, 1, 0);
        helper.setBlock(stonePos, Blocks.STONE);
        BlockPos world = helper.absolutePos(stonePos);

        CaptureResult.Captured plain = capture(
            new LevelCapture(helper.getLevel()),
            box(world),
            SnapshotPolicy.minimal()
        );
        try (SnapshotLease lease = plain.lease()) {
            OccupantSnapshot occupant = occupant(lease.view(), point(world));
            helper.assertTrue(occupant.traits().isEmpty(), "a minimal policy captures no traits");
            helper.assertTrue(occupant.slot(OccupantTraits.inventory.id()) == null, "an unasked trait has no slot");
        }

        List<NamespacedId> requested = List.of(
            OccupantTraits.inventory.id(),
            OccupantTraits.energy.id(),
            OccupantTraits.fluid.id(),
            OccupantTraits.blockEntityNbt.id(),
            OccupantTraits.entityNbt.id()
        );
        CaptureResult.Captured captured = capture(
            new LevelCapture(helper.getLevel()),
            box(world),
            policy(Set.copyOf(requested))
        );
        try (SnapshotLease lease = captured.lease()) {
            OccupantSnapshot occupant = occupant(lease.view(), point(world));
            helper.assertValueEqual(OccupantKind.block, occupant.descriptor().kind(), "stone is a plain block");
            for (NamespacedId id : requested) {
                helper.assertTrue(
                    occupant.slot(id) instanceof TraitSlot.Absent,
                    "a plain stone cell has no " + id.text()
                );
            }
            helper.assertTrue(occupant.trait(OccupantTraits.inventory) == null, "an absent trait has no value");
        }

        NamespacedId unknown = NamespacedId.of("test", "unknown_trait");
        CaptureResult.Captured refused = capture(
            new LevelCapture(helper.getLevel()),
            box(world),
            policy(Set.of(unknown))
        );
        try (SnapshotLease lease = refused.lease()) {
            OccupantSnapshot occupant = occupant(lease.view(), point(world));
            if (!(occupant.slot(unknown) instanceof TraitSlot.Refused refusal)) {
                helper.fail("an unknown requested trait is not refused");
                return;
            }
            helper.assertValueEqual(ReadStatus.denied, refusal.refusal().status(), "the unknown trait status");
            helper.assertValueEqual("SC-MC-0002", refusal.refusal().code().text(), "the unknown trait code");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void blockEntityNbtIsPresentAndCopied(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        BlockPos world = helper.absolutePos(chestPos);

        CaptureResult.Captured captured = capture(
            new LevelCapture(helper.getLevel()),
            box(world),
            policy(Set.of(OccupantTraits.blockEntityNbt.id(), OccupantTraits.entityNbt.id()))
        );
        try (SnapshotLease lease = captured.lease()) {
            OccupantSnapshot occupant = occupant(lease.view(), point(world));
            helper.assertValueEqual(OccupantKind.blockEntity, occupant.descriptor().kind(), "the chest kind");
            if (!(occupant.slot(OccupantTraits.blockEntityNbt.id()) instanceof TraitSlot.Present)) {
                helper.fail("the block entity nbt is not present");
                return;
            }
            CompoundTag tag = occupant.trait(OccupantTraits.blockEntityNbt);
            if (tag == null) {
                helper.fail("the typed block entity nbt is missing");
                return;
            }
            helper.assertValueEqual(1, tag.getList("Items", Tag.TAG_COMPOUND).size(), "the captured item list");
            helper.assertTrue(
                occupant.slot(OccupantTraits.entityNbt.id()) instanceof TraitSlot.Absent,
                "a block entity has no entity nbt"
            );
            chest.setItem(1, new ItemStack(Items.EMERALD, 2));
            helper.assertValueEqual(
                1,
                tag.getList("Items", Tag.TAG_COMPOUND).size(),
                "the chest mutation does not reach the captured nbt"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void entityNbtIsPresent(GameTestHelper helper) {
        BlockPos floor = new BlockPos(0, 1, 0);
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(new BlockPos(0, 2, 0), Blocks.AIR);
        Entity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(0.5, 1.5, 0.5));
        BlockPos world = helper.absolutePos(floor);
        Cuboid bounds = Cuboid
                .of(world.getX(), world.getY(), world.getZ(), world.getX() + 1, world.getY() + 2, world.getZ() + 1);

        CaptureResult.Captured captured = capture(
            new LevelCapture(helper.getLevel()),
            bounds,
            entityPolicy(Set.of(OccupantTraits.entityNbt.id(), OccupantTraits.blockEntityNbt.id()))
        );
        try (SnapshotLease lease = captured.lease()) {
            List<OccupantSnapshot> entities = lease.view()
                    .occupants(bounds, OccupantFilter.ofKind(OccupantKind.entity));
            helper.assertValueEqual(1, entities.size(), "one captured entity");
            OccupantSnapshot occupant = entities.get(0);
            helper.assertValueEqual(
                NamespacedId.of("minecraft", "zombie"),
                occupant.descriptor().type(),
                "entity type"
            );
            CompoundTag tag = occupant.trait(OccupantTraits.entityNbt);
            if (tag == null) {
                helper.fail("the entity nbt is not present");
                return;
            }
            helper.assertTrue(tag.getUUID("UUID").equals(zombie.getUUID()), "the captured entity uuid");
            helper.assertTrue(tag.contains("Pos"), "the captured entity position");
            helper.assertTrue(
                occupant.slot(OccupantTraits.blockEntityNbt.id()) instanceof TraitSlot.Absent,
                "an entity has no block entity nbt"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void failingTraitReadIsRefusedAndKeepsTheCellReadable(GameTestHelper helper) {
        BlockPos absentPos = new BlockPos(0, 1, 0);
        BlockPos malformedPos = new BlockPos(1, 1, 0);
        BlockPos throwingPos = new BlockPos(2, 1, 0);
        helper.setBlock(absentPos, Blocks.STONE);
        helper.setBlock(malformedPos, Blocks.STONE);
        helper.setBlock(throwingPos, Blocks.STONE);
        BlockPos absent = helper.absolutePos(absentPos);
        BlockPos malformed = helper.absolutePos(malformedPos);
        BlockPos throwing = helper.absolutePos(throwingPos);
        Cuboid bounds = Cuboid.of(
            absent.getX(),
            absent.getY(),
            absent.getZ(),
            throwing.getX() + 1,
            throwing.getY() + 1,
            throwing.getZ() + 1
        );

        CaptureResult.Captured captured = capture(
            new LevelCapture(
                helper.getLevel(),
                NestedExtentLookup.capabilities(),
                fakeTraitLookup(malformed, throwing)
            ),
            bounds,
            policy(Set.of(OccupantTraits.inventory.id()))
        );
        try (SnapshotLease lease = captured.lease()) {
            WorldView view = lease.view();
            helper.assertTrue(
                occupant(view, point(absent)).slot(OccupantTraits.inventory.id()) instanceof TraitSlot.Absent,
                "a null capability is a positive absence"
            );
            assertRefused(helper, view, point(malformed));
            assertRefused(helper, view, point(throwing));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void childFrameCapturesTraits(GameTestHelper helper) {
        BlockPos anchorPos = new BlockPos(0, 1, 0);
        BlockPos chestPos = new BlockPos(1, 1, 0);
        helper.setBlock(anchorPos, Blocks.STONE);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        BlockPos worldAnchor = helper.absolutePos(anchorPos);
        BlockPos worldChest = helper.absolutePos(chestPos);
        NestedExtent extent = new NestedExtent(
            NamespacedId.of("test", "trait_child"),
            Cuboid.of(
                worldAnchor.getX(),
                worldAnchor.getY(),
                worldAnchor.getZ(),
                worldAnchor.getX() + 2,
                worldAnchor.getY() + 2,
                worldAnchor.getZ() + 2
            ),
            LatticePoint.of(worldAnchor.getX(), worldAnchor.getY(), worldAnchor.getZ())
        );

        CaptureResult.Captured captured = capture(
            new LevelCapture(helper.getLevel(), new SingleExtentLookup(worldAnchor, extent)),
            box(worldAnchor),
            nestedPolicy(Set.of(OccupantTraits.inventory.id()))
        );
        try (SnapshotLease lease = captured.lease()) {
            WorldView child = lease.view().nested(point(worldAnchor));
            if (child == null) {
                helper.fail("the child frame is missing");
                return;
            }
            helper.assertValueEqual(1, child.frame(), "the child frame");
            LatticePoint localChest = LatticePoint.of(
                worldChest.getX() - worldAnchor.getX(),
                worldChest.getY() - worldAnchor.getY(),
                worldChest.getZ() - worldAnchor.getZ()
            );
            OccupantSnapshot chestOccupant = occupant(child, localChest);
            helper.assertValueEqual(OccupantKind.blockEntity, chestOccupant.descriptor().kind(), "the child chest");
            helper.assertValueEqual(1, chestOccupant.traits().size(), "the child captures the requested trait");
            List<ItemStack> items = inventory(chestOccupant);
            helper.assertTrue(items.get(0).is(Items.DIAMOND), "the child inventory is captured");
            helper.assertValueEqual(3, items.get(0).getCount(), "the child stack size");
            helper.assertTrue(
                occupant(lease.view(), point(worldAnchor))
                        .slot(OccupantTraits.inventory.id()) instanceof TraitSlot.Absent,
                "the root stone has no inventory"
            );
        }
        helper.succeed();
    }

    private static SnapshotPolicy policy(Set<NamespacedId> traits) {
        return new SnapshotPolicy(1_000_000, 0, Set.of(), traits, List.of());
    }

    private static SnapshotPolicy entityPolicy(Set<NamespacedId> traits) {
        return new SnapshotPolicy(1_000_000, 0, Set.of(OccupantKind.entity), traits, List.of());
    }

    private static SnapshotPolicy nestedPolicy(Set<NamespacedId> traits) {
        return new SnapshotPolicy(1_000_000, 1, Set.of(OccupantKind.nested), traits, List.of());
    }

    private static CaptureResult.Captured capture(LevelCapture capture, Cuboid bounds, SnapshotPolicy policy) {
        CaptureResult result = capture.capture(new CaptureRequest(bounds, policy));
        if (result instanceof CaptureResult.Captured captured) {
            return captured;
        }
        throw new GameTestAssertException("capture did not succeed: " + result);
    }

    private static Cuboid box(BlockPos world) {
        return Cuboid
                .of(world.getX(), world.getY(), world.getZ(), world.getX() + 1, world.getY() + 1, world.getZ() + 1);
    }

    private static LatticePoint point(BlockPos world) {
        return LatticePoint.of(world.getX(), world.getY(), world.getZ());
    }

    private static OccupantSnapshot occupant(WorldView view, LatticePoint cell) {
        OccupantSnapshot occupant = view.occupant(cell);
        if (occupant == null) {
            throw new GameTestAssertException("the cell is unreadable: " + cell);
        }
        return occupant;
    }

    private static List<ItemStack> inventory(OccupantSnapshot occupant) {
        TraitSlot slot = occupant.slot(OccupantTraits.inventory.id());
        if (!(slot instanceof TraitSlot.Present)) {
            throw new GameTestAssertException("the inventory is not present: " + slot);
        }
        List<ItemStack> items = occupant.trait(OccupantTraits.inventory);
        if (items == null) {
            throw new GameTestAssertException("the typed inventory trait is missing");
        }
        return items;
    }

    private static void assertRefused(GameTestHelper helper, WorldView view, LatticePoint cell) {
        OccupantSnapshot occupant = occupant(view, cell);
        TraitSlot slot = occupant.slot(OccupantTraits.inventory.id());
        if (!(slot instanceof TraitSlot.Refused refused)) {
            helper.fail("the trait is not refused: " + slot);
            return;
        }
        helper.assertValueEqual(ReadStatus.failed, refused.refusal().status(), "the refusal status");
        helper.assertValueEqual("SC-MC-0002", refused.refusal().code().text(), "the refusal code");
        helper.assertValueEqual(ReadStatus.readable, view.statusOf(cell), "the cell stays readable");
        helper.assertTrue(view.read(cell) instanceof CellRead.Found, "the occupant stays readable");
        helper.assertValueEqual(1, occupant.traits().size(), "only the requested trait has a slot");
    }

    private record SingleExtentLookup(BlockPos pos, NestedExtent extent) implements NestedExtentLookup {

        @Override
        public @Nullable NestedExtent block(
            ServerLevel level,
            BlockPos query,
            BlockState state,
            @Nullable BlockEntity blockEntity
        ) {
            return query.equals(pos) ? extent : null;
        }

        @Override
        public @Nullable NestedExtent entity(Entity entity) {
            return null;
        }
    }

    private static TraitLookup fakeTraitLookup(BlockPos malformed, BlockPos throwing) {
        return TraitLookup.builder().add(new TraitProvider<List<ItemStack>>() {
            @Override
            public TraitKey<List<ItemStack>> key() {
                return OccupantTraits.inventory;
            }

            @Override
            public TraitSlot readBlock(BlockTraitContext context) {
                if (context.pos().equals(throwing)) {
                    throw new IllegalStateException("fake trait capability");
                }
                if (context.pos().equals(malformed)) {
                    throw new IllegalStateException("malformed fake trait capability");
                }
                return new TraitSlot.Absent(OccupantTraits.inventory.id());
            }
        }).build();
    }
}
