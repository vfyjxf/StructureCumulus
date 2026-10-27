package dev.vfyjxf.cumulus.mc;

import dev.vfyjxf.cumulus.kernel.core.NamespacedId;
import dev.vfyjxf.cumulus.kernel.world.ReadStatus;
import dev.vfyjxf.cumulus.kernel.world.SnapshotPolicy;
import dev.vfyjxf.cumulus.kernel.world.Trait;
import dev.vfyjxf.cumulus.kernel.world.TraitKey;
import dev.vfyjxf.cumulus.kernel.world.TraitSlot;
import dev.vfyjxf.cumulus.mc.trait.BlockTraitContext;
import dev.vfyjxf.cumulus.mc.trait.EntityTraitContext;
import dev.vfyjxf.cumulus.mc.trait.StandardTraitProviders;
import dev.vfyjxf.cumulus.mc.trait.TraitCaptureContext;
import dev.vfyjxf.cumulus.mc.trait.TraitLookup;
import dev.vfyjxf.cumulus.mc.trait.TraitProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("NullAway")
class TraitLookupTest {

    private static final NamespacedId missing = NamespacedId.of("test", "missing");

    @Test
    void theStandardLookupHasFiveProviders() {
        TraitLookup lookup = TraitLookup.builtin();
        for (TraitKey<?> key : List.of(
            OccupantTraits.inventory,
            OccupantTraits.energy,
            OccupantTraits.fluid,
            OccupantTraits.blockEntityNbt,
            OccupantTraits.entityNbt
        )) {
            TraitProvider<?> provider = lookup.provider(key.id());
            assertEquals(key, provider == null ? null : provider.key(), "provider for " + key.id());
        }
        assertNull(lookup.provider(missing), "an unknown id has no provider");
    }

    @Test
    void anUnknownIdIsDenied() {
        assertEquals(ReadStatus.denied, refused(blockSlot(TraitLookup.builtin(), missing).get(0)).refusal().status());
    }

    @Test
    void duplicateProviderIdsAreRejected() {
        TraitLookup.Builder builder = TraitLookup.builder().add(StandardTraitProviders.inventory);
        assertThrows(IllegalArgumentException.class, () -> builder.add(StandardTraitProviders.inventory));
    }

    @Test
    void aCustomProviderReadsBlockAndEntity() {
        TraitKey<Integer> custom = new TraitKey<>(NamespacedId.of("test", "custom"), Integer.class);
        TraitProvider<Integer> provider = new TraitProvider<>() {
            @Override
            public TraitKey<Integer> key() {
                return custom;
            }

            @Override
            public TraitSlot readBlock(BlockTraitContext context) {
                return present(custom, 7);
            }

            @Override
            public TraitSlot readEntity(EntityTraitContext context) {
                return present(custom, 8);
            }
        };
        TraitLookup lookup = TraitLookup.builder().add(provider).build();
        TraitSlot block = OccupantTraits.block(
            policy(Set.of(custom.id())),
            lookup,
            TraitCaptureContext.empty(),
            null,
            BlockPos.ZERO,
            Blocks.STONE.defaultBlockState(),
            null
        ).get(0);
        assertEquals(7, ((Trait<?>) assertInstanceOf(TraitSlot.Present.class, block).trait()).value());
        assertEquals(
            8,
            ((Trait<?>) assertInstanceOf(TraitSlot.Present.class, provider.readEntity(null)).trait()).value()
        );
    }

    @Test
    void aProviderDefaultsToAbsent() {
        TraitKey<Integer> custom = new TraitKey<>(NamespacedId.of("test", "default"), Integer.class);
        TraitProvider<Integer> provider = () -> custom;
        TraitSlot block = OccupantTraits.block(
            policy(Set.of(custom.id())),
            TraitLookup.builder().add(provider).build(),
            TraitCaptureContext.empty(),
            null,
            BlockPos.ZERO,
            Blocks.STONE.defaultBlockState(),
            null
        ).get(0);
        assertInstanceOf(TraitSlot.Absent.class, block);
        assertInstanceOf(TraitSlot.Absent.class, provider.readBlock(null));
        assertInstanceOf(TraitSlot.Absent.class, provider.readEntity(null));
    }

