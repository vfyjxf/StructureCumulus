package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.claim.ClaimRow;
import dev.vfyjxf.cumulus.kernel.core.Budget;
import dev.vfyjxf.cumulus.kernel.core.CancelToken;
import dev.vfyjxf.cumulus.kernel.core.ContextView;
import dev.vfyjxf.cumulus.kernel.core.InstanceId;
import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.core.Phase;
import dev.vfyjxf.cumulus.kernel.core.SnapshotId;
import dev.vfyjxf.cumulus.kernel.core.World;
import dev.vfyjxf.cumulus.kernel.finding.Codes;
import dev.vfyjxf.cumulus.kernel.finding.Finding;
import dev.vfyjxf.cumulus.kernel.finding.Label;
import dev.vfyjxf.cumulus.kernel.space.Cuboid;
import dev.vfyjxf.cumulus.kernel.space.CuboidSet;
import dev.vfyjxf.cumulus.kernel.space.LatticePoint;
import dev.vfyjxf.cumulus.kernel.space.Locus;
import dev.vfyjxf.cumulus.kernel.space.Orientation;
import dev.vfyjxf.cumulus.kernel.space.Siting;
import dev.vfyjxf.cumulus.kernel.structure.BayView;
import dev.vfyjxf.cumulus.kernel.structure.Expectation;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationContext;
import dev.vfyjxf.cumulus.kernel.structure.ExpectationMeta;
import dev.vfyjxf.cumulus.kernel.structure.FocusContext;
import dev.vfyjxf.cumulus.kernel.structure.ReadScope;
import dev.vfyjxf.cumulus.kernel.structure.Reply;
import dev.vfyjxf.cumulus.kernel.world.CellRead;
import dev.vfyjxf.cumulus.kernel.world.OccupantSnapshot;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.Trait;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import dev.vfyjxf.cumulus.kernel.world.WorldView;
import dev.vfyjxf.cumulus.mc.trait.BlockTraitContext;
import dev.vfyjxf.cumulus.mc.trait.EntityTraitContext;
import dev.vfyjxf.cumulus.mc.trait.TraitCaptureContext;
import dev.vfyjxf.cumulus.mc.trait.TraitHolder;
import dev.vfyjxf.cumulus.mc.trait.TraitHolders;
import dev.vfyjxf.cumulus.mc.trait.TraitLookup;
import dev.vfyjxf.cumulus.mc.trait.TraitProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The HatchExpectation example of 04-api.md §4, transcribed and driven through the real trait pipeline. */
@SuppressWarnings("NullAway")
class AddonExampleTest {

    private static final SnapshotId snapshot = new SnapshotId(World.of(id("world")), 1);
    private static final LatticePoint cell = LatticePoint.origin;

    private static NamespacedId id(String path) {
        return NamespacedId.of("addon_example", path);
    }

    /** The example expectation, copied from 04-api.md §4 with only the id factory swapped. */
    private static final class HatchExpectation implements Expectation<OccupantSnapshot> {

        static final NamespacedId id = id("hatch");
        static final TraitKey<IItemHandler> inventory = new TraitKey<>(id("inventory"), IItemHandler.class);

        @Override
        public Class<OccupantSnapshot> subject() {
            return OccupantSnapshot.class;
        }

        @Override
        public ExpectationMeta meta() {
            return ExpectationMeta.of(id, 80, Codes.expected.code());
        }

        @Override
        public ReadScope reads() {
            return ReadScope.none().withTraits(Set.of(inventory.id()));
        }

        @Override
        public Reply test(ExpectationContext<OccupantSnapshot> ctx) {
            OccupantSnapshot occupant = ctx.subject();
            return switch (occupant.slot(inventory.id())) {
                case TraitSlot.Present present -> Reply.match();
                case TraitSlot.Absent absent -> Reply.mismatch(
                    Finding.builder(Codes.expected).phase(ctx.phase()).message("expected an inventory-bearing hatch")
                            .label(Label.primary(ctx.locus(), "expected an inventory")).build()
                );
                case TraitSlot.Refused refused -> Reply.unreadable(
                    Finding.builder(Codes.unreadableRead).phase(ctx.phase())
                            .message("the inventory trait is unreadable")
                            .label(Label.primary(ctx.locus(), "the inventory could not be read"))
                            .note("refusal: " + refused.refusal().status()).build()
                );
                case null -> Reply.unreadable(
                    Finding.builder(Codes.unreadableRead).phase(ctx.phase())
                            .message("the inventory trait was not captured")
                            .label(Label.primary(ctx.locus(), "the inventory was not asked for")).build()
                );
            };
        }
    }

    @Test
    void theReadsOverrideDeclaresTheTrait() {
        assertTrue(
            new HatchExpectation().reads().traits().contains(HatchExpectation.inventory.id()),
            "without the override the capture policy never asks for the inventory"
        );
    }

    @Test
    void aPresentInventoryMatches() {
        IItemHandler handler = items();
        TraitLookup lookup = TraitLookup.builder().add(presentProvider(HatchExpectation.inventory, handler)).build();

        Reply reply = reply(lookup, Set.of(HatchExpectation.inventory.id()));

        assertInstanceOf(Reply.Match.class, reply);
        OccupantSnapshot occupant = occupant(lookup, Set.of(HatchExpectation.inventory.id()));
        assertSame(handler, Objects.requireNonNull(occupant.find(HatchExpectation.inventory)).value());
    }

