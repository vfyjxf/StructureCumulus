package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.Trait;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import dev.vfyjxf.cumulus.mc.trait.BlockTraitContext;
import dev.vfyjxf.cumulus.mc.trait.BlockTraitProviders;
import dev.vfyjxf.cumulus.mc.trait.BuiltinTraitPlugin;
import dev.vfyjxf.cumulus.mc.trait.EntityTraitContext;
import dev.vfyjxf.cumulus.mc.trait.TraitCaptureContext;
import dev.vfyjxf.cumulus.mc.trait.TraitHolder;
import dev.vfyjxf.cumulus.mc.trait.TraitHolders;
import dev.vfyjxf.cumulus.mc.trait.TraitLookup;
import dev.vfyjxf.cumulus.mc.trait.TraitProvider;
import dev.vfyjxf.cumulus.mc.trait.TraitRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("NullAway")
class TraitLookupTest {

    private static final NamespacedId missing = NamespacedId.of("test", "missing");
    private static final TraitKey<Integer> customKey = new TraitKey<>(NamespacedId.of("test", "custom"), Integer.class);
    private static final TraitCaptureContext capture = TraitCaptureContext.empty();

    @Test
    void theStandardLookupHasThreeTraitsOnBothHolders() {
        TraitLookup lookup = TraitLookup.builtin();
        for (TraitKey<?> key : List.of(OccupantTraits.inventory, OccupantTraits.energy, OccupantTraits.fluid)) {
            assertProvider(lookup, key, TraitHolders.block);
            assertProvider(lookup, key, TraitHolders.entity);
            assertTrue(lookup.knows(key.id()), "the standard lookup knows " + key.id());
        }
        assertFalse(lookup.knows(NamespacedId.of("structurecumulus", "block_entity_nbt")), "no block entity NBT trait");
        assertFalse(lookup.knows(NamespacedId.of("structurecumulus", "entity_nbt")), "no entity NBT trait");
        assertNull(lookup.provider(missing, TraitHolders.block), "an unknown id has no provider");
    }

    @Test
    void theBuiltinPluginRegistersTheSameProvidersAsTheBuiltinLookup() {
        TraitLookup.Builder builder = TraitLookup.builder();
        TraitRegister register = new TraitRegister() {
            @Override
            public <T, H> void register(TraitProvider<T, H> provider) {
                builder.add(provider);
            }
        };
        new BuiltinTraitPlugin().registerTraits(register);
        TraitLookup loaded = builder.build();

        for (TraitKey<?> key : List.of(OccupantTraits.inventory, OccupantTraits.energy, OccupantTraits.fluid)) {
            assertSame(
                TraitLookup.builtin().provider(key, TraitHolders.block),
                loaded.provider(key, TraitHolders.block)
            );
            assertSame(
                TraitLookup.builtin().provider(key, TraitHolders.entity),
                loaded.provider(key, TraitHolders.entity)
            );
        }
    }

    @Test
    void anUnknownIdIsDenied() {
        assertEquals(ReadStatus.denied, refused(blockSlot(TraitLookup.builtin(), missing).get(0)).refusal().status());
        assertEquals(
            ReadStatus.denied,
            refused(entitySlot(TraitLookup.builtin(), missing, marker()).get(0)).refusal().status()
        );
    }

    @Test
    void duplicateTraitHolderPairsAreRejected() {
        TraitLookup.Builder builder = TraitLookup.builder().add(BlockTraitProviders.inventory);
        assertThrows(IllegalArgumentException.class, () -> builder.add(BlockTraitProviders.inventory));
    }

    @Test
    void theSameTraitCanHaveAProviderPerHolder() {
        TraitProvider<Integer, BlockTraitContext> blockProvider = provider(TraitHolders.block, 7);
        TraitProvider<Integer, EntityTraitContext> entityProvider = provider(TraitHolders.entity, 8);
        TraitLookup lookup = TraitLookup.builder().add(blockProvider).add(entityProvider).build();

        assertSame(blockProvider, lookup.provider(customKey, TraitHolders.block));
        assertSame(entityProvider, lookup.provider(customKey, TraitHolders.entity));
        assertEquals(7, value(blockSlot(lookup, customKey.id()).get(0)));
        assertEquals(8, value(entitySlot(lookup, customKey.id(), marker()).get(0)));
    }

    @Test
    void aKnownTraitWithoutAProviderForTheHolderIsAbsent() {
        TraitLookup lookup = TraitLookup.builder().add(provider(TraitHolders.block, 7)).build();

        assertTrue(lookup.knows(customKey.id()), "the trait is known");
        assertEquals(7, value(blockSlot(lookup, customKey.id()).get(0)));
        assertInstanceOf(TraitSlot.Absent.class, entitySlot(lookup, customKey.id(), marker()).get(0));
        assertEquals(ReadStatus.denied, refused(blockSlot(lookup, missing).get(0)).refusal().status());
    }