    @Test
    void anExceptionReturnsAFailedRefusal() {
        TraitKey<Integer> custom = new TraitKey<>(NamespacedId.of("test", "boom"), Integer.class);
        TraitProvider<Integer> provider = new TraitProvider<>() {
            @Override
            public TraitKey<Integer> key() {
                return custom;
            }

            @Override
            public TraitSlot readBlock(BlockTraitContext context) {
                throw new IllegalStateException("boom");
            }
        };
        TraitSlot slot = OccupantTraits.block(
            policy(Set.of(custom.id())),
            TraitLookup.builder().add(provider).build(),
            TraitCaptureContext.empty(),
            null,
            BlockPos.ZERO,
            Blocks.STONE.defaultBlockState(),
            null
        ).get(0);
        assertEquals(ReadStatus.failed, refused(slot).refusal().status());
    }

    @Test
    void aNullOrWrongIdSlotReturnsAFailedRefusal() {
        TraitKey<Integer> custom = new TraitKey<>(NamespacedId.of("test", "wrong"), Integer.class);
        TraitProvider<Integer> provider = new TraitProvider<>() {
            @Override
            public TraitKey<Integer> key() {
                return custom;
            }

            @Override
            public TraitSlot readBlock(BlockTraitContext context) {
                return null;
            }
        };
        TraitSlot slot = OccupantTraits.block(
            policy(Set.of(custom.id())),
            TraitLookup.builder().add(provider).build(),
            TraitCaptureContext.empty(),
            null,
            BlockPos.ZERO,
            Blocks.STONE.defaultBlockState(),
            null
        ).get(0);
        assertEquals(ReadStatus.failed, refused(slot).refusal().status());

        TraitKey<Integer> wrong = new TraitKey<>(NamespacedId.of("test", "other"), Integer.class);
        TraitProvider<Integer> wrongId = new TraitProvider<>() {
            @Override
            public TraitKey<Integer> key() {
                return custom;
            }

            @Override
            public TraitSlot readBlock(BlockTraitContext context) {
                return new TraitSlot.Present(Trait.of(wrong, 1));
            }
        };
        TraitSlot wrongSlot = OccupantTraits.block(
            policy(Set.of(custom.id())),
            TraitLookup.builder().add(wrongId).build(),
            TraitCaptureContext.empty(),
            null,
            BlockPos.ZERO,
            Blocks.STONE.defaultBlockState(),
            null
        ).get(0);
        assertEquals(ReadStatus.failed, refused(wrongSlot).refusal().status());
    }

    @Test
    void aMissingBlockEntityIsNotCaptured() {
        TraitSlot slot = OccupantTraits.block(
            policy(Set.of(OccupantTraits.inventory.id())),
            TraitLookup.builtin(),
            TraitCaptureContext.empty(),
            null,
            BlockPos.ZERO,
            Blocks.CHEST.defaultBlockState(),
            null
        ).get(0);
        assertEquals(ReadStatus.notCaptured, refused(slot).refusal().status());
    }

    private static List<TraitSlot> blockSlot(TraitLookup lookup, NamespacedId id) {
        return OccupantTraits.block(
            policy(Set.of(id)),
            lookup,
            TraitCaptureContext.empty(),
            null,
            BlockPos.ZERO,
            Blocks.STONE.defaultBlockState(),
            null
        );
    }

    private static SnapshotPolicy policy(Set<NamespacedId> traits) {
        return new SnapshotPolicy(1024, 0, Set.of(), traits, List.of());
    }

    private static TraitSlot.Refused refused(TraitSlot slot) {
        return assertInstanceOf(TraitSlot.Refused.class, slot);
    }

    private static TraitSlot present(TraitKey<Integer> key, int value) {
        return new TraitSlot.Present(Trait.of(key, value));
    }
}