    @Test
    void anAbsentInventoryMismatchesWithTheExpectedCode() {
        TraitLookup lookup = TraitLookup.builder().add(entityOnlyProvider(HatchExpectation.inventory)).build();

        Reply.Mismatch mismatch = assertInstanceOf(
            Reply.Mismatch.class,
            reply(lookup, Set.of(HatchExpectation.inventory.id()))
        );

        Finding primary = Objects.requireNonNull(mismatch.findings().primary(), "primary");
        assertTrue(primary.code().equals(Codes.expected.code()), "the mismatch keeps its declared code");
        assertTrue(primary.message().contains("inventory-bearing hatch"), primary.message());
    }

    @Test
    void aRefusedInventoryIsUnreadableWithTheRefusalNote() {
        TraitLookup lookup = TraitLookup.builder().add(throwingProvider(HatchExpectation.inventory)).build();

        Reply.Unreadable unreadable = assertInstanceOf(
            Reply.Unreadable.class,
            reply(lookup, Set.of(HatchExpectation.inventory.id()))
        );

        Finding primary = Objects.requireNonNull(unreadable.findings().primary(), "primary");
        assertTrue(primary.code().equals(Codes.unreadableRead.code()), "the refusal is unreadable, not a mismatch");
        assertTrue(
            primary.notes().stream().anyMatch(note -> note.message().contains("refusal: failed")),
            primary.notes().toString()
        );
    }

    @Test
    void anUncapturedInventoryIsUnreadableBecauseItWasNotAskedFor() {
        TraitLookup lookup = TraitLookup.builder().add(presentProvider(HatchExpectation.inventory, items())).build();

        Reply.Unreadable unreadable = assertInstanceOf(
            Reply.Unreadable.class,
            reply(lookup, Set.of(OccupantTraits.energy.id()))
        );

        Finding primary = Objects.requireNonNull(unreadable.findings().primary(), "primary");
        assertTrue(primary.message().contains("was not captured"), primary.message());
        assertTrue(primary.labels().stream().anyMatch(label -> label.message().contains("not asked for")));
    }

    private static Reply reply(TraitLookup lookup, Set<NamespacedId> traits) {
        OccupantSnapshot subject = occupant(lookup, traits);
        return new HatchExpectation().test(ExpectationContext.of(new ExampleFocus(), subject, List.of(subject)));
    }

    private static OccupantSnapshot occupant(TraitLookup lookup, Set<NamespacedId> traits) {
        BlockPos pos = new BlockPos(3, 64, 5);
        BlockState state = Blocks.STONE.defaultBlockState();
        List<TraitSlot> captured = OccupantTraits
                .block(policy(traits), lookup, TraitCaptureContext.empty(), null, pos, state, null);
        return OccupantDescriptors.block(snapshot, 0, cell, state, captured);
    }

    private static SnapshotPolicy policy(Set<NamespacedId> traits) {
        return new SnapshotPolicy(1024, 0, Set.of(), traits, List.of());
    }

    private static TraitProvider<IItemHandler, BlockTraitContext> presentProvider(
        TraitKey<IItemHandler> key,
        IItemHandler handler
    ) {
        return new TraitProvider<>() {
            @Override
            public TraitKey<IItemHandler> key() {
                return key;
            }

            @Override
            public TraitHolder<BlockTraitContext> holder() {
                return TraitHolders.block;
            }

            @Override
            public TraitSlot get(BlockTraitContext actual, TraitCaptureContext context) {
                return new TraitSlot.Present(Trait.of(key, handler));
            }
        };
    }

    private static TraitProvider<IItemHandler, EntityTraitContext> entityOnlyProvider(TraitKey<IItemHandler> key) {
        return new TraitProvider<>() {
            @Override
            public TraitKey<IItemHandler> key() {
                return key;
            }

            @Override
            public TraitHolder<EntityTraitContext> holder() {
                return TraitHolders.entity;
            }

            @Override
            public TraitSlot get(EntityTraitContext actual, TraitCaptureContext context) {
                throw new AssertionError("the block occupant never calls the entity holder");
            }
        };
    }

    private static TraitProvider<IItemHandler, BlockTraitContext> throwingProvider(TraitKey<IItemHandler> key) {
        return new TraitProvider<>() {
            @Override
            public TraitKey<IItemHandler> key() {
                return key;
            }

            @Override
            public TraitHolder<BlockTraitContext> holder() {
                return TraitHolders.block;
            }

            @Override
            public TraitSlot get(BlockTraitContext actual, TraitCaptureContext context) {
                throw new IllegalStateException("the optional mod is absent");
            }
        };
    }

    private static IItemHandler items() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 3;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return true;
            }
        };
    }

    private record ExampleFocus() implements FocusContext {

        @Override
        public BayView bay() {
            return new BayView(id("hatch_bay"), CuboidSet.of(Cuboid.of(cell)), view(), siting());
        }

        @Override
        public WorldView view() {
            throw new AssertionError("the example expectation reads no cell");
        }

        @Override
        public Siting siting() {
            return Siting.of(LatticePoint.origin, Orientation.identity);
        }

        @Override
        public InstanceId instance() {
            return new InstanceId(snapshot.world(), 1);
        }

        @Override
        public CuboidSet cells() {
            return CuboidSet.of(Cuboid.of(cell));
        }

        @Override
        public Phase phase() {
            return Phase.check;
        }

        @Override
        public Locus locus() {
            return Locus.world(cells());
        }

        @Override
        public ContextView context() {
            throw new AssertionError("the example expectation reads no context");
        }

        @Override
        public Budget remaining() {
            return Budget.unbounded();
        }

        @Override
        public CancelToken cancel() {
            return CancelToken.never();
        }

        @Override
        public CellRead read(LatticePoint point) {
            throw new AssertionError("the example expectation reads no cell");
        }

        @Override
        public List<ClaimRow> claimsAt(LatticePoint point) {
            return List.of();
        }
    }
}
