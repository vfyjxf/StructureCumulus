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
import dev.vfyjxf.cumulus.kernel.world.Trait;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import dev.vfyjxf.cumulus.mc.capability.BlockNestedExtentContext;
import dev.vfyjxf.cumulus.mc.capability.CapabilityNestedExtentProvider;
import dev.vfyjxf.cumulus.mc.capability.NestedExtent;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentHolder;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentHolders;
import dev.vfyjxf.cumulus.mc.capability.NestedExtentLookup;
import dev.vfyjxf.cumulus.mc.trait.BlockTraitContext;
import dev.vfyjxf.cumulus.mc.trait.TraitCaptureContext;
import dev.vfyjxf.cumulus.mc.trait.TraitHolder;
import dev.vfyjxf.cumulus.mc.trait.TraitHolders;
import dev.vfyjxf.cumulus.mc.trait.TraitLookup;
import dev.vfyjxf.cumulus.mc.trait.TraitProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** GameTests for the three stable trait keys and the four-state trait slots. */
@NullMarked
@GameTestHolder(Constants.modId)
@PrefixGameTestTemplate(false)
public final class TraitCaptureGameTest {

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void chestInventoryIsTheLiveCapability(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
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
            IItemHandler handler = inventory(occupant);
            helper.assertValueEqual(27, handler.getSlots(), "the chest slot count");
            helper.assertTrue(handler.getStackInSlot(0).is(Items.DIAMOND), "the diamond is captured");
            helper.assertValueEqual(3, handler.getStackInSlot(0).getCount(), "the stack size is captured");
            chest.setItem(0, new ItemStack(Items.EMERALD, 5));
            helper.assertTrue(handler.getStackInSlot(0).is(Items.EMERALD), "the trait is the live capability");
            helper.assertValueEqual(5, handler.getStackInSlot(0).getCount(), "the live stack size");
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

        List<NamespacedId> requested = List
                .of(OccupantTraits.inventory.id(), OccupantTraits.energy.id(), OccupantTraits.fluid.id());
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
    public void blockFluidIsTheLiveCapability(GameTestHelper helper) {
        BlockPos cauldronPos = new BlockPos(0, 1, 0);
        helper.setBlock(cauldronPos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        BlockPos world = helper.absolutePos(cauldronPos);

        CaptureResult.Captured captured = capture(
            new LevelCapture(helper.getLevel()),
            box(world),
            policy(Set.of(OccupantTraits.fluid.id()))
        );
        try (SnapshotLease lease = captured.lease()) {
            OccupantSnapshot occupant = occupant(lease.view(), point(world));
            IFluidHandler handler = fluid(occupant);
            helper.assertValueEqual(Fluids.WATER, handler.getFluidInTank(0).getFluid(), "the cauldron fluid");
            helper.assertValueEqual(1000, handler.getFluidInTank(0).getAmount(), "the cauldron amount");
            helper.setBlock(cauldronPos, Blocks.LAVA_CAULDRON);
            helper.assertValueEqual(
                Fluids.LAVA,
                handler.getFluidInTank(0).getFluid(),
                "the trait is the live capability"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void entityInventoryIsTheLiveCapability(GameTestHelper helper) {
        BlockPos floor = new BlockPos(0, 1, 0);
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(new BlockPos(0, 2, 0), Blocks.AIR);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(0.5, 1.5, 0.5));
        zombie.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        BlockPos world = helper.absolutePos(floor);
        Cuboid bounds = Cuboid
                .of(world.getX(), world.getY(), world.getZ(), world.getX() + 1, world.getY() + 2, world.getZ() + 1);

        CaptureResult.Captured captured = capture(
            new LevelCapture(helper.getLevel()),
            bounds,
            entityPolicy(Set.of(OccupantTraits.inventory.id(), OccupantTraits.energy.id(), OccupantTraits.fluid.id()))
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
            IItemHandler handler = inventory(occupant);
            helper.assertValueEqual(6, handler.getSlots(), "the hands and armor slots");
            helper.assertTrue(handler.getStackInSlot(0).is(Items.DIAMOND_SWORD), "the main hand is captured");
            zombie.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
            helper.assertTrue(handler.getStackInSlot(0).is(Items.EMERALD), "the trait is the live capability");
            helper.assertTrue(
                occupant.slot(OccupantTraits.energy.id()) instanceof TraitSlot.Absent,
                "an entity without an energy capability is absent"
            );
            helper.assertTrue(
                occupant.slot(OccupantTraits.fluid.id()) instanceof TraitSlot.Absent,
                "an entity without a fluid capability is absent"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aCapturedEntityTypeOutlivesTheLiveEntity(GameTestHelper helper) {
        BlockPos floor = new BlockPos(0, 1, 0);
        helper.setBlock(floor, Blocks.STONE);
        var chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(0.5, 2.0, 0.5));
        BlockPos world = helper.absolutePos(floor);
        Cuboid bounds = Cuboid
                .of(world.getX(), world.getY(), world.getZ(), world.getX() + 1, world.getY() + 2, world.getZ() + 1);

        CaptureResult.Captured captured = capture(new LevelCapture(helper.getLevel()), bounds, entityPolicy(Set.of()));
        try (SnapshotLease lease = captured.lease()) {
            WorldView view = lease.view();
            LatticePoint entityCell = Objects.requireNonNull(point(world).offset(0, 1, 0), "entity cell");
            helper.assertValueEqual(
                EntityType.CHICKEN,
                view.get(entityCell, HostTypes.entityType),
                "the chicken type is captured"
            );
            chicken.discard();
            helper.assertValueEqual(
                EntityType.CHICKEN,
                view.get(entityCell, HostTypes.entityType),
                "the captured type answers after the live entity is gone"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void failingTraitReadIsRefusedAndKeepsTheCellReadable(GameTestHelper helper) {
        BlockPos absentPos = new BlockPos(0, 1, 0);
        BlockPos throwingPos = new BlockPos(1, 1, 0);
        BlockPos wrongIdPos = new BlockPos(2, 1, 0);
        helper.setBlock(absentPos, Blocks.STONE);
        helper.setBlock(throwingPos, Blocks.STONE);
        helper.setBlock(wrongIdPos, Blocks.STONE);
        BlockPos absent = helper.absolutePos(absentPos);
        BlockPos throwing = helper.absolutePos(throwingPos);
        BlockPos wrongId = helper.absolutePos(wrongIdPos);
        Cuboid bounds = Cuboid.of(
            absent.getX(),
            absent.getY(),
            absent.getZ(),
            wrongId.getX() + 1,
            wrongId.getY() + 1,
            wrongId.getZ() + 1
        );

        CaptureResult.Captured captured = capture(
            new LevelCapture(helper.getLevel(), NestedExtentLookup.capabilities(), fakeTraitLookup(throwing, wrongId)),
            bounds,
            policy(Set.of(OccupantTraits.inventory.id()))
        );
        try (SnapshotLease lease = captured.lease()) {
            WorldView view = lease.view();
            helper.assertTrue(
                occupant(view, point(absent)).slot(OccupantTraits.inventory.id()) instanceof TraitSlot.Absent,
                "a provider that answers absent is a positive absence"
            );
            assertRefused(helper, view, point(throwing));
            assertRefused(helper, view, point(wrongId));
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
            new LevelCapture(helper.getLevel(), new SingleExtentLookup(worldAnchor, extent).lookup()),
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
            IItemHandler handler = inventory(chestOccupant);
            helper.assertTrue(
                handler.getStackInSlot(0).is(Items.DIAMOND),
                "the child inventory is the live capability"
            );
            helper.assertValueEqual(3, handler.getStackInSlot(0).getCount(), "the child stack size");
            helper.assertTrue(
                occupant(lease.view(), point(worldAnchor))
                        .slot(OccupantTraits.inventory.id()) instanceof TraitSlot.Absent,
                "the root stone has no inventory"
            );
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Constants.modId, template = "empty", timeoutTicks = 100)
    public void aCustomEnergyProviderCarriesTheDirectValue(GameTestHelper helper) {
        BlockPos stonePos = new BlockPos(0, 1, 0);
        helper.setBlock(stonePos, Blocks.STONE);
        BlockPos world = helper.absolutePos(stonePos);
        IEnergyStorage blockStorage = new FakeEnergyStorage(42);

        CaptureResult.Captured blockCaptured = capture(
            new LevelCapture(
                helper.getLevel(),
                TraitLookup.builder().add(energyProvider(TraitHolders.block, blockStorage)).build(),
                TraitCaptureContext.empty()
            ),
            box(world),
            policy(Set.of(OccupantTraits.energy.id()))
        );
        try (SnapshotLease lease = blockCaptured.lease()) {
            OccupantSnapshot occupant = occupant(lease.view(), point(world));
            helper.assertTrue(
                occupant.trait(OccupantTraits.energy) == blockStorage,
                "the block energy trait is the provider's exact value"
            );
        }

        helper.setBlock(new BlockPos(0, 2, 0), Blocks.AIR);
        helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(0.5, 1.5, 0.5));
        Cuboid bounds = Cuboid
                .of(world.getX(), world.getY(), world.getZ(), world.getX() + 1, world.getY() + 2, world.getZ() + 1);
        IEnergyStorage entityStorage = new FakeEnergyStorage(7);
        CaptureResult.Captured entityCaptured = capture(
            new LevelCapture(
                helper.getLevel(),
                TraitLookup.builder().add(energyProvider(TraitHolders.entity, entityStorage)).build(),
                TraitCaptureContext.empty()
            ),
            bounds,
            entityPolicy(Set.of(OccupantTraits.energy.id()))
        );
        try (SnapshotLease lease = entityCaptured.lease()) {
            List<OccupantSnapshot> entities = lease.view()
                    .occupants(bounds, OccupantFilter.ofKind(OccupantKind.entity));
            helper.assertValueEqual(1, entities.size(), "one captured entity");
            helper.assertTrue(
                entities.get(0).trait(OccupantTraits.energy) == entityStorage,
                "the entity energy trait is the provider's exact value"
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

    private static IItemHandler inventory(OccupantSnapshot occupant) {
        IItemHandler handler = occupant.trait(OccupantTraits.inventory);
        if (handler == null) {
            throw new GameTestAssertException(
                "the inventory trait is not present: " + occupant.slot(OccupantTraits.inventory.id())
            );
        }
        return handler;
    }

    private static IFluidHandler fluid(OccupantSnapshot occupant) {
        IFluidHandler handler = occupant.trait(OccupantTraits.fluid);
        if (handler == null) {
            throw new GameTestAssertException(
                "the fluid trait is not present: " + occupant.slot(OccupantTraits.fluid.id())
            );
        }
        return handler;
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

    private static <H> TraitProvider<IEnergyStorage, H> energyProvider(TraitHolder<H> holder, IEnergyStorage storage) {
        return new TraitProvider<>() {
            @Override
            public TraitKey<IEnergyStorage> key() {
                return OccupantTraits.energy;
            }

            @Override
            public TraitHolder<H> holder() {
                return holder;
            }

            @Override
            public TraitSlot get(H actual, TraitCaptureContext capture) {
                return new TraitSlot.Present(Trait.of(OccupantTraits.energy, storage));
            }
        };
    }

    private static TraitLookup fakeTraitLookup(BlockPos throwing, BlockPos wrongId) {
        return TraitLookup.builder().add(new TraitProvider<IItemHandler, BlockTraitContext>() {
            @Override
            public TraitKey<IItemHandler> key() {
                return OccupantTraits.inventory;
            }

            @Override
            public TraitHolder<BlockTraitContext> holder() {
                return TraitHolders.block;
            }

            @Override
            public TraitSlot get(BlockTraitContext actual, TraitCaptureContext capture) {
                if (actual.pos().equals(throwing)) {
                    throw new IllegalStateException("fake trait capability");
                }
                if (actual.pos().equals(wrongId)) {
                    return new TraitSlot.Present(Trait.absent(OccupantTraits.energy));
                }
                return new TraitSlot.Absent(OccupantTraits.inventory.id());
            }
        }).build();
    }

    private record SingleExtentLookup(BlockPos pos, NestedExtent extent) {

        private NestedExtentLookup lookup() {
            return NestedExtentLookup.builder()
                    .add(new CapabilityNestedExtentProvider<BlockNestedExtentContext, @Nullable Direction>() {
                        @Override
                        public NestedExtentHolder<BlockNestedExtentContext> holder() {
                            return NestedExtentHolders.block;
                        }

                        @Override
                        public @Nullable NestedExtent get(
                            BlockNestedExtentContext actual,
                            @Nullable Direction context
                        ) {
                            return actual.pos().equals(pos) ? extent : null;
                        }
                    }).build();
        }
    }

    private static final class FakeEnergyStorage implements IEnergyStorage {

        private final int stored;

        private FakeEnergyStorage(int stored) {
            this.stored = stored;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return stored;
        }

        @Override
        public int getMaxEnergyStored() {
            return stored;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