    @Test
    void aProviderReceivesTheActualHolderAndTheCapture() {
        BlockPos pos = new BlockPos(3, 4, 5);
        BlockState state = Blocks.DISPENSER.defaultBlockState();
        BlockEntity blockEntity = BlockEntityType.CHEST.create(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        Marker marker = marker();
        AtomicReference<BlockTraitContext> seenBlock = new AtomicReference<>();
        AtomicReference<TraitCaptureContext> seenBlockCapture = new AtomicReference<>();
        AtomicReference<EntityTraitContext> seenEntity = new AtomicReference<>();
        AtomicReference<TraitCaptureContext> seenEntityCapture = new AtomicReference<>();
        TraitLookup lookup = TraitLookup.builder().add(new TraitProvider<Integer, BlockTraitContext>() {
            @Override
            public TraitKey<Integer> key() {
                return customKey;
            }

            @Override
            public TraitHolder<BlockTraitContext> holder() {
                return TraitHolders.block;
            }

            @Override
            public TraitSlot get(BlockTraitContext actual, TraitCaptureContext context) {
                seenBlock.set(actual);
                seenBlockCapture.set(context);
                return present(customKey, 7);
            }
        }).add(new TraitProvider<Integer, EntityTraitContext>() {
            @Override
            public TraitKey<Integer> key() {
                return customKey;
            }

            @Override
            public TraitHolder<EntityTraitContext> holder() {
                return TraitHolders.entity;
            }

            @Override
            public TraitSlot get(EntityTraitContext actual, TraitCaptureContext context) {
                seenEntity.set(actual);
                seenEntityCapture.set(context);
                return present(customKey, 8);
            }
        }).build();

        blockSlot(lookup, customKey.id(), null, pos, state, blockEntity);
        BlockTraitContext blockSeen = Objects.requireNonNull(seenBlock.get(), "the block provider was not called");
        assertEquals(pos, blockSeen.pos());
        assertEquals(state, blockSeen.state());
        assertSame(blockEntity, blockSeen.blockEntity());
        assertSame(capture, seenBlockCapture.get());

        entitySlot(lookup, customKey.id(), marker);
        EntityTraitContext entitySeen = Objects.requireNonNull(seenEntity.get(), "the entity provider was not called");
        assertSame(marker, entitySeen.entity());
        assertSame(capture, seenEntityCapture.get());
    }

    @Test
    void anExceptionReturnsAFailedRefusal() {
        TraitKey<Integer> boom = new TraitKey<>(NamespacedId.of("test", "boom"), Integer.class);
        TraitProvider<Integer, BlockTraitContext> provider = new TraitProvider<>() {
            @Override
            public TraitKey<Integer> key() {
                return boom;
            }

            @Override
            public TraitHolder<BlockTraitContext> holder() {
                return TraitHolders.block;
            }

            @Override
            public TraitSlot get(BlockTraitContext actual, TraitCaptureContext context) {
                throw new IllegalStateException("boom");
            }
        };
        TraitSlot slot = blockSlot(TraitLookup.builder().add(provider).build(), boom.id()).get(0);
        assertEquals(ReadStatus.failed, refused(slot).refusal().status());
    }

    @Test
    void aLinkageErrorReturnsAFailedRefusal() {
        TraitKey<Integer> absent = new TraitKey<>(NamespacedId.of("test", "absent_mod"), Integer.class);
        TraitProvider<Integer, BlockTraitContext> provider = new TraitProvider<>() {
            @Override
            public TraitKey<Integer> key() {
                return absent;
            }

            @Override
            public TraitHolder<BlockTraitContext> holder() {
                return TraitHolders.block;
            }

            @Override
            public TraitSlot get(BlockTraitContext actual, TraitCaptureContext context) {
                throw new NoClassDefFoundError("the optional mod is absent");
            }
        };
        TraitSlot slot = blockSlot(TraitLookup.builder().add(provider).build(), absent.id()).get(0);
        assertEquals(ReadStatus.failed, refused(slot).refusal().status());
    }

    @Test
    void aNullOrWrongIdSlotReturnsAFailedRefusal() {
        TraitKey<Integer> wrong = new TraitKey<>(NamespacedId.of("test", "other"), Integer.class);
        TraitProvider<Integer, BlockTraitContext> nullSlot = new TraitProvider<>() {
            @Override
            public TraitKey<Integer> key() {
                return customKey;
            }

            @Override
            public TraitHolder<BlockTraitContext> holder() {
                return TraitHolders.block;
            }

            @Override
            public TraitSlot get(BlockTraitContext actual, TraitCaptureContext context) {
                return null;
            }
        };
        TraitSlot nullSlotResult = blockSlot(TraitLookup.builder().add(nullSlot).build(), customKey.id()).get(0);
        assertEquals(ReadStatus.failed, refused(nullSlotResult).refusal().status());

        TraitProvider<Integer, BlockTraitContext> wrongId = new TraitProvider<>() {
            @Override
            public TraitKey<Integer> key() {
                return customKey;
            }

            @Override
            public TraitHolder<BlockTraitContext> holder() {
                return TraitHolders.block;
            }

            @Override
            public TraitSlot get(BlockTraitContext actual, TraitCaptureContext context) {
                return new TraitSlot.Present(Trait.of(wrong, 1));
            }
        };
        TraitSlot wrongSlot = blockSlot(TraitLookup.builder().add(wrongId).build(), customKey.id()).get(0);
        assertEquals(ReadStatus.failed, refused(wrongSlot).refusal().status());
    }

    @Test
    void aMissingBlockEntityIsNotCaptured() {
        TraitLookup lookup = TraitLookup.builder().add(BlockTraitProviders.inventory).build();
        TraitSlot slot = blockSlot(
            lookup,
            OccupantTraits.inventory.id(),
            null,
            BlockPos.ZERO,
            Blocks.CHEST.defaultBlockState(),
            null
        ).get(0);
        assertEquals(ReadStatus.notCaptured, refused(slot).refusal().status());

        TraitLookup entityOnly = TraitLookup.builder().add(provider(TraitHolders.entity, 8)).build();
        TraitSlot absent = blockSlot(
            entityOnly,
            customKey.id(),
            null,
            BlockPos.ZERO,
            Blocks.CHEST.defaultBlockState(),
            null
        ).get(0);
        assertInstanceOf(TraitSlot.Absent.class, absent);
    }

    @Test
    void holdersAreTypedIdsOrderedById() {
        assertEquals(NamespacedId.of("structurecumulus", "block"), TraitHolders.block.id());
        assertEquals(NamespacedId.of("structurecumulus", "entity"), TraitHolders.entity.id());
        assertEquals(TraitHolder.of(TraitHolders.block.id()), TraitHolders.block);
        assertTrue(TraitHolders.block.compareTo(TraitHolders.entity) < 0, "block sorts before entity");
        assertTrue(TraitHolders.entity.compareTo(TraitHolders.block) > 0, "entity sorts after block");
    }

    private static <H> void assertProvider(TraitLookup lookup, TraitKey<?> key, TraitHolder<H> holder) {
        TraitProvider<?, H> provider = lookup.provider(key.id(), holder);
        assertNotNull(provider, "provider for " + key.id() + " on " + holder.id());
        assertEquals(key, provider.key(), "the provider keeps the key");
        assertEquals(holder, provider.holder(), "the provider keeps the holder");
        assertSame(provider, lookup.provider(key, holder), "the typed lookup resolves the same provider");
    }

    private static List<TraitSlot> blockSlot(TraitLookup lookup, NamespacedId id) {
        return blockSlot(lookup, id, null, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null);
    }

    private static List<TraitSlot> blockSlot(
        TraitLookup lookup,
        NamespacedId id,
        @Nullable ServerLevel level,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity
    ) {
        return OccupantTraits.block(policy(Set.of(id)), lookup, capture, level, pos, state, blockEntity);
    }

    private static List<TraitSlot> entitySlot(TraitLookup lookup, NamespacedId id, Entity entity) {
        return OccupantTraits.entity(policy(Set.of(id)), lookup, capture, entity);
    }

    private static SnapshotPolicy policy(Set<NamespacedId> traits) {
        return new SnapshotPolicy(1024, 0, Set.of(), traits, List.of());
    }

    private static <H> TraitProvider<Integer, H> provider(TraitHolder<H> holder, int value) {
        return new TraitProvider<>() {
            @Override
            public TraitKey<Integer> key() {
                return customKey;
            }

            @Override
            public TraitHolder<H> holder() {
                return holder;
            }

            @Override
            public TraitSlot get(H actual, TraitCaptureContext context) {
                return present(customKey, value);
            }
        };
    }

    private static Marker marker() {
        return new Marker(EntityType.MARKER, null);
    }

    private static int value(TraitSlot slot) {
        TraitSlot.Present present = assertInstanceOf(TraitSlot.Present.class, slot);
        return (Integer) present.trait().value();
    }

    private static TraitSlot.Refused refused(TraitSlot slot) {
        return assertInstanceOf(TraitSlot.Refused.class, slot);
    }

    private static TraitSlot present(TraitKey<Integer> key, int value) {
        return new TraitSlot.Present(Trait.of(key, value));
    }
}
